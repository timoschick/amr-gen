package gen;

import dag.Amr;
import dag.Vertex;
import edu.stanford.nlp.ling.BasicDatum;
import edu.stanford.nlp.ling.Datum;
import edu.stanford.nlp.ling.RVFDatum;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.Pair;
import misc.Debugger;
import misc.WordLists;
import ml.AutoLoadParams;
import ml.FirstStageMaxentModel;

import java.io.IOException;
import java.util.*;

/**
 * This class implements the transition system for the first stage defined in the thesis; i.e., it handles MERGE, SWAP, DELETE and KEEP transitions.
 * Methods of this class can be used both for training the maximum entropy model for the first stage (see {@link FirstStageMaxentModel}) and for
 * actually processing AMR graphs during testing.
 */
public class FirstStageProcessor {

    // the maximum entropy model for the first stage
    private FirstStageMaxentModel maxentModel;

    // the AMR graph that is currently being processed
    private Amr amr;

    // the node buffer
    private List<Vertex> buffer;

    // this set stores all pairs of vertices that have already been swapped; it is required to prevent that two vertices are swapped twice
    private Set<Pair<Vertex,Vertex>> swapMemory;

    /**
     * Creates a new processor for the first generation stage, using the given maximum entropy model.
     * @param firstStageMaxentModel the maximum entropy model to be used
     */
    public FirstStageProcessor(FirstStageMaxentModel firstStageMaxentModel) {
        this.maxentModel = firstStageMaxentModel;
    }

    /**
     * Performs the gold MERGE, SWAP, DELETE and KEEP transitions for a list of AMR graphs; this function
     * may also be used for training the maximum entropy model {@link FirstStageProcessor#maxentModel}.
     * @param trainingAmrs the AMR graphs for which gold transitions should be applied; if this function is used for training, this list should contain all training AMRs.
     * @param devAmrs another list of AMR graphs for which gold transitions should be applied; if this function is used for training, this list should contain all
     *                development AMRs. Otherwise, this list may simply be kept empty.
     * @param params AutoLoad parameters for training, see {@link AutoLoadParams}, may be {@code null} if no training should be performed.
     * @param filename the name of the file in which the trained classifier should be saved, may be {@code null} if no training should be performed.
     * @param train whether training should be performed using the determined gold transitions
     * @throws IOException if something goes wrong with saving the trained classifier
     */
    public void performGoldTransitionsFirstStage(List<Amr> trainingAmrs, List<Amr> devAmrs, AutoLoadParams params, String filename, boolean train) throws IOException {

        Debugger.println("getting training data for first stage");
        List<Datum<String,String>> trainingData = getDataForTrainingFirstStage(trainingAmrs);
        Debugger.println("done getting training data for first stage");

        Debugger.println("getting dev data for first stage");
        List<Datum<String,String>> devData = getDataForTrainingFirstStage(devAmrs);
        Debugger.println("done getting dev data for first stage");

        if(!train) return;

        Debugger.println("training first stage");
        maxentModel.trainWithData(trainingData, devData, params, filename);
        Debugger.println("done training first stage");
    }

    /**
     * Processes a list of AMR graphs by performing all gold MERGE, SWAP, DELETE and KEEP transitions and returns the corresponding (feature, transition) vectors.
     * @param amrs the AMR graphs to process
     * @return the (feature, transition) vectors
     */
    private List<Datum<String,String>> getDataForTrainingFirstStage(List<Amr> amrs) {
        List<Datum<String,String>> ret = new ArrayList<>();
        for(Amr amr: amrs) {
            ret.addAll(getDataForTrainingFirstStage(amr));
        }
        return ret;
    }

    /**
     * Processes a single AMR graph by performing all gold MERGE, SWAP, DELETE and KEEP transitions and returns the corresponding (feature, transition) vectors.
     * This is basically an implementation of the first half of the training algorithm described in the thesis.
     * @param amr the AMR graph to process
     * @return the (feature, transition) vectors
     */
    private List<Datum<String,String>> getDataForTrainingFirstStage(Amr amr) {

        List<Datum<String,String>> trainingData = new ArrayList<>();

        this.amr = amr;
        buffer = amr.dag.getVerticesBottomUp();
        swapMemory = new HashSet<>();

        while(!buffer.isEmpty()) {

            Vertex current = buffer.remove(0);

            List<Datum<String,String>> datumList = maxentModel.toDatumList(amr, current, false);
            if(datumList.isEmpty()) continue;

            Datum<String,String> datum = datumList.get(0);

            // apply the correct action
            if(applyTransition(current, datum.label())) {
                trainingData.add(datum);
            }
            else {
                if(datum instanceof RVFDatum) {
                    ((RVFDatum<String, String>) datum).setLabel(GoldTransitions.KEEP);
                }
                else if(datum instanceof BasicDatum) {
                    ((BasicDatum<String,String>) datum).setLabel(GoldTransitions.KEEP);
                }

                else {
                    throw new AssertionError("unknown or unsupported type of datum, " + datum.getClass() + " is neither RVFDatum nor BasicDatum");
                }

            }
        }

        return trainingData;
    }

    /**
     * Processes a list of AMR graphs by applying all MERGE, SWAP, DELETE and KEEP transitions predicted by the maximum entropy model {@link FirstStageProcessor#maxentModel}.
     * This function is used during testing; it implements the algorithm {@code generateGreedy_restr} as defined in the thesis.
     * @param amrs the AMR graphs to process
     */
    public void processFirstStage(List<Amr> amrs) {
        for(Amr amr: amrs) {
            processFirstStage(amr);
        }
    }

    /**
     * Processes an AMR graph by applying all MERGE, SWAP, DELETE and KEEP transitions predicted by the maximum entropy model {@link FirstStageProcessor#maxentModel}.
     * This function is used during testing; it implements the algorithm {@code generateGreedy_restr} as defined in the thesis.
     * @param amr the AMR graph to process
     */
    public void processFirstStage(Amr amr) {

        this.amr = amr;
        buffer = amr.dag.getVerticesBottomUp();
        swapMemory = new HashSet<>();

        while(!buffer.isEmpty()) {

            Vertex current = buffer.remove(0);

            String bestTransition = "";
            double bestScore = -Double.MAX_VALUE;

            List<Datum<String,String>> datumList = maxentModel.toDatumList(amr, current, "", true);
            if(datumList.isEmpty()) continue;

            Counter<String> probs = maxentModel.classifier.logProbabilityOf(datumList.get(0));

            for(String transition: maxentModel.classifier.labels()) {

                if(!isApplicable(current, transition)) continue;

                double prob = probs.getCount(transition);

                if(prob > bestScore) {
                    bestScore = prob;
                    bestTransition = transition;
                }

            }

            // apply the best found action
            if(!bestTransition.isEmpty()) {
                applyTransition(current, bestTransition);
            }
        }
    }

    /**
     * Checks whether a transition is applicable if a given vertex is the top element of the node buffer.
     * @param current the top element of the node buffer
     * @param transition the transition to check
     * @return whether {@code transition} is applicable with {@code current} on top of the {@link FirstStageProcessor#buffer}
     */
    private boolean isApplicable(Vertex current, String transition) {

        // for vertices that must be deleted according to forceDelete, the gold transition may not be DELETE
        if(!transition.equals(GoldTransitions.DELETE)) {
            if(forceDelete(current)) return false;
        }

        // links and nodes with names may only be kept
        if((current.isLink() || !current.name.isEmpty()) && !transition.equals(GoldTransitions.KEEP)) {
            return false;
        }

        // KEEP transitions are always allowed
        if(transition.equals(GoldTransitions.KEEP)) return true;

        // if a vertex is contained within the set NEVER_DELETE, it may never be deleted
        if(transition.equals(GoldTransitions.DELETE)) return !WordLists.NEVER_DELETE.contains(current.getInstance());

        if(transition.equals(GoldTransitions.SWAP)) {
            if(current.getIncomingEdges().isEmpty()) return false;
            Vertex parent = current.getIncomingEdges().get(0).getFrom();
            if(!parent.name.isEmpty()) return false;
            if(swapMemory.contains(new Pair<>(current, parent))) return false;
            return true;
        }

        if(transition.equals(GoldTransitions.MERGE)) {
            if(current.getIncomingEdges().isEmpty()) return false;
            Vertex parent = current.getIncomingEdges().get(0).getFrom();
            String instance = current.getInstance();
            String parentInstance = parent.getInstance();
            String pair = parentInstance + "\t" + instance;

            // MERGE is only allowed if we observed a merge including parentInstance and instance during training.
            return maxentModel.bestMerges.containsKey(pair);
        }

        return false;

    }

    /**
     * We enforce a DELETE transition on a small number of vertices; this function checks whether this is the case for a given vertex.
     * Peforming this check slightly improves both the speed and the result of our generator.
     * @param v the vertex to check
     * @return true iff the vertex must be deleted
     */
    private boolean forceDelete(Vertex v) {
        // we query a list of concepts which should always be deleted
        if(WordLists.ALWAYS_DELETE.contains(v.getInstance())) return true;

        // if some ARG-parent of the vertex is a verb in imperative form, all instances of "you" and "we" are removed
        if(!v.getIncomingEdges().isEmpty()) {
            Vertex parent = v.getIncomingEdges().get(0).getFrom();
            if ((v.getInstance().equals("you") || v.getInstance().equals("we")) && parent.mode.equals("imperative") && v.getIncomingEdges().get(0).getLabel().startsWith(":ARG")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Applies a transition.
     * @param current the top element of the node biffer
     * @param transition the transition to apply
     * @return true if the application was successful, false otherwise
     */
    private boolean applyTransition(Vertex current, String transition) {

        Vertex parent = current.getIncomingEdges().isEmpty()?null:current.getIncomingEdges().get(0).getFrom();

        switch(transition) {
            case GoldTransitions.KEEP:
                return true;
            case GoldTransitions.DELETE:
                current.annotation.delete = true;
                return true;
            case GoldTransitions.SWAP:
                if(!swapMemory.contains(new Pair<>(current, parent))) {
                    amr.swap(parent, current);
                    swapMemory.add(new Pair<>(parent, current));
                    buffer.remove(parent);
                    buffer.add(0, current);
                    buffer.add(0, parent);
                    return true;
                }
                return false;
            case GoldTransitions.MERGE:
                String instance = current.getInstance();
                String parentInstance = parent.getInstance();
                String pair = parentInstance + "\t" + instance;
                String bestMerge = maxentModel.bestMerges.get(pair);
                if(bestMerge != null) {
                    String[] comps = bestMerge.split("\t");
                    String inst = comps[0];
                    String pos = comps[1];
                    amr.merge(parent, current, inst, pos);
                    return true;
                }
                return false;
        }
        return false;
    }
}