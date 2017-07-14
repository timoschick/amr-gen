package gen;

import java.util.Arrays;
import java.util.List;

/**
 * This class stores all hyperparameters used in the generation pipeline. Note that reordering of these hyperparams or adding new ones must also be
 * properly reflected in the {@link main.PathList#HYPERPARAMS_LIST} file.
 */
public class Hyperparams {

    public Hyperparams() { }

    /**
     * the number of REORDER transitions to consider per vertex during the generation process
     * (see Definition 4.9, prune<sub>n</sub>)
     */
    public Hyperparam positionHelper_params_takeBestN = new IntHyperparam(5, 1, 8);

    /**
     * the maximum probability decrement for REORDER transitions to consider per vertex during the generation process
     * (see Definition 4.9, prune<sub>(n,r)</sub>)
     */
    public Hyperparam positionHelper_params_maxProbDecrement = new Hyperparam(0.4d);

    /**
     * the number of syntactic annotation values for key "NUMBER" to consider per vertex during the generation process
     * (see Definition 4.9, prune<sub>n</sub>)
     */
    public Hyperparam numberMaxEnt_params_takeBestN = new IntHyperparam(2, 1, 2);

    /**
     * the maximum probability decrement of syntactic annotation values for key "NUMBER" to consider per vertex during the generation process
     * (see Definition 4.9, prune<sub>(n,r)</sub>)
     */
    public Hyperparam numberMaxEnt_params_maxProbDecrement = new Hyperparam(0.4d);

    /**
     * the number of syntactic annotation values for key "VOICE" to consider per vertex during the generation process
     * (see Definition 4.9, prune<sub>n</sub>)
     */
    public Hyperparam voiceMaxEnt_params_takeBestN = new IntHyperparam(2, 1, 2);

    /**
     * the maximum probability decrement of syntactic annotation values for key "VOICE" to consider per vertex during the generation process
     * (see Definition 4.9, prune<sub>(n,r)</sub>)
     */
    public Hyperparam voiceMaxEnt_params_maxProbDecrement = new Hyperparam(0.05d);

    /**
     * the number of syntactic annotation values for key "TENSE" to consider per vertex during the generation process
     * (see Definition 4.9, prune<sub>n</sub>)
     */
    public Hyperparam tenseMaxEnt_params_takeBestN = new IntHyperparam(3, 1, 4);

    /**
     * the maximum probability decrement of syntactic annotation values for key "TENSE" to consider per vertex during the generation process
     * (see Definition 4.9, prune<sub>(n,r)</sub>)
     */
    public Hyperparam tenseMaxEnt_params_maxProbDecrement = new Hyperparam(0.4d, 0.1d, 0.6d);

    /**
     * the weight for the language model term in the score function of our model
     */
    public Hyperparam vertexTranslator_lmWeight = new Hyperparam(38.5d, 35, 45);

    /**
     * the weight for INSERT-BETWEEN transitions with p = left if the edge connecting &sigma;<sub>1</sub> and &beta;<sub>1</sub> is
     * a PropBank core role (ARG0 - ARG5)
     */
    public Hyperparam vertexTranslator_beforeInsArgWeight = new Hyperparam(1.5d, 0.1, 5);

    /**
     * the weight for INSERT-BETWEEN transitions with p = left if the edge connecting &sigma;<sub>1</sub> and &beta;<sub>1</sub> is
     * <b>not</b> a PropBank core role (ARG0 - ARG5)
     */
    public Hyperparam vertexTranslator_beforeInsWeight = new Hyperparam(1.5d, 0.1, 5);

    /**
     * the weight for INSERT-BETWEEN transitions with p = right
     */
    public Hyperparam vertexTranslator_afterInsWeight = new Hyperparam(2, 0, 5);

    /**
     * the probability assigned to default realizations
     */
    public Hyperparam vertexTranslator_defaultRealizationScore = new Hyperparam(.3, 0, 0.25);

    /**
     * a smoothing factor added to the probabilities of denominators
     */
    public Hyperparam vertexTranslator_articleSmoothing = new Hyperparam(0.066666d,0.066666d,0.066666d);

    /**
     * In the language model score, we divide by the number of words, but articles are not counted as full words, but instead weighted with this factor.
     * This is to prevent the insertion of too many articles as in general, they are strongly favoured by the language model.
     */
    public Hyperparam vertexTranslator_articleLmWeight = new Hyperparam(0.7f);

    /**
     * the weight for REORDER transitions
     */
    public Hyperparam vertexTranslator_reorderingWeight = new Hyperparam(4.5d, 0, 8);

    /**
     * the maximum number of REALIZE predictions per POS tag
     */
    public Hyperparam maxNrOfRealizePredictionsPerPos = new IntHyperparam(3, 1, 8);

    /**
     * the maximum number of REALIZE predictions in total
     */
    public Hyperparam maxNrOfRealizePredictions = new IntHyperparam(8, 1, 12);

    /**
     * the maximum number of partial transition functions to store per vertex
     */
    public Hyperparam maxNrOfPredictionsPerVertex = new IntHyperparam(11, 1, 11);

    /**
     * the number of REALIZE transitions to consider per vertex and syntactic annotation during the generation process
     * (see Definition 4.9, prune<sub>n</sub>)
     */
    public Hyperparam realizeMaxEnt_params_takeBestN = new IntHyperparam(2, 1, 5);

    /**
     * the maximum probability decrement of REALIZE transitions to consider per vertex and syntactic annotation during the generation process
     * (see Definition 4.9, prune<sub>(n,r)</sub>)
     */
    public Hyperparam realizeMaxEnt_params_maxProbDecrement = new Hyperparam(0.25);

    /**
     * the number of INSERT_BETWEEN transitions to consider per vertex with edge matching :ARG[0-9] during the generation process
     * (see Definition 4.9, prune<sub>n</sub>)
     */
    public Hyperparam argInsertionMaxEnt_params_takeBestN = new IntHyperparam(8, 1, 12);

    /**
     * the maximum probability decrement of INSERT_BETWEEN transitions to consider per vertex with edge matching :ARG[0-9] during the generation process
     * (see Definition 4.9, prune<sub>(n,r)</sub>)
     */
    public Hyperparam argInsertionMaxEnt_params_maxProbDecrement = new Hyperparam(0.4d);

    /**
     * the number of INSERT_BETWEEN transitions to consider per vertex with edge not matching :ARG[0-9] during the generation process
     * (see Definition 4.9, prune<sub>n</sub>)
     */
    public Hyperparam othersInsertionMaxEnt_params_takeBestN = new IntHyperparam(8, 1, 12);

    /**
     * the maximum probability decrement of INSERT_BETWEEN transitions to consider per vertex with edge not matching :ARG[0-9] during the generation process
     * (see Definition 4.9, prune<sub>(n,r)</sub>)
     */
    public Hyperparam othersInsertionMaxEnt_params_maxProbDecrement = new Hyperparam(0.5d);

    /**
     * the number of syntactic annotation values for key "DENOM" to consider per vertex during the generation process
     * (see Definition 4.9, prune<sub>n</sub>)
     */
    public Hyperparam denomMaxEnt_params_takeBestN = new IntHyperparam(3, 3, 3);

    /**
     * the maximum probability decrement of syntactic annotation values for key "DENOM" to consider per vertex during the generation process
     * (see Definition 4.9, prune<sub>(n,r)</sub>)
     */
    public Hyperparam denomMaxEnt_params_maxProbDecrement = new Hyperparam(0.8d);

    /**
     * the number of syntactic annotation values for key "POS" to consider per vertex during the generation process
     * (see Definition 4.9, prune<sub>n</sub>)
     */
    public Hyperparam posMaxEnt_params_takeBestN = new IntHyperparam(3, 1, 5);

    /**
     * the maximum probability decrement of syntactic annotation values for key "POS" to consider per vertex during the generation process
     * (see Definition 4.9, prune<sub>(n,r)</sub>)
     */
    public Hyperparam posMaxEnt_params_maxProbDecrement = new Hyperparam(0.6);

    /**
     * the weight for REALIZE transitions
     */
    public Hyperparam vertexTranslator_realizationWeight = new Hyperparam(0.5, 0, 5);

    /**
     * the weight for syntactic annotation key POS
     */
    public Hyperparam posWeight = new Hyperparam(2, 0, 5);

    /**
     * the weight for syntactic annotation key NUMBER
     */
    public Hyperparam numberWeight = new Hyperparam(2, 0, 5);

    /**
     * the weight for syntactic annotation key DENOM
     */
    public Hyperparam denomWeight = new Hyperparam(0.1, 0, 1);

    /**
     * the weight for syntactic annotation key VOICE
     */
    public Hyperparam voiceWeight = new Hyperparam(2, 0, 5);

    /**
     * the weight for syntactic annotation key TENSE
     */
    public Hyperparam tenseWeight = new Hyperparam(2, 0, 5);
}
