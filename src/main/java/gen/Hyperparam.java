package gen;

import misc.Debugger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * This class is used to represent a hyperparameter used in the generation pipeline. For a list of all hyperparams, see {@link Hyperparams}.
 * For further details on hyperparameter optimization, refer to section "Hyperparameter Optimization" of the thesis.
 */
public class Hyperparam {

    // this flag decides whether new hyperparameter assignments are searched randomly or deterministically
    public static boolean REALIZE_RANDOM = false;

    // the minimum number of hyperparams to modify for random updates
    public static int MIN_PARAMS_TO_UPDATE = 1;
    public static int MAX_PARAMS_TO_UPDATE = 5;

    public static int NUMBER_OF_STEPS = 11;

    private static final Random RAND = new Random();
    public static List<Hyperparam> allParams = new ArrayList<>();
    public static int currentParam = -1;

    public double defaultValue;
    public double bestValue;
    public double min;
    public double max;

    private double lastRealization = -1;

    /**
     * Creates a new hyperparameter with range (0,1)
     * @param defaultValue the default value for this hyperparameter
     */
    public Hyperparam(double defaultValue) {
        this(defaultValue, 0, 1);
    }

    /**
     * Creates a new hyperparameter with range (0, max)
     * @param defaultValue the default value for this hyperparameter
     * @param max the maximum value for this hyperparameter
     */
    public Hyperparam(double defaultValue, double max) {
        this(defaultValue, 0, max);
    }

    /**
     * Creates a new hyperparameter with range(min, max)
     * @param defaultValue the default value for this hyperparameter
     * @param min the minimum value for this hyperparameter
     * @param max the maximum value for this hyperparameter
     */
    public Hyperparam(double defaultValue, double min, double max) {
        this.defaultValue = defaultValue;
        this.bestValue = defaultValue;
        this.min = min;
        this.max = max;
        allParams.add(this);
    }

    /**
     * @return the current realization of the hyperparameter or a new random realization if {@code REALIZE_RANDOM = true}
     */
    public double realize() {
        if(REALIZE_RANDOM) {
            lastRealization = min + RAND.nextDouble()*(max-min);
            return lastRealization;
        }
        else return defaultValue;
    }

    /**
     * Computes a random value in the range {@code [min, max]}.
     * @return the random value
     */
    private double getRandomValue() {
        return min + RAND.nextDouble()*(max-min);
    }

    /**
     * Computes a random value in the range {@code [(1-r) * defaultValue, (1+r) * defaultValue]}.
     * @return the random value
     */
    private double getRandomValue(double r) {
        int sign = RAND.nextBoolean()?-1:1;
        double val = RAND.nextDouble() * r;
        return defaultValue * (1 + sign*val);
    }

    protected double stepSize() {
        return (max-min)/(double)NUMBER_OF_STEPS;
    }

    /**
     * Updates the localized hyperparameter optimization procedure by setting a new random value for
     * at least {@link Hyperparam#MIN_PARAMS_TO_UPDATE} and at most {@link Hyperparam#MAX_PARAMS_TO_UPDATE} hyperparameters;
     * the actual number of updated hyperparameters is chosen randomly.
     * @param newBestScore whether the previous random update achieved a new best score
     */
    public static void updateRandom(boolean newBestScore) {

        if(newBestScore) {
            for (Hyperparam param : allParams) {
                param.bestValue = param.defaultValue;
            }
        }
        else {
            for (Hyperparam param: allParams) {
                param.defaultValue = param.bestValue;
            }
        }

        int paramsToUpdate = MIN_PARAMS_TO_UPDATE + RAND.nextInt(MAX_PARAMS_TO_UPDATE-MIN_PARAMS_TO_UPDATE+1);
        List<Hyperparam> shuffledParams = new ArrayList<>(allParams);
        Collections.shuffle(shuffledParams);
        shuffledParams.subList(0, paramsToUpdate).forEach(hyp -> hyp.defaultValue = hyp.getRandomValue());
    }

    /**
     * Updates the localized hyperparameter optimization procedure by setting a new random value for
     * at least {@link Hyperparam#MIN_PARAMS_TO_UPDATE} and at most {@link Hyperparam#MAX_PARAMS_TO_UPDATE} hyperparameters from a given list.
     * The actual number of updated hyperparameters is chosen randomly and the new value is in the range {@code [(1-r) * defaultValue, (1+r) * defaultValue]}.
     * @param hyperparams the list of hyperparams
     * @param r the range parameter
     * @param newBestScore whether the previous random update achieved a new best score
     */
    public static void updateRandom(List<Hyperparam> hyperparams, double r, boolean newBestScore) {

        if(newBestScore) {
            for (Hyperparam param : hyperparams) {
                param.bestValue = param.defaultValue;
            }
        }
        else {
            for (Hyperparam param: hyperparams) {
                param.defaultValue = param.bestValue;
            }
        }

        int paramsToUpdate = MIN_PARAMS_TO_UPDATE + RAND.nextInt(MAX_PARAMS_TO_UPDATE-MIN_PARAMS_TO_UPDATE+1);
        List<Hyperparam> shuffledParams = new ArrayList<>(hyperparams);
        Collections.shuffle(shuffledParams);
        shuffledParams.subList(0, Math.min(hyperparams.size(), paramsToUpdate)).forEach(hyp -> hyp.defaultValue = hyp.getRandomValue(r));
    }

    /**
     * Updates the localized hyperparameter optimization procedure by setting a new value for the currently considered hyperparameter {@code allParams.get(currentParam)}.
     * @param valueGivesNewBest whether the previous value achieved a new best score
     * @return whether all possible values for the current parameter have been tried
     */
    public static boolean update(boolean valueGivesNewBest) {

        boolean nextParam = false;
        Hyperparam current = null;

        if(currentParam >= 0) {
            current = allParams.get(currentParam);

            if (valueGivesNewBest) {
                current.bestValue = current.defaultValue;
            }
        }

        if(currentParam == -1 || current.defaultValue == current.max) {
            nextParam = true;
        }

        if(nextParam) {
            if(current != null) {
                current.defaultValue = current.bestValue;
            }
            do {
                currentParam++;
                if(currentParam >= allParams.size()) currentParam = 0;
            }
            while(allParams.get(currentParam).min == allParams.get(currentParam).max);
            current = allParams.get(currentParam);
            current.defaultValue = current.min;
        }
        else {
            current.defaultValue = Math.min(current.max, current.defaultValue + current.stepSize());
        }

        return nextParam;

    }

    /**
     * prints the current assignment to all hyperparameters, i.e. the list (theta_1, ..., theta_n)
     */
    public static void printDebug() {
        StringBuilder ret = new StringBuilder();
        for(Hyperparam param: allParams) {
            double val = param.lastRealization>=0?param.lastRealization:param.defaultValue;
            ret.append(val + "; ");
        }
        Debugger.println("hyperparams = " +  allToString());
    }

    /**
     * returns the current assignment to all hyperparameters, i.e. the list (theta_1, ..., theta_n), as a string.
     */
    public static String allToString() {
        StringBuilder ret = new StringBuilder();
        ret.append("(");
        for(Hyperparam param: allParams) {
            double val = param.lastRealization>=0?param.lastRealization:param.defaultValue;
            ret.append(val + "; ");
        }
        ret.append(")");
        return ret.toString();
    }

    /**
     * Initializes all hyperparameters from a file which contains the values for all hyperparameters, separated by semicolons.
     * If multiple such entries are found in the file (i.e. it contains more than one line), the parameters written in the very last line are assumed to
     * be the most up to date ones.
     * @param filename a file containing one or multiple newline-separated strings as required by {@link Hyperparam#initializeFromString(String)}
     */
    public static void initializeFromFile(String filename) throws IOException {
        List<String> lines = Files.readAllLines(Paths.get(filename));
        if(lines.size() == 0) {
            throw new AssertionError("no hyperparameter values found in file " + filename);
        }
        else if(lines.size() > 1){
            Debugger.println("found more than one hyperparameter specification in file " + filename + ", taking the specification found in line " + lines.size());
        }
        initializeFromString(lines.get(lines.size()-1));
    }

    /**
     * Initializes all hyperparameters from a string similar to the one returned by {@link Hyperparam#printDebug()}.
     * @param desc A string describing all hyperparameters of the form {@code (val_1; val_2; ...; val_n; )}
     */
    public static void initializeFromString(String desc) {
        String[] vals = desc.substring(1, desc.length()-3).split("; ");
        if(vals.length != allParams.size()) throw new AssertionError("too many or too little values found");
        for(int i=0; i<vals.length; i++) {

            Hyperparam param = allParams.get(i);
            double value = Double.valueOf(vals[i]);

            if(param instanceof IntHyperparam) {
                value = (int)value;
            }

            param.defaultValue = value;
            param.bestValue = value;
        }
    }

}
