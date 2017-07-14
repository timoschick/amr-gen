package ml;

import dag.Amr;
import dag.Edge;
import dag.Vertex;
import edu.stanford.nlp.ling.BasicDatum;
import edu.stanford.nlp.ling.Datum;
import gen.GoldTransitions;
import main.PathList;
import misc.StaticHelper;
import misc.WordLists;
import misc.WordNetHelper;
import net.sf.extjwnl.data.POS;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;


public class FirstStageMaxentModel extends StanfordMaxentModelImplementation {

    // a map containing for each pair of vertices their best merge, extracted from the training corpus
    public final Map<String,String> bestMerges;

    public FirstStageMaxentModel() throws IOException {
        super();
        this.bestMerges = StaticHelper.mapFromFile(PathList.MERGEMAP_PATH);
    }

    @Override
    public List<Datum<String, String>> toDatumList(Amr amr, Vertex vertex, boolean forTesting) {
        String result = forTesting?"": GoldTransitions.getGoldActionFirstStage(amr, vertex);
        return toDatumList(amr, vertex, result, forTesting);
    }

    public List<Datum<String, String>> toDatumList(Amr amr, Vertex vertex, String result, boolean forTesting) {

        Edge instanceEdge = vertex.getInstanceEdge();
        if(instanceEdge == null || !vertex.name.isEmpty()) return Collections.emptyList();

        List<Edge> outEdges = new ArrayList<>(vertex.getOutgoingEdges());
        outEdges.remove(instanceEdge);

        List<String> outStrings = outEdges.stream().map(e -> e.getLabel()).distinct().collect(Collectors.toList());
        Collections.sort(outStrings);

        ListFeature argFeatures = new ListFeature("argFeatures");
        ListFeature argLinkFeatures = new ListFeature("argLinkFeatures");

        for (int i = 0; i < 4; i++) {
            if (outStrings.contains(":ARG" + i)) {
                argFeatures.add(i + "pr");
            } else {
                argFeatures.add(i + "no");
            }
        }

        for (int i = 0; i < 4; i++) {

            int finalI = i;
            Optional<Edge> outE = outEdges.stream().filter(e -> e.getLabel().equals(":ARG"+ finalI)).findFirst();
            if(outE.isPresent()) {
                if(outE.get().getTo().isLink()) {
                    argLinkFeatures.add(i + "link");
                }
                else {
                    argLinkFeatures.add(i + "pr");
                }
            }
            else {
                argLinkFeatures.add(i + "no");
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

        if (parentVertex != null) {
            parentInstance = StaticHelper.getInstanceOrNumeric(parentVertex);
            parentPos = parentVertex.isPropbankEntry() ? ":PROP" : parentVertex.getPos();
            inLabel = vertex.getIncomingEdges().get(0).getLabel();
        }

        if (grandparentVertex != null) {
            grandparentInstance = grandparentVertex.getInstance();
        }

        Vertex from = null;
        if(vertex.getIncomingEdges().size()>0) {
            from = vertex.getIncomingEdges().get(0).getFrom();
        }

        List<String> childrenWithLabels = vertex.getOutgoingEdges().stream().filter(e -> !e.isInstanceEdge()).map(e -> e.getLabel() + " " + e.getTo().getInstance()).collect(Collectors.toList());

        if(from != null) {
            parentInstance = from.getInstance();
            grandparentInstance = from.getIncomingEdges().isEmpty()?":ROOT":from.getIncomingEdges().get(0).getFrom().getInstance();
        }

        boolean mergeable = from != null;
        if(mergeable) {
            mergeable = bestMerges.containsKey(parentInstance + "\t" + instance);
        }

       List<IndicatorFeature> features = new ArrayList<>();

        features.add(new StringFeature("inLabel", inLabel));
        features.add(new ListFeature("childrenWithInLabel", childrenWithLabels));
        features.add(new ListFeature("childrenWithInLabel", childrenWithLabels).composeWith(new StringFeature("instance", instance), "*c0"));
        features.add(new StringFeature("parentInst-inLabel-inst", parentInstance + inLabel + instance));
        features.add(new StringFeature("nrOfSwapsDowns", vertex.annotation.nrOfSwapDowns));
        features.add(new StringFeature("parentNrOfSwapsDowns", from!=null?from.annotation.nrOfSwapDowns:0));
        features.add(new StringFeature("grandparentInstance", grandparentInstance));

        features.add(new StringFeature("instance", instance));
        features.add(new StringFeature("instance-onlyMod", (outEdges.stream().filter(e -> !e.getLabel().equals(":mod")).count()==0) + instance));
        features.add(new StringFeature("instance-inLabel", instance + inLabel));
        features.add(new StringFeature("instance-outEmpty", instance + outStrings.isEmpty()));
        features.add(new StringFeature("instance-onlyModQuantPossDomain", (outEdges.stream().filter(e -> !Arrays.asList(":mod", ":quant", ":poss", ":domain").contains(e.getLabel())).count()==0)+instance));
        features.add(new ListFeature("outLabel", outStrings));
        features.add(new ListFeature("outLabel", outStrings).composeWith(new StringFeature("instance", instance), "*c2"));
        features.add(new StringFeature("isSpecial", Arrays.asList("person", "thing", "country").contains(instance)));
        features.add(new StringFeature("isThing", instance.equals("thing")));
        features.add(new StringFeature("isPronounAndParentMode", Arrays.asList("you", "i", "we").contains(instance) + (from==null?"":from.mode)));

        features.add(new StringFeature("parentInstance-instance", parentInstance + instance));
        features.add(new StringFeature("mergeable", mergeable));

        features.add(new StringFeature("parentInstance", parentInstance));
        features.add(new StringFeature("outEmpty", outStrings.isEmpty()));
        features.add(new StringFeature("hasName", !vertex.name.isEmpty()));
        features.add(new StringFeature("parentInstance-parentHasOtherChildren", (from!=null && from.getOutgoingEdges().size()>2) + parentInstance ));
        features.add(new StringFeature("parentHierarchy", parentInstance).composeWith(new StringFeature("parentHasOtherChildren", (from!=null && from.getOutgoingEdges().size()>2)), "*c1"));

        featureManager.addAllUnaries(features);

        List<String> context = featureManager.toContext();
        return Collections.singletonList(new BasicDatum<>(context, result));

    }
}

