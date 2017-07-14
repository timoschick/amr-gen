package ml;

import dag.Amr;
import dag.Vertex;
import misc.Debugger;
import opennlp.tools.ml.maxent.GIS;
import opennlp.tools.ml.maxent.GISModel;
import opennlp.tools.ml.maxent.io.GISModelReader;
import opennlp.tools.ml.maxent.io.GISModelWriter;
import opennlp.tools.ml.maxent.io.SuffixSensitiveGISModelReader;
import opennlp.tools.ml.maxent.io.SuffixSensitiveGISModelWriter;
import opennlp.tools.ml.model.DataIndexer;
import opennlp.tools.ml.model.Event;
import opennlp.tools.ml.model.OnePassDataIndexer;
import opennlp.tools.util.ObjectStream;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

/**
 * An implementation of a maximum entropy model based on the {@link GISModel} class from OpenNLP.
 */
public abstract class OpenNlpMaxentModelImplementation {

    /**
     * The autoload params used by this model, see {@link AutoLoadParams}
     */
    public AutoLoadParams params;

    /**
     * The feature manager used by this model, see {@link FeatureManager}
     */
    protected FeatureManager featureManager;

    private double bestScore;
    private GISModel model;

    /**
     * Creates a new Open NLP maximum entropy model.
     */
    public OpenNlpMaxentModelImplementation() {
        featureManager = new FeatureManager();
    }

    /**
     * Extracts from a vertex of an AMR graph a list of feature vectors with outcomes, represented by the {@link Event} class.
     * @param amr the AMR graph
     * @param vertex the vertex
     * @param forTesting if the feature vector and the outcome should be the gold feature vector and the gold outcome, set this to false.
     *                   Otherwise, if the feature vector should be computed for testing, set this to true.
     * @return the list of events
     */
    public abstract List<Event> toEvents(Amr amr, Vertex vertex, boolean forTesting);

    /**
     * Automatically loads this maximum entropy model using the parameters specified by an instance of {@link AutoLoadParams}
     * @param params the parameters to use for loading the model
     * @param filename the file name under which the model can be found
     * @param train whether the model should be (re)trained or left as is
     */
    public void autoLoad(AutoLoadParams params, String filename, boolean train) throws IOException {

        this.params = params.makeCopy();
        loadMetaInformations(filename);

        if(train) {

             for(Integer nrOfIterations: params.iterNrs) {

                Debugger.println("starting iteration with iterNr " + nrOfIterations);

                List<Event> events = deriveEvents(params.trainingData);

                train(events, nrOfIterations, false);
                double trainScore = test(params.devData).total;

                if (trainScore > bestScore) {
                    bestScore = trainScore;
                    saveModelToFile(filename);
                    saveMetaInformations(filename, nrOfIterations, bestScore);
                    Debugger.println("[NEW] saving new best result: score = " + bestScore + ", nrOfIterations = " + nrOfIterations);
                }
            }
        }
        else {
            loadModelFromFile(filename);
        }
    }

    /**
     * Trains this maximum entropy model.
     * @param events the events from which the model should be trained
     * @param iterations the number of iterations
     * @param debug if set to true, additional debugging information is printed
     */
    private void train(List<Event> events, int iterations, boolean debug) throws IOException {

        PrintStream original = null;

        if(!debug) {
            original = System.out;
            System.setOut(new PrintStream(new OutputStream() {public void write(int b) { }}));
        }

        if(events.isEmpty()) {
            throw new AssertionError("cannot train maximum entropy model because the list of events is empty");
        }

        ObjectStream<Event> reorderEventStream = new ReorderEventStream(events);
        DataIndexer dataIndexer = new OnePassDataIndexer(reorderEventStream);
        model = GIS.trainModel(iterations, dataIndexer, debug, false, null, 0);

        if(!debug) {
            System.setOut(original);
        }
    }

    /**
     * Derives a list of events from a list of AMR graphs using {@link OpenNlpMaxentModelImplementation#toEvents(Amr, Vertex, boolean)}.
     * @param amrs the list of AMR graphs
     * @return the list of events
     */
    private List<Event> deriveEvents(List<Amr> amrs) {

        List<Event> events = new ArrayList<>();

        for(Amr amr: amrs) {
            for(Vertex vertex: amr.dag) {
                events.addAll(toEvents(amr, vertex, false));
            }
        }
        return events;
    }

    /**
     * Returns the n-best predictions (where n is determined by {@link AutoLoadParams#takeBestN}) given a feature vector.
     * @param context the feature vector, represented by a String array
     * @return the list of predictions
     */
    public List<Prediction> getNBestSorted(String[] context) {
        return getNBestSorted(context, params.takeBestN, params.maxProbDecrement);
    }

    /**
     * Returns the n-best predictions such that the score of no prediction is below the maximum score minus {@code maxProbDifference}, given a feature vector.
     * @param context the feature vector, represented by a String array
     * @param n the maximum number of predictions
     * @param maxProbDifference the threshold, the score of no returned prediction is below the maximum score minus this threshold.
     * @return the list of predictions
     */
    public List<Prediction> getNBestSorted(String[] context, int n, double maxProbDifference) {

        List<Prediction> predictions;
        double[] scores = model.eval(context);

        predictions = new ArrayList<>();
        double bestScore = -1;
        while(n >= 1) {
            String prediction = model.getBestOutcome(scores);
            double score = scores[model.getIndex(prediction)];

            if(predictions.isEmpty()) {
                bestScore = score;
            }

            if(score >= bestScore - maxProbDifference) {
                predictions.add(new Prediction(prediction, score));
            }

            n--;
            scores[model.getIndex(prediction)] = 0;
        }

        return predictions;
    }

    private LossEvaluator test(List<Amr> amrs) {

        LossEvaluator lossEvaluator = new LossEvaluator();

        double wrongCount = 0, totalCount = 0;

        for(Amr amr: amrs) {
            for(Vertex v: amr.dag) {

                List<Event> events = toEvents(amr, v, false);

                for(Event event: events) {

                    List<Prediction> predictions = getNBestSorted(event.getContext(), params.takeBestN, params.maxProbDecrement);

                    if(!predictions.get(0).getValue().equals(event.getOutcome())) {
                        wrongCount++;
                    }
                    totalCount++;
                }
            }
        }
        lossEvaluator.total = 1 - wrongCount/totalCount;
        return lossEvaluator;
    }

    private void saveModelToFile(String filename) throws IOException {
        if(model == null) throw new AssertionError("model is null, cannot be saved");
        File outputFile = new File(filename);
        GISModelWriter writer = new SuffixSensitiveGISModelWriter(model, outputFile);
        writer.persist();
    }

    private void loadModelFromFile(String filename) throws IOException {
        File inputFile = new File(filename);
        GISModelReader reader = new SuffixSensitiveGISModelReader(inputFile);
        model = (GISModel) reader.getModel();
    }

    private void saveMetaInformations(String filename, int nrOfIterations, double bestResult) throws IOException {
        List<String> metaInfo = new ArrayList<>();
        metaInfo.add(nrOfIterations+"");
        metaInfo.add(bestResult+"");
        Files.write(Paths.get(filename+".meta"), metaInfo);
    }

    private void loadMetaInformations(String filename) throws IOException {
        if(new File(filename + ".meta").isFile()) {
            List<String> metaInfo = Files.readAllLines(Paths.get(filename + ".meta"));
            bestScore = Double.valueOf(metaInfo.get(1));
        }
        else {
            bestScore = 0;
        }
    }

    private static class ReorderEventStream implements ObjectStream<Event> {

        private List<Event> events;
        int currentIndex;

        ReorderEventStream(List<Event> events) {
            this.events = events;
            currentIndex = 0;
        }

        @Override
        public Event read() throws IOException {
            if(currentIndex < events.size()) {
                Event ret = events.get(currentIndex);
                currentIndex++;
                return ret;
            }
            return null;
        }

        @Override
        public void reset() throws IOException, UnsupportedOperationException {
            currentIndex=0;
        }

        @Override
        public void close() throws IOException {
            events.clear();
        }
    }
}
