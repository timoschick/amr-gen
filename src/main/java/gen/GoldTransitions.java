package gen;

import dag.*;
import edu.stanford.nlp.tagger.maxent.MaxentTagger;
import misc.PosHelper;
import misc.StaticHelper;
import misc.WordLists;

import java.util.*;
import java.util.stream.Collectors;

/**
 * A static class that contains functions to get the gold transitions to be applied to a vertex.
 */
public class GoldTransitions {

    // transitions for the first stage of the generator
    public static final String DELETE = "_delete";
    public static final String SWAP = "_swap";
    public static final String KEEP = "_realize";
    public static final String MERGE = "_merge";

    // possible events for the <_* maximum entropy model (see Eq. 19, Section 4.2.1 Modeling)
    public static final String CHILD_BEFORE_PARENT = "C <_* P";
    public static final String PARENT_BEFORE_CHILD = "P <_* C";

    // possible events for the <_l and <_r maximum entropy models (see Eq. 19, Section 4.2.1 Modeling)
    public static final String E1_BEFORE_E2 = "e1 < e2";
    public static final String E2_BEFORE_E1 = "e2 < e1";

    // non-instantiable class
    private GoldTransitions() {}

    /**
     * This function returns the gold transition for the first stage, i.e. one of MERGE, SWAP, DELETE and KEEP. Although DELETE-REENTRANCE transitions
     * are also part of the first stage, they are handled deterministically via {@link DirectedGraph#convertToTree()} during the preprocessing
     * performed by {@link Amr#prepare(List, MaxentTagger, boolean)}.
     * @param amr the AMR graph to which the vertex belongs
     * @param vertex the vertex
     * @return the gold transition, which is one of {@link GoldTransitions#MERGE}, {@link GoldTransitions#SWAP}, {@link GoldTransitions#KEEP}
     * and {@link GoldTransitions#DELETE}
     */
    public static String getGoldActionFirstStage(Amr amr, Vertex vertex) {
        Edge edge = vertex.getInstanceEdge();

        // check whether the vertex is not aligned to any word and must thus be deleted
        boolean keep = edge == null || (amr.alignment.containsKey(edge) && !amr.alignment.get(edge).isEmpty()
                && !WordLists.ALWAYS_DELETE.contains(edge.getLabel()))
                || WordLists.NEVER_DELETE.contains(edge.getLabel());
        if(!keep) return DELETE;

        // check whether a MERGE transition is required
        String mergeResult = getGoldMerge(amr, vertex);
        boolean merge = mergeResult!=null;
        if(merge) return MERGE;

        // check whether a SWAP transition is required
        boolean swap = getGoldSwap(amr, vertex);
        if(swap) return SWAP;

        // if none of the above holds, the vertex must simply be kept in place
        return KEEP;
    }

    /**
     * This function returns either the gold merge transition to be applied to a vertex, or null if no merge transition is required.
     * @param amr the AMR graph to which the vertex belongs
     * @param vertex the vertex
     * @return a tab-separated string containing the concept and POS of the gold merge, if a MERGE transition is required, and {@code null} otherwise
     */
    public static String getGoldMerge(Amr amr, Vertex vertex) {

        // if a vertex has no parent, no merge transition can be performed
        if(vertex.getIncomingEdges().isEmpty()) return null;

        Edge parentInstance = vertex.getIncomingEdges().get(0).getFrom().getInstanceEdge();
        Edge thisInstance = vertex.getInstanceEdge();

        if(amr.alignment.containsKey(parentInstance) && amr.alignment.containsKey(thisInstance)) {

            Set<Integer> parentAlignment = amr.alignment.get(parentInstance);
            Set<Integer> thisAlignment = amr.alignment.get(thisInstance);

            // check whether both vertices' alignments have some common element
            if(!Collections.disjoint(parentAlignment, thisAlignment)) {
                Set<Integer> combinedAlignment = new HashSet<>();
                combinedAlignment.addAll(parentAlignment);
                combinedAlignment.addAll(thisAlignment);

                // if the combined alignment of both vertices is not contiguous, there is no way of assigning a meaningful merged concept to them
                if(!StaticHelper.isContiguous(combinedAlignment)) {
                    return null;
                }

                List<Integer> sortedAlignment = new ArrayList<>(combinedAlignment);
                Collections.sort(sortedAlignment);

                StringBuilder resultBuilder = new StringBuilder();
                String pos = PosHelper.POS_ANY;

                for(int i: sortedAlignment) {
                    resultBuilder.append(amr.sentence[i]);
                    resultBuilder.append(" ");
                    if(sortedAlignment.size() == 1) {
                        pos = amr.pos[i];
                    }
                }

                String word = resultBuilder.toString().toLowerCase().trim();
                String mappedPos = PosHelper.mapPos(pos, false);
                return word + "\t" + mappedPos;
            }
        }
        // if the alignments have no common element, no MERGE transition is required
        return null;
    }

    /**
     * This function returns whether a SWAP transition needs to be applied to some vertex.
     * @param amr the AMR graph to which the vertex belongs
     * @param vertex the vertex
     * @return true iff a SWAP transition needs to be applied
     */
    public static boolean getGoldSwap(Amr amr, Vertex vertex) {

        // if a vertex has no parent, no swap transition can be performed
        if(vertex.getIncomingEdges().isEmpty()) return false;

        Edge parentInstance = vertex.getIncomingEdges().get(0).getFrom().getInstanceEdge();
        Edge thisInstance = vertex.getInstanceEdge();

        if(amr.alignment.containsKey(parentInstance) && amr.span.containsKey(parentInstance) && amr.span.containsKey(thisInstance)) {

            Span parentSpan = amr.span.get(parentInstance);
            Span thisSpan = amr.span.get(thisInstance);

            // if there is some element in the parent span that is not an element of the hull of the span of the current vertex, no SWAP transition
            // is to be performed.
            for(int i: parentSpan.getElements()) {
                if(i < thisSpan.min) return false;
                if(i > thisSpan.max) return false;
            }

            Set<Integer> parentWithoutChildSpan = new HashSet<>(parentSpan.getElements());
            parentWithoutChildSpan.removeAll(thisSpan.getElements());

            // if swapping doesn't resolve the issue, i.e. after performing a (parent,child) swap, a (child,parent) swap would have to be performed,
            // we do not perform a SWAP transition.
            if(!parentWithoutChildSpan.isEmpty()) {
                if(!StaticHelper.isContiguous(parentWithoutChildSpan)) return false;
            }

            Set<Integer> parentAlignment = amr.alignment.get(parentInstance);

            for(Integer i: parentAlignment) {
                // if the parent vertex is aligned to some node that lies in the span of the current vertex, a SWAP transition must be performed.
                if(thisSpan.min <= i && thisSpan.max >= i) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * This function returns the gold REALIZE transition to be applied to some vertex.
     * @param amr the AMR graph to which the vertex belongs
     * @param edge the instance edge of the vertex
     * @return the realization of the vertex or an empty string if no realization is found
     */
    public static String getGoldRealization(Amr amr, Edge edge) {

        if(amr.alignment.containsKey(edge)) {
            Set<Integer> alignment = amr.alignment.get(edge);
            int min = alignment.stream().mapToInt(i -> i).min().getAsInt();
            int max = alignment.stream().mapToInt(i -> i).max().getAsInt();
            if (alignment.size() != max + 1 - min) {
                int maxtmp = max;
                for (int i = min; i < maxtmp; i++) {
                    if (!alignment.contains(i)) break;
                    max = i;
                }
            }

            Vertex current = edge.getFrom();

            // check whether some other vertex is also aligned to a word to which the current vertex is aligned. If this is the case, we ignore the
            // current vertex for testing
            List<Vertex> othersToCheck = current.getOutgoingEdges().stream().filter(e -> !e.isInstanceEdge()).map(e -> e.getTo()).collect(Collectors.toList());
            if(!current.getIncomingEdges().isEmpty()) {
                othersToCheck.add(current.getIncomingEdges().get(0).getFrom());
            }

            for(Vertex other: othersToCheck) {
                Edge otherInstEdge = other.getInstanceEdge();
                int finalMax = max;
                if(amr.alignment.containsKey(otherInstEdge) && amr.alignment.get(otherInstEdge).stream().anyMatch(i -> i >= min && i <= finalMax)) {
                    return "";
                }
            }

            List<String> translationWords = new ArrayList<>();

            for (int i = min; i <= max; i++) {
                translationWords.add(amr.sentence[i].toLowerCase());
            }

            String translation = String.join(" ", translationWords);
            if(translation.equals("n't")) translation = "not";
            return translation;
        }
        return "";
    }

    /**
     * This function returns the gold INSERT_CHILD transition to be applied to some vertex.
     * @param amr the AMR graph to which the vertex belongs
     * @param vertex the vertex
     * @return the list of all words which have to be inserted through INSERT_CHILD transitions
     */
    public static List<String> getGoldChildInsertion(Amr amr, Vertex vertex) {

        List<String> ret = new ArrayList<>();

        EdgeTypePair eat_st = new EdgeTypePair(vertex.getInstanceEdge(), AlignmentType.DT_CHILD_INSERTION_LEFT);
        if(amr.typeAlignment.containsKey(eat_st)) {
            List<Integer> result = new ArrayList<>(amr.typeAlignment.get(eat_st));
            Collections.sort(result);
            ret.add(getLemma(amr.sentence[result.get(result.size()-1)]).toLowerCase());
        }
        else {
            ret.add("");
        }
        return ret;
    }

    /**
     * Helper function to get the lemma for forms of the commonly inserted auxiliary verbs "be", "have" and "do".
     * @param word the word for which a lemma is required
     * @return the lemma of the word, if it is some form of "be", "have" or "do", and the word itself otherwise
     */
    private static String getLemma(String word) {
        if(WordLists.beForms.contains(word)) return "be";
        if(WordLists.haveForms.contains(word)) return "have";
        if(Arrays.asList("do", "did", "doing").contains(word)) return "do";
        return word;
    }

    /**
     * This function returns the gold INSERT_BETWEEN transition to be applied to some vertex.
     * @param amr the AMR graph to which the vertex belongs
     * @param edge the edge such that INSERT_BETWEEN is applied when {@code edge.from} is the top element of the node buffer and
     *             {@code edge.to} is the top element of the child buffer
     * @return the word which has to be inserted through INSERT_BETWEEN transitions or an empty string if no such word exists. As we
     * consider both the dependency-tree based and agnostic approaches, they both may give different results so a list containing
     * both results is returned.
     */
    public static List<String> getGoldBetweenInsertion(Amr amr, Edge edge) {

        List<String> ret = new ArrayList<>();

        EdgeTypePair eat_st = new EdgeTypePair(edge, AlignmentType.BEFORE_INSERTION);
        EdgeTypePair eat_dt = new EdgeTypePair(edge, AlignmentType.DT_HEAD_INSERTION_LEFT);
        if(amr.typeAlignment.containsKey(eat_dt)) {
            List<Integer> result = new ArrayList<>(amr.typeAlignment.get(eat_dt));
            Collections.sort(result);
            ret.add(amr.sentence[result.get(0)].toLowerCase());
        }
        else {
            ret.add("");
        }

        if(amr.typeAlignment.containsKey(eat_st)) {
            ret.add(String.join(" ", amr.typeAlignment.get(eat_st).stream().map( i -> amr.sentence[i].toLowerCase()).collect(Collectors.toList())));
        }
        else {
            ret.add("");
        }

        return ret;

    }

    /**
     * This function returns the gold order for two vertices, one of which is a child of the other. This is the equivalent of the &lt;<sub>*</sub> relation
     * introduced in Section 4.2.1 Modeling.
     * @param amr the AMR graph to which both vertices belong
     * @param parentInstanceEdge the instance edge of the parent vertex
     * @param e the edge connecting the parent and child node
     * @return the gold order for both vertices, or an empty string, if no meaningful order can be derived
     */
    public static String getGoldParentChildOrder(Amr amr, Edge parentInstanceEdge, Edge e) {
        Edge eInst = e.getTo().getInstanceEdge();
        if(amr.span.containsKey(eInst) && amr.alignment.containsKey(parentInstanceEdge)) {
            if (amr.span.get(eInst).median() < StaticHelper.median(amr.alignment.get(parentInstanceEdge))) {
                return CHILD_BEFORE_PARENT;
            }
            else if (StaticHelper.median(amr.alignment.get(parentInstanceEdge)) < amr.span.get(eInst).median()) {
                return PARENT_BEFORE_CHILD;
            }
        }
        return "";
    }

    /**
     * This function returns the gold order for two vertices which are siblings. This is the equivalent of the &lt;<sub>l</sub> and &lt;<sub>r</sub> relations
     * introduced in Section 4.2.1 Modeling.
     * @param amr the AMR graph to which both vertices belong
     * @param e1 the edge connecting the common parent of both edges to the first child
     * @param e2 the edge connecting the common parent of both edges to the second child
     * @return the gold order for both vertices, or an empty string, if no meaningful order can be derived
     */
    public static String getGoldSiblingOrder(Amr amr, Edge e1, Edge e2) {

        Edge e1Inst = e1.getTo().getInstanceEdge();
        Edge e2Inst = e2.getTo().getInstanceEdge();

        if(amr.span.containsKey(e1Inst) && amr.span.containsKey(e2Inst)) {
            if (amr.span.get(e1Inst).median() < amr.span.get(e2Inst).median()) {
                return E1_BEFORE_E2;
            }
            else if (amr.span.get(e2Inst).median() < amr.span.get(e1Inst).median()) {
                return E2_BEFORE_E1;
            }
        }
        return "";
    }
}