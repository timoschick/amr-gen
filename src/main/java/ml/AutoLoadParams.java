package ml;

import dag.Amr;
import edu.stanford.nlp.ling.Datum;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * This class contains parameters required for automatically loading a maximum entropy model.
 */
public class AutoLoadParams {

    /**
     * The set of training data used to train the maximum entropy model
     */
    public List<Amr> trainingData;

    /**
     * The set of development data used to train the maximum entropy model
     */
    public List<Amr> devData;

    /**
     * The numbers of iterations to try for training the maximum entropy model. This parameter is only required
     * for the OpenNLP implementation (see {@link OpenNlpMaxentModelImplementation}).
     */
    public List<Integer> iterNrs = Collections.singletonList(10);

    /**
     * The maximum number of predictions to be returned by the {@link OpenNlpMaxentModelImplementation#getNBestSorted(String[])} method (for OpenNLP models)
     * or the {@link StanfordMaxentModelImplementation#getNBestSorted(Datum)} method (for Stanford models).
     */
    public int takeBestN = 1;

    /**
     * Only predictions with {@code probability >= best_prediction_probability - maxProbDecrement} are returned by
     * the {@link OpenNlpMaxentModelImplementation#getNBestSorted(String[])} method (for OpenNLP models)
     * or the {@link StanfordMaxentModelImplementation#getNBestSorted(Datum)} method (for Stanford models).
     */
    public double maxProbDecrement = 0.05;

    /**
     * Makes a copy of this AutoLoadParams instance.
     * @return the copy
     */
    public AutoLoadParams makeCopy() {
        AutoLoadParams params = new AutoLoadParams();
        params.trainingData = trainingData;
        params.devData = devData;
        params.iterNrs = new ArrayList<>(iterNrs);
        params.takeBestN = takeBestN;
        params.maxProbDecrement = maxProbDecrement;
        return params;
    }
}
