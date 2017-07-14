package gen;

import dag.Amr;
import dag.Edge;
import dag.Vertex;
import edu.stanford.nlp.ling.Datum;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.Pair;
import misc.Debugger;
import misc.StaticHelper;
import ml.SiblingReorderMaxentModel;
import ml.ParentChildReorderMaxentModel;
import ml.Prediction;

import java.util.*;
import java.util.stream.Collectors;

/**
 * This class provides methods to compute the probability of a REORDER transition and to get the takeBestN-best REORDER transitions
 * as defined in the thesis (Section 4.2.1 Modeling).
 */
public class PositionHelper {

    /**
     * This parameter sets the maximum number of outgoing edges allowed; for any vertex with more outgoing edges, no reordering is performed.
     * While this slightly worsens the results, it makes the generation process much faster. The default value of 7 was determined impirically.
     */
    private static final int PERFORMANCE_MAX_NUMBER_OF_OUT_EDGES = 7;

    // for the meaning takeBestN and maxProbDecrement, see the corresponding fields in the maximum entropy model implementations
    public int takeBestN = 5;
    public double maxProbDecrement = 0.1d;

    private final ParentChildReorderMaxentModel parentChildReorderMaxentModel;
    private final SiblingReorderMaxentModel leftMaxEnt, rightMaxEnt;

    // these maps store computed probabilities of the form e1 <_l e2 and e1 <_r e2 (see Eq. 19, Section 4.2.1 Modelng) to make
    // the process of determining the n-best REORDER transitions more efficient
    private Map<Pair<Edge,Edge>, Pair<Double,Double>> leftMaxEntProbs = new HashMap<>();
    private Map<Pair<Edge,Edge>, Pair<Double,Double>> rightMaxEntProbs = new HashMap<>();

    /**
     * Creates a new PositionHelper.
     * @param parentChildReorderMaxentModel A maximum entropy model for &lt;<sub>*</sub> as defined in Eq. 19 (Section 4.2.1 Modeling)
     * @param leftMaxEnt A maximum entropy model for &lt;<sub>l</sub> as defined in Eq. 19 (Section 4.2.1 Modeling)
     * @param rightMaxEnt A maximum entropy model for &lt;<sub>r</sub> as defined in Eq. 19 (Section 4.2.1 Modeling)
     */
    public PositionHelper(ParentChildReorderMaxentModel parentChildReorderMaxentModel, SiblingReorderMaxentModel leftMaxEnt, SiblingReorderMaxentModel rightMaxEnt) {
        this.parentChildReorderMaxentModel = parentChildReorderMaxentModel;
        this.leftMaxEnt = leftMaxEnt;
        this.rightMaxEnt = rightMaxEnt;
    }

    /**
     * Returns the n-best reorderings (according to {@link PositionHelper#takeBestN}) for the outgoing edges of a vertex.
     * @param amr the corresponding AMR graph
     * @param v the vertex to consider
     * @param leftChildInsertions the children inserted to the left of v
     * @param realization the realization of v
     * @param voice the voice of v. If v is a passive verb, this must be set to {@link GoldSyntacticAnnotations#PASSIVE}.
     *              Otherwise, it should be left empty or set to {@link GoldSyntacticAnnotations#ACTIVE}.
     * @return a list containing the n-best reorderings along with their probabilities
     */
    public List<Pair<List<Edge>, Double>> getNBestReorderings(Amr amr, Vertex v, List<Edge> leftChildInsertions, String realization, String voice) {

        List<Edge> allOutEdges = new ArrayList<>(v.getOutgoingEdges());
        allOutEdges.addAll(leftChildInsertions);

        if(allOutEdges.size() <= 1 || allOutEdges.size() > PERFORMANCE_MAX_NUMBER_OF_OUT_EDGES) {
            if(allOutEdges.size() > PERFORMANCE_MAX_NUMBER_OF_OUT_EDGES) {
                // Debugger.printlnErr("too many out edges to process, found " + allOutEdges.size() + ", max is " + PERFORMANCE_MAX_NUMBER_OF_OUT_EDGES);
                return Collections.singletonList(new Pair<>(allOutEdges, 1d));
            }
            // if we have only one vertex, no reordering is required
            return Collections.emptyList();
        }

        boolean deleteInstanceEdge = v.isDeleted();

        Edge instanceEdge = v.getInstanceEdge();
        List<Edge> allOutEdgesWithoutInstanceEdge = new ArrayList<>(allOutEdges);
        allOutEdgesWithoutInstanceEdge.remove(instanceEdge);

        // a map to store the probability of c < v for each child c of v
        Map<Edge,Double> edgeIsLeftOfRelProbabilities = new HashMap<>();

        // if we have only two vertices, one of which is deleted, no reordering is required
        if(deleteInstanceEdge && v.getOutgoingEdges().size() == 2) {
            return Collections.emptyList();
        }

        List<Pair<List<Edge>, Double>> reorderings = new ArrayList<>(takeBestN +1);

        if(!deleteInstanceEdge) {
            for(Edge e: allOutEdgesWithoutInstanceEdge) {
                if(leftChildInsertions.contains(e)) {
                    edgeIsLeftOfRelProbabilities.put(e, 1d);
                }
                else {

                    List<Prediction> result;

                    Datum<String,String> context = parentChildReorderMaxentModel.toEvent(amr, v, e, "", true, realization, voice);
                    result = parentChildReorderMaxentModel.getNBestSorted(context, 1, 0);

                    for (Prediction p : result) {
                        if (p.getValue().equals(GoldTransitions.CHILD_BEFORE_PARENT)) {
                            edgeIsLeftOfRelProbabilities.put(e, p.getScore());
                        } else {
                            edgeIsLeftOfRelProbabilities.put(e, 1 - p.getScore());
                        }
                    }
                }
            }
        }

        // compute a list of all possible permutations, i.e. all possible reorderings
        List<List<Edge>> allCombinations;
        if(deleteInstanceEdge) {
            allCombinations = StaticHelper.listPermutations(allOutEdgesWithoutInstanceEdge);
        }
        else {
            allCombinations = StaticHelper.listPermutations(allOutEdges);
        }

        for(List<Edge> permutation: allCombinations) {

            double prob = getProbability(v, permutation, edgeIsLeftOfRelProbabilities);
            Pair<List<Edge>,Double> pred = new Pair<>(permutation, prob);

            reorderings.add(pred);

            reorderings.sort((o1, o2) -> {
                if(o1.second().doubleValue() == o2.second().doubleValue()) return 0;
                if(o1.second() > o2.second()) return -1;
                return 1;
            });

            if(reorderings.size() > takeBestN) {
                reorderings.remove(reorderings.size()-1);
            }
        }

        double bestScore = reorderings.get(0).second();
        Set<Pair<List<Edge>, Double>> removables = new HashSet<>();
        for(Pair<List<Edge>, Double> reordering: reorderings) {
            if(reordering.second() < bestScore - maxProbDecrement) {
                removables.add(reordering);
            }
        }
        reorderings.removeAll(removables);

        return reorderings;

    }

    /**
     * Computes the probability of a reordering.
     * @param vertex the vertex for which the reordering should be applied
     * @param reordering the actual reordering that must contain {@code vertex} and all of its children.
     * @param edgeIsLeftOfRelProbabilities  this map must contain the probability of {@code c < vertex} for each child {@code c} of {@code vertex}.
     * @return the probability of the reordering
     */
    private double getProbability(Vertex vertex, List<Edge> reordering, Map<Edge,Double> edgeIsLeftOfRelProbabilities) {

        Edge instanceEdge = vertex.getInstanceEdge();
        boolean deleteInstanceEdge = vertex.isDeleted();

        // make sure date entries are ordered
        List<String> labels = reordering.stream().map(e -> e.getLabel()).collect(Collectors.toList());
        int monthIndex = labels.indexOf(":month");
        int dayIndex = labels.indexOf(":day");
        int yearIndex = labels.indexOf(":year");
        if(monthIndex >= 0 && dayIndex >= 0) {
            if(monthIndex > dayIndex) return 0;
        }
        if(monthIndex >= 0 && yearIndex >= 0) {
            if(monthIndex > yearIndex) return 0;
        }
        if(dayIndex >= 0 && yearIndex >= 0) {
            if(dayIndex > yearIndex) return 0;
        }

        // make sure that multi-sentences are ordered
        List<String> sntList = labels.stream().filter(e -> e.matches(":snt[1-9]")).collect(Collectors.toList());
        List<String> sntClone = new ArrayList<>(sntList);
        Collections.sort(sntClone);
        if(!sntClone.equals(sntList)) {
            return 0;
        }

        // make sure that the op's are ordered and between two ops is nothing but the instance edge, and the instance edge is either deleted or at position [MAX_OP-1]
        List<Edge> opRelList = reordering.stream().filter(e -> e == instanceEdge || e.getLabel().matches(":op[1-9]")).collect(Collectors.toList());
        if(opRelList.contains(instanceEdge) && opRelList.size()>2) {
            if(opRelList.indexOf(instanceEdge) != opRelList.size()-2) return 0;
        }
        opRelList.remove(instanceEdge);
        if(opRelList.size() >= 2) {
            int expectedIndex = 1;
            for(Edge e: opRelList) {
                if(!e.getLabel().equals(":op"+expectedIndex)) return 0;
                expectedIndex++;
            }

            List<Edge> allButRelList = new ArrayList<>(reordering);
            allButRelList.remove(instanceEdge);

            boolean foundFirstOp = false, foundLastOp = false;

            for(Edge e: allButRelList) {
                if(e.getLabel().equals(":op1")) {
                    foundFirstOp = true;
                }
                else if(foundFirstOp && ! foundLastOp) {
                    if(!e.getLabel().matches(":op[1-9]")) {
                        foundLastOp = true;
                    }
                }
                else if(foundLastOp) {
                    if(e.getLabel().matches(":op[1-9]")) {
                        return 0;
                    }
                }
            }
        }

        List<Edge> left, right;
        double relPosProb = 1;

        // construct left and right half
        if (deleteInstanceEdge) {
            left = reordering;
            right = Collections.emptyList();
        } else {
            int instanceIndex = reordering.indexOf(instanceEdge);

            left = reordering.subList(0, instanceIndex);

            right = reordering.subList(instanceIndex + 1, reordering.size());
            for (Edge e : left) {
                relPosProb *= edgeIsLeftOfRelProbabilities.get(e);
            }
            for (Edge e : right) {
                relPosProb *= (1 - edgeIsLeftOfRelProbabilities.get(e));
            }
        }

        double leftProb = getProbability(vertex, left, leftMaxEnt);
        double rightProb = getProbability(vertex, right, rightMaxEnt);
        return relPosProb * leftProb * rightProb;
    }

    /**
     * Computes the probability of either the left or the right half of a reordering.
     * @param vertex the vertex for which the reordering should be applied
     * @param partialReordering the left or right half of a reordering.
     * @param siblingReorderMaxentModel this must be a maximum entropy model either for <_l or for <_r as required in Eq. 19 (Section 4.2.1 Modeling)
     * @return the probability of this half of the reordering
     */
    private double getProbability(Vertex vertex, List<Edge> partialReordering, SiblingReorderMaxentModel siblingReorderMaxentModel) {

        double prob = 1;

        for(int i=0; i < partialReordering.size(); i++) {
            for(int j = i+1; j < partialReordering.size(); j++) {

                Edge tmp1 = partialReordering.get(i);
                Edge tmp2 = partialReordering.get(j);

                int comp = SiblingReorderMaxentModel.compare(tmp1, tmp2);
                Edge e1 = siblingReorderMaxentModel.getFirst(comp, tmp1, tmp2);
                Edge e2 = siblingReorderMaxentModel.getSecond(comp, tmp1, tmp2);
                String result = SiblingReorderMaxentModel.optionalInverse(comp, GoldTransitions.E1_BEFORE_E2);
                prob *= getProbability(vertex, e1, e2, result, siblingReorderMaxentModel, partialReordering);
            }
        }

        return prob;
    }

    /**
     * Computes the probability of {@code e1} occuring before {@code e2} (if {@code result ==} {@link GoldTransitions#E1_BEFORE_E2})
     * or of {@code e1} occuring after {@code e2} (if {@code result ==} {@link GoldTransitions#E2_BEFORE_E1}).
     * @param vertex The vertex for which both {@code e1} and {@code e2} are outgoing edges
     * @param e1 the first edge
     * @param e2 the second edge
     * @param result either {@link GoldTransitions#E1_BEFORE_E2} or {@link GoldTransitions#E2_BEFORE_E1}, depending on
     *               whether the probability of {@code e1 < e2} or {@code e2 < e1} is needed
     * @param siblingReorderMaxentModel the maximum entropy model to use for the probability computation
     * @param reordering the complete reordering currently considered
     * @return
     */
    private double getProbability(Vertex vertex, Edge e1, Edge e2, String result, SiblingReorderMaxentModel siblingReorderMaxentModel, List<Edge> reordering) {

        Map<Pair<Edge,Edge>, Pair<Double,Double>> maxEntProbs;
        if(siblingReorderMaxentModel == leftMaxEnt) {
            maxEntProbs = leftMaxEntProbs;
        }
        else maxEntProbs = rightMaxEntProbs;

        Pair<Edge,Edge> pair = new Pair<>(e1,e2);
        if(!maxEntProbs.containsKey(pair)) {

            Datum<String,String> datum = siblingReorderMaxentModel.toEvent(vertex, e1, e2, result, reordering);
            Counter<String> eval = siblingReorderMaxentModel.classifier.probabilityOf(datum);

            Pair<Double,Double> probs = new Pair<>();
            probs.setFirst(eval.getCount(GoldTransitions.E1_BEFORE_E2));
            probs.setSecond(eval.getCount(GoldTransitions.E2_BEFORE_E1));

            maxEntProbs.put(pair, probs);
        }

        if(result.equals(GoldTransitions.E1_BEFORE_E2)) return maxEntProbs.get(pair).first();
        else return maxEntProbs.get(pair).second();
    }

}
