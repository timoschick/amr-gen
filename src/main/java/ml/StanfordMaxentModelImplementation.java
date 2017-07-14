package ml;

import dag.Amr;
import dag.Vertex;
import edu.stanford.nlp.classify.*;
import edu.stanford.nlp.ling.BasicDatum;
import edu.stanford.nlp.ling.Datum;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import misc.Debugger;
import opennlp.tools.ml.maxent.GISModel;
import opennlp.tools.ml.model.Event;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Function;

/**
 * An implementation of a maximum entropy model based on the {@link LinearClassifier} class from Stanford NLP.
 */
public abstract class StanfordMaxentModelImplementation {

    /**
     * The default minimum value of sigma to be used for training; see {@link LinearClassifier}.
     */
    private static final double MIN_SIGMA = 0.01;

    /**
     * The default maximum value of sigma to be used for training; see {@link LinearClassifier}.
     */
    private static final double MAX_SIGMA = 2.0;

    /**
     * The autoload params used by this model, see {@link AutoLoadParams}
     */
    public AutoLoadParams params;

    /**
     * A flag indicating whether this maximum entropy model uses real valued features (RVFs)
     */
    public boolean usesRVF = false;

    /**
     * The actual maximum entropy classifier used by this implementation
     */
    public LinearClassifier<String, String> classifier;

    /**
     * The feature manager used by this model, see {@link FeatureManager}
     */
    protected FeatureManager featureManager;

    private double bestSigma;
    private double bestScore;

    /**
     * Creates a new Stanford maximum entropy model.
     */
    public StanfordMaxentModelImplementation() {
        featureManager = new FeatureManager();
    }

    /**
     * Extracts from a vertex of an AMR graph a list of feature vectors with outcomes, represented by the {@link Datum} class.
     * @param amr the AMR graph
     * @param vertex the vertex
     * @param forTesting if the feature vector and the outcome should be the gold feature vector and the gold outcome, set this to false.
     *                   Otherwise, if the feature vector should be computed for testing, set this to true.
     * @return the list of datum objects
     */
    public abstract List<Datum<String, String>> toDatumList(Amr amr, Vertex vertex, boolean forTesting);

    /**
     * This may be used to differentiate between "positive" and "negative" results to allow the {@link LossEvaluator} to count true/false positives and negatives
     * and compute various metrics based on this information.
     * @param output the output to check
     * @return true iff the output represents a "positive" event
     */
    public boolean isPositive(String output) {
        return false;
    }

    /**
     * Automatically loads this maximum entropy model using the parameters specified by an instance of {@link AutoLoadParams}
     * @param params the parameters to use for loading the model
     * @param filename the file name under which the model can be found
     * @param train whether the model should be (re)trained or left as is
     */
    public void autoLoad(AutoLoadParams params, String filename, boolean train) throws IOException {
        autoLoad(params, filename, train, (this::test), params.devData, -1);
    }

    /**
     * Automatically loads this maximum entropy model using the parameters specified by an instance of {@link AutoLoadParams}
     * @param params the parameters to use for loading the model
     * @param filename the file name under which the model can be found
     * @param train whether the model should be (re)trained or left as is
     * @param testFunction the function used for testing which should map a list of AMR graphs to an instance of {@link LossEvaluator}
     * @param testAmrs the AMRs to be used for testing
     * @param sigma the value of sigma to be used by the classifier, see {@link LinearClassifier}
     */
    public void autoLoad(AutoLoadParams params, String filename, boolean train, Function<List<Amr>, LossEvaluator> testFunction, List<Amr> testAmrs, double sigma) throws IOException {

        this.params = params.makeCopy();
        loadMetaInformations(filename);

        if (train) {

            List<Datum<String, String>> events = deriveDatumList(params.trainingData);
            List<Datum<String, String>> devEvents = deriveDatumList(params.devData);

            double trainSigma = train(events, devEvents, usesRVF, sigma);
            double trainScore = testFunction.apply(testAmrs).total;

            if (trainScore > bestScore) {
                bestScore = trainScore;
                bestSigma = trainSigma;
                saveModelToFile(filename);
                saveMetaInformations(filename, bestSigma, bestScore);
                Debugger.println("[NEW] saving new best result: score = " + bestScore + ", sigma = " + bestSigma);
            }
        } else {
            loadModelFromFile(filename);
        }
        System.out.println("");
    }

    /**
     * Trains this maximum entropy model given a list of training and development data.
     * @param trainingData the training data to be used
     * @param devData the development data to be used
     * @param params the parameters for training, see {@link AutoLoadParams}
     * @param filename the name of the file in which the trained model should be stored
     */
    public void trainWithData(List<Datum<String, String>> trainingData, List<Datum<String, String>> devData, AutoLoadParams params, String filename) throws IOException {

        this.params = params.makeCopy();
        loadMetaInformations(filename);

        double trainSigma = train(trainingData, devData, usesRVF, -1);
        double trainScore = test(params.devData).total;

        saveModelToFile(filename);
        saveMetaInformations(filename, trainSigma, trainScore);
        Debugger.println("[NEW] saving new best result: score = " + trainScore + ", sigma = " + trainSigma);
    }

    /**
     * Returns the n-best predictions (where n is determined by {@link AutoLoadParams#takeBestN}) given a feature vector.
     * @param datum the feature vector, represented by an instance of {@link Datum}
     * @return the list of predictions
     */
    public List<Prediction> getNBestSorted(Datum<String, String> datum) {
        return getNBestSorted(datum, params.takeBestN, params.maxProbDecrement);
    }

    /**
     * Returns the n-best predictions such that the score of no prediction is below the maximum score minus {@code maxProbDifference}, given a feature vector.
     * @param datum the feature vector, represented by an instance of {@link Datum}
     * @param n the maximum number of predictions
     * @param maxProbDifference the threshold, the score of no returned prediction is below the maximum score minus this threshold.
     * @return the list of predictions
     */
    public List<Prediction> getNBestSorted(Datum<String, String> datum, int n, double maxProbDifference) {

        Counter<String> scores = classifier.probabilityOf(datum);

        List<Prediction> predictions;

        predictions = new ArrayList<>();
        double bestScore = -1;
        while (n >= 1) {
            String prediction = Counters.argmax(scores);
            double score = scores.getCount(prediction);

            if (predictions.isEmpty()) {
                bestScore = score;
            }

            if (score >= bestScore - maxProbDifference) {
                predictions.add(new Prediction(prediction, score));
            }

            n--;
            scores.setCount(prediction, 0);
        }

        return predictions;
    }

    private double train(List<Datum<String, String>> events, List<Datum<String, String>> devEvents, boolean realValued, double sigma) throws IOException {

        if (events.isEmpty() || devEvents.isEmpty()) {
            throw new AssertionError("no training or development events found. |train| = " + events.size() + ", |dev| = " + devEvents.size());
        }

        GeneralDataset<String, String> trainData, devData;

        if (realValued) {
            trainData = new RVFDataset<>();
            devData = new RVFDataset<>();
        } else {
            trainData = new Dataset<>();
            devData = new Dataset<>();
        }

        trainData.addAll(events);
        devData.addAll(devEvents);

        LinearClassifierFactory<String, String> factory = new LinearClassifierFactory<>();
        factory.useConjugateGradientAscent();
        factory.setVerbose(true);

        if (sigma > 0) {
            factory.setSigma(sigma);
            classifier = factory.trainClassifier(trainData);
        } else {
            classifier = factory.trainClassifierV(trainData, devData, MIN_SIGMA, MAX_SIGMA, true);
        }
        return factory.getSigma();
    }

    private List<Datum<String, String>> deriveDatumList(List<Amr> amrs) {

        List<Datum<String, String>> datumList = new ArrayList<>();

        for (Amr amr : amrs) {
            for (Vertex vertex : amr.dag) {
                datumList.addAll(toDatumList(amr, vertex, false));
            }
        }
        return datumList;
    }

    private void saveModelToFile(String filename) {
        if (classifier == null) throw new AssertionError("model is null, cannot be saved");
        LinearClassifier.writeClassifier(classifier, filename);
    }

    private void loadModelFromFile(String filename) {
        classifier = LinearClassifier.readClassifier(filename);
    }

    private void saveMetaInformations(String filename, double sigma, double bestResult) throws IOException {
        List<String> metaInfo = new ArrayList<>();
        metaInfo.add(sigma + "");
        metaInfo.add(bestResult + "");
        Files.write(Paths.get(filename + ".meta"), metaInfo);
    }

    private void loadMetaInformations(String filename) throws IOException {
        if(new File(filename + ".meta").isFile()) {
            List<String> metaInfo = Files.readAllLines(Paths.get(filename + ".meta"));
            bestSigma = Double.valueOf(metaInfo.get(0));
            bestScore = Double.valueOf(metaInfo.get(1));
        }
        else {
            bestScore = 0;
            bestSigma = -1;
        }
    }

    private LossEvaluator test(List<Amr> amrs) {
        return test(amrs, false);
    }

    /**
     * This function tests the maximum entropy model using a list of test AMR graphs, constructs a {@link LossEvaluator} containing information about the quality of the
     * model and optionally modifies the test graphs using the output of the model via {@link StanfordMaxentModelImplementation#applyModification(Amr, Vertex, List)}.
     * @param amrs the AMR graphs on which the maximum entropy model should be tested
     * @param modifyAmrs whether the AMRs should be modified by this method
     * @return the loss evaluator
     */
    public LossEvaluator test(List<Amr> amrs, boolean modifyAmrs) {

        LossEvaluator lossEvaluator = new LossEvaluator();

        double wrongCount = 0, totalCount = 0;

        for (Amr amr : amrs) {
            for (Vertex v : amr.dag) {

                List<Datum<String, String>> datumList = toDatumList(amr, v, modifyAmrs);

                for (Datum<String, String> datum : datumList) {

                    List<Prediction> predictions = getNBestSorted(datum, params.takeBestN, params.maxProbDecrement);

                    if (modifyAmrs) {
                        applyModification(amr, v, predictions);
                    }

                    if(!predictions.get(0).getValue().equals(datum.label())) {
                        wrongCount++;
                        if (isPositive(datum.label())) {
                            lossEvaluator.fn++;
                        } else {
                            lossEvaluator.fp++;
                        }
                    }
                    else {
                        if (isPositive(datum.label())) {
                            lossEvaluator.tp++;
                        } else {
                            lossEvaluator.tn++;
                        }
                    }
                    totalCount++;
                }
            }
        }
        lossEvaluator.total = 1 - wrongCount / totalCount;
        return lossEvaluator;
    }

    /**
     * Applies a modification to a vertex of an AMR graph given a list of predictions made by this maximum entropy model.
     * @param amr the AMR graph
     * @param vertex the vertex
     * @param prediction the list of predictions
     */
    public void applyModification(Amr amr, Vertex vertex, List<Prediction> prediction) {
        applyModification(amr, vertex, prediction.get(0).getValue());
    }

    /**
     * Applies a modification to a vertex of an AMR graph given a single prediction made by this maximum entropy model.
     * @param amr the AMR graph
     * @param vertex the vertex
     * @param prediction the prediction
     */
    public void applyModification(Amr amr, Vertex vertex, String prediction) {
        throw new AssertionError("applyModification(Amr, Vertex, String) must be overwritten in order for test(Amr,Boolean) to work");
    }
}