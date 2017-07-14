package ml;

import dag.Amr;
import dag.Edge;
import dag.Vertex;
import edu.stanford.nlp.ling.BasicDatum;
import edu.stanford.nlp.ling.Datum;
import gen.GoldSyntacticAnnotations;
import gen.GoldTransitions;
import misc.StaticHelper;

import java.util.*;
import java.util.stream.Collectors;

/**
 * This class implements a maximum entropy model for scoring &lt;<sub>*</sub> as required to score REORDER transitions (see Eq. 19, Section 4.2.1 Modeling).
 * The maximum entropy models for &lt;<sub>l</sub> and &lt;<sub>r</sub> are implemented by {@link SiblingReorderMaxentModel}.
 * See {@link StanfordMaxentModelImplementation} for further details on the implemented methods. The features used
 * by {@link ParentChildReorderMaxentModel#toDatumList(Amr, Vertex, boolean)} are explained in the thesis.
 */
public class ParentChildReorderMaxentModel extends StanfordMaxentModelImplementation {

    @Override
    public List<Datum<String, String>> toDatumList(Amr amr, Vertex v, boolean forTesting) {

        // when v is deleted or has no children, no meaninful feature vector can be derived from it
        if(v.getOutgoingEdges().size() <= 1 || v.isDeleted()) {
            return Collections.emptyList();
        }

        List<Datum<String,String>> ret = new ArrayList<>();
        Edge instanceEdge = v.getInstanceEdge();

        for(Edge e: v.getOutgoingEdges()) {
            if(e == instanceEdge) continue;
            String result = forTesting?"": GoldTransitions.getGoldParentChildOrder(amr, instanceEdge, e);

            if(!result.isEmpty()) {
                ret.add(toEvent(amr, v, e, result, forTesting, "", ""));
            }
        }
        return ret;
    }

    public Datum<String,String> toEvent(Amr amr, Vertex vertex, Edge edge, String result, boolean forTesting, String fromRealization, String fromVoice) {

        if (!forTesting) {
            fromRealization = GoldTransitions.getGoldRealization(amr, vertex.getInstanceEdge());
            fromVoice = GoldSyntacticAnnotations.getGoldVoice(amr, vertex);
        }

        if(fromVoice == null || !fromVoice.equals(GoldSyntacticAnnotations.PASSIVE)) {
            fromVoice = GoldSyntacticAnnotations.ACTIVE;
        }

        Edge instanceEdge = vertex.getInstanceEdge();
        List<Edge> outEdges = new ArrayList<>(vertex.getOutgoingEdges());
        outEdges.remove(instanceEdge);

        List<Edge> childOutEdges = new ArrayList<>(edge.getTo().getOutgoingEdges());
        outEdges.remove(edge.getTo().getInstanceEdge());

        List<String> outStrings = outEdges.stream().map(e -> e.getLabel()).distinct().collect(Collectors.toList());
        List<String> childOutStrings = childOutEdges.stream().map(e -> e.getLabel()).distinct().collect(Collectors.toList());
        Collections.sort(outStrings);

        ListFeature argFeatures = new ListFeature("argFeatures");
        ListFeature argLinkFeatures = new ListFeature("argLinkFeatures");

        ListFeature childArgFeatures = new ListFeature("childArgFeatures");
        ListFeature childArgLinkFeatures = new ListFeature("childArgLinkFeatures");

        for (int i = 0; i < 4; i++) {
            if (outStrings.contains(":ARG" + i)) {
                argFeatures.add(i + "pr");
            } else {
                argFeatures.add(i + "no");
            }
        }

        for (int i = 0; i < 4; i++) {

            int finalI = i;
            Optional<Edge> outE = outEdges.stream().filter(e -> e.getLabel().equals(":ARG" + finalI)).findFirst();
            if (outE.isPresent()) {
                if (outE.get().getTo().isLink()) {
                    argLinkFeatures.add(i + "link");
                } else {
                    argLinkFeatures.add(i + "pr");
                }
            } else {
                argLinkFeatures.add(i + "no");
            }
        }

        for (int i = 0; i < 4; i++) {
            if (childOutStrings.contains(":ARG" + i)) {
                childArgFeatures.add(i + "pr");
            } else {
                childArgFeatures.add(i + "no");
            }
        }

        for (int i = 0; i < 4; i++) {

            int finalI = i;
            Optional<Edge> outE = childOutEdges.stream().filter(e -> e.getLabel().equals(":ARG" + finalI)).findFirst();
            if (outE.isPresent()) {
                if (outE.get().getTo().isLink()) {
                    childArgLinkFeatures.add(i + "link");
                } else {
                    childArgLinkFeatures.add(i + "pr");
                }
            } else {
                childArgLinkFeatures.add(i + "no");
            }
        }

        int depth = edge.getTo().subtreeSize();
        String edgeLabel = edge.getLabel();
        String instPOS = vertex.getPos();
        Vertex target = edge.getTo();

        String inst = StaticHelper.getInstanceOrNumeric(vertex);
        String edgeInst = StaticHelper.getInstanceOrNumeric(target);

        List<IndicatorFeature> features = new ArrayList<>();

        List<String> outLabelPosTag = new ArrayList<>();
        List<String> childOutLabelPosTag = new ArrayList<>();

        for (Edge e : outEdges) {
            outLabelPosTag.add(e.getLabel() + "-" + (e.getTo().isPropbankEntry() ? ":PROP" : e.getTo().getPos()));
        }
        for (Edge e : childOutEdges) {
            childOutLabelPosTag.add(e.getLabel() + "-" + (e.getTo().isPropbankEntry() ? ":PROP" : e.getTo().getPos()));
        }

        String edgePOSifNotPropbank = target.isPropbankEntry() ? ":PROP" : target.getPos();

        features.add(new StringFeature("instance", inst));
        features.add(new StringFeature("instPos", instPOS));
        features.add(new StringFeature("instPosPassive", instPOS + fromVoice));
        features.add(new StringFeature("instReal", inst + "," + instPOS + fromVoice));
        features.add(new ListFeature("instOutEdges", outStrings));
        features.add(new StringFeature("instOutEdgesSize", outStrings.size()));
        features.add(new ListFeature("instOutLabelPosTag", outLabelPosTag));
        features.add(new ListFeature("instChildrenWithLabels", outEdges.stream().map(e -> e.getLabel() + StaticHelper.getInstanceOrNumeric(e.getTo())).collect(Collectors.toList())));
        features.add(new StringFeature("instDepth", vertex.subtreeSize()));
        features.add(new StringFeature("instMode", vertex.mode));

        features.add(new StringFeature("edgeLabel", edgeLabel));
        features.add(new StringFeature("edgeDepth", depth));
        features.add(new StringFeature("edgeInst", edgeInst));
        features.add(new StringFeature("edgeInstHasChildren", edge.getTo().getOutgoingEdges().size() > 1));
        features.add(new StringFeature("edgePOS", edgePOSifNotPropbank));
        features.add(new ListFeature("childOutEdges", childOutStrings));
        features.add(new StringFeature("childOutEdgesSize", childOutStrings.size()));
        features.add(new ListFeature("childOutLabelPosTag", childOutLabelPosTag));
        features.add(new ListFeature("childChildrenWithLabels", childOutEdges.stream().map(e -> e.getLabel() + StaticHelper.getInstanceOrNumeric(e.getTo())).collect(Collectors.toList())));

        features.add(argFeatures);
        features.add(argLinkFeatures);
        features.add(childArgFeatures);
        features.add(childArgLinkFeatures);

        List<IndicatorFeature> newFeatures = new ArrayList<>();
        int i=0;
        StringFeature instFeature = new StringFeature("edgeLabel", edgeLabel);
        for(IndicatorFeature feature: features) {
            newFeatures.add(feature.composeWith(instFeature, "*c"+i));
            i++;
        }
        features.addAll(newFeatures);
        featureManager.addAllUnaries(features);

        List<String> context = featureManager.toContext();
        return new BasicDatum<>(context, result);
    }
}