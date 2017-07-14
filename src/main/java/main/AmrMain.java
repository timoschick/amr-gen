package main;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import dag.*;
import edu.berkeley.nlp.lm.ArrayEncodedNgramLanguageModel;
import edu.berkeley.nlp.lm.io.LmReaders;
import edu.stanford.nlp.mt.metrics.BLEUMetric;
import edu.stanford.nlp.mt.util.ArraySequence;
import edu.stanford.nlp.mt.util.Sequence;
import edu.stanford.nlp.tagger.maxent.MaxentTagger;
import gen.*;
import misc.Debugger;
import misc.StaticHelper;
import misc.WordNetHelper;
import ml.*;
import net.sf.extjwnl.JWNLException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Main class of the transition-based generator defined in the thesis. This class provides a command-line interface for generating sentences from
 * a file containing AMR graphs.
 */
public class AmrMain {

    private MaxentModelWrapper maxentModels;
    private MaxentTagger posTagger;
    private Hyperparams hyperparams;
    private PositionHelper positionHelper;

    private FirstStageProcessor firstStageProcessor;
    private SecondStageProcessor secondStageProcessor;
    private PostProcessor postProcessor;

    private boolean setUp = false;

    public static void main(String[] args) throws IOException, JWNLException {

        args = new String[]{"-i", "in1.txt", "-o", "out1.txt"};

        Amr.setUp();
        AmrMain main = new AmrMain(args);

        System.in.read();
        main.demo1();
        System.in.read();
        main.demo2();
        System.in.read();
        main.demo3();
    }

    // generates a sentence from the "the developer wants to sleep" AMR graph
    private void demo1() throws IOException {

        List<Amr> amrs = AmrParser.fromFile("in1.txt", null, null, -1, null);

        new AmrFrame(amrs.get(0));

        Amr.prepare(amrs, posTagger, true);
        firstStageProcessor.processFirstStage(amrs);

        new AmrFrame(amrs.get(0));

        List<String> sentences = generate(amrs, false, false);

        new AmrFrame(amrs.get(0));
        System.out.println("Resulting sentence before postprocessing: " + sentences.get(0));

        List<String> postprocessedSentences = postProcessor.postProcess(amrs);

        System.out.println("Resulting sentence after postprocessing: " + postprocessedSentences.get(0));
    }

    // generates a sentence from the AMR graph whose tokenized reference realization is
    // "Wen stated that the Chinese government supports plans for peace in the Middle East and remains firmly opposed to violent retaliation ."
    private void demo2() throws IOException {

        List<Amr> amrs = AmrParser.fromFile("in1comp.txt", null, null, -1, null);

        /*
        secondStageProcessor.maxNrOfComposedPredictions = 50;
        secondStageProcessor.maxNrOfPosRealizationPredictions = 50;
        secondStageProcessor.maxNrOfRealizationPredictions = 50;
        */

        new AmrFrame(amrs.get(0));

        Amr.prepare(amrs, posTagger, true);
        List<String> sentences = generate(amrs, true, false);

        System.out.println("");

        for(Vertex v: amrs.get(0).dag.getVerticesBottomUp()) {
            System.out.println("partial yield of the n-best transition sequences for node " + v.getInstance() + (v.name.isEmpty()?"":" '"+v.name+"'") +":");
            List<Prediction> predictions = v.predictions.get("realization");
            for(Prediction pred: predictions) {
                System.out.println("\t" + pred.value);
            }
            System.out.println("");
        }

        new AmrFrame(amrs.get(0));
    }

    // shows AMR graphs from the LDC2014T12 development corpus
    private void demo3() throws IOException {

        List<Amr> amrs = loadAmrGraphs(PathList.DEVELOPMENT_DIR, false, 1, -1, -1);

        BigraphAlignmentBuilder builder = new BigraphAlignmentBuilder();
        builder.buildTypeAlignment(amrs.get(2), amrs.get(2).dependencyTree);

        new AmrFrame(amrs.get(2));
        new DependencyTreeFrame(amrs.get(2).dependencyTree);
    }

    // shows the "the developer wants to sleep" AMR graph as used for training
    /*private void demo4() throws IOException {

        PathList.AMR_SUBDIRECTORIES = Collections.singletonList("example/");
        List<Amr> amrs = loadAmrGraphs("", false, 1, -1, -1);
        new AmrFrame(amrs.get(0));
        new DependencyTreeFrame(amrs.get(0).dependencyTree);
    }*/

    private AmrMain(String[] args) throws IOException, JWNLException {

        maxentModels = new MaxentModelWrapper();

        // support for command line interface
        CommandGenerate gen = new CommandGenerate();
        JCommander jCommander = new JCommander(gen);

        jCommander.parse(args);

        // show help for the command line interface
        if (gen.help) {
            jCommander.usage();
        }

        // generate sentences from a list of AMR graphs
        else {

            if(gen.outputFile == null) {
                throw new AssertionError("an output file must be specified using '--output path/to/output'.");
            }

            setUp();

            List<Amr> amrs;

            if (gen.inputFile == null) {
                amrs = loadAmrGraphs(PathList.TEST_DIR, true);
            } else {
                amrs = loadAmrGraphs(gen.inputFile);
            }

            List<String> generatedSentences = generate(amrs, true, true);

            if (gen.printOutputToStdout) {
                AmrMain.compareGeneratedSentencesWithGoldRealizations(amrs, generatedSentences);
            }

            if (gen.bleu) {
                double score = getBleu(amrs, generatedSentences);
                Debugger.println("Bleu = " + score + " ( Nr of AMR graphs = " + amrs.size() + " )");
            }

            Files.write(Paths.get(gen.outputFile), generatedSentences);
        }
    }

    /**
     * Optimizes hyperparameters of the fully trained generator using the development data found in the subdirectories (according to
     * {@link PathList#AMR_SUBDIRECTORIES}) of {@link PathList#DEVELOPMENT_DIR} and writes the resulting best configuration of
     * hyperparameters to the {@link PathList#HYPERPARAMS_LIST} file. <br/>
     * <b>Important: </b> Note that this function never terminates; however, it can be terminated manually at all times.
     */
    private void optimizeHyperparams() throws IOException, JWNLException {

        setUp();

        double bestScore = 0;
        boolean firstIter = true;

        List<Amr> amrs = loadAmrGraphs(PathList.DEVELOPMENT_DIR, true);

        for(Hyperparam hyperparam: Hyperparam.allParams) {
            // set the minimum and maximum for each integer hyperparameter to its current default value +-2
            if(hyperparam instanceof IntHyperparam) {
                hyperparam.min = Math.max(hyperparam.min, hyperparam.defaultValue - 2);
                hyperparam.max = Math.min(hyperparam.max, hyperparam.defaultValue + 2);
            }

            // set the minimum and maximum for each real valued hyperparameter to its current default value +-15%
            else {
                hyperparam.min = Math.max(hyperparam.min, 0.85 * hyperparam.defaultValue);
                hyperparam.max = Math.min(hyperparam.max, 1.15 * hyperparam.defaultValue);
            }
        }

        while(true) {
            applyCurrentHyperparams();
            Debugger.println("----------------------------------");
            Hyperparam.printDebug();

            List<String> generatedSentences = generate(amrs, firstIter, true);
            double score = getBleu(amrs, generatedSentences);
            boolean newBest = false;

            if (score > bestScore) {
                newBest = true;
                bestScore = score;
                Debugger.println("[NEW] Current hyperparameter configuration achieved new best BLEU score: " + bestScore);
                String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(Calendar.getInstance().getTime());
                String addToFile = System.lineSeparator() + "-----------------------------------------------" + System.lineSeparator()
                        + "SCORE: " + bestScore + " ( " + timeStamp + ")" + System.lineSeparator() + Hyperparam.allToString();
                StaticHelper.addLineToFile(PathList.HYPERPARAMS_LIST, addToFile);
            }
            else {
                Debugger.println("BLEU = " + score + " ( current best = " + bestScore + ")");
            }
            Hyperparam.update(newBest);
            firstIter = false;
        }
    }


    /**
     * Trains all maximum entropy models used by the generator using the training and development data found in the
     * subdirectories (according to {@link PathList#AMR_SUBDIRECTORIES}) of {@link PathList#TRAINING_DIR} and {@link PathList#DEVELOPMENT_DIR}.
     * Note that this does <b>not</b> reload the additional ressources found in the "res" directory. To reload these ressources, the corresponding
     * methods in {@link StaticHelper} have to be explicitly called. <br/>
     * <b>Important:</b> This function was not tested as a whole because it requires a considerable amount of time to train all maximum entropy models
     * at once. Instead, the maximum entropy models were trained one by one through calls to {@link AmrMain#setUp(List, boolean)}.
     */
    private void train() throws IOException, JWNLException {
        setUp(Arrays.asList(Models.NUMBER, Models.DENOM, Models.VOICE, Models.TENSE, Models.POS,
                Models.INSERT_ARGS, Models.INSERT_OTHERS, Models.INSERT_CHILD, Models.FIRST_STAGE), true);
        setUp(Arrays.asList(Models.REORDER, Models.REORDER_LEFT, Models.REORDER_RIGHT, Models.REALIZE), false);
    }

    /**
     * Sets up the generator for testing by loading the language model, the POS tagger, and all relevant maximum entropy models.
     */
    private void setUp() throws IOException, JWNLException {
        setUp(new ArrayList<>(), false);
    }

    /**
     * Sets up the generator by loading the language model, the POS tagger, and all relevant maximum entropy models.
     * @param modelsToTrain a list of maximum entropy models which should be trained. All other maximum entropy models are assumed to be pretrained and loaded from
     *                      the corresponding files specified in {@link PathList}.
     * @param stopAfterFirstStage this parameter is only relevant for the full training procedure and specifies whether set up should stop after training the first
     *                            stage. For must usages, this parameter should be set to {@code false}.
     */
    private void setUp(List<Models> modelsToTrain, boolean stopAfterFirstStage) throws JWNLException, IOException {

        posTagger = new MaxentTagger(PathList.POS_TAGGER_PATH);
        hyperparams = new Hyperparams();

        AutoLoadParams params = new AutoLoadParams();
        WordNetHelper wordNetHelper = new WordNetHelper();
        DefaultRealizer defaultRealizer = new DefaultRealizer(wordNetHelper);
        ArrayEncodedNgramLanguageModel<String> languageModel =
                (ArrayEncodedNgramLanguageModel) LmReaders.readLmBinary(PathList.LANGUAGE_MODEL_PATH);

        // if the maximum entropy models are to be trained, load the training and development data and
        // build type alignments from the corresponding dependency trees
        if(!modelsToTrain.isEmpty()) {
            BigraphAlignmentBuilder builder = new BigraphAlignmentBuilder();

            params.trainingData = loadAmrGraphs(PathList.TRAINING_DIR, false);
            params.devData = loadAmrGraphs(PathList.DEVELOPMENT_DIR, false);

            List<Amr> allAmrs = new ArrayList<>();
            allAmrs.addAll(params.trainingData);
            allAmrs.addAll(params.devData);

            for (Amr amr : allAmrs) {
                if (amr.dependencyTree != null) {
                    builder.buildTypeAlignment(amr, amr.dependencyTree);
                }
            }
        }

        // syntactic annotation maximum entropy classifiers
        maxentModels.posMaxentModel = new PosMaxentModel(wordNetHelper);
        maxentModels.tenseMaxentModel = new TenseMaxentModel();
        maxentModels.voiceMaxentModel = new VoiceMaxentModel();
        maxentModels.denomMaxentModel = new DenomMaxentModel();
        maxentModels.numberMaxentModel = new NumberMaxentModel();

        // first stage maximum entropy classifier
        maxentModels.firstStageMaxentModel = new FirstStageMaxentModel();

        // realization maximum entropy classifier
        maxentModels.realizeMaxentModel = new RealizeMaxentModel();

        // insertion maximum entropy classifiers
        maxentModels.otherInsertionMaxentModel = new OtherInsertionMaxentModel();
        maxentModels.argInsertionMaxentModel = new ArgInsertionMaxentModel();
        maxentModels.childInsertionMaxentModel = new ChildInsertionMaxentModel();

        // order maximum entropy classifiers
        maxentModels.parentChildReorderMaxentModel = new ParentChildReorderMaxentModel();
        maxentModels.leftMaxEnt = new SiblingReorderMaxentModel(SiblingReorderMaxentModel.LEFT_ONLY);
        maxentModels.rightMaxEnt = new SiblingReorderMaxentModel(SiblingReorderMaxentModel.RIGHT_ONLY);

        positionHelper = new PositionHelper(maxentModels.parentChildReorderMaxentModel, maxentModels.leftMaxEnt, maxentModels.rightMaxEnt);

        firstStageProcessor = new FirstStageProcessor(maxentModels.firstStageMaxentModel);
        secondStageProcessor = new SecondStageProcessor(maxentModels.realizeMaxentModel, maxentModels.argInsertionMaxentModel,
                maxentModels.otherInsertionMaxentModel, maxentModels.childInsertionMaxentModel, maxentModels.denomMaxentModel, defaultRealizer, positionHelper, languageModel);
        postProcessor = new PostProcessor(languageModel, maxentModels.denomMaxentModel);

        // automatically load all maximum entropy models
        maxentModels.posMaxentModel.autoLoad(params, PathList.POS_MAXENT_PATH, modelsToTrain.contains(Models.POS));
        maxentModels.denomMaxentModel.autoLoad(params, PathList.DENOM_MAXENT_PATH, modelsToTrain.contains(Models.DENOM));
        maxentModels.numberMaxentModel.autoLoad(params, PathList.NUMBER_MAXENT_PATH, modelsToTrain.contains(Models.NUMBER));
        maxentModels.tenseMaxentModel.autoLoad(params, PathList.TENSE_MAXENT_PATH, modelsToTrain.contains(Models.TENSE));
        maxentModels.voiceMaxentModel.autoLoad(params, PathList.VOICE_MAXENT_PATH, modelsToTrain.contains(Models.VOICE));

        maxentModels.otherInsertionMaxentModel.autoLoad(params, PathList.OTHERS_INSERTION_MAXENT_PATH, modelsToTrain.contains(Models.INSERT_OTHERS));
        maxentModels.argInsertionMaxentModel.autoLoad(params, PathList.ARG_INSERTION_MAXENT_PATH, modelsToTrain.contains(Models.INSERT_ARGS));
        maxentModels.childInsertionMaxentModel.autoLoad(params, PathList.CHILD_INSERTION_MAXENT_PATH, modelsToTrain.contains(Models.INSERT_CHILD));

        if(!modelsToTrain.contains(Models.FIRST_STAGE)) {
            maxentModels.firstStageMaxentModel.autoLoad(params, PathList.FIRST_STAGE_MAXENT_PATH, false);
        }
        else {
            firstStageProcessor.performGoldTransitionsFirstStage(params.trainingData, params.devData, params, PathList.FIRST_STAGE_MAXENT_PATH, true);
        }

        if(!modelsToTrain.isEmpty() && !modelsToTrain.contains(Models.FIRST_STAGE)) {
            firstStageProcessor.processFirstStage(params.devData);
            firstStageProcessor.processFirstStage(params.trainingData);
        }

        if(stopAfterFirstStage) return;

        maxentModels.parentChildReorderMaxentModel.autoLoad(params, PathList.REORDER_MAXENT_PATH, modelsToTrain.contains(Models.REORDER));
        maxentModels.rightMaxEnt.autoLoad(params, PathList.RIGHT_REORDER_MAXENT_PATH, modelsToTrain.contains(Models.REORDER_RIGHT));
        maxentModels.leftMaxEnt.autoLoad(params, PathList.LEFT_REORDER_MAXENT_PATH, modelsToTrain.contains(Models.REORDER_LEFT));
        maxentModels.realizeMaxentModel.autoLoad(params, PathList.REALIZE_MAXENT_PATH, modelsToTrain.contains(Models.REALIZE));

        setUp = true;
        Hyperparam.initializeFromFile(PathList.HYPERPARAMS_LIST);
        applyCurrentHyperparams();
    }

    /**
     * Generates realizations from AMR graphs using the Generation algorithm as described in the thesis.
     * @param amrs the AMR graphs for which realizations should be generated
     * @return the list of generated realizations
     */
    public List<String> generate(List<Amr> amrs) {
        return generate(amrs, true, true);
    }

    /**
     * Generates realizations from AMR graphs using the Generation algorithm as described in the thesis.
     * @param amrs the AMR graphs for which realizations should be generated
     * @param firstStage whether first-stage processing (i.e. MERGE, SWAP, DELETE and KEEP transitions) should also be performed; in most cases,
     *                   this parameter should be set to true but for hyperparameter optimization, it is more efficient to peform first-stage processing only once
     *                   and leave the AMR graphs as is in subsequent calls to this function.
     * @param postProcess whether post-processing should be performed
     * @return the list of generated realizations
     */
    public List<String> generate(List<Amr> amrs, boolean firstStage, boolean postProcess) {

        if(!setUp) {
            throw new AssertionError("setUp() must be called before using the generator.");
        }

        Debugger.println("starting first-stage processing of " + amrs.size() + " AMR graphs...");

        if(firstStage) {
            firstStageProcessor.processFirstStage(amrs);
        }

        Debugger.println("finished first-stage processing of " + amrs.size() + " AMR graphs.");

        // load syntactic annotations for all vertices by calling the test methods of the corresponding maximum entropy models
        maxentModels.posMaxentModel.test(amrs, true);
        maxentModels.numberMaxentModel.test(amrs, true);
        maxentModels.voiceMaxentModel.test(amrs, true);
        maxentModels.tenseMaxentModel.test(amrs, true);
        maxentModels.denomMaxentModel.test(amrs, true);

        long time = System.nanoTime();
        List<String> generatedSentences = new ArrayList<>();

        Debugger.println("starting second-stage processing of " + amrs.size() + " AMR graphs...");

        for(Amr amr: amrs) {
            generatedSentences.add(secondStageProcessor.getBestRealizationAsString(amr));
        }

        Debugger.println("finished second-stage processing of " + amrs.size() + " AMR graphs.");

        if(postProcess) {
            generatedSentences = postProcessor.postProcess(amrs);
        }

        Debugger.println("generated sentences from " + amrs.size() + " AMR graphs in " + ((double) (System.nanoTime() - time) / (1000000000.0)) + " seconds.");
        return generatedSentences;
    }

    /**
     * This function updates all classes using hyperparameters with the current values according to {@link AmrMain#hyperparams}.
     * For a detailed explanation of the hyperparameters, see {@link Hyperparams}.
     */
    private void applyCurrentHyperparams() {

        if(!setUp) {
            throw new AssertionError("setUp() must be called before applying hyperparams.");
        }

        // update hyperparameters for pruning of maximum entropy models

        positionHelper.takeBestN = (int) hyperparams.positionHelper_params_takeBestN.realize();
        positionHelper.maxProbDecrement = hyperparams.positionHelper_params_maxProbDecrement.realize();

        maxentModels.numberMaxentModel.params.takeBestN = (int) hyperparams.numberMaxEnt_params_takeBestN.realize();
        maxentModels.numberMaxentModel.params.maxProbDecrement = hyperparams.numberMaxEnt_params_maxProbDecrement.realize();

        maxentModels.voiceMaxentModel.params.takeBestN = (int) hyperparams.voiceMaxEnt_params_takeBestN.realize();
        maxentModels.voiceMaxentModel.params.maxProbDecrement = hyperparams.voiceMaxEnt_params_maxProbDecrement.realize();

        maxentModels.tenseMaxentModel.params.takeBestN = (int) hyperparams.tenseMaxEnt_params_takeBestN.realize();
        maxentModels.tenseMaxentModel.params.maxProbDecrement = hyperparams.tenseMaxEnt_params_maxProbDecrement.realize();

        maxentModels.denomMaxentModel.params.takeBestN = (int) hyperparams.denomMaxEnt_params_takeBestN.realize();
        maxentModels.denomMaxentModel.params.maxProbDecrement = hyperparams.denomMaxEnt_params_maxProbDecrement.realize();

        maxentModels.posMaxentModel.params.takeBestN = (int) hyperparams.posMaxEnt_params_takeBestN.realize();
        maxentModels.posMaxentModel.params.maxProbDecrement = hyperparams.posMaxEnt_params_maxProbDecrement.realize();

        maxentModels.realizeMaxentModel.params.takeBestN = (int) hyperparams.realizeMaxEnt_params_takeBestN.realize();
        maxentModels.realizeMaxentModel.params.maxProbDecrement = hyperparams.realizeMaxEnt_params_maxProbDecrement.realize();

        maxentModels.argInsertionMaxentModel.params.takeBestN = (int) hyperparams.argInsertionMaxEnt_params_takeBestN.realize();
        maxentModels.argInsertionMaxentModel.params.maxProbDecrement = hyperparams.argInsertionMaxEnt_params_maxProbDecrement.realize();

        maxentModels.otherInsertionMaxentModel.params.takeBestN = (int) hyperparams.othersInsertionMaxEnt_params_takeBestN.realize();
        maxentModels.otherInsertionMaxentModel.params.maxProbDecrement = hyperparams.othersInsertionMaxEnt_params_maxProbDecrement.realize();

        // update weights for scoring transition sequences
        secondStageProcessor.lmWeight = hyperparams.vertexTranslator_lmWeight.realize();
        secondStageProcessor.beforeInsWeight = hyperparams.vertexTranslator_beforeInsWeight.realize();
        secondStageProcessor.beforeInsArgWeight = hyperparams.vertexTranslator_beforeInsArgWeight.realize();
        secondStageProcessor.afterInsWeight = hyperparams.vertexTranslator_afterInsWeight.realize();
        secondStageProcessor.articleLmWeight = hyperparams.vertexTranslator_articleLmWeight.realize();
        secondStageProcessor.reorderingWeight = hyperparams.vertexTranslator_reorderingWeight.realize();
        secondStageProcessor.realizationWeight = hyperparams.vertexTranslator_realizationWeight.realize();

        // update weights for syntactic annotations
        secondStageProcessor.syntacticAnnotationWeights.put("pos", hyperparams.posWeight.realize());
        secondStageProcessor.syntacticAnnotationWeights.put("number", hyperparams.numberWeight.realize());
        secondStageProcessor.syntacticAnnotationWeights.put("voice", hyperparams.voiceWeight.realize());
        secondStageProcessor.syntacticAnnotationWeights.put("tense", hyperparams.tenseWeight.realize());
        secondStageProcessor.syntacticAnnotationWeights.put("denom", hyperparams.denomWeight.realize());

        // update additional hyperparameters used by the second stage processor
        secondStageProcessor.maxNrOfPosRealizationPredictions = (int) hyperparams.maxNrOfRealizePredictionsPerPos.realize();
        secondStageProcessor.maxNrOfRealizationPredictions = (int) hyperparams.maxNrOfRealizePredictions.realize();
        secondStageProcessor.maxNrOfComposedPredictions = (int) hyperparams.maxNrOfPredictionsPerVertex.realize();
        secondStageProcessor.defaultRealizationScore = hyperparams.vertexTranslator_defaultRealizationScore.realize();
        secondStageProcessor.articleSmoothing = hyperparams.vertexTranslator_articleSmoothing.realize();
        secondStageProcessor.linkRealizationScore = -0.5;
        secondStageProcessor.minTranslationScore = 0.025d;

        // update hyperparameters used by the post processor
        postProcessor.linkRemovalHandicap = 0.7;
        postProcessor.articleAdditionHandicap = 1.15;
        postProcessor.articleLmWeight = 0.6;
        postProcessor.defaultArticlePredScore = 0.05;
        postProcessor.defaultArticleSmoothing = 0.2;
    }

    /**
     * Prints sentences generated from some AMR graphs and the graphs' reference realization.
     * @param amrs the AMR graphs. These graphs must contain reference realizations in order for this function to work properly.
     * @param generatedSentences the sentences generated from the AMR graphs
     */
    private static void compareGeneratedSentencesWithGoldRealizations(List<Amr> amrs, List<String> generatedSentences) {
        if(amrs.size() != generatedSentences.size()) {
            throw new AssertionError("Number of generated sentences and AMR graphs with gold realization is not equal. Found "
                    + amrs.size() + " AMRs and " + generatedSentences.size() + " generated sentences.");
        }
        for (int i = 0; i < amrs.size(); i++) {
            System.out.println("Gold:      " + String.join(" ", amrs.get(i).sentence).toLowerCase());
            System.out.println("Generated: " + generatedSentences.get(i));
            System.out.println("----------------------------------------------");
        }
    }

    /**
     * Computes the 1..4-gram lower-cased BLEU score of a list of sentences generated from some AMR graphs by comparing them with the graphs' reference realizations.
     * @param amrs the AMR graphs. These graphs must contain reference realizations in order for this function to work properly.
     * @param generatedSentences the sentences generated from the AMR graphs
     * @return the BLEU score
     */
    private static double getBleu(List<Amr> amrs, List<String> generatedSentences) {
        if(amrs.size() != generatedSentences.size()) {
            throw new AssertionError("Number of generated sentences and AMR graphs with gold realization is not equal. Found "
                    + amrs.size() + " AMRs and " + generatedSentences.size() + " generated sentences.");
        }
        List<String> references = new ArrayList<>();
        for (Amr amr : amrs) {
            if(amr.sentence == null) {
                throw new AssertionError("Found no reference realization for AMR graph " + amr + ", unable to compute BLEU score.");
            }
            String reference = String.join(" ", amr.sentence).toLowerCase().replaceAll(" [ ]+", " ").trim();
            references.add(reference);
        }
        List<Sequence<String>> hyps = generatedSentences.stream().map(string -> new ArraySequence<>(string.split(" "))).collect(Collectors.toList());
        List<List<ArraySequence<String>>> rfs = references.stream().map(string -> Collections.singletonList(new ArraySequence<>(string.split(" ")))).collect(Collectors.toList());

        BLEUMetric bleu = new BLEUMetric(rfs, false);
        return bleu.scoreSeq(hyps);
    }

    /**
     * Loads a list of AMR graphs from a given directory.
     * @param directory the directory from which the AMR graphs are loaded. This directory must not directly contain the AMR graphs, but instead contain
     *                  subdirectories as specified by {@link PathList#AMR_SUBDIRECTORIES} in which the actual AMR graphs are stored.
     * @param forTesting whether the AMR graphs should be prepared for testing (in which case no gold annotations, POS tagging and alignments are loaded) or for training
     * @return the preprocessed AMR graphs
     */
    private List<Amr> loadAmrGraphs(String directory, boolean forTesting) throws IOException {
        return loadAmrGraphs(directory, forTesting, -1, -1, -1);
    }

    /**
     * Loads a list of AMR graphs from a given directory.
     * @param directory the directory from which the AMR graphs are loaded. This directory must not directly contain the AMR graphs, but instead contain
     *                  subdirectories as specified by {@link PathList#AMR_SUBDIRECTORIES} in which the actual AMR graphs are stored.
     * @param forTesting whether the AMR graphs should be prepared for testing (in which case no gold annotations, POS tagging and alignments are loaded) or for training
     * @param limitPerSubdirectory the maximum number of AMR graphs to load per subdirectory. If all AMR graphs should be loaded, simply set this to some value below zero
     * @param minRealLength the minimum length (in words) of the reference realization for each AMR graph to be included in the returned list. If this is set to some value
     *                      &lt;0, all AMR graphs are included.
     * @param maxRealLenght the maximum length (in words) of the reference realization for each AMR graph to be included in the returned list. If this is set to some value
     *                      &lt;0, all AMR graphs are included.
     * @return the preprocessed AMR graphs
     */
    private List<Amr> loadAmrGraphs(String directory, boolean forTesting, int limitPerSubdirectory, int minRealLength, int maxRealLenght) throws IOException {

        List<Amr> ret = new ArrayList<>();

        boolean useJamrAlignments = !forTesting;
        boolean useEmAlignments = !forTesting;

        for (String subdirectory : PathList.AMR_SUBDIRECTORIES) {

            String path = directory + subdirectory;

            String posTagFilePath = null;
            String dependencyTreeFilePath = null;
            String amrsPath = path + PathList.AMR_FILENAME;

            if(!forTesting) {
                posTagFilePath = path + PathList.POS_FILENAME;
                dependencyTreeFilePath = path + PathList.DEPENDENCIES_FILENAME;
            }

            List<Amr> amrs = AmrParser.fromFile(amrsPath, dependencyTreeFilePath, posTagFilePath, limitPerSubdirectory, useJamrAlignments?AmrLineFormat.JAMR:null);

            if (useEmAlignments) {
                AmrParser.addAlignmentsFromFile(amrs, path + PathList.EM_ALIGNMENTS_FILENAME, AmrLineFormat.EXTERNAL);
            }

            Amr.prepare(amrs, posTagger, forTesting);
            Debugger.println("loaded and prepared " + amrs.size() + " AMR graphs from " + path);

            for(Amr amr: amrs) {
                boolean include = true;
                if(minRealLength >= 0 && amr.sentence.length < minRealLength) {
                    include = false;
                }
                if(maxRealLenght >= 0 && amr.sentence.length > maxRealLenght) {
                    include = false;
                }
                if(include) {
                    ret.add(amr);
                }
            }
        }
        return ret;
    }

    /**
     * Loads AMR graphs for testing from a file.
     * @param filename the name of the file
     * @return the preprocessed AMR graphs
     */
    private List<Amr> loadAmrGraphs(String filename) throws IOException {
        List<Amr> ret = AmrParser.fromFile(filename, null, null, -1, null);
        Amr.prepare(ret, posTagger, true);
        return ret;
    }

    /**
     * This class is a simple wrapper for all maximum entropy models required by the generator.
     */
    private class MaxentModelWrapper {

        // syntactic annotation maximum entropy classifiers
        PosMaxentModel posMaxentModel;
        TenseMaxentModel tenseMaxentModel;
        VoiceMaxentModel voiceMaxentModel;
        DenomMaxentModel denomMaxentModel;
        NumberMaxentModel numberMaxentModel;

        // first stage maximum entropy classifier
        FirstStageMaxentModel firstStageMaxentModel;

        // realization maximum entropy classifier
        RealizeMaxentModel realizeMaxentModel;

        // insertion maximum entropy classifiers
        OtherInsertionMaxentModel otherInsertionMaxentModel;
        ArgInsertionMaxentModel argInsertionMaxentModel;
        ChildInsertionMaxentModel childInsertionMaxentModel;

        // order maximum entropy classifiers
        ParentChildReorderMaxentModel parentChildReorderMaxentModel;
        SiblingReorderMaxentModel leftMaxEnt;
        SiblingReorderMaxentModel rightMaxEnt;

        MaxentModelWrapper() {}
    }

    private enum Models { POS, TENSE, VOICE, DENOM, NUMBER, FIRST_STAGE, REALIZE, INSERT_OTHERS, INSERT_ARGS, INSERT_CHILD, REORDER, REORDER_LEFT, REORDER_RIGHT}
}

@Parameters(commandDescription = "Generates English sentences from a list of AMR graphs.")
class CommandGenerate {
    @Parameter(names = {"--help", "-h"}, description="Help")
    Boolean help = false;

    @Parameter(names = {"--input", "-i"}, description = "The file in which the AMR graphs are stored. If left empty, the default test directory and its subdirectory as d" +
            "defined in PathLists.java are searched for AMR graphs")
    String inputFile;

    @Parameter(names = {"--output", "-o"}, description = "The file in which the generated sentences should be saved")
    String outputFile;

    @Parameter(names = {"--bleu", "-b"}, description = "Compute the BLEU score achieved by the generator on the given data set. This is only possible if the AMR graphs " +
            "are stored with tokenized reference realizations in the input file.")
    Boolean bleu = false;

    @Parameter(names = {"--show-output", "-s"}, description = "Show pairs of (reference realization, generated sentence) in the console when the generator is finished. " +
            "This is only possible if the AMR graphs are stored with tokenized reference realizations in the input file.")
    Boolean printOutputToStdout = false;
}