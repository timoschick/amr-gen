package ml;

import dag.Amr;
import dag.Edge;
import dag.Vertex;
import edu.stanford.nlp.ling.Datum;
import edu.stanford.nlp.ling.RVFDatum;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import gen.GoldSyntacticAnnotations;
import misc.StaticHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * This class implements a maximum entropy model for tenses. See {@link StanfordMaxentModelImplementation} for further details on the
 * implemented methods. The features used by {@link TenseMaxentModel#toDatumList(Amr, Vertex, boolean)} are explained in the thesis.
 */
public class TenseMaxentModel extends StanfordMaxentModelImplementation {

    @Override
    public List<Datum<String, String>> toDatumList(Amr amr, Vertex vertex, boolean forTesting) {

        String result = "";

        if(!forTesting) {
            if(!vertex.getPos().equals("VB")) return Collections.emptyList();
            result = GoldSyntacticAnnotations.getGoldTense(amr, vertex);
            if(result.isEmpty()) return Collections.emptyList();
        }


        List<Edge> outEdges = new ArrayList<>(vertex.getOutgoingEdges());
        outEdges.remove(vertex.getInstanceEdge());

        List<String> outStrings = outEdges.stream().map(e -> e.getLabel()).distinct().collect(Collectors.toList());
        Collections.sort(outStrings);

        ListFeature argFeatures = new ListFeature("argFeatures");

        for (int i = 0; i < 4; i++) {
            if (outStrings.contains(":ARG" + i)) {
                argFeatures.add(i + "pr");
            } else {
                argFeatures.add(i + "no");
            }
        }

        String instance = vertex.getInstance();
        String inLabel, parentInstance;

        ListFeature parentInLabels = new ListFeature("parentInLabels");
        ListFeature parentPosTags = new ListFeature("parentPosTags");
        ListFeature parentPropEntries = new ListFeature("parentPropEntries");

        Vertex currentVertex = vertex;
        Vertex parentVertex = null, grandparentVertex = null;

        int distanceToRoot = 0;
        while (!currentVertex.getIncomingEdges().isEmpty()) {

            if (distanceToRoot > 0) {
                parentInLabels.add(currentVertex.getIncomingEdges().isEmpty() ? ":ROOT" : currentVertex.getIncomingEdges().get(0).getLabel());
                parentPropEntries.add(currentVertex.isPropbankEntry() + "_d:" + distanceToRoot);
                parentPosTags.add(currentVertex.isPropbankEntry() ? ":PROP" : currentVertex.getPos());
            }

            if (currentVertex == vertex) {
                parentVertex = currentVertex.getIncomingEdges().get(0).getFrom();
            } else if (currentVertex == parentVertex) {
                grandparentVertex = currentVertex.getIncomingEdges().get(0).getFrom();
            }

            distanceToRoot++;
            currentVertex = currentVertex.getIncomingEdges().get(0).getFrom();
        }

        String parentPos;

        inLabel = ":ROOT";
        parentInstance = ":ROOT";
        parentPos = ":ROOT";

        String grandparentInstance = ":ROOT";
        String parentInLabel = ":ROOT";
        String grandparentPos = ":ROOT";

        if (parentVertex != null) {
            parentInstance = StaticHelper.getInstanceOrNumeric(parentVertex);
            parentPos = parentVertex.isPropbankEntry() ? ":PROP" : parentVertex.getPos();
            inLabel = vertex.getIncomingEdges().get(0).getLabel();
        }

        if (grandparentVertex != null) {
            grandparentInstance = grandparentVertex.getInstance();
            grandparentPos = grandparentVertex.isPropbankEntry() ? ":PROP" : grandparentVertex.getPos();
            parentInLabel = parentVertex.getIncomingEdges().get(0).getLabel();
        }

        List<String> argOfFeatures = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            if (inLabel.equals(":ARG" + i + "-of")) {
                argOfFeatures.add(i + "pr");
            } else {
                argOfFeatures.add(i + "npr");
            }
        }

        List<IndicatorFeature> features = new ArrayList<>();

        List<String> neighbourLabels = new ArrayList<>();
        List<String> neighbourInstances = new ArrayList<>();
        List<String> neighbourPosTags = new ArrayList<>();
        List<String> neighbourLabelPosTags = new ArrayList<>();
        List<String> neighbourLabelInstances = new ArrayList<>();
        if (parentVertex != null) {
            for (Edge e : parentVertex.getOutgoingEdges()) {
                if (!e.isInstanceEdge() && e != vertex.getIncomingEdges().get(0)) {
                    neighbourLabels.add(e.getLabel());
                    neighbourInstances.add(StaticHelper.getInstanceOrNumeric(e.getTo()));
                    neighbourPosTags.add(e.getTo().isPropbankEntry() ? ":PROP" : e.getTo().getPos());

                    neighbourLabelPosTags.add(e.getLabel() + "-" + (e.getTo().isPropbankEntry()?":PROP":e.getTo().getPos()));
                    neighbourLabelInstances.add(e.getLabel() + "-" + StaticHelper.getInstanceOrNumeric(e.getTo()));

                }
            }
        }

        List<String> outLabelPosTag = new ArrayList<>();

        for (Edge e : outEdges) {
            outLabelPosTag.add(e.getLabel() + "-" + (e.getTo().isPropbankEntry() ? ":PROP" : e.getTo().getPos()));
        }

        boolean hasInverseLabel = inLabel.endsWith("-of");
        boolean hasArgLabel = inLabel.startsWith(":ARG");

        List<String> parentInstances = new ArrayList<>();
        if(parentVertex != null) {
            parentInstances.add(parentInstance);
            for (Vertex v : amr.dag) {
                if (v.isLink()) {
                    if (v.annotation.original == vertex) {
                        if(!v.getIncomingEdges().isEmpty()) {
                            parentInstances.add(v.getIncomingEdges().get(0).getFrom().getInstance());
                        }
                    }
                }
            }
        }

        List<Vertex> vertices = amr.dag.getVertices();
        Set<Edge> edges = amr.dag.getEdges();

        features.add(new ListFeature("wordsWithInLabel", vertices.stream().map(v -> (v.name.isEmpty()?v.getInstance():v.name) + (v.getIncomingEdges().isEmpty()?":ROOT":v.getIncomingEdges().get(0).getLabel())).collect(Collectors.toList())));
        features.add(new ListFeature("labels", edges.stream().map(e -> e.getLabel()).collect(Collectors.toList())));

        features.add(new ListFeature("successorsWithInLabel", vertex.getVerticesBottomUp().stream().map(v -> (v.name.isEmpty()?v.getInstance():v.name) + (v.getIncomingEdges().isEmpty()?":ROOT":v.getIncomingEdges().get(0).getLabel())).collect(Collectors.toList())));
        features.add(new ListFeature("successorsLabels", vertex.getEdgesBottomUp().stream().map(e -> e.getLabel()).collect(Collectors.toList())));

        features.add(new ListFeature("outLabels", outStrings));
        features.add(new StringFeature("instance", instance));
        features.add(new ListFeature("argOfFeatures", argOfFeatures));
        features.add(new ListFeature("neighbourLabels", neighbourLabels));
        features.add(new ListFeature("neighbourInstances", neighbourInstances));
        features.add(new ListFeature("neighbourPosTags", neighbourPosTags));
        features.add(new ListFeature("neighbourLabelPosTags", neighbourLabelPosTags));
        features.add(new ListFeature("neighbourLabelInstances", neighbourLabelInstances));

        features.add(new ListFeature("children", outEdges.stream().filter(e -> !e.isInstanceEdge()).map(e -> StaticHelper.getInstanceOrNumeric(e.getTo())).collect(Collectors.toList())));
        features.add(new ListFeature("nonLinkChildren", outEdges.stream().filter(e -> !e.isInstanceEdge() && !e.getTo().isLink()).map(e -> StaticHelper.getInstanceOrNumeric(e.getTo())).collect(Collectors.toList())));

        features.add(new StringFeature("parentPos", parentPos));
        features.add(new StringFeature("parentInst", parentInstance));
        features.add(new ListFeature("parentInstances", parentInstances));

        features.add(new StringFeature("grandparentPos", grandparentPos));
        features.add(new StringFeature("grandparentInst", grandparentInstance));
        features.add(new StringFeature("parentInLabel", parentInLabel));

        features.add(parentPosTags);
        features.add(parentInLabels);
        features.add(parentPropEntries);
        features.add(new StringFeature("distToRoot", distanceToRoot));

        features.add(new StringFeature("inLabel", inLabel));
        features.add(new StringFeature("outSize", outEdges.size()));
        features.add(new StringFeature("outEmpty", outEdges.isEmpty()));

        features.add(new ListFeature("outLabel-posTag", outLabelPosTag));
        features.add(new ListFeature("childrenWithLabels", outEdges.stream().map(e -> e.getLabel() + StaticHelper.getInstanceOrNumeric(e.getTo())).collect(Collectors.toList())));

        features.add(argFeatures);
        features.add(new StringFeature("numberOfArgs", outEdges.stream().filter(e -> e.getLabel().matches(":ARG[0-9]")).count() + ""));
        features.add(new StringFeature("mode", vertex.mode));

        features.add(new StringFeature("depth", vertex.subtreeSize()));

        features.add(new StringFeature("hasInverseLabel", hasInverseLabel));
        features.add(new StringFeature("hasInvArgFeature", (hasInverseLabel && hasArgLabel)));

        List<IndicatorFeature> newFeatures = new ArrayList<>();
        int i=0;
        StringFeature instFeature = new StringFeature("inst", instance);
        for(IndicatorFeature feature: features) {
            newFeatures.add(feature.composeWith(instFeature, "*c"+i));
            i++;
        }
        features.addAll(newFeatures);
        featureManager.addAllUnaries(features);

        List<String> context = featureManager.toContext();

        this.usesRVF = true;

        Counter<String> counter = new ClassicCounter<>();

        for(String contextString: context) {
            counter.setCount(contextString, 1);
        }

        return Collections.singletonList(new RVFDatum<>(counter, result));

    }


    @Override
    public void applyModification(Amr amr, Vertex vertex, List<Prediction> predictions) {
        vertex.predictions.put("tense", predictions);
    }

}
