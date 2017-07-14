package ml;

import dag.*;
import edu.stanford.nlp.ling.BasicDatum;
import edu.stanford.nlp.ling.Datum;
import gen.GoldSyntacticAnnotations;
import gen.GoldTransitions;

import java.util.*;
import java.util.stream.Collectors;

/**
 * This class implements a maximum entropy model for scoring INSERT_CHILD transitions. See {@link StanfordMaxentModelImplementation} for further details on the
 * implemented methods. The features used by {@link ChildInsertionMaxentModel#toDatumList(Amr, Vertex, boolean)} are explained in the thesis.
 */
public class ChildInsertionMaxentModel extends StanfordMaxentModelImplementation {

    @Override
    public List<Datum<String,String>> toDatumList(Amr amr, Vertex v, boolean forTesting) {
        return toDatumList(amr, v, forTesting, null, null);
    }

    public List<Datum<String,String>> toDatumList(Amr amr, Vertex v, boolean forTesting, String realization, String passiveString) {

        if(v.getInstanceEdge() == null) return Collections.emptyList();
        if(v.isLink() && forTesting) return Collections.emptyList();

        // we allow INSERT_CHILD transitions only for PropBank entries to improve performance
        if(!v.isPropbankEntry()) return Collections.emptyList();

        List<String> result = Collections.singletonList("");
        if(!forTesting) {
            result = GoldTransitions.getGoldChildInsertion(amr, v);
        }

        if(v.isDeleted()) return Collections.emptyList();

        String inLabel = v.getIncomingEdges().isEmpty()?":ROOT":v.getIncomingEdges().get(0).getLabel();
        List<IndicatorFeature> features = new ArrayList<>();

        String parentString = ":ROOT";
        String parentMode = ":ROOT";
        Vertex current = v;

        while(!current.getIncomingEdges().isEmpty()) {
            current = current.getIncomingEdges().get(0).getFrom();

            if(!current.getPos().equals("CC")) {
                parentString = current.getInstance();
                parentMode = current.mode;
                break;
            }
        }

        String pos = v.getPos();
        String vRealization;
        boolean isPassive;

        if(forTesting) {
            vRealization = realization;
            isPassive = passiveString != null && passiveString.equals(GoldSyntacticAnnotations.PASSIVE);
        }
        else {
            vRealization = GoldTransitions.getGoldRealization(amr, v.getInstanceEdge());
            isPassive = GoldSyntacticAnnotations.getGoldVoice(amr, v).equals(GoldSyntacticAnnotations.PASSIVE);
        }

        List<Edge> outEdges = new ArrayList<>(v.getOutgoingEdges());
        outEdges.remove(v.getInstanceEdge());
        List<String> outStrings = outEdges.stream().map(e -> e.getLabel()).distinct().collect(Collectors.toList());
        Collections.sort(outStrings);

        ListFeature argFeatures = new ListFeature("argFeatures");

        features.add(new StringFeature("realization", vRealization));

        features.add(new StringFeature("parentMode", parentMode));
        features.add(new StringFeature("mode", v.mode));
        features.add(new StringFeature("hasPolarity", v.getOutgoingEdges().stream().anyMatch(e -> e.getLabel().equals(":polarity"))));
        features.add(new StringFeature("pos-isPassive",pos + isPassive));

        features.add(argFeatures.composeWith(new StringFeature("inLabel", inLabel), "*c0"));
        features.add(argFeatures.composeWith(new StringFeature("pos", pos), "*c0"));
        features.add(argFeatures.composeWith(new StringFeature("realization", vRealization), "*c0"));
        features.add(new StringFeature("realization-parent", vRealization + parentString));

        featureManager.addAllUnaries(features);

        List<String> context = featureManager.toContext();

        List<Datum<String,String>> ret = new ArrayList<>();

        for(String res: result) {
            Datum<String,String> datum = new BasicDatum<>(context, res);
            ret.add(datum);
        }

        return ret;
    }

    @Override
    public void applyModification(Amr amr, Vertex vertex, List<Prediction> predictions) {
        vertex.predictions.put("childIns", predictions);
    }

}
