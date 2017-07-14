package ml;

import gen.PartialTransitionFunction;

import java.text.DecimalFormat;
import java.util.List;
import java.util.Map;

/**
 * This class represents a partial transition function along with an assigned score. For reasons of efficiency, the partial yield of the partial transition function
 * is stored in {@link Prediction#value} and the score without the language model factor is stored in {@link Prediction#lmFreeScore}.
 */
public class Prediction {

    public PartialTransitionFunction partialTransitionFunction;
    public String value;

    private double score;
    private double lmFreeScore;

    public Prediction(String value, double score) {
        this(value, score, score);
    }

    public Prediction(String value, double score, double lmFreeScore) {
        this.value = value;
        this.score = score;
        this.lmFreeScore = lmFreeScore;
        this.partialTransitionFunction = new PartialTransitionFunction();
    }

    public String getValue() {
        return value;
    }

    public double getLmFreeScore() {
        return lmFreeScore;
    }

    public double getScore() {
        return score;
    }

    public void setScoreAndLmFreeScore(double score) {
        this.score = score;
        this.lmFreeScore = score;
    }

    public void setScoreAndLmFreeScore(double score, double lmFreeScore) {
        this.score = score;
        this.lmFreeScore = lmFreeScore;
    }

}
