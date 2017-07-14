package dag;

import edu.stanford.nlp.ling.TaggedWord;
import edu.stanford.nlp.ling.Word;
import edu.stanford.nlp.tagger.maxent.MaxentTagger;
import main.PathList;
import misc.Debugger;
import misc.PosHelper;
import misc.StaticHelper;
import misc.WordLists;
import gen.GoldTransitions;
import gen.PartialTransitionFunction;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Class to represent an Abstract Meaning Representation (AMR) graph. The actual graph is represented by an instance of {@link DirectedGraph}.
 */
public class Amr {

    // the actual AMR graph
    public DirectedGraph dag;
    // the reference sentence or null if none is given
    public String[] sentence;
    // the part of speech tags of the reference sentence
    public String[] pos;
    // the dependency tree of the reference sentence
    public DependencyTree dependencyTree;

    // an alignment linking edges of the AMR graph to words of the sentence
    public Map<Edge,Set<Integer>> alignment;
    // an additional alignment that may be loaded from another source
    public Map<Edge, Set<Integer>> backupAlignment;
    // a type alignment linking EdgeTypePairs to words of the sentence
    public Map<EdgeTypePair, Set<Integer>> typeAlignment;
    // the span of each instance edge as defined in the thesis
    public Map<Edge,Span> span;

    // the best partial transition function found for this AMR graph or null if none is given
    public PartialTransitionFunction partialTransitionFunction;

    public static final String LEFT_BRACKET_INDICATOR = "-lrb-";
    public static final String RIGHT_BRACKET_INDICATOR = "-rrb-";
    public static final String AMR_UNKNOWN_INSTANCE = "amr-unknown";

    private static boolean SET_UP = false;

    // static maps and sets required for the AMR preparation process
    private static Map<String,String> deverbalizationMap;
    private static Set<String> deverbalizationConcepts;
    private static Map<String,String> bestPosTags;

    /**
     * Static method to load some maps required for the preparation of AMR graphs as done by {@link Amr#prepare(List, MaxentTagger, boolean)}.
     * This method must be called before calling any of {@link Amr#prepare(List, MaxentTagger, boolean)},
     * {@link Amr#prepareForTraining(MaxentTagger)}, {@link Amr#prepareForTesting(MaxentTagger)}.
     */
    public static void setUp() throws IOException {
        if(SET_UP)  {
            Debugger.printlnErr("setUp() has already been called.");
            return;
        }
        deverbalizationMap = new HashMap<>();
        deverbalizationConcepts = new HashSet<>();
        List<String> lines = Files.readAllLines(Paths.get(PathList.VERBALIZATION_PATH));
        for(String line: lines) {
            String[] comps = line.split("\t",2);
            deverbalizationConcepts.add(comps[1].split("\t")[0]);
            deverbalizationMap.put(comps[1], comps[0]);
        }
        bestPosTags = StaticHelper.mapFromFile(PathList.BESTPOSTAGS_PATH);
        Debugger.println("done preparing AMR statics");
        SET_UP = true;
    }

    /**
     * Creates an empty AMR graph
     */
    public Amr() {
        dag = new DirectedGraph();
        alignment = new HashMap<>();
        typeAlignment = new HashMap<>();
    }

    /**
     * Loads the POS tags of this AMR's reference sentence from a tab-separated string
     * @param posString the tab-separated string of POS tags
     */
    public void posFromString(String posString) {
        if(posString.equals(StaticHelper.POS_TAGGING_ERROR)) return;
        pos = posString.split("\t");
    }

    /**
     * Prepares an AMR graph for training by doing the following:
     * <ol>
     *     <li>Assign POS tags to the reference realization and the vertices of the AMR graph</li>
     *     <li>Refine the given alignments through several handwritten rules</li>
     *     <li>Convert the AMR graph to a tree by applying DELETE-REENTRANCE transitions (see {@link DirectedGraph#convertToTree()})</li>
     *     <li>Merge named entities into a single entity (see {@link Amr#mergeNameEntities(Vertex)})</li>
     *     <li>Merge verbalized concepts into a single entity (see {@link Amr#deverbalize(Vertex)})</li>
     *     <li>Remove :wiki edges from the AMR graph (see {@link Vertex#removeWikiTags()})</li>
     *     <li>Annotate each vertex with its mode (see {@link Amr#annotateModes(Vertex)})</li>
     *     <li>Calculate the span of each vertex contained within the AMR graph (see {@link Amr#calculateSpan()})</li>
     * </ol>
     * @param tagger a maximum entropy POS tagger
     */
    public void prepareForTraining(MaxentTagger tagger) {

        if(!SET_UP) {
            throw new AssertionError("setUp() must be called before any prepare() method");
        }

        // if there is a backup alignment for some previously unaligned edge, adopt the backup alignment
        for(Edge e: backupAlignment.keySet()) {
            if(!alignment.containsKey(e)) {
                alignment.put(e , backupAlignment.get(e));
            }
        }
        backupAlignment.keySet().removeAll(alignment.keySet());

        // convert the graph to a tree by applying DELETE-REENTRANCE transitions
        dag.convertToTree();

        // remove alignments for forms of the verbs "have" and "be"
        for(Vertex v: dag) {
            if(alignment.containsKey(v.getInstanceEdge())) {
                Set<Integer> vAlign = alignment.get(v.getInstanceEdge());
                Set<Integer> removables = new HashSet<>();
                for(int i: vAlign) {
                    if(WordLists.haveForms.contains(sentence[i].toLowerCase())) {
                        if(!WordLists.haveInstances.contains(v.getInstance())) {
                            removables.add(i);
                        }
                    }
                    else if(WordLists.beForms.contains(sentence[i].toLowerCase())) {
                        removables.add(i);
                    }
                }
                vAlign.removeAll(removables);
                if(vAlign.isEmpty()) {
                    alignment.remove(v.getInstanceEdge());
                }
            }
        }

        // add POS tags to all words
        List<Word> words = new ArrayList<>();
        for(String word: sentence) {
            words.add(new Word(word));
        }

        if(pos == null) {
            pos = new String[sentence.length];
            List<TaggedWord> taggedWords = tagger.apply(words);
            for (int i = 0; i < taggedWords.size(); i++) {
                pos[i] = taggedWords.get(i).tag();
            }
        }

        // merge name entities (e.g. name :op1 United  :op2 Nations -> United Nations) and verbalized words (e.g. person :ARG1-of develop -> developer)
        for(Vertex v: dag.getVertices()) {
            mergeNameEntities(v);
            deverbalize(v);
        }

        // add POS tags to all vertices
        for (Vertex v : dag) {
            if (!v.isLink()) {
                if(v.getPos() == null) {
                    if (!v.isPropbankEntry()) {
                        if(bestPosTags.containsKey(v.getInstance())) {
                            v.setSimplifiedPos(bestPosTags.get(v.getInstance()));
                        }
                        else {
                            v.setSimplifiedPos(tagger);
                        }
                    } else {
                        getRealizationPOS(v, tagger);
                    }
                }
            }
        }

        for(Vertex v: dag.getVertices()) {
            if(v.isLink()) {
                v.setSimplifiedPos(v.annotation.original.getPos());
            }
            v.removeWikiTags();

            // mode management
            annotateModes(v);
        }

        // remove all alignments for edges that are defined as NO_ALIGNMENT_CONCEPTS or that are no instance edges
        for(Vertex v: dag) {
            for(Edge e: v.getOutgoingEdges()) {
                if(!e.isInstanceEdge()) {
                    alignment.remove(e);
                }
                else if (WordLists.NO_ALIGNMENT_CONCEPTS.contains(e.getLabel())) {
                    alignment.remove(e);
                }
                else if(alignment.containsKey(e)) {
                    for(int align: alignment.get(e)) {
                        if(WordLists.NO_ALIGNMENT_WORDS.contains(sentence[align].toLowerCase())) {
                            alignment.remove(e);
                        }
                    }
                }
            }

            String clearedInstance = v.getClearedInstance();

            // align all words of the form "at_-_least" with vertices of the form "at-least"
            if(clearedInstance.contains("-") && clearedInstance.length()>1) {

                String comps[] = clearedInstance.split("-");
                List<String> compList = new ArrayList<>();
                List<String> compListWithoutMinus = Arrays.asList(comps);
                for(String comp : comps) {
                    compList.add(comp);
                    compList.add("-");
                }

                compList.remove(compList.size()-1);

                List<String> sentenceAsWord = Arrays.stream(sentence).map(s -> s.toLowerCase()).collect(Collectors.toList());
                for(int i=0; i < sentenceAsWord.size() - compList.size(); i++) {
                    if(sentenceAsWord.subList(i, i+compList.size()).equals(compList)) {
                        setAlignment(v.getInstanceEdge(), i, i + compList.size());
                    }
                }
                for(int i=0; i < sentenceAsWord.size() - compListWithoutMinus.size(); i++) {
                    if(sentenceAsWord.subList(i, i+compListWithoutMinus.size()).equals(compListWithoutMinus)) {
                        setAlignment(v.getInstanceEdge(), i, i + compListWithoutMinus.size());
                    }
                }
            }
        }

        Set<Edge> removableEdges = new HashSet<>();
        Map<Integer, EdgeTypePair> reverseTypeAlignment = new HashMap<>();

        // remove alignments to several unalignable words such as punctuation and brackets
        for(Edge e: alignment.keySet()) {

            Set<Integer> align = alignment.get(e);
            Set<Integer> removables = new HashSet<>();
            for(int a: align) {
                if(sentence[a].matches("[^a-zA-Z\\d$%]*") || sentence[a].toLowerCase().equals(LEFT_BRACKET_INDICATOR) || sentence[a].toLowerCase().equals(RIGHT_BRACKET_INDICATOR) || WordLists.articles.contains(sentence[a].toLowerCase())) {
                    removables.add(a);
                }
                else if(WordLists.beforeInsertableWords.contains(sentence[a].toLowerCase()) || WordLists.afterInsertableWords.contains(sentence[a].toLowerCase())) {
                    if(!e.isInstanceEdge()) {
                        removables.add(a);
                    }
                }
            }
            align.removeAll(removables);
            if(align.isEmpty()) {
                removableEdges.add(e);
            }

        }

        alignment.keySet().removeAll(removableEdges);

        // get the reverse alignment
        Map<Integer,Set<Edge>> reverseAlignment = new HashMap<>();
        for(Edge e: alignment.keySet()) {
            for(Integer i: alignment.get(e)) {
                if(!reverseAlignment.containsKey(i)) {
                    reverseAlignment.put(i, new HashSet<>());
                }
                reverseAlignment.get(i).add(e);
            }
        }

        // get only unaligned words
        for(int i=0; i < sentence.length; i++) {
            if(reverseAlignment.containsKey(i)) continue;

            // align before_insertions
            if(WordLists.beforeInsertableWords.contains(sentence[i].toLowerCase())) {

                List<Vertex> parents = new ArrayList<>();

                // get parent backwards
                for(int j=i-1; j >= 0 && j >= i - 3; j--) {
                    if(reverseAlignment.containsKey(j)) {
                        Vertex parentCandidate = new ArrayList<>(reverseAlignment.get(j)).get(0).getFrom();
                        parents.add(parentCandidate);
                        if(parentCandidate.getIncomingEdges().size() > 0) {
                            parents.add(parentCandidate.getIncomingEdges().get(0).getFrom());
                        }
                        break;
                    }
                }

                parentIteration: for(Vertex parent: parents) {
                    // get child forwards
                    boolean foundChild = false;
                    for (int j = i + 1; j < sentence.length; j++) {
                        if(reverseAlignment.containsKey(j)) {
                            for(Edge e: reverseAlignment.get(j)) {
                                if(e.isInstanceEdge() && e.getFrom().getIncomingEdges().size() > 0) {

                                    List<Edge> relevantEdges = new ArrayList<>();
                                    relevantEdges.add(e.getFrom().getIncomingEdges().get(0));
                                    if(relevantEdges.get(0).getFrom().getIncomingEdges().size() > 0) {
                                        relevantEdges.add(relevantEdges.get(0).getFrom().getIncomingEdges().get(0));
                                    }

                                    for(Edge relevantEdge: relevantEdges) {
                                        if(WordLists.NO_ALIGNMENT_EDGES.contains(relevantEdge.getLabel())) continue;
                                        if(WordLists.INSERTION_CONSTRAINTS.containsKey(sentence[i].toLowerCase())) {
                                            if(!WordLists.INSERTION_CONSTRAINTS.get(sentence[i].toLowerCase()).contains(relevantEdge.getLabel())) continue;
                                        }
                                        if (parent.outgoingEdges.contains(relevantEdge)) {

                                            if (reverseTypeAlignment.containsKey(i)) {
                                                typeAlignment.remove(reverseTypeAlignment.get(i));
                                            }
                                            typeAlignment.put(new EdgeTypePair(relevantEdge, AlignmentType.BEFORE_INSERTION), Collections.singleton(i));
                                            break parentIteration;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // align articles
            else if(WordLists.articles.contains(sentence[i].toLowerCase())) {

                int correspondingNounIndex = -1;
                boolean foundNN = false;

                for(int j = i+1; j < sentence.length && j < i + 5; j++) {

                    if(PosHelper.mapPos(pos[j], true).equals("NN")) {
                        foundNN = true;
                        if(reverseAlignment.containsKey(j)) {
                            correspondingNounIndex = j;
                        }
                    }
                    else if(foundNN) {
                        break;
                    }
                }

                if(foundNN && correspondingNounIndex >= 0) {
                    for (Edge e : reverseAlignment.get(correspondingNounIndex)) {
                        if(e.isInstanceEdge()) {
                            AlignmentType t = AlignmentType.ARTICLE;
                            typeAlignment.put(new EdgeTypePair(e, t), Collections.singleton(i));
                        }
                    }
                }

            }

            // align forms of "be" and "have"
            else if(WordLists.beForms.contains(sentence[i].toLowerCase()) || WordLists.haveForms.contains(sentence[i].toLowerCase())) {

                boolean isBe = WordLists.beForms.contains(sentence[i].toLowerCase());

                boolean aligned = false;

                // check if a domain parent exists
                if(isBe) {
                    for (int j = i + 1; j < sentence.length && j < i + 4; j++) {
                        if (reverseAlignment.containsKey(j)) {
                            for (Edge e : reverseAlignment.get(j)) {
                                Vertex v = e.getFrom();
                                Optional<Edge> oe = v.getOutgoingEdges().stream().filter(o -> o.getLabel().equals(":domain")).findAny();
                                if (oe.isPresent()) {
                                    aligned = true;
                                    EdgeTypePair eat = new EdgeTypePair(oe.get(), AlignmentType.BE);
                                    typeAlignment.put(eat, Collections.singleton(i));
                                } else if (!v.getIncomingEdges().isEmpty()) {
                                    Vertex parent = v.getIncomingEdges().get(0).getFrom();
                                    Optional<Edge> poe = parent.getOutgoingEdges().stream().filter(o -> o.getLabel().equals(":domain")).findAny();
                                    if (poe.isPresent()) {
                                        aligned = true;
                                        EdgeTypePair eat = new EdgeTypePair(poe.get(), AlignmentType.BE);
                                        typeAlignment.put(eat, Collections.singleton(i));
                                    }
                                }
                            }
                            break;
                        }
                    }
                }

                // if no domain parent exists
                if(!aligned) {

                    boolean foundArticle = false;

                    for(int j=i+1; j < sentence.length && j < i+5; j++) {

                        if(aligned) break;

                        if(WordLists.articles.contains(sentence[j].toLowerCase())) {
                            foundArticle = true;
                        }

                        String mappedPos = PosHelper.mapPos(pos[j], true);

                        boolean align = false;
                        if((mappedPos.equals("VBN") || mappedPos.equals("VBG")) && !foundArticle) align = true;
                        if(mappedPos.equals("NN") && foundArticle) align = true;
                        if(pos[j].startsWith("JJ") && (j == sentence.length-1 || !PosHelper.mapPos(pos[j+1],true).equals("NN")) && isBe) align = true;

                        if(align) {
                            aligned = true;
                            if(reverseAlignment.containsKey(j)) {
                                for (Edge e : reverseAlignment.get(j)) {
                                    if (e.getFrom().isPropbankEntry()) {
                                        EdgeTypePair eat = new EdgeTypePair(e, AlignmentType.BE);
                                        typeAlignment.put(eat, Collections.singleton(i));
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // if the word is still not aligned
            if(!reverseAlignment.containsKey(i) && !reverseTypeAlignment.containsKey(i)) {
                // align remaining words if a vertex with identical concept is found
                for(Vertex v: dag) {
                    Edge instanceEdge = v.getInstanceEdge();
                    if(!alignment.containsKey(instanceEdge)) {

                        String word = sentence[i].toLowerCase();

                        boolean instancesMatch = v.getClearedInstance().equals(word);
                        if(!instancesMatch) {
                            if(WordLists.ALIGNMENT_MAP.containsKey(word)) {
                                if(WordLists.ALIGNMENT_MAP.get(word).contains(v.getClearedInstance())) instancesMatch = true;
                            }
                            else if(v.getInstance().equals(AMR_UNKNOWN_INSTANCE) && WordLists.questionWords.contains(word)) {
                                instancesMatch = true;
                            }
                        }

                        if(instancesMatch) {
                            if(v.isPropbankEntry()) v.setSimplifiedPos(pos[i]);
                            Set<Integer> align = new HashSet<>();
                            align.add(i);
                            alignment.put(instanceEdge, align);
                        }
                    }

                    for(Edge e: v.getOutgoingEdges()) {
                        if (WordLists.NO_ALIGNMENT_CONCEPTS.contains(e.getLabel())) {
                            alignment.remove(e);
                        }
                        // align all vertices of the form prep-x with x (z.B. prep-with)
                        if(!alignment.containsKey(e)) {
                            if(e.getLabel().startsWith(":prep-")) {
                                String preposition = e.getLabel().substring(6);
                                for(int wi=0; wi < sentence.length; wi++) {
                                    if(sentence[wi].toLowerCase().equals(preposition)) {
                                        EdgeTypePair eat = new EdgeTypePair(e, AlignmentType.BEFORE_INSERTION);
                                        typeAlignment.put(eat, Collections.singleton(wi));
                                        break;
                                    }
                                }
                            }
                            if(e.getLabel().equals(":condition")) {
                                for(int wi=0; wi < sentence.length; wi++) {
                                    if(sentence[wi].toLowerCase().equals("if")) {
                                        EdgeTypePair eat = new EdgeTypePair(e, AlignmentType.BEFORE_INSERTION);
                                        typeAlignment.put(eat, Collections.singleton(wi));
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // align have-org-roles and :degree more for the merge maxent
        for(Vertex v: dag) {
            if(v.getInstance().equals("have-org-role-91")) {
                Optional<Edge> arg2Edge = v.getOutgoingEdges().stream().filter(e -> e.getLabel().equals(":ARG2")).findAny();
                Optional<Edge> arg1Edge = v.getOutgoingEdges().stream().filter(e -> e.getLabel().equals(":ARG1")).findAny();
                if(arg2Edge.isPresent()) {
                    Vertex arg2 = arg2Edge.get().getTo();
                    if(alignment.containsKey(arg2.getInstanceEdge())) {
                        alignment.put(v.getInstanceEdge(), new HashSet<>(alignment.get(arg2.getInstanceEdge())));
                    }
                }
                else if(arg1Edge.isPresent()) {
                    Vertex arg1 = arg1Edge.get().getTo();
                    if(alignment.containsKey(arg1.getInstanceEdge())) {
                        alignment.put(v.getInstanceEdge(), new HashSet<>(alignment.get(arg1.getInstanceEdge())));
                    }
                }
            }

            else if(v.getInstance().equals("-")) {
                if(!alignment.containsKey(v.getInstanceEdge())) {
                    if(!v.getIncomingEdges().isEmpty()) {
                        Vertex parent = v.getIncomingEdges().get(0).getFrom();
                        if(alignment.containsKey(parent.getInstanceEdge())) {
                            for(int i: alignment.get(parent.getInstanceEdge())) {
                                if((sentence[i].startsWith("un") || sentence[i].startsWith("in") || sentence[i].startsWith("no")) && sentence[i].length() > 4) {
                                    Set<Integer> align = new HashSet<>();
                                    align.add(i);
                                    alignment.put(v.getInstanceEdge(), align);
                                    break;
                                }
                            }
                        }
                    }
                }
            }

            else if(v.getInstance().equals("more")) {
                if(!v.getIncomingEdges().isEmpty()) {
                    if(v.getIncomingEdges().get(0).getLabel().equals(":degree")) {
                        Vertex parent = v.getIncomingEdges().get(0).getFrom();
                        for(int i: alignment.get(parent.getInstanceEdge())) {
                            if(pos[i].equals("JJR") || pos[i].equals("RBR")) {
                                Set<Integer> align = new HashSet<>();
                                align.add(i);
                                alignment.put(v.getInstanceEdge(), align);
                                break;
                            }
                        }

                    }
                }
            }

        }

        for(Vertex v: dag) {
            if (!v.isLink() && isDeleted(v.getInstanceEdge())) {
                v.annotation.delete = true;
            }
        }

        // make after-insertions
        /* nextVertex: for(Vertex v: dag) {
            if(v.getFlags().contains(Flag.VERB)) {
                if(v.getFlags().contains(Flag.PASSIVE) || v.getPos().equals("NN") || v.getPos().equals("JJ")) {
                    Set<Integer> ids = typeAlignment.get(new EdgeTypePair(v.getInstanceEdge(), AlignmentType.BE));
                    for(int id: ids) {
                        for(int i=id-1; i > 0 && i > id-4; i--) {
                            if(reverseAlignment.containsKey(i)) {
                                for (Edge e : reverseAlignment.get(i)) {
                                    if(e.getFrom().getPos() == null) {
                                        break;
                                    }
                                    if(e.getFrom().getPos().equals("NN")) {
                                        if (!Collections.disjoint(e.getFrom().getIncomingEdges(), v.getOutgoingEdges())) {
                                            typeAlignment.put(new EdgeTypePair(e, AlignmentType.AFTER_INSERTION), Collections.singleton(id));
                                            break nextVertex;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }*/

        // calculate the span
        calculateSpan();
    }

    /**
     * Prepares an AMR graph for training by doing the following:
     * <ol>
     *     <li>Assign POS tags to the vertices of the AMR graph that are not PropBank framesets</li>
     *     <li>Convert the AMR graph to a tree by applying DELETE-REENTRANCE transitions (see {@link DirectedGraph#convertToTree()})</li>
     *     <li>Merge named entities into a single entity (see {@link Amr#mergeNameEntities(Vertex)})</li>
     *     <li>Merge verbalized concepts into a single entity (see {@link Amr#deverbalize(Vertex)})</li>
     *     <li>Remove :wiki edges from the AMR graph (see {@link Vertex#removeWikiTags()})</li>
     *     <li>Annotate each vertex with its mode (see {@link Amr#annotateModes(Vertex)})</li>
     * </ol>
     * @param tagger a maximum entropy POS tagger
     */
    public void prepareForTesting(MaxentTagger tagger) {

        if(!SET_UP) {
            throw new AssertionError("setUp() must be called before any prepare() method");
        }

        dag.convertToTree();

        span = Collections.emptyMap();
        alignment = Collections.emptyMap();
        backupAlignment = Collections.emptyMap();
        typeAlignment = Collections.emptyMap();

        pos = null;
        dependencyTree = null;

        for(Vertex v: dag.getVertices()) {
            mergeNameEntities(v);
            deverbalize(v);
        }

        for (Vertex v : dag) {
            if (!v.isLink()) {
                if(v.getPos() == null) {
                    if (!v.isPropbankEntry()) {
                        if(bestPosTags.containsKey(v.getInstance())) {
                            v.setSimplifiedPos(bestPosTags.get(v.getInstance()));
                        }
                        else {
                            v.setSimplifiedPos(tagger);
                        }
                    } else {
                        v.setSimplifiedPos(PosHelper.POS_ANY);
                    }
                }
            }
        }

        for(Vertex v: dag.getVertices()) {
            if(v.isLink()) {
                v.setSimplifiedPos(v.annotation.original.getPos());
            }
            v.removeWikiTags();

            // mode management
            annotateModes(v);
        }
    }

    /**
     * Annotates a vertex with its mode and removes the instance of the concept representing this mode.
     * @param v the vertex to annotate
     */
    private void annotateModes(Vertex v) {

        if(!v.getIncomingEdges().isEmpty()) {
            String inLabel = v.getIncomingEdges().get(0).getLabel();
            if(inLabel.equals(":mode") && Arrays.asList("interrogative", "imperative", "expressive").contains(v.getInstance())) {
                Vertex from = v.getIncomingEdges().get(0).getFrom();
                from.setMode(v.getInstance());
                from.getOutgoingEdges().remove(v.getIncomingEdges().get(0));
                if(v.getInstance().equals("imperative")) {
                    from.setPos("VB");
                    if(alignment.containsKey(from.getInstanceEdge())) {
                        for(int i: alignment.get(from.getInstanceEdge())) {
                            pos[i] = "VB";
                        }
                    }
                }
            }
        }

        if(v.getInstance().equals(AMR_UNKNOWN_INSTANCE)) {
            Vertex current = v;
            while(!current.getIncomingEdges().isEmpty()) {
                current = current.getIncomingEdges().get(0).getFrom();
                if(current.isPropbankEntry()) {
                    current.mode = "interrogative";
                }
            }
        }
    }

    /**
     * Checks whether a vertex is an instance of the concept name and if so, merges it together with all of its children to a single instance
     * containing the represented name. For example, (name (:op1 united) (:op2 nations)) gets merged to a single instance named ''united nations''.
     * @param v the vertex to check
     */
    private void mergeNameEntities(Vertex v) {
        if(v.getInstance().equals("name")) {
            Edge instanceEdge = v.getInstanceEdge();
            String name = "";

            Set<Integer> additionalAlignments = new HashSet<>();
            Set<Edge> removableEdges = new HashSet<>();

            for(Edge e: v.getOutgoingEdges()) {
                if(e.getTo() != Vertex.EMPTY_VERTEX) {
                    if(!(e.getTo().getInstance().startsWith("\"") && e.getTo().getInstance().endsWith("\""))) {
                        continue;
                    }
                    name = name + e.getTo().getInstance().replaceAll("\"","").replaceAll("-", " - ") + " ";
                    Edge eInstanceEdge = e.getTo().getInstanceEdge();
                    if(alignment.containsKey(eInstanceEdge)) {
                        additionalAlignments.addAll(alignment.get(eInstanceEdge));
                        alignment.remove(eInstanceEdge);
                    }
                    removableEdges.add(e);
                }
            }
            name = name.replaceAll(" - ", "-");
            name = name.trim();

            v.getOutgoingEdges().removeAll(removableEdges);

            if(!name.isEmpty()) {
                if (v.getIncomingEdges().size() > 0) {
                    Edge inEdge = v.getIncomingEdges().get(0);
                    Vertex from = inEdge.getFrom();

                    alignment.remove(v.getInstanceEdge());
                    from.getOutgoingEdges().remove(inEdge);

                    for(Edge e: v.getOutgoingEdges().stream().filter(e -> !e.isInstanceEdge()).collect(Collectors.toList())) {
                        e.from = from;
                        from.getOutgoingEdges().add(e);
                    }

                    from.name = name;

                    if (alignment.containsKey(from.getInstanceEdge())) {
                        alignment.get(from.getInstanceEdge()).addAll(additionalAlignments);
                    } else if (!additionalAlignments.isEmpty()) {
                        alignment.put(from.getInstanceEdge(), additionalAlignments);
                    }

                }
                else {
                    v.name = name;
                }

            }
        }
    }

    /**
     * Implementation of the MERGE transition described in the thesis.
     * @param master the parent vertex
     * @param slave the child vertex
     * @param instLabel the instance label of the resulting merged vertex
     * @param pos the POS tag of the resulting merged vertex
     */
    public void merge(Vertex master, Vertex slave, String instLabel, String pos) {

        Edge masterInstanceEdge = master.getInstanceEdge();
        master.instance = instLabel;
        //master.name = instLabel;
        master.setPos(pos);

        Edge relevantEdge = slave.getIncomingEdges().get(0);
        if(!relevantEdge.getFrom().equals(master)) {
            if(!relevantEdge.getFrom().equals(master.getIncomingEdges().get(0).getFrom())) {
                throw new AssertionError("merged vertices are neither in a parent-child relationship nor siblings");
            }
        }

        relevantEdge.getFrom().getOutgoingEdges().remove(relevantEdge);

        List<Edge> newEdges = new ArrayList<>(slave.getOutgoingEdges());
        newEdges.remove(slave.getInstanceEdge());

        master.getOutgoingEdges().addAll(newEdges);
        Set<Integer> instAlign = alignment.remove(masterInstanceEdge);

        masterInstanceEdge.label = master.getInstance();
        if(instAlign != null) alignment.put(masterInstanceEdge, instAlign);

        EdgeTypePair etp = new EdgeTypePair(slave.getInstanceEdge(), AlignmentType.ARTICLE);
        if(typeAlignment.containsKey(etp)) {
            typeAlignment.put(new EdgeTypePair(master.getInstanceEdge(), AlignmentType.ARTICLE), typeAlignment.get(etp));
            typeAlignment.remove(etp);
        }

        if(alignment.containsKey(slave.getInstanceEdge())) {
            if(!alignment.containsKey(master.getInstanceEdge())) {
                alignment.put(master.getInstanceEdge(), new HashSet<>());
            }
            alignment.get(master.getInstanceEdge()).addAll(alignment.get(slave.getInstanceEdge()));
        }

        for(Edge e: slave.getOutgoingEdges()) {
            e.from = master;
        }

        alignment.remove(relevantEdge);
        alignment.remove(slave.getInstanceEdge());
    }

    /**
     * Implementation of the SWAP transition described in the thesis.
     * @param parent the parent vertex
     * @param child the child vertex
     */
    public void swap(Vertex parent, Vertex child) {

        parent.annotation.nrOfSwapDowns++;
        child.annotation.nrOfSwapDowns--;

        Edge connection = child.getIncomingEdges().get(0);
        if(connection.getFrom() != parent) {
            throw new AssertionError("cannot swap if nodes are not in parent-child relationship");
        }

        child.getIncomingEdges().remove(connection);
        parent.getOutgoingEdges().remove(connection);

        connection.to = parent;
        connection.from = child;
        if(connection.label.endsWith("-of")) {
            connection.label = connection.label.substring(0, connection.label.length() - 3);
        }
        else {
            connection.label = connection.label + "-of";
        }

        if(parent.getIncomingEdges().isEmpty()) {
            dag.root = child;
        }
        else {
            Edge grandparentEdge = parent.getIncomingEdges().remove(0);
            grandparentEdge.to = child;
            child.getIncomingEdges().add(grandparentEdge);
        }

        child.getOutgoingEdges().add(connection);
        parent.getIncomingEdges().add(connection);

        getSpan(child);
    }

    /**
     * Checks for verbalized constructions like (person (:ARG1-of develop-02)) and deterministically merges them into a single node (developer).
     * The required deverbalization map is extracted from http://amr.isi.edu/download/lists/verbalization-list-v1.06.txt
     * @param v the vertex to check
     */
    public void deverbalize(Vertex v) {
        if(deverbalizationConcepts.contains(v.getInstance())) {

            boolean deverbalize = false;
            String deverbalization = "";
            Edge relevantEdge = null;
            Edge instanceEdge = null;

            for(Edge e: v.getOutgoingEdges()) {
                if(e.getTo() == Vertex.EMPTY_VERTEX) continue;
                if(e.getTo().getOutgoingEdges().size() > 1) continue;
                String keyString = v.getInstance() + "\t" + e.getLabel() + "\t" + e.getTo().getInstance();
                if(deverbalizationMap.containsKey(keyString)) {
                    deverbalize = true;
                    deverbalization = deverbalizationMap.get(keyString);
                    relevantEdge = e;
                    instanceEdge = v.getInstanceEdge();
                    break;
                }
            }

            if(deverbalize) {
                v.instance = deverbalization;
                //v.name = deverbalization;
                v.setPos("NN");
                v.getOutgoingEdges().remove(relevantEdge);
                List<Edge> newEdges = relevantEdge.getTo().getOutgoingEdges();
                newEdges.remove(relevantEdge.getTo().getInstanceEdge());
                v.getOutgoingEdges().addAll(newEdges);

                Set<Integer> instAlign = alignment.remove(instanceEdge);

                instanceEdge.label = deverbalization;

                if(instAlign != null) alignment.put(instanceEdge, instAlign);

                if(alignment.containsKey(relevantEdge.getTo().getInstanceEdge())) {
                    if(!alignment.containsKey(v.getInstanceEdge())) {
                        alignment.put(v.getInstanceEdge(), new HashSet<>());
                    }
                    alignment.get(v.getInstanceEdge()).addAll(alignment.get(relevantEdge.getTo().getInstanceEdge()));
                }

                if(alignment.containsKey(v.getInstanceEdge())) {
                    for(int i: alignment.get(v.getInstanceEdge())) {
                        pos[i] = "NN";
                    }
                }
                for(Edge e: relevantEdge.getTo().getOutgoingEdges()) {
                    e.from = v;
                }
                alignment.remove(relevantEdge);
            }
        }
    }

    /**
     * Determines the POS tag of the realization of some vertex v. To this end, all words to which v is aligned are checked and the POS tag of the word that resembles
     * v the most (by comparing prefixes) is chosen for v. If no such prefix mach can be found, a default POS tag is assigned by a maximum entropy tagger.
     * @param v the vertex
     * @param tagger the maxent tagger
     */
    public void getRealizationPOS(Vertex v, MaxentTagger tagger) {

        Edge e = v.getInstanceEdge();
        String instance = v.getInstance();

        for(int takeFirst=0; takeFirst <= 2 ; takeFirst++) {
            for (Integer i : alignment.getOrDefault(e, new HashSet<>())) {
                if (!WordLists.beforeInsertableWords.contains(sentence[i])) {
                    if ((takeFirst == 0 && matches(instance, sentence[i])) || (takeFirst == 1 && instance.charAt(0) == sentence[i].toLowerCase().charAt(0)) || takeFirst == 2) {
                        v.setSimplifiedPos(pos[i]);
                        return;
                    }
                }
            }
        }
        v.setSimplifiedPos(tagger);
    }

    /**
     * Helper function to check whether an AMR instance and an English word share a common prefix
     * @param amrWord the AMR instance
     * @param englishWord the English word
     * @return true iff both strings share a common prefix according to {@link Amr#toCompString(String)}
     */
    private boolean matches(String amrWord, String englishWord) {
        String amrComp = toCompString(amrWord);
        String engComp = toCompString(englishWord);
        return amrComp.equals(engComp);
    }

    private String toCompString(String w) {
        String ret = w.replaceAll("[^a-zA-Z]", "");
        ret = ret.substring(0, Math.min(ret.length(), 3)).toLowerCase();
        return ret;
    }

    /**
     * Sets the alignment for an Edge to be {min, min+1, ..., max-1, max}
     * @param instanceEdge the edge for which the alignment should be added
     * @param min the minimum value
     * @param max the maximum value
     */
    private void setAlignment(Edge instanceEdge, int min, int max) {
        if(instanceEdge == null) return;
        Set<Integer> indices = new HashSet<>();
        for(int i=min; i<max; i++) {
            indices.add(i);
        }
        alignment.put(instanceEdge, indices);
    }

    /**
     * Checks whether a DELETE transition must be applied to a vertex.
     * @param edge the instance edge of the vertex to check
     * @return true iff a DELETE transition must be applied to {@code edge.getFrom()}
     */
    private boolean isDeleted(Edge edge) {
        if(WordLists.NEVER_DELETE.contains(edge.getLabel())) return false;
        if(WordLists.ALWAYS_DELETE.contains(edge.getLabel())) return true;
        boolean keep = this.alignment.containsKey(edge) && !this.alignment.get(edge).isEmpty();
        return !keep;
    }

    /**
     * Calculates the span of every vertex in the AMR graph as defined in the thesis.
     */
    public void calculateSpan() {
        span = new HashMap<>();
        getSpan(dag.getRoot());
    }

    /**
     * Calculates the span of a vertex v and every vertex in the v-subgraph of this AMR graph.
     * @param v the vertex to calculate the spanf or
     * @return the span of v
     */
    public Span getSpan(Vertex v) {

        Span vSpan = new Span();

        if(alignment.containsKey(v.getInstanceEdge())) {
            vSpan.addAll(alignment.get(v.getInstanceEdge()));
        }

        for(Edge e: v.getOutgoingEdges()) {
            if(e.isInstanceEdge()) continue;
            Span childSpan = getSpan(e.getTo());
            if(childSpan.hasElements) {
                vSpan.add(childSpan.min);
                vSpan.add(childSpan.max);
            }
        }

        if(vSpan.hasElements) {
            this.span.put(v.getInstanceEdge(), vSpan);
        }
        return vSpan;
    }

    /**
     * Helper function to find the first AMR graph within a collection of graphs whose reference realization starts with the specified beginning.
     * @param beginning the string with which the searched AMR graphs realization begins
     * @param amrs the collection of AMR graphs
     * @return the first matching AMR graph or null if no such graph is found
     */
    public static Amr findBySentence(String beginning, Collection<Amr> amrs) {
        for(Amr amr: amrs) {
            String sentence = String.join(" ", amr.sentence);
            if(sentence.startsWith(beginning)) return amr;
        }
        return null;
    }

    /**
     * Prepares a list of AMR graphs for either training or testing by calling either {@link Amr#prepareForTraining(MaxentTagger)} or {@link Amr#prepareForTesting(MaxentTagger)}.
     * @param amrs the list of AMRs to prepare
     * @param tagger a maximum entropy POS tagger
     * @param forTesting whether the AMR graphs should be prepared for testing ({@code forTesting = true}) or for training ({@code forTesting = false})
     */
    public static void prepare(List<Amr> amrs, MaxentTagger tagger, boolean forTesting) {
        if(!SET_UP) {
            throw new AssertionError("setUp() must be called before any prepare() method");
        }
        for (Amr amr : amrs) {

            // fix the small number of cyclic AMRs where the root has incoming edges by removing these incoming edges
            for (Edge e : amr.dag.getRoot().getIncomingEdges()) {
                e.getFrom().getOutgoingEdges().remove(e);
            }
            amr.dag.getRoot().getIncomingEdges().clear();

            if(forTesting) {
                amr.prepareForTesting(tagger);
            }
            else {
                amr.prepareForTraining(tagger);
            }
        }
    }

    /**
     * Returns the partial yield of this AMR graph given a partial transition function as defined in the thesis.
     * The yield is represented by a string of space-separated words.
     * @param ptf the partial transition function
     * @return the partial yield of this AMR graph
     */
    public String yield(PartialTransitionFunction ptf) {
        return AlignedWord.yield(yieldList(ptf), true);
    }

    /**
     * Returns the partial yield of this AMR graph given a partial transition function as defined in the thesis.
     * The yield is represented by a list of {@link AlignedWord} instances.
     * @param ptf the partial transition function
     * @return the partial yield of this AMR graph
     */
    public List<AlignedWord> yieldList(PartialTransitionFunction ptf) {
        return yieldList(dag.getRoot(), ptf);
    }

    /**
     * Returns the partial yield of the vertex-subgraph of this AMR graph given a partial transition function as defined in the thesis.
     * The yield is represented by a list of {@link AlignedWord} instances.
     * @param vertex the root of the subgraph to consider
     * @param ptf the partial transition function
     * @return the partial yield of the vertex-subgraph of this AMR graph
     */
    public List<AlignedWord> yieldList(Vertex vertex, PartialTransitionFunction ptf) {
        List<AlignedWord> ret = new ArrayList<>();
        List<Edge> trueChildren = vertex.getOutgoingEdges().stream().filter(e -> e.getTo() != Vertex.EMPTY_VERTEX).collect(Collectors.toList());
        int transSize = trueChildren.size();
        if(vertex.getIncomingEdges().isEmpty()) {
            ret.add(new AlignedWord(ptf.denominator.getOrDefault(vertex, ""), vertex, AlignmentType.ARTICLE));
        }
        if(transSize >= 1) {
            for (Edge e : ptf.reordering.getOrDefault(vertex, vertex.getOutgoingEdges())) {
                if (!e.isInstanceEdge()) {
                    Vertex target = e.getTo();
                    ret.add(new AlignedWord(ptf.beforeIns.getOrDefault(target,""), target, AlignmentType.BEFORE_INSERTION));
                    ret.add(new AlignedWord(ptf.denominator.getOrDefault(target,""), target, AlignmentType.ARTICLE));
                    ret.addAll(yieldList(target, ptf));
                    ret.add(new AlignedWord(ptf.afterIns.getOrDefault(target,""), target, AlignmentType.AFTER_INSERTION));
                } else {
                    ret.add(new AlignedWord(ptf.realization.getOrDefault(vertex, ""), vertex, AlignmentType.TRANSLATION));
                }
            }
        }
        else {
            ret.add(new AlignedWord(ptf.realization.getOrDefault(vertex, ""), vertex, AlignmentType.TRANSLATION));
        }
        ret.add(new AlignedWord(ptf.punctuation.getOrDefault(vertex, ""), vertex, AlignmentType.PUNCTUATION));
        return ret;
    }
}
