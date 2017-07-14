package ml;

import dag.*;
import edu.stanford.nlp.ling.Datum;
import edu.stanford.nlp.ling.RVFDatum;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import gen.GoldTransitions;
import misc.PosHelper;
import misc.StaticHelper;

import java.util.*;
import java.util.stream.Collectors;

/**
 * This class implements a maximum entropy model for scoring INSERT_BETWEEN transitions when the label of the relevant edge is <b>not</b> a PropBank argument slot,
 * i.e. it does not match :ARG[0-9]. For all PropBank edge labels, {@link ArgInsertionMaxentModel} should be used. See {@link StanfordMaxentModelImplementation} for further
 * details on the implemented methods. The features used by {@link OtherInsertionMaxentModel#toDatumList(Amr, Vertex, boolean)} are explained in the thesis.
 */
public class OtherInsertionMaxentModel extends StanfordMaxentModelImplementation {

    public List<Datum<String,String>> toDatumList(Amr amr, Vertex vertex, boolean forTesting) {
        List<Datum<String,String>> ret = new ArrayList<>();
        Edge instanceEdge = vertex.getInstanceEdge();
        for(Edge e: vertex.getOutgoingEdges()) {
            if(e == instanceEdge) continue;
            ret.addAll(toDatumList(amr, e, forTesting, null, null, null));
        }
        return ret;
    }

    public List<Datum<String,String>> toDatumList(Amr amr, Edge edge, boolean forTesting, String relPos, String fromRealization, String toRealization) {

        boolean isArg = edge.getLabel().matches(":ARG[0-9]");
        if(isArg) return Collections.emptyList();

        if(edge.getTo().isLink()) return Collections.emptyList();

        List<String> result = Collections.singletonList("");
        if(!forTesting) {
            result = GoldTransitions.getGoldBetweenInsertion(amr, edge);
        }

        Vertex from = edge.getFrom();
        Vertex to = edge.getTo();

        if(to.getPos() != null && to.getPos().equals("CC")) {
            Optional<Edge> trueChild = edge.getTo().getOutgoingEdges().stream().filter(e -> e.getLabel().equals(":op1")).findFirst();
            if(trueChild.isPresent()) {
                to = trueChild.get().getTo();
            }
        }

        String fromPOS, toPOS, simpleFromPos;

        fromPOS = PosHelper.mapPos(from.getPos(), from.isPropbankEntry());
        toPOS = PosHelper.mapPos(to.getPos(), to.isPropbankEntry());

        simpleFromPos = fromPOS;
        if(simpleFromPos.equals("VBN") || simpleFromPos.equals("VBG")) simpleFromPos = "VB";

        String fromInst = from.getInstance();
        String toInst = to.getInstance();

        String label = edge.getLabel();

        String relativePosition;

        // get the gold relative position
        if(!forTesting) {
            if (from.isDeleted() || !amr.alignment.containsKey(from.getInstanceEdge())) {
                relativePosition = "d";
            } else if (GoldTransitions.getGoldParentChildOrder(amr, from.getInstanceEdge(), edge).equals(GoldTransitions.CHILD_BEFORE_PARENT)) {
                relativePosition = "l";
            } else {
                relativePosition = "r";
            }

        }
        else {
            relativePosition = relPos;
        }

        String fromReal, toReal;
        if(forTesting) {
            fromReal = fromRealization;
            toReal = toRealization;
        }
        else {
            fromReal = GoldTransitions.getGoldRealization(amr, from.getInstanceEdge());
            toReal = GoldTransitions.getGoldRealization(amr, to.getInstanceEdge());
        }

        String fromRealSing = fromReal==null?"":fromReal.endsWith("s")?fromReal.substring(0, fromReal.length()-1):fromReal;
        String toRealSing = toReal==null?"":toReal.endsWith("s")?toReal.substring(0, toReal.length()-1):toReal;

        int depth = to.subtreeSize();

        List<Edge> fromOutEdges = new ArrayList<>(from.getOutgoingEdges());
        fromOutEdges.remove(from.getInstanceEdge());

        List<Edge> toOutEdges = new ArrayList<>(to.getOutgoingEdges());
        toOutEdges.remove(to.getInstanceEdge());

        List<String> fromOutStrings = fromOutEdges.stream().map(e -> e.getLabel()).distinct().collect(Collectors.toList());
        Collections.sort(fromOutStrings);

        List<String> toOutStrings = toOutEdges.stream().map(e -> e.getLabel()).distinct().collect(Collectors.toList());
        Collections.sort(fromOutStrings);

        ListFeature fromArgFeatures = new ListFeature("fromArgFeatures");
        ListFeature toArgFeatures = new ListFeature("toArgFeatures");

        for (int i = 0; i < 4; i++) {
            if (fromOutStrings.contains(":ARG" + i)) {
                fromArgFeatures.add(i + "pr");
            } else {
                fromArgFeatures.add(i + "no");
            }

            if (toOutStrings.contains(":ARG" + i)) {
                toArgFeatures.add(i + "pr");
            } else {
                toArgFeatures.add(i + "no");
            }
        }

        Vertex currentVertex = from;

        int distanceToRoot = 0;
        while (!currentVertex.getIncomingEdges().isEmpty()) {
            distanceToRoot++;
            currentVertex = currentVertex.getIncomingEdges().get(0).getFrom();
        }

        List<IndicatorFeature> features = new ArrayList<>();

        List<String> fromOutLabelPosTag = new ArrayList<>();
        List<String> toOutLabelPosTag = new ArrayList<>();

        for (Edge e : fromOutEdges) {
            fromOutLabelPosTag.add(e.getLabel() + "-" + (e.getTo().isPropbankEntry() ? ":PROP" : e.getTo().getPos()));
        }

        for (Edge e : toOutEdges) {
            toOutLabelPosTag.add(e.getLabel() + "-" + (e.getTo().isPropbankEntry() ? ":PROP" : e.getTo().getPos()));
        }

        boolean hasInverseLabel = label.endsWith("-of");

        features.add(new StringFeature("label-fromNumberOfArgs", label + "," + fromOutEdges.stream().filter(e -> e.getLabel().matches(":ARG[0-9]")).count() ));
        features.add(new StringFeature("toWithPos-fromMode", toInst + toPOS + from.mode));

        features.add(new ListFeature("toOutLabels", toOutStrings).composeWith(new StringFeature("label", label), "*c0"));
        features.add(new StringFeature("label-relPos", label + relativePosition));
        features.add(new StringFeature("label-fromPOS", label + fromPOS));
        features.add(new StringFeature("fromInst", fromInst));
        features.add(new StringFeature("label-toRealSing", label + toRealSing));
        features.add(new StringFeature("label-toPOS", label + toPOS));
        features.add(new StringFeature("relPos-fromPOS", relativePosition + fromPOS));
        features.add(new StringFeature("relPos-toPOS", relativePosition + toPOS));

        features.add(new StringFeature("label", label));
        features.add(new StringFeature("label-toDepth", label + Math.min(4, depth)));
        features.add(new StringFeature("relativePosition", relativePosition));

        features.add(new StringFeature("toInst", toInst));
        features.add(new StringFeature("fromPos", fromPOS));
        features.add(new StringFeature("toPos", toPOS));
        features.add(new StringFeature("fromWithSimplePos", fromInst + simpleFromPos));
        features.add(new StringFeature("toWithPos", toInst + toPOS));
        features.add(new StringFeature("distToRoot", distanceToRoot));
        features.add(new StringFeature("fromMode", from.mode));
        features.add(new StringFeature("toMode", to.mode));
        features.add(toArgFeatures);
        features.add(toArgFeatures.composeWith(new StringFeature("label", label), "*c1"));

        features.add(new StringFeature("label-toNumberOfArgs", label + ";"+toOutEdges.stream().filter(e -> e.getLabel().matches(":ARG[0-9]")).count() + ""));

        features.add(new StringFeature("toOutSize", toOutEdges.size()));
        features.add(new StringFeature("toOutEmpty", toOutEdges.isEmpty()));

        features.add(new StringFeature("label-toOutSize", label + ";" + toOutEdges.size()));
        features.add(new StringFeature("label-toOutEmpty", label +";" + toOutEdges.isEmpty()));

        features.add(new ListFeature("to:outLabel-posTag", toOutLabelPosTag));
        features.add(new ListFeature("to:outLabelposTag", toOutLabelPosTag).composeWith(new StringFeature("label", label), "*c2"));

        features.add(new ListFeature("toChildren", toOutEdges.stream().filter(e -> !e.isInstanceEdge()).map(e -> StaticHelper.getInstanceOrNumeric(e.getTo())).collect(Collectors.toList())));
        features.add(new ListFeature("toChildrenWithLabels", toOutEdges.stream().map(e -> e.getLabel() + StaticHelper.getInstanceOrNumeric(e.getTo())).collect(Collectors.toList())));

        features.add(new StringFeature("hasInverseLabel", hasInverseLabel));

        features.add(new StringFeature("toName", to.name));
        features.add(new StringFeature("label-toName", label + "," + to.name));

        featureManager.addAllUnaries(features);
        List<String> context = featureManager.toContext();

        List<Datum<String,String>> ret = new ArrayList<>();

        this.usesRVF = true;

        Counter<String> counter = new ClassicCounter<>();

        for(String contextString: context) {
            counter.setCount(contextString, 1);
        }

        for(String res: result) {

            Datum<String,String> datum = new RVFDatum<>(counter, res);
            ret.add(datum);
        }

        return ret;
    }

    @Override
    public void applyModification(Amr amr, Vertex vertex, List<Prediction> predictions) {
        vertex.predictions.put("beforeIns", predictions);
    }
}
