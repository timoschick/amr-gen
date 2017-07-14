package gen;

/**
 * This class is used to represent a special kind of hyperparameter that allows only integer values (see {@link Hyperparam} for more details).
 */
public class IntHyperparam extends Hyperparam {

    /**
     * Creates a new hyperparameter with range(min, max)
     * @param defaultValue the default value for this hyperparameter
     * @param min the minimum value for this hyperparameter
     * @param max the maximum value for this hyperparameter
     * @throws AssertionError if {@code defaultValue}, {@code min} or {@code max} is not an integer
     */
    public IntHyperparam(double defaultValue, double min, double max) {
        super(defaultValue, min, max);
        if (!isInteger(defaultValue) || !isInteger(min) || !isInteger(max)) {
            throw new AssertionError("IntHyperparam values must be integers. Found (defaultValue = " + defaultValue + ", min = " + min + ", max = " + max + ")");
        }
    }

    @Override
    protected double stepSize() {
        return 1;
    }

    private boolean isInteger(double d){
        return (d % 1) == 0;
    }
}
