package ml;

import java.util.Collections;
import java.util.List;

/**
 * This class represents a feature that consists of a single String value.
 */
class StringFeature extends IndicatorFeature {

    public String featureString;

    public StringFeature(String name, int val) {
        this(name, val+"");
    }

    public StringFeature(String name, boolean val) {
        this(name, val+"");
    }

    public StringFeature(String featureString) {
        this(IndicatorFeature.UNNAMED, featureString);
    }
    public StringFeature(String name, String featureString) {
        this.setName(name);
        this.featureString = featureString;
    }

    public List<String> toContext() {
        return Collections.singletonList(featureString +"_f"+ getId());
    }

    @Override
    public boolean isListFeature() {
        return false;
    }
}
