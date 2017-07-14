package ml;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * This class represents a feature that consists of several String values.
 */
class ListFeature extends IndicatorFeature {

    public static final String EMPTY = "EMPTY";
    private boolean withEmptyFlag = true;

    public List<String> featureStrings;
    public ListFeature() {
        this(IndicatorFeature.UNNAMED);
    }
    public ListFeature(String name) {
        this.setName(name);
        featureStrings = new ArrayList<>();
    }

    public ListFeature(String name, List<String> strings) {
        this(name);
        this.featureStrings = strings;
    }

    public ListFeature(String name, List<String> strings, boolean withEmptyFlag) {
        this(name, strings);
        this.withEmptyFlag = withEmptyFlag;
    }

    public void add(String featureString) {
        featureStrings.add(featureString);
    }

    public List<String> toContext() {
        if(!featureStrings.isEmpty()) {
            return featureStrings.stream().map(f -> f + "_f" + getId()).sorted().collect(Collectors.toList());
        }
        else if(withEmptyFlag) {
            return Collections.singletonList(EMPTY + "_f" + getId());
        }
        else return Collections.emptyList();
    }

    @Override
    public boolean isListFeature() {
        return true;
    }

}
