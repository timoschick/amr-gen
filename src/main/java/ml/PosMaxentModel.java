package ml;

import dag.*;
import edu.stanford.nlp.ling.Datum;
import edu.stanford.nlp.ling.RVFDatum;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import gen.GoldSyntacticAnnotations;
import gen.GoldTransitions;
import misc.StaticHelper;
import misc.WordNetHelper;
import net.sf.extjwnl.data.POS;

import java.util.*;
import java.util.stream.Collectors;

/**
 * This class implements a maximum entropy model for parts of speech (POS). See {@link StanfordMaxentModelImplementation} for further details on the
 * implemented methods. The features used by {@link PosMaxentModel#toDatumList(Amr, Vertex, boolean)} are explained in the thesis.
 */
public class PosMaxentModel extends StanfordMaxentModelImplementation {

    private final WordNetHelper wordNetHelper;

    /**
     * Creates a new PosMaxentModel.
     * @param wordNetHelper The WordNetHelper used by this PosMaxentModel to calculate WordNet related features
     */
    public PosMaxentModel(WordNetHelper wordNetHelper) {
        super();
        this.wordNetHelper = wordNetHelper;
    }

    @Override
    public List<Datum<String, String>> toDatumList(Amr amr, Vertex vertex, boolean forTesting) {

        if(!vertex.isPropbankEntry() || vertex.isDeleted() || vertex.isLink()) return Collections.emptyList();

        if(!forTesting) {
            String mergeResult = GoldTransitions.getGoldMerge(amr, vertex);
            // we ignore vertices that need to be merged for the training process
            if(mergeResult != null) return Collections.emptyList();
        }

        Edge instanceEdge = vertex.getInstanceEdge();
        String result = GoldSyntacticAnnotations.getGoldPos(amr, vertex);

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

        Map<POS, Integer> wordNetCount;
        String clearedInst = vertex.getClearedInstance();

        wordNetCount = wordNetHelper.getAllPOSTagsWithCount(clearedInst, true);

        String bestWordNet = "";
        int bestCount = -1;
        for (POS pos : wordNetCount.keySet()) {
            if (wordNetCount.get(pos) > bestCount) {
                bestCount = wordNetCount.get(pos);
                bestWordNet = pos.getLabel();
            }
        }

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

        String lemma = instance.substring(0, instance.lastIndexOf('-'));

        List<IndicatorFeature> features = new ArrayList<>();

        List<String> neighbourLabels = new ArrayList<>();
        List<String> neighbourInstances = new ArrayList<>();
        List<String> neighbourPosTags = new ArrayList<>();
        List<String> neighbourLabelPosTags = new ArrayList<>();
        if (parentVertex != null) {
            for (Edge e : parentVertex.getOutgoingEdges()) {
                if (!e.isInstanceEdge() && e != vertex.getIncomingEdges().get(0)) {
                    neighbourLabels.add(e.getLabel());
                    neighbourInstances.add(StaticHelper.getInstanceOrNumeric(e.getTo()));
                    neighbourPosTags.add(e.getTo().isPropbankEntry() ? ":PROP" : e.getTo().getPos());
                    neighbourLabelPosTags.add(e.getLabel() + "," + (e.getTo().isPropbankEntry() ? ":PROP" : e.getTo().getPos()));
                }
            }
        }

        List<String> outLabelPosTag = new ArrayList<>();

        for (Edge e : outEdges) {
            outLabelPosTag.add(e.getLabel() + "-" + (e.getTo().isPropbankEntry() ? ":PROP" : e.getTo().getPos()));
        }

        List<String> allParentConcepts = new ArrayList<>();
        List<String> allInLabels = new ArrayList<>();
        List<String> allPosInLabels = new ArrayList<>();
        allParentConcepts.add(parentInstance);
        allInLabels.add(inLabel);
        allPosInLabels.add(inLabel + "," + parentPos);

        if(parentVertex != null) {
            for (Vertex v : amr.dag) {
                if (v.isLink() && v.annotation.original == vertex) {
                    if(!v.getIncomingEdges().isEmpty()) {
                        Vertex newParent = v.getIncomingEdges().get(0).getFrom();
                        allParentConcepts.add(newParent.getInstance());
                        allInLabels.add(v.getIncomingEdges().get(0).getLabel());
                        allPosInLabels.add(v.getIncomingEdges().get(0).getLabel() + "," + newParent.getInstance());
                    }
                }
            }
        }

        boolean hasInverseLabel = inLabel.endsWith("-of");
        boolean hasArgLabel = inLabel.startsWith(":ARG");

        features.add(new StringFeature("instance", instance));

        features.add(new StringFeature("lemma", lemma + (parentVertex==null?":ROOT":parentVertex.mode)));
        features.add(new StringFeature("instance-outEmpty", instance + outEdges.isEmpty()));

        features.add(argLinkFeatures.composeWith(new StringFeature("lemma", lemma), "*c3"));

        features.add(new StringFeature("parentInst", parentInstance));
        features.add(new StringFeature("parentInst-inLabel", parentInstance + inLabel));
        features.add(new StringFeature("inLabel", inLabel));
        features.add(new StringFeature("outSize", outEdges.size()));
        features.add(new StringFeature("depth", vertex.subtreeSize()));
        features.add(new StringFeature("numberOfArgs", outEdges.stream().filter(e -> e.getLabel().matches(":ARG[0-9]")).count() + ""));
        features.add(new StringFeature("distToRoot-outSize", distanceToRoot + outEdges.size()));
        features.add(new StringFeature("bestWordNet-inLabel", bestWordNet + inLabel));
        features.add(new ListFeature("allPosInLabels-depth", allPosInLabels).composeWith(new StringFeature("depth", vertex.subtreeSize()), "*c0"));
        features.add(new ListFeature("allInLabels-outSize", allInLabels).composeWith(new StringFeature("outSize", outEdges.size()), "*c1"));
        features.add(new ListFeature("outLabelPosTag", outLabelPosTag).composeWith(new StringFeature("inLabel", inLabel), "*c2"));

        features.add(new ListFeature("allParentInsts", allParentConcepts).composeWith(new StringFeature("hasNoArgChildren",  outStrings.stream().anyMatch(s -> !s.startsWith(":ARG"))), "*c4"));

        features.add(new StringFeature("instance-inLabel", instance + inLabel));
        features.add(new StringFeature("instance-outSize", instance + outEdges.size()));
        features.add(new StringFeature("instance-mode", instance + vertex.mode));
        features.add(new StringFeature("instance-depth", instance + vertex.subtreeSize()));
        features.add(new StringFeature("inLabel-outSize", inLabel + outEdges.size()));
        features.add(new StringFeature("inLabel-numberOfArgs", inLabel + + outEdges.stream().filter(e -> e.getLabel().matches(":ARG[0-9]")).count()));
        features.add(new StringFeature("inLabel-depth", inLabel + vertex.subtreeSize()));
        features.add(new StringFeature("parentInst-grandparentInst", parentInstance + grandparentInstance));
        features.add(new ListFeature("outLabels", outStrings).composeWith(new StringFeature("inLabel", inLabel), "*c1"));
        features.add(new StringFeature("bestWordNet-parentPos", bestWordNet + parentPos));
        features.add(new StringFeature("bestWordNet-parentInst", bestWordNet + parentInstance));
        features.add(new StringFeature("parentInst-numberOfArgs", parentInstance +  outEdges.stream().filter(e -> e.getLabel().matches(":ARG[0-9]")).count()));
        features.add(new StringFeature("parentPos-hasInvArgFeature", parentPos + (hasInverseLabel && hasArgLabel)));

        features.add(new ListFeature("outLabels", outStrings));
        features.add(new StringFeature("instance", instance));
        features.add(new StringFeature("lemma", lemma));

        features.add(new StringFeature("bestWordNet", bestWordNet));
        features.add(new ListFeature("argOfFeatures", argOfFeatures));
        features.add(new ListFeature("neighbourLabels", neighbourLabels));
        features.add(new ListFeature("neighbourInstances", neighbourInstances));
        features.add(new ListFeature("neighbourPosTags", neighbourPosTags));
        features.add(new ListFeature("neighbourLabels", neighbourLabels));
        features.add(new ListFeature("neighbourLabelPosTags", neighbourLabelPosTags));

        features.add(new ListFeature("children", outEdges.stream().filter(e -> !e.isInstanceEdge()).map(e -> StaticHelper.getInstanceOrNumeric(e.getTo())).collect(Collectors.toList())));
        features.add(new ListFeature("nonLinkChildren", outEdges.stream().filter(e -> !e.isInstanceEdge() && !e.getTo().isLink()).map(e -> StaticHelper.getInstanceOrNumeric(e.getTo())).collect(Collectors.toList())));

        features.add(new StringFeature("neighbourSize", neighbourLabels.size()));
        features.add(new StringFeature("noNeighbours", neighbourLabels.isEmpty()));

        features.add(new StringFeature("parentPos", parentPos));
        features.add(new ListFeature("allParentInsts", allParentConcepts));

        features.add(new ListFeature("allInLabels", allInLabels));
        features.add(new ListFeature("allPosInLabels", allPosInLabels));

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

        features.add(new ListFeature("outLabelPosTag", outLabelPosTag));
        features.add(new ListFeature("childrenWithLabels", outEdges.stream().map(e -> e.getLabel() + StaticHelper.getInstanceOrNumeric(e.getTo())).collect(Collectors.toList())));

        features.add(argFeatures);
        features.add(argLinkFeatures);

        features.add(new StringFeature("numberOfArgs", outEdges.stream().filter(e -> e.getLabel().matches(":ARG[0-9]")).count() + ""));
        features.add(new StringFeature("mode", vertex.mode));
        features.add(new StringFeature("parentMode", parentVertex==null?":ROOT":parentVertex.mode));

        features.add(new StringFeature("depth", vertex.subtreeSize()));

        features.add(new StringFeature("hasInverseLabel", hasInverseLabel));
        features.add(new StringFeature("hasInvArgFeature", (hasInverseLabel && hasArgLabel)));

        features.add(new StringFeature("hasArgChildren", outStrings.stream().anyMatch(s -> s.startsWith(":ARG") && !s.endsWith("-of"))));
        features.add(new StringFeature("hasNoArgChildren", outStrings.stream().anyMatch(s -> !s.startsWith(":ARG"))));

        featureManager.addAllUnaries(features);
        this.usesRVF = true;
        List<String> context = featureManager.toContext();

        Counter<String> counter = new ClassicCounter<>();

        for(String contextString: context) {
            counter.setCount(contextString, 1);
        }

        counter.setCount("nrOfChildren", outEdges.size());

        return Collections.singletonList(new RVFDatum<>(counter, result));
    }

    @Override
    public void applyModification(Amr amr, Vertex vertex, List<Prediction> predictions) {
        vertex.predictions.put("pos", predictions);
    }

}
