package ml;

import java.util.*;
import java.util.stream.Collectors;

/**
 * A helper class to manage a list of features and to turn them into a list of strings required for maximum entropy modeling.
 */
public class FeatureManager {

    private List<IndicatorFeature> features;
    private int filterIndex;

    /**
     * Creates a new feature manager.
     */
    public FeatureManager() {
        reset();
    }

    /**
     * Adds a feature to the feature manager.
     * @param feature the feature to add
     */
    public void add(IndicatorFeature feature) {
        features.add(feature);
        feature.setId(filterIndex);
        filterIndex++;
    }

    /**
     * Turns the list of {@link IndicatorFeature}s stored by this feature manager into a list of strings using their {@link IndicatorFeature#toContext()} methods.
     * @return the list of strings extracted from the {@link FeatureManager#features} of this feature manager
     */
    public List<String> toContext() {
        List<String> ret = new ArrayList<>();
        for (IndicatorFeature f: features) {
            ret.addAll(f.toContext());
        }
        reset();
        return ret;
    }

    /**
     * Adds to this feature manager all features contained within the given list.
     * @param featureList the list of features
     */
    public void addAllUnaries(List<IndicatorFeature> featureList) {
        for (IndicatorFeature feature : featureList) {
            add(feature);
        }
    }

    /**
     * Adds to this feature manager all pairwise combinations of features contained within the given list.
     * @param featureList the list of features
     */
    public void addAllPairs(List<IndicatorFeature> featureList) {
        for (int i = 0; i < featureList.size(); i++) {
            for (int j = i + getStartIndexIncrement(featureList.get(i)); j < featureList.size(); j++) {
                add(featureList.get(i).composeWith(featureList.get(j), i + "_" + j));
            }
        }
    }

    /**
     * Adds to this feature manager all triple combinations of features contained within the given list.
     * @param featureList the list of features
     */
    public void addAllTriplets(List<IndicatorFeature> featureList) {
        for (int i = 0; i < featureList.size(); i++) {
            for (int j = i + getStartIndexIncrement(featureList.get(i)); j < featureList.size(); j++) {
                for (int k = j + getStartIndexIncrement(featureList.get(j)); k < featureList.size(); k++) {
                    add(featureList.get(i).composeWith(featureList.get(j), "").composeWith(featureList.get(k), i + "_" + j + "_" + k));
                }
            }
        }
    }

    private void reset() {
        features = new ArrayList<>();
        filterIndex = 0;
    }

    private int getStartIndexIncrement(IndicatorFeature feature) {
        if(feature.isListFeature()) return 0;
        else return 1;
    }

}

