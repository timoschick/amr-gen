package gen;

import dag.Amr;
import dag.Edge;
import dag.Vertex;
import edu.berkeley.nlp.lm.ArrayEncodedNgramLanguageModel;
import edu.berkeley.nlp.lm.collections.BoundedList;
import edu.stanford.nlp.ling.Datum;
import edu.stanford.nlp.util.Pair;
import main.PathList;
import misc.PosHelper;
import misc.PrunedList;
import misc.StaticHelper;
import misc.WordLists;
import ml.*;
import opennlp.tools.ml.model.Event;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * This class implements the transition system for the second stage defined in the thesis; i.e., it handles all transitions but MERGE, SWAP, DELETE and KEEP.
 */
public class SecondStageProcessor {

    // this map stores the scores for already observed n-grams; it improves efficiency as it reduces
    // the number of times the language model has to be queried
    private static Map<List<String>,Float> nGramScores = new HashMap<>();

    // the maximum entropy models used by the second stage processor
    private final RealizeMaxentModel realizationMaxentModel;
    private final ArgInsertionMaxentModel argInsertionMaxEnt;
    private final OtherInsertionMaxentModel othersInsertionMaxEnt;
    private final ChildInsertionMaxentModel childInsertionMaxEnt;
    private final DenomMaxentModel denomMaxentModel;

    private final ArrayEncodedNgramLanguageModel languageModel;
    private final PositionHelper positionHelper;
    private final DefaultRealizer defaultRealizer;

    // weights used by the second stage processor to compute the score of a partial transition function, see Hyperparams class
    public Map<String,Double> syntacticAnnotationWeights = new HashMap<>();
    public double lmWeight;
    public double beforeInsWeight;
    public double beforeInsArgWeight;
    public double afterInsWeight;
    public double articleLmWeight;
    public double reorderingWeight;
    public double realizationWeight;

    // additional hyperparameters, see Hyperparams class
    public double defaultRealizationScore;
    public double articleSmoothing;
    public double minTranslationScore;
    public double linkRealizationScore;
    public int maxNrOfComposedPredictions;
    public int maxNrOfRealizationPredictions;
    public int maxNrOfPosRealizationPredictions;

    // a map counting the observation of named entity realizations observed in a training corpus, required for the default realizations of named entities
    private Map<String,Integer> namedEntityCounts = new HashMap<>();

    // a set storing every concept observed during training
    private final Set<String> observedConcepts;

    /**
     * Creates a new processor for the second stage of the generation process.
     * @param realizationMaxentModel the maximum entropy model to use for REALIZE transitions
     * @param argInsertionMaxEnt the maximum entropy model to use for INSERT_BETWEEN transitions when the edge label matches :ARG[0-9]+
     * @param othersInsertionMaxEnt the maximum entropy model to use for INSERT_BETWEEN transitions when the edge label does not match :ARG[0-9]+
     * @param childInsertionMaxEnt the maximum entrpoy model to use for INSERT_CHILD transitions
     * @param denomMaxentModel the maximum entropy model to use for denominators
     * @param positionHelper the position helper to be used for computing the best REORDER transitions
     * @param languageModel the language model to be used by the score function
     */
    public SecondStageProcessor(RealizeMaxentModel realizationMaxentModel, ArgInsertionMaxentModel argInsertionMaxEnt, OtherInsertionMaxentModel othersInsertionMaxEnt,
                                ChildInsertionMaxentModel childInsertionMaxEnt, DenomMaxentModel denomMaxentModel, DefaultRealizer defaultRealizer,
                                PositionHelper positionHelper, ArrayEncodedNgramLanguageModel<String> languageModel) throws IOException {

        this.realizationMaxentModel = realizationMaxentModel;
        this.argInsertionMaxEnt = argInsertionMaxEnt;
        this.othersInsertionMaxEnt = othersInsertionMaxEnt;
        this.childInsertionMaxEnt = childInsertionMaxEnt;
        this.denomMaxentModel = denomMaxentModel;
        this.defaultRealizer = defaultRealizer;
        this.languageModel = languageModel;
        this.positionHelper = positionHelper;
        observedConcepts = new HashSet<>(StaticHelper.listFromFile(PathList.CONCEPT_LIST));

        Map<String,String> nameEntityCountsString = StaticHelper.mapFromFile(PathList.NAMED_ENTITIES_MAP);
        for(String key: nameEntityCountsString.keySet()) {
            namedEntityCounts.put(key, Integer.valueOf(nameEntityCountsString.get(key)));
        }
    }

    /**
     * Computes the best realization of an AMR graph. As a side effect, the best found partial transition function
     * is stored in the AMR graph's {@link Amr#partialTransitionFunction} variable. Note that the so-computed String
     * is not post-processed, but post-processing can be done afterwards using the {@link Amr#partialTransitionFunction}
     * of the AMR graph.
     * @param amr the AMR graph
     * @return the generated sentence
     */
    public String getBestRealizationAsString(Amr amr) {
        Prediction pred = generateBottomUp(amr);
        if(pred == null || pred.partialTransitionFunction == null) return "";

        amr.partialTransitionFunction = pred.partialTransitionFunction;
        return amr.yield(pred.partialTransitionFunction);
    }

    /**
     * Performs a bottom-up generation from an AMR graph; this implements the second part of the final generation algorithm defined in the thesis.
     * @param amr the AMR graph to process
     * @return the best partial transition function along with its score, represented by an instance of {@link Prediction}
     */
    private Prediction generateBottomUp(Amr amr) {

        // special handling for multi-sentence AMR graphs
        if(amr.dag.getRoot().getInstance().equals("multi-sentence")) {
            List<Edge> sortedOut = new ArrayList<>(amr.dag.getRoot().getOutgoingEdges());
            sortedOut.sort(Comparator.comparing(Edge::getLabel));
            Prediction bestPred = new Prediction("",1);
            bestPred.partialTransitionFunction.reordering.put(amr.dag.getRoot(), sortedOut);
            for(Edge e: sortedOut) {
                if(e.isInstanceEdge()) continue;
                PrunedList pl = getBest(amr, e.getTo());
                if(!pl.isEmpty()) {
                    bestPred.partialTransitionFunction.addCopy(pl.get(0).partialTransitionFunction);
                }
                else {
                    return null;
                }
            }
            return bestPred;
        }

        PrunedList bestRealizations = getBest(amr, amr.dag.getRoot());

        if(!bestRealizations.isEmpty()) {
            return bestRealizations.get(0);
        }
        return null;
    }

    /**
     * This implements the best transition sequence algorithm described in the thesis; i.e. given a vertex v of an AMR graph, it computes
     * the n-best partial transition sequences for vertex v and all of its successors in the graph bottom-up.
     * @param amr the AMR graph
     * @param v the vertex
     * @return the list of n-best partial transition functions along with their scores
     */
    private PrunedList getBest(Amr amr, Vertex v) {

        boolean applyPunctuation = false;
        boolean isLast = true;

        // special handling for multi-sentence AMR graphs
        if(!v.getInstance().equals("multi-sentence")) {

            if (v.getIncomingEdges().isEmpty() || v.getIncomingEdges().get(0).getFrom().getInstance().equals("multi-sentence")) {
                applyPunctuation = true;
                if (!v.getIncomingEdges().isEmpty()) {
                    isLast = false;
                    Edge in = v.getIncomingEdges().get(0);
                    List<Edge> parentOut = in.getFrom().getOutgoingEdges();
                    if (in.equals(parentOut.get(parentOut.size() - 1))) {
                        isLast = true;
                    }
                }
            }
        }

        Edge instanceEdge = v.getInstanceEdge();

        List<Edge> trueChildren = v.getOutgoingEdges().stream().filter(e -> e.getTo() != Vertex.EMPTY_VERTEX).collect(Collectors.toList());

        for(Edge childEdge: trueChildren) {
            getBest(amr, childEdge.getTo());
        }

        PrunedList realizationPredictions = getBestRealizationsForAllSyntacticAnnotations(amr, v);
        PrunedList bestRealizations = new PrunedList(maxNrOfComposedPredictions);

        // if, for some reason, no realization is found by the maximum entropy model, we take an empty string as fallback realization and set the probability
        // of this realization to a very low value so that if another constellation exists under which the maximum entropy model finds a realization, this other
        // constellation is always preferred.
        if(realizationPredictions.isEmpty()) {
            Prediction p = new Prediction("",Math.log(Double.MIN_VALUE));
            p.partialTransitionFunction.pos.put(v, PosHelper.POS_ANY);
            p.partialTransitionFunction.realization.put(v, "");
            realizationPredictions.add(p);
        }

        // iterate over the n-best REALIZE transitions
        for(Prediction realizationPrediction: realizationPredictions) {

            if(v.isPropbankEntry()) {
                v.setSimplifiedPos(realizationPrediction.partialTransitionFunction.pos.getOrDefault(v, PosHelper.POS_ANY));
            }

            // apply INSERT_CHILD transitions
            List<Datum<String,String>> childIns = childInsertionMaxEnt.toDatumList(amr, v, true, realizationPrediction.getValue(), realizationPrediction.partialTransitionFunction.voice.get(v));
            if(!childIns.isEmpty()) {
                List<Prediction> pl = childInsertionMaxEnt.getNBestSorted(childIns.get(0));
                if(!pl.get(0).getValue().isEmpty()) {

                    Vertex childInsertion = new Vertex(pl.get(0).getValue());
                    PrunedList childBestTranslations = getBestRealizationsForAllSyntacticAnnotationsGivenPos(amr, childInsertion);
                    childInsertion.predictions.put("realization", childBestTranslations);

                    childInsertion.setPos(PosHelper.POS_ANY);
                    //childInsertion.mode = v.mode;
                    Edge e = new Edge(v, childInsertion, ":ins", false);
                    e.inserted = true;

                    realizationPrediction.partialTransitionFunction.childInsertions.put(v, Collections.singletonList(e));
                    realizationPrediction.partialTransitionFunction.pos.put(childInsertion, PosHelper.POS_ANY);
                }
            }

            List<Edge> childInsertions = realizationPrediction.partialTransitionFunction.childInsertions.getOrDefault(v, new ArrayList<>());

            int totalChildrenSize = trueChildren.size() + childInsertions.size();

            if(totalChildrenSize >= 1) {

                List<Pair<List<Edge>,Double>> orderPredictions;
                if (v.isDeleted() && totalChildrenSize == 1) {
                    orderPredictions = Collections.singletonList(new Pair<>(Collections.singletonList(trueChildren.get(0)), 1d));
                } else {
                    // if there is more than one child, get the n-best REORDER transitions
                    String realization = realizationPrediction.partialTransitionFunction.realization.get(v);
                    String voice = realizationPrediction.partialTransitionFunction.voice.get(v);
                    orderPredictions = positionHelper.getNBestReorderings(amr, v, childInsertions, realization, voice);
                }

                // iterate over the n-best REORDER transitions
                for (Pair<List<Edge>,Double> orderPrediction : orderPredictions) {

                    PrunedList bestOrderRealizations = new PrunedList(maxNrOfComposedPredictions);
                    List<Edge> order = orderPrediction.first();

                    // get the best denominator and explicitly handle the corresponding INSERT_CHILD transition
                    List<Datum<String,String>> denomDatumList = denomMaxentModel.toDatumList(amr, v, true, realizationPrediction.partialTransitionFunction.number.get(v), realizationPrediction.partialTransitionFunction.realization.get(v));
                    if(!denomDatumList.isEmpty()) {
                        Datum<String,String> articleDatum = denomDatumList.get(0);
                        List<Prediction> articlePredictions = denomMaxentModel.getNBestSorted(articleDatum);
                        for(Prediction p: articlePredictions) {
                            Prediction articlePred = new Prediction(p.getValue().replace("-", ""), syntacticAnnotationWeights.get("denom") * Math.log(p.getScore()));
                            bestOrderRealizations.add(articlePred);
                        }
                    }

                    if (articleDisallowed(v) || WordLists.NO_ALIGNMENT_CONCEPTS.contains(v.getInstance()) || realizationPredictions.size() == 1 && realizationPredictions.get(0).getValue().isEmpty()) {
                        bestOrderRealizations.clear();
                    }

                    for (Prediction p : bestOrderRealizations) {
                        p.partialTransitionFunction.denominator.put(v, p.getValue());
                    }

                    if (bestOrderRealizations.isEmpty()) {
                        Prediction emptyPred = new Prediction("", 0);
                        emptyPred.partialTransitionFunction.denominator.put(v, "");
                        bestOrderRealizations.add(emptyPred);
                    }

                    // process each child node and the current node itself
                    for (int i = 0; i < order.size(); i++) {
                        Edge e = order.get(i);
                        PrunedList edgeRealizationPredictions = new PrunedList(maxNrOfRealizationPredictions);

                        if ((e.getTo() == Vertex.EMPTY_VERTEX && e != instanceEdge) || (e == instanceEdge && v.isDeleted())) {
                            continue;
                        }

                        if (e == instanceEdge) {
                            edgeRealizationPredictions.add(realizationPrediction);
                        } else {
                            edgeRealizationPredictions.addAll(e.getTo().predictions.get("realization"));
                        }
                        bestOrderRealizations = getBestAppendedRealizations(amr, order, bestOrderRealizations, edgeRealizationPredictions, e, realizationPrediction);
                    }

                    for (Prediction p : bestOrderRealizations) {

                        double orderingScore = reorderingWeight * Math.log(orderPrediction.second());
                        p.setScoreAndLmFreeScore(p.getScore() + orderingScore, p.getLmFreeScore() + orderingScore);
                        p.partialTransitionFunction.reordering.put(v, order);
                    }
                    bestRealizations.addAll(bestOrderRealizations);
                }
            }
            else {
                bestRealizations.add(realizationPrediction);
            }
        }

        if(applyPunctuation) {
            applyPunctuation(v, bestRealizations, isLast);
        }

        // explicitly check whether an article has been added although the assigned POS tag is not NN or the realization is empty; if so, delete the inserted article
        for(Prediction p: bestRealizations) {
            if(p.partialTransitionFunction.denominator.containsKey(v) && !p.partialTransitionFunction.denominator.get(v).isEmpty() && (!p.partialTransitionFunction.pos.containsKey(v) || !p.partialTransitionFunction.pos.get(v).equals("NN"))) {
                String[] split = p.getValue().split(" ", 2);
                if(WordLists.articles.contains(split[0]) && split.length >= 2) {
                    p.value = split[1];
                    p.partialTransitionFunction.denominator.put(v, "");
                }
            }
        }

        v.predictions.put("realization", bestRealizations);
        return bestRealizations;
    }

    /**
     * This helper function computes the n-best partial transition functions for a child of the currently considered vertex. As the realizations are already known,
     * only insertions must be considered.
     * @param amr the AMR graph
     * @param order the determined order
     * @param currentSentence the predictions for the generated sentence so far, considering only all vertices that occur in {@code order} before the current one
     * @param newWord the realization of the child currently considered
     * @param newEdge the edge corresponding to the child currently considered
     * @param instancePred the prediction for the realization of the instance edge, i.e. the parent node
     * @return the list of n-best partial transition functions
     */
    private PrunedList getBestAppendedRealizations(Amr amr, List<Edge> order, PrunedList currentSentence, PrunedList newWord, Edge newEdge, Prediction instancePred) {

        if(newWord == null) return currentSentence;

        if(newWord.isEmpty()) {
            Vertex newVertex;
            if(newEdge.isInstanceEdge()) newVertex = newEdge.getFrom();
            else newVertex = newEdge.getTo();
            Prediction p = new Prediction("",Math.log(Double.MIN_VALUE));
            p.partialTransitionFunction.pos.put(newVertex, PosHelper.POS_ANY);
            p.partialTransitionFunction.realization.put(newVertex, "");
            p.partialTransitionFunction.reordering.put(newVertex, newVertex.getOutgoingEdges());
            newWord.add(p);
        }

        PrunedList ret = new PrunedList(maxNrOfComposedPredictions);

        List<Prediction> beforeInsPredictions;
        List<Prediction> afterInsPredictions = Collections.singletonList(new Prediction("", 1));

        // INSERT_BETWEEN transitions with p = r (i.e. insertions after the realization of the currently considered node) occur rarely.
        // Of the few cases where they occur, the most frequent one is that after a vertex whose incoming edge label is ":domain", some form of "be" must be inserted.
        // This case is handled here by hand to improve efficiency.
        if(newEdge.isInstanceEdge()) {
            afterInsPredictions = Collections.singletonList(new Prediction("",1));
        }
        else {
            Vertex v = newEdge.getTo();
            if (!v.getIncomingEdges().isEmpty()) {
                String inLabel = v.getIncomingEdges().get(0).getLabel();
                if (inLabel.equals(":domain") && !v.getIncomingEdges().get(0).getFrom().getInstance().equals("possible")) {
                    List<Prediction> bePredictions = new ArrayList<>();

                    if (v.getIncomingEdges().get(0).getFrom().mode.equals("imperative")) {
                        bePredictions.add(new Prediction("be", 1));
                    } else {
                        bePredictions.add(new Prediction("", 0.33));
                        bePredictions.add(new Prediction("are", 0.33));
                        bePredictions.add(new Prediction("is", 0.33));
                    }
                    afterInsPredictions = bePredictions;
                }
            }
        }

        // iterate over the n-best transitions for all previous vertices
        for(Prediction p1 : currentSentence) {
            // iterate over the n-best transitions for the current vertex
            for(Prediction p2: newWord) {

                boolean beforeInsIsArg = false;

                if(newEdge.getTo().isPropbankEntry()) {
                    newEdge.getTo().setPos(p2.partialTransitionFunction.pos.getOrDefault(newEdge.getTo(), PosHelper.POS_ANY));
                }
                if(newEdge.isInstanceEdge() || p2.getValue().isEmpty() || instancePred.getValue().isEmpty() || newEdge.isInserted()) {
                    beforeInsPredictions = Collections.singletonList(new Prediction("", 1));
                    afterInsPredictions = Collections.singletonList(new Prediction("", 1));
                }
                else {

                    // compute the n-best INSERT-BETWEEN transitions

                    String relPos;

                    String fromRealization = p1.partialTransitionFunction.realization.get(newEdge.getFrom());
                    String fromVoice = p1.partialTransitionFunction.voice.get(newEdge.getFrom());
                    String toRealization = p2.getValue();

                    // determine the relative position of the child w.r.t its parent according to the current order
                    if(!order.contains(newEdge.getFrom().getInstanceEdge())) relPos = "d";
                    else if(order.indexOf(newEdge.getFrom().getInstanceEdge()) < order.indexOf(newEdge)) relPos = "r";
                    else relPos = "l";

                    beforeInsPredictions = new ArrayList<>();
                    List<Datum<String,String>> argEps;

                    if(fromRealization != null && ! fromRealization.isEmpty()) {
                        argEps = argInsertionMaxEnt.toDatumList(amr, newEdge, true, relPos, fromRealization, toRealization, fromVoice);
                    }
                    else {
                        argEps = Collections.emptyList();
                    }
                    if(!argEps.isEmpty()) {
                        beforeInsPredictions.addAll(argInsertionMaxEnt.getNBestSorted(argEps.get(0)));
                        beforeInsIsArg = true;
                    }
                    else {
                        List<Datum<String,String>> otherEps = othersInsertionMaxEnt.toDatumList(amr, newEdge, true, relPos, fromRealization, toRealization);

                        if(!otherEps.isEmpty()) {
                            beforeInsPredictions.addAll(othersInsertionMaxEnt.getNBestSorted(otherEps.get(0)));
                        }
                        else {
                            beforeInsPredictions = Collections.singletonList(new Prediction("",1));
                        }
                    }
                }

                // for each INSERT-BETWEEN transition
                for(Prediction beforeIns: beforeInsPredictions) {
                    for(Prediction afterIns: afterInsPredictions) {

                        // compute the current realization of the partial AMR graph
                        String value = ((p1.getValue() + " " + beforeIns.getValue() + " " + p2.getValue() +" "+ afterIns.getValue()).trim().replaceAll("  "," ")).toLowerCase();

                        // get the score of the realization
                        double lmFreeScore = p1.getLmFreeScore() + p2.getLmFreeScore() + (beforeInsIsArg?beforeInsArgWeight:beforeInsWeight) * Math.log(beforeIns.getScore()) + afterInsWeight * Math.log(afterIns.getScore());

                        boolean endBounded = order.indexOf(newEdge) == order.size()-1;
                        double score = lmFreeScore + lmWeight * scoreSent(value, true, endBounded);

                        Prediction p = new Prediction(value, score, lmFreeScore);

                        if(!newEdge.isInstanceEdge()) {
                            p.partialTransitionFunction.beforeIns.put(newEdge.getTo(), beforeIns.getValue());
                            p.partialTransitionFunction.afterIns.put(newEdge.getTo(), afterIns.getValue());
                        }

                        p.partialTransitionFunction.addCopy(p1.partialTransitionFunction);
                        p.partialTransitionFunction.addCopy(p2.partialTransitionFunction);
                        ret.add(p);
                    }

                }

            }
        }

        return ret;

    }

    /**
     * This function checks all syntactic annotations for a vertex v of some AMR graph, computes the n-best REALIZE transitions for each syntactic annotation,
     * and then returns the n-best REALIZE transitions along with their syntactic annotation. Per syntactic annotation, up to
     * {@link SecondStageProcessor#maxNrOfPosRealizationPredictions} are considered; in total, up to {@link SecondStageProcessor#maxNrOfRealizationPredictions} are returned.
     * @param amr the AMR graph
     * @param v the vertex
     * @return the n-best REALIZE transitions
     */
    private PrunedList getBestRealizationsForAllSyntacticAnnotations(Amr amr, Vertex v) {

        PrunedList predictions = new PrunedList(maxNrOfRealizationPredictions);

        // for links, we allow only default realizations
        if(v.isLink()) {
            for(String defaultRealization: defaultRealizer.getDefaultRealizations(v, new HashMap<>())) {
                Prediction p = new Prediction(defaultRealization, linkRealizationScore);
                p.partialTransitionFunction.realization.put(v, p.getValue());
                if(v.isPropbankEntry()) {
                    p.partialTransitionFunction.pos.put(v,PosHelper.POS_ANY);
                }
                predictions.add(p);
            }
            Prediction empty = new Prediction("", 0);
            empty.partialTransitionFunction.realization.put(v, "");
            predictions.add(empty);

        }

        // for named entities, we also allow only default realizations but we look up the most common realizations using the namedEntityCounts map
        else if(!v.name.isEmpty()) {

            String complexKey = v.name.toLowerCase() + "\t" + v.getInstance() + "\t";
            String simpleKey = v.getInstance() + "\t";

            for(String baseKey: Arrays.asList(complexKey, simpleKey)) {

                String leftKey = baseKey + StaticHelper.InstPosition.LEFT.getValue();
                String rightKey = baseKey + StaticHelper.InstPosition.RIGHT.getValue();
                String delKey = baseKey + StaticHelper.InstPosition.DELETE.getValue();

                int sumCount = namedEntityCounts.getOrDefault(leftKey, 0) + namedEntityCounts.getOrDefault(rightKey, 0) + namedEntityCounts.getOrDefault(delKey, 0);
                if (sumCount != 0) {
                    if (namedEntityCounts.containsKey(leftKey)) {
                        predictions.add(new Prediction(v.getInstance() + " " + v.name.toLowerCase(),  Math.log(namedEntityCounts.get(leftKey) / (double) sumCount)));
                    }
                    if (namedEntityCounts.containsKey(rightKey)) {
                        predictions.add(new Prediction(v.name.toLowerCase() + " " + v.getInstance(),  Math.log(namedEntityCounts.get(rightKey) / (double) sumCount)));
                    }
                    if (namedEntityCounts.containsKey(delKey)) {
                        predictions.add(new Prediction(v.name.toLowerCase(), Math.log(namedEntityCounts.get(delKey) / (double) sumCount)));
                    }

                    for (Prediction p : predictions) {
                        p.partialTransitionFunction.pos.put(v, "NN");
                        p.partialTransitionFunction.realization.put(v, p.getValue());
                    }
                    break;
                }
            }

            if(!predictions.isEmpty()) {
                Prediction best = predictions.get(0);
                predictions.clear();
                predictions.add(best);
            }

            if(predictions.isEmpty()) {
                predictions.add(new Prediction(v.name.toLowerCase(), 0));
                predictions.get(0).partialTransitionFunction.pos.put(v, "NN");
                predictions.get(0).partialTransitionFunction.realization.put(v, predictions.get(0).getValue());
            }

            // for countries, we also provide adjective forms (e.g. China -> Chinese)
            if(WordLists.countryforms.containsKey(v.name.toLowerCase())) {
                Prediction adjP = new Prediction(WordLists.countryforms.get(v.name.toLowerCase()), 0);
                adjP.partialTransitionFunction.pos.put(v, "JJ");
                adjP.partialTransitionFunction.realization.put(v, adjP.getValue());
                predictions.add(adjP);
            }

        }

        // if a DELETE transition has been applied in the first stage, the only allowed realization is the empty string
        else if(v.isDeleted()) {
            predictions.add(new Prediction("",0));
            predictions.get(0).partialTransitionFunction.realization.put(v, "");
        }

        // if v is a number, or not translatable for some other reason according to Vertex.isTranslatable(), we take only the default realizations
        else if(!v.isTranslatable()) {
            for(String defaultTranslation: defaultRealizer.getDefaultRealizations(v, new HashMap<>())) {
                Prediction defaultPred = new Prediction(defaultTranslation, 0);
                defaultPred.partialTransitionFunction.realization.put(v, defaultPred.getValue());
                predictions.add(defaultPred);
            }
        }
        else {
            // for PropBank entries, we try all possible POS tags determined by the POS maximum entropy model
            if(v.predictions.containsKey("pos") && v.isPropbankEntry()) {
                String oldPos = v.getPos();
                for (Prediction pred : v.predictions.get("pos")) {
                    double predLogScore = Math.log(pred.getScore());
                    String pos = pred.getValue();
                    v.setSimplifiedPos(pos);

                    List<Prediction> posPredictions = getBestRealizationsForAllSyntacticAnnotationsGivenPos(amr, v);
                    for (Prediction p : posPredictions) {
                        p.setScoreAndLmFreeScore(p.getScore()+(predLogScore * syntacticAnnotationWeights.get("pos")));
                    }
                    predictions.addAll(posPredictions);
                }

                // if no suitable REALIZE transition was found using all POS tags proposed by the POS maximum entropy model, we simply try out all POS tags
                if(predictions.isEmpty()) {
                    for (String pos: PosHelper.posCategories) {
                        v.setSimplifiedPos(pos);
                        predictions.addAll(getBestRealizationsForAllSyntacticAnnotationsGivenPos(amr, v));
                    }
                    // if we still have not found a suitable REALIZE transition, we simply set the realization to the instance itself, but we remove the sense tag
                    // (e.g. "want-01" becomes "want")
                    if(predictions.isEmpty()) {
                        predictions.add(new Prediction(v.getClearedInstance(), realizationWeight * Math.log(defaultRealizationScore * 0.25)));
                    }
                }
                v.setPos(oldPos);
            }
            else {
                if(v.isPropbankEntry()) {
                    throw new AssertionError("propbank entries must have a POS annotation by the POS maximum entropy model");
                }
                predictions.addAll(getBestRealizationsForAllSyntacticAnnotationsGivenPos(amr, v));
            }
        }

        return predictions;
    }

    /**
     * Given  a vertex v that has been assigned some POS tag, this function checks all syntactic annotations for v compatible with the assigned POS tag,
     * computes the n-best REALIZE transitions for each syntactic annotation and then returns the n-best REALIZE transitions along with their syntactic annotation;
     * the parameter n is defined by {@link SecondStageProcessor#maxNrOfPosRealizationPredictions}.
     * @param amr the AMR graph
     * @param v the vertex
     * @return the n-best REALIZE transitions
     */
    private PrunedList getBestRealizationsForAllSyntacticAnnotationsGivenPos(Amr amr, Vertex v) {

        // for nouns (i.e. POS = NN), we must only take the syntactic annotation key "number" into consideration
        if(v.getPos() != null && v.getPos().equals("NN") && v.predictions != null && v.predictions.containsKey("number")) {
            PrunedList ret = new PrunedList(maxNrOfPosRealizationPredictions);
            Map<String,Prediction> syntacticAnnotation = new HashMap<>();
            for(Prediction pred: v.predictions.get("number")) {
                syntacticAnnotation.clear();
                syntacticAnnotation.put("number", pred);
                ret.addAll(getBestRealizations(amr, v, syntacticAnnotation));
            }
            return ret;
        }

        // for verbs, we must take the syntactic annotation keys "voice" and "tense" into consideration
        else if(v.getPos() != null && v.getPos().equals("VB") && v.predictions != null && v.predictions.containsKey("voice")) {
            PrunedList ret = new PrunedList(maxNrOfPosRealizationPredictions);
            Map<String,Prediction> syntacticAnnotation = new HashMap<>();
            for(Prediction pred: v.predictions.get("voice")) {
                syntacticAnnotation.clear();
                syntacticAnnotation.put("voice", pred);

                if(v.getPos().equals("VBN") || !v.predictions.containsKey("tense")) {
                    ret.addAll(getBestRealizations(amr, v, syntacticAnnotation));
                }
                else {
                    for(Prediction tempPred: v.predictions.get("tense")) {
                        syntacticAnnotation.put("tense", tempPred);
                        ret.addAll(getBestRealizations(amr, v, syntacticAnnotation));
                    }
                }
            }
            return ret;
        }

        else if(v.getPos() != null && v.getPos().equals("VB") && v.predictions != null && v.predictions.containsKey("tense")) {
            PrunedList ret = new PrunedList(maxNrOfPosRealizationPredictions);
            Map<String,Prediction> syntacticAnnotation = new HashMap<>();
            for(Prediction tempPred: v.predictions.get("tense")) {
                syntacticAnnotation.put("tense", tempPred);
                ret.addAll(getBestRealizations(amr, v, syntacticAnnotation));
            }
            return ret;
        }

        else {
            return getBestRealizations(amr, v, new HashMap<>());
        }

    }

    /**
     * Given  a vertex v that has been assigned some POS tag and a syntactic annotation, this function computes the n-best REALIZE transitions
     * where the parameter n is defined by {@link SecondStageProcessor#maxNrOfPosRealizationPredictions}.
     * @param amr the AMR graph
     * @param v the vertex
     * @param syntacticAnnotation the syntactic annotation of the vertex
     * @return the n-best REALIZE transitions
     */
    public PrunedList getBestRealizations(Amr amr, Vertex v, Map<String,Prediction> syntacticAnnotation) {

        // turn the vertex v into its feature vector representation
        List<Event> events = realizationMaxentModel.toEvents(amr, v, true, syntacticAnnotation);

        PrunedList predictions = new PrunedList(maxNrOfPosRealizationPredictions);
        if(events.isEmpty()) {
            // add default realizations if no feature vector was found
            List<String> defaultRealizations = defaultRealizer.getDefaultRealizations(v, syntacticAnnotation);
            for(String defaultRealization: defaultRealizations) {
                predictions.add(new Prediction(defaultRealization, realizationWeight * Math.log(defaultRealizationScore)));
            }
        }
        else {

            // take the feature vector corresponding to v
            Event event = events.get(0);

            // if the concept corresponding to v has already been observed, we add to the list of REALIZE transitions the transitions predicted by our maximum
            // entropy model
            if(observedConcepts.contains(v.getInstance())) {
                predictions.addAll(realizationMaxentModel.getNBestSorted(event.getContext(), realizationMaxentModel.params.takeBestN, realizationMaxentModel.params.maxProbDecrement));
            }

            Set<Prediction> removables = new HashSet<>();
            Set<Prediction> addables = new HashSet<>();
            for (int i=0; i < predictions.size(); i++) {
                Prediction p = predictions.get(i);

                // remove all but the best prediction if their score is below the minTranslationScore
                if((i > 0 && p.getScore() < minTranslationScore) || (i == 0 && p.getScore() < minTranslationScore / 100)) {
                    removables.add(p);
                }
                else {

                    // handle passive verbs by manually adding the correct form of "be"
                    if(syntacticAnnotation.containsKey("voice") && syntacticAnnotation.get("voice").getValue().equals(GoldSyntacticAnnotations.PASSIVE)) {
                        if(syntacticAnnotation.containsKey("tense") && syntacticAnnotation.get("tense").getValue().equals(GoldSyntacticAnnotations.PRESENT)) {
                            addables.add(new Prediction("are " + p.value, p.getScore()));
                            p.value = "is " + p.value;
                        }
                        else if(syntacticAnnotation.containsKey("tense") && syntacticAnnotation.get("tense").getValue().equals(GoldSyntacticAnnotations.FUTURE)) {
                            p.value = "be " + p.value;
                        }
                        else {
                            addables.add(new Prediction("were " + p.value, p.getScore()));
                            p.value = "was " + p.value;
                        }
                    }

                    double score = Math.log(p.getScore()) * realizationWeight;
                    for(String syntacticAnnotationKey: syntacticAnnotation.keySet()) {
                        score += Math.log(syntacticAnnotation.get(syntacticAnnotationKey).getScore()) * syntacticAnnotationWeights.getOrDefault(syntacticAnnotationKey, 0d);
                    }

                    p.setScoreAndLmFreeScore(score);
                }
            }
            predictions.removeAll(removables);
            predictions.addAll(addables);

            // add all default realizations
            for (String defaultRealization : defaultRealizer.getDefaultRealizations(v, syntacticAnnotation)) {
                predictions.add(new Prediction(defaultRealization, realizationWeight * Math.log(defaultRealizationScore)));
            }

        }

        // update the partial transition function with the given syntactic annotation and realization
        for(Prediction p: predictions) {
            p.partialTransitionFunction.pos.put(v, v.getPos());
            p.partialTransitionFunction.realization.put(v, p.getValue());
            if(syntacticAnnotation.containsKey("number")) {
                p.partialTransitionFunction.number.put(v, syntacticAnnotation.get("number").getValue());
            }

            if(syntacticAnnotation.containsKey("voice")) {
                p.partialTransitionFunction.voice.put(v, syntacticAnnotation.get("voice").getValue());
            }

            if(syntacticAnnotation.containsKey("tense")) {
                p.partialTransitionFunction.tense.put(v, syntacticAnnotation.get("tense").getValue());
            }
        }

        return predictions;
    }

    /**
     * This function adds the punctuation to a vertex as described in the thesis. Although this is considered
     * a part of the post-processing in the thesis, we handle punctuation already in the second stage to improve efficiency.
     * @param v the vertex
     * @param predictions the partial transition functions assigned to this vertex, along with their scores
     * @param isLast whether this vertex is the last vertex according to the reordering assigned to its parent
     */
    private void applyPunctuation(Vertex v, PrunedList predictions, boolean isLast) {

        String interp = ".";
        if(v.mode.equals("interrogative")) interp = "?";
        else {
            for(Edge e : v.getOutgoingEdges()) {
                if(e.getTo().getInstance().equals("amr-unknown")) interp = "?";
            }
        }

        for(Prediction prediction: predictions) {
            int words = prediction.getValue().split(" ").length;
            if(interp.equals("?") || interp.equals("!")) {
                prediction.value = prediction.value + " " + interp;
                prediction.partialTransitionFunction.punctuation.put(v, interp);
            }
            else {
                if(words > 4) {
                    prediction.value = prediction.value + " .";
                    prediction.partialTransitionFunction.punctuation.put(v, ".");
                }
                else {
                    if(!isLast) {
                        prediction.value = prediction.value + " ,";
                        prediction.partialTransitionFunction.punctuation.put(v, ",");
                    }
                }
            }
        }
    }

    /**
     * Helper function that converts a sentence into a list of words.
     * @param sent the space-separated sentence
     * @return the list of words
     */
    private static List<String> toArray(String sent) {
        return Arrays.asList(sent.split(" "));
    }

    /**
     * Helper function to score a sentence using a language model.
     * @param sent the space-separated sentence
     * @param startBounded whether "start of sentence"-tags should be added at the start of the sentence
     * @param endBounded whether "end of sentence"-tags should be added at the end of the sentence
     * @return the language model score assigned to the sentence
     */
    private double scoreSent(String sent, boolean startBounded, boolean endBounded) {
        List<String> sentence = toArray(sent);

        int articleCount = 0, remainingCount = 0;
        for(String word: sentence) {
            if(WordLists.articles.contains(word)) articleCount++;
            else remainingCount++;
        }
        double quotient = remainingCount + articleCount * articleLmWeight;
        return scoreSentence(sentence, languageModel, startBounded, endBounded)/quotient;
    }

    /**
     * This function checks manually for several situations in which a vertex is not allowed to have an article and slightly improves both the speed and the quality
     * of the generation process.
     * @param v the vertex
     * @return true iff the vertex is not allowed to have an article
     */
    static boolean articleDisallowed(Vertex v) {

        // we check all outgoing edges
        for(Edge e: v.getOutgoingEdges()) {
            if(e.isInstanceEdge()) continue;

            // we check whether the child node represents a pronoun
            boolean toPronoun = WordLists.pronouns.contains(e.getTo().getInstance());

            // if the child's POS tag is one of "IN", "DT", "PRP", "PRP$" and it is a mod(ification) of v, we disallow articles
            if(e.getLabel().equals(":mod") && !e.getTo().isPropbankEntry() && Arrays.asList("IN","DT","PRP","PRP$").contains(e.getTo().getPos())) return true;

            // if the child indicates possession (e.g. "his house") and is either a pronoun or a link to some other concept, we disallow articles
            if(e.getLabel().equals(":poss") && (e.getTo().isLink() || toPronoun)) return true;

            // if a pronoun is attached to the vertex as either ARG0 or mod, we disallow articles
            if(e.getLabel().equals(":ARG0") && toPronoun) return true;
            if(e.getLabel().equals(":mod") && toPronoun) return true;

            // if a quantity is specified, we disallow articles
            if(e.getLabel().equals(":quant")) return true;

        }
        return false;
    }

    /**
     * Helper function to score a sentence using a language model.
     * @param sentence the sentence, represented as a list of words
     * @param lm the language model to be used
     * @param startBounds whether "start of sentence"-tags should be added at the start of the sentence
     * @param endBounds whether "end of sentence"-tags should be added at the end of the sentence
     * @return the language model score assigned to the sentence
     */
    static float scoreSentence(final List<String> sentence, final ArrayEncodedNgramLanguageModel<String> lm, boolean startBounds, boolean endBounds) {
        final List<String> sentenceWithBounds = new BoundedList<>(sentence, lm.getWordIndexer().getStartSymbol(), lm.getWordIndexer().getEndSymbol());

        final int lmOrder = lm.getLmOrder();
        float sentenceScore = 0.0f;
        if(startBounds) {
            for (int i = 1; i < lmOrder - 1 && i <= sentenceWithBounds.size() + 1; ++i) {
                final List<String> ngram = sentenceWithBounds.subList(-1, i);

                if(!nGramScores.containsKey(ngram)) {
                    nGramScores.put(ngram, lm.getLogProb(ngram));
                }
                sentenceScore += nGramScores.get(ngram);
            }
        }
        for (int i = lmOrder - 1; i < sentenceWithBounds.size() + (endBounds?2:1); ++i) {
            final List<String> ngram = sentenceWithBounds.subList(i - lmOrder, i);
            if(!nGramScores.containsKey(ngram)) {
                nGramScores.put(ngram, lm.getLogProb(ngram));
            }
            sentenceScore += nGramScores.get(ngram);
        }
        return sentenceScore;
    }
}