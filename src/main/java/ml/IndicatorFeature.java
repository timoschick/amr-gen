package ml;

import java.util.List;

/**
 * Abstract class to model an indicator feature.
 */
abstract class IndicatorFeature {

    protected static final String UNNAMED = "unnamed";

    private String name;
    private boolean mandatory;
    private int id;

    /**
     * Turns this feature into a context, i.e. a list of strings that can then be turned into feature functions by a maximum entropy model.
     * @return the feature's representation as a list of string
     */
    abstract List<String> toContext();

    /**
     * Returns whether the feature can be composed with other features to form a composite feature.
     * @return whether the feature is composable
     */
    public boolean isComposable() {
        return true;
    }

    /**
     * Composes this feature with another indicator feature.
     * @param feature the feature to compose this feature with
     * @param id a unique id assigned to the resulting feature
     * @return the composed feature or null if one of the features is not composable as defined by {@link IndicatorFeature#isComposable()}
     */
    public IndicatorFeature composeWith(IndicatorFeature feature, String id) {

        if(!isComposable() || !feature.isComposable()) return null;

        List<String> thisContext = toContext();
        List<String> thatContext = feature.toContext();

        ListFeature ret = new ListFeature(this.getName() + "-" + feature.getName());

        for(String c1: thisContext) {
            for(String c2: thatContext) {
                ret.add(c1 + "~" + c2 + "~" + id);
            }
        }
        return ret;
    }

    /**
     * Returns whether the feature represents a list of elements (e.g. the set of outgoing edge labels of some vertex)
     * or just a single element (e.g. the concept label of a vertex).
     * @return whether the feature represents a list of elements
     */
    public abstract boolean isListFeature();

    public boolean isMandatory() {
        return mandatory;
    }

    public IndicatorFeature makeMandatory() {
        mandatory = true;
        return this;
    }

    public String getName() {
        return name;
    }

    protected void setName(String name) {
        this.name = name;
    }

    protected int getId() {
        return id;
    }

    protected void setId(int id) {
        this.id = id;
    }
}
