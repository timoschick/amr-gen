package ml;

/**
 * Helper class to evaluate precision, recall and F1 score of binary classifiers.
 */
public class LossEvaluator {

    public int tp, fp, tn, fn;
    public double total;

    /**
     * Constructs a new LossEvaluator with the following (initially empty) fields:
     * <ul>
     *     <li>{@code tp}: the number of true positives </li>
     *     <li>{@code fp}: the number of false positives</li>
     *     <li>{@code tn}: the number of true negatives </li>
     *     <li>{@code fn}: the number of false negatives</li>
     *     <li>{@code total}: the total score</li>
     * </ul>
     */
    public LossEvaluator() {

    }

    public double getPrecision() {
        if(tp + fp == 0) return 0.01;
        return tp / (double)(tp + fp);
    }

    public double getRecall() {
        return tp / (double)(tp + fn);
    }

    public double getF1Score() {

        double precision = getPrecision();
        double recall = getRecall();

        return 2 * precision * recall / (precision + recall);
    }

    public String toString() {
        return  "total = " + total + "\n" +
                "      gold+     gold- \n" +
                "best+ " + tp +"   "+ fp +"\n" +
                "best- " + fn +"   "+ tn +"\n";
    }
}
