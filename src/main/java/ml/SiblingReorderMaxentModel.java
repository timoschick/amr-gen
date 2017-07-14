package ml;

import dag.Amr;
import dag.Edge;
import dag.Vertex;
import edu.stanford.nlp.ling.Datum;
import edu.stanford.nlp.ling.RVFDatum;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import gen.GoldTransitions;
import misc.PosHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * This class implements a maximum entropy model for scoring &lt;<sub>l</sub> and &lt;<sub>r</sub> as required to score REORDER transitions (see Eq. 19,
 * Section 4.2.1 Modeling). The maximum entropy model for &lt;<sub>*</sub> is implemented by {@link ParentChildReorderMaxentModel}.
 * See {@link StanfordMaxentModelImplementation} for further details on the implemented methods. The features used
 * by {@link SiblingReorderMaxentModel#toDatumList(Amr, Vertex, boolean)} are explained in the thesis.
 */
public class SiblingReorderMaxentModel extends StanfordMaxentModelImplementation {

    public static int LEFT_ONLY = 1;
    public static int RIGHT_ONLY = 2;

    private int type;

    public SiblingReorderMaxentModel(int type) {
        super();
        this.type = type;
    }

    @Override
    public List<Datum<String, String>> toDatumList(Amr amr, Vertex v, boolean forTesting) {

        if (v.getOutgoingEdges().size() <= 1 || (v.isDeleted() && v.getOutgoingEdges().size() <= 2)) {
            return Collections.emptyList();
        }

        List<Datum<String,String>> ret = new ArrayList<>();
        Edge instanceEdge = v.getInstanceEdge();
        List<Edge> leftEdges = new ArrayList<>(), rightEdges = new ArrayList<>();

        // if v is deleted, we simply consider all of its children to be left of it
        if(v.isDeleted()) {
            leftEdges.addAll(v.getOutgoingEdges());
            leftEdges.remove(instanceEdge);
        }

        else {
            // group all children of v in a left and right half
            for (Edge e : v.getOutgoingEdges()) {
                if (e == instanceEdge) continue;
                if (GoldTransitions.getGoldParentChildOrder(amr, instanceEdge, e).equals(GoldTransitions.CHILD_BEFORE_PARENT)) {
                    leftEdges.add(e);
                } else if (GoldTransitions.getGoldParentChildOrder(amr, instanceEdge, e).equals(GoldTransitions.PARENT_BEFORE_CHILD)) {
                    rightEdges.add(e);
                }
            }
        }

        if(type == LEFT_ONLY) ret.addAll(toEventPairs(amr, v, leftEdges));
        else if(type == RIGHT_ONLY) ret.addAll(toEventPairs(amr, v, rightEdges));
        return ret;
    }

    public List<Datum<String,String>> toEventPairs(Amr amr, Vertex v, List<Edge> edges) {

        if (edges.size() <= 1) return Collections.emptyList();

        List<Datum<String,String>> ret = new ArrayList<>();

        for(int i=0; i < edges.size(); i++) {
            for (int j = i + 1; j < edges.size(); j++) {
                Edge tmp1 = edges.get(i);
                Edge tmp2 = edges.get(j);
                int comp = compare(tmp1, tmp2);
                Edge e1 = getFirst(comp, tmp1, tmp2);
                Edge e2 = getSecond(comp, tmp1, tmp2);
                String result = GoldTransitions.getGoldSiblingOrder(amr, e1, e2);

                if (!result.isEmpty()) {
                    ret.add(toEvent(v, e1, e2, result, edges));
                }
            }
        }

        return ret;
    }

    public Datum<String,String> toEvent(Vertex vertex, Edge e1, Edge e2, String result, List<Edge> permutation) {

        if(compare(e1, e2) > 0) throw new AssertionError("toEvent can only be called on edges that are ordered according to compare(Edge,Edge)");

        List<String> args = vertex.getOutgoingEdges().stream().filter(e -> !e.isInstanceEdge()).map(e -> e.getLabel()).collect(Collectors.toList());
        List<String> argsPos = vertex.getOutgoingEdges().stream().filter(e -> !e.isInstanceEdge()).map(e -> e.getLabel() + (e.getTo().isPropbankEntry()?":PROP":e.getTo().getPos())).collect(Collectors.toList());
        Collections.sort(args);

        List<String> limitedArgs = permutation.stream().map(e -> e.getLabel()).collect(Collectors.toList());
        List<String> limitedArgsPos = permutation.stream().map(e -> e.getLabel() + (e.getTo().isPropbankEntry()?":PROP":e.getTo().getPos())).collect(Collectors.toList());

        String e1Lab = e1.getLabel();
        String e2Lab = e2.getLabel();

        int firstDepth = e1.getTo().subtreeSize();
        int secondDepth = e2.getTo().subtreeSize();

        String instPOS = PosHelper.mapPos(vertex.getPos(), false);
        String inst = vertex.getInstance();
        String instWithPos = vertex.getInstance() + instPOS;
        String e1Pos = (e1.getTo().isPropbankEntry()?":PROP":e1.getTo().getPos());
        String e2Pos = (e2.getTo().isPropbankEntry()?":PROP":e2.getTo().getPos());

        String e1Inst = e1.getTo().getInstance();
        String e2Inst = e2.getTo().getInstance();

        List<Edge> e1Out = e1.getTo().getOutgoingEdges().stream().filter(e -> !e.isInstanceEdge()).collect(Collectors.toList());
        List<Edge> e2Out = e2.getTo().getOutgoingEdges().stream().filter(e -> !e.isInstanceEdge()).collect(Collectors.toList());

        if(e1Inst.matches("[0-9.]+")) e1Inst = ":NUMERIC";
        if(e2Inst.matches("[0-9.]+")) e2Inst = ":NUMERIC";

        List<IndicatorFeature> features = new ArrayList<>();
        args.remove(":REL");

        // features for <_r
        if(type == SiblingReorderMaxentModel.RIGHT_ONLY) {
            features.add(new StringFeature("e1Lab", e1Lab));
            features.add(new StringFeature("e1Lab-inst", e1Lab + inst));
            features.add(new StringFeature("e2Lab-e2Depth", e2Lab + secondDepth));
            features.add(new StringFeature("e1Lab-e2Depth", e1Lab + secondDepth));
            features.add(new StringFeature("e1Depth-e2Depth", firstDepth + "-" + secondDepth));
            features.add(new StringFeature("e1Depth", firstDepth));
            features.add(new StringFeature("e1Lab-e2Lab-inst", e1Lab + e2Lab + inst));
            features.add(new StringFeature("e2Lab-e2Instance", e2Lab + e2Inst));
            features.add(new StringFeature("e1Lab-e1Instance", e1Lab + e1Inst));
            features.add(new StringFeature("e2Lab", e2Lab));
            features.add(new StringFeature("e2Lab-inst", e2Lab + inst));
            features.add(new StringFeature("e2Lab-e1Depth", e2Lab + firstDepth));
            features.add(new StringFeature("e1Lab-e1Depth", e1Lab + firstDepth));
            features.add(new StringFeature("e2Depth", secondDepth));

            List<IndicatorFeature> combFeatures = new ArrayList<>();

            combFeatures.add(new StringFeature("e1Lab", e1Lab));
            combFeatures.add(new StringFeature("e1Inst", e1Inst));
            combFeatures.add(new StringFeature("e1Lemma", e1.getTo().getClearedInstance()));
            combFeatures.add(new StringFeature("e1Depth", firstDepth));
            combFeatures.add(new StringFeature("e1OutSize", e1Out.size()));
            combFeatures.add(new ListFeature("e1OutLabels", e1Out.stream().map(e -> e.getLabel()).collect(Collectors.toList())));
            combFeatures.add(new ListFeature("e1OutLabelsPosTags", e1Out.stream().map(e -> e.getLabel() + (e.getTo().isPropbankEntry()?":PROP":e.getTo().getPos())).collect(Collectors.toList())));
            combFeatures.add(new StringFeature("e1Pos", e1Pos));

            combFeatures.add(new StringFeature("e2Lab", e2Lab));
            combFeatures.add(new StringFeature("e2Inst", e2Inst));
            combFeatures.add(new StringFeature("e2Lemma", e2.getTo().getClearedInstance()));
            combFeatures.add(new StringFeature("e2Depth", firstDepth));
            combFeatures.add(new StringFeature("e2OutSize", e2Out.size()));
            combFeatures.add(new ListFeature("e2OutLabels", e2Out.stream().map(e -> e.getLabel()).collect(Collectors.toList())));
            combFeatures.add(new ListFeature("e2OutLabelsPosTags", e2Out.stream().map(e -> e.getLabel() + (e.getTo().isPropbankEntry()?":PROP":e.getTo().getPos())).collect(Collectors.toList())));
            combFeatures.add(new StringFeature("e2Pos", e2Pos));

            combFeatures.add(new StringFeature("argsSize", args.size()));
            combFeatures.add(new StringFeature("limitedArgsSize", limitedArgs.size()));
            combFeatures.add(new ListFeature("args", args));
            combFeatures.add(new ListFeature("limitedArgs", limitedArgs));
            combFeatures.add(new ListFeature("argsPos", argsPos));
            combFeatures.add(new ListFeature("limitedArgsPos", limitedArgsPos));
            combFeatures.add(new StringFeature("e1Lab-e2Lab", e1Lab + e2Lab));

            combFeatures.add(new StringFeature("instance", inst));
            combFeatures.add(new StringFeature("instancePos", instPOS));
            combFeatures.add(new StringFeature("instanceWithPos", instWithPos));

            combFeatures.add(new StringFeature("e1NrOfSwapsDowns", e1.getTo().annotation.nrOfSwapDowns));
            combFeatures.add(new StringFeature("e2NrOfSwapsDowns", e2.getTo().annotation.nrOfSwapDowns));

            combFeatures.add(new StringFeature("e1InitialConcept", e1.getTo().annotation.initialConcept));
            combFeatures.add(new StringFeature("e2InitialConcept", e2.getTo().annotation.initialConcept));

            List<IndicatorFeature> newFeatures = new ArrayList<>();
            int i=0;
            StringFeature e1LabFeature = new StringFeature("e1Lab", e1Lab);
            StringFeature e2LabFeature = new StringFeature("e2Lab", e2Lab);
            for(IndicatorFeature feature: combFeatures) {
                newFeatures.add(feature.composeWith(e1LabFeature, "*c1."+i));
                newFeatures.add(feature.composeWith(e2LabFeature, "*c2."+i));
                i++;
            }
            features.addAll(newFeatures);
        }

        // features for <_l
        else if(type == SiblingReorderMaxentModel.LEFT_ONLY) {

            features.add(new StringFeature("e1Instance-e2Depth", e1Inst + secondDepth));
            features.add(new StringFeature("e1Instance-instance", e1Inst + inst));
            features.add(new StringFeature("e1Instance-e2Instance", e1Inst + e2Inst));
            features.add(new StringFeature("e2Instance-e2Depth", e2Inst + secondDepth));
            features.add(new StringFeature("e1Instance-e2Lab-e2Depth", e1Inst + e2Lab + secondDepth));
            features.add(new StringFeature("e1Lab-e2Depth", e1Lab + secondDepth));
            features.add(new StringFeature("e2Instance-instance", e2Inst + inst));
            features.add(new StringFeature("e2Lab-e1Instance", e2Lab + e1Inst));
            features.add(new ListFeature("largs", limitedArgs).composeWith(new StringFeature("e2Depth", secondDepth), "*c0"));
            features.add(new ListFeature("largs", limitedArgs).composeWith(new StringFeature("e1Lab-e2Lab", e1Lab + "-" + e2Lab), "*c1"));
            features.add(new ListFeature("largs", limitedArgs).composeWith(new StringFeature("e2Instance", e2Inst), "*c2"));

            features.add(new StringFeature("e1Instance-e1Depth", e1Inst + firstDepth));
            features.add(new StringFeature("e2Instance-e1Depth", e2Inst + firstDepth));
            features.add(new StringFeature("e2Instance-e1Lab-e1Depth", e2Inst + e1Lab + firstDepth));
            features.add(new StringFeature("e2Lab-e1Depth", e2Lab + firstDepth));
            features.add(new StringFeature("e1Lab-e2Instance", e1Lab + e2Inst));

            features.add(new ListFeature("largs", limitedArgs).composeWith(new StringFeature("e1Depth", firstDepth), "*c3"));
            features.add(new ListFeature("largs", limitedArgs).composeWith(new StringFeature("e1Lab-e2Lab", e1Lab + "-" + e2Lab), "*c4"));
            features.add(new ListFeature("largs", limitedArgs).composeWith(new StringFeature("e1Instance", e1.getTo().getInstance()), "*c5"));

            List<IndicatorFeature> combFeatures = new ArrayList<>();

            combFeatures.add(new StringFeature("e1Lab", e1Lab));
            combFeatures.add(new StringFeature("e1Inst", e1Inst));
            combFeatures.add(new StringFeature("e1Lemma", e1.getTo().getClearedInstance()));
            combFeatures.add(new StringFeature("e1Depth", firstDepth));
            combFeatures.add(new StringFeature("e1OutSize", e1Out.size()));
            combFeatures.add(new ListFeature("e1OutLabels", e1Out.stream().map(e -> e.getLabel()).collect(Collectors.toList())));
            combFeatures.add(new ListFeature("e1OutLabelsPosTags", e1Out.stream().map(e -> e.getLabel() + (e.getTo().isPropbankEntry()?":PROP":e.getTo().getPos())).collect(Collectors.toList())));
            combFeatures.add(new StringFeature("e1Pos", e1Pos));

            combFeatures.add(new StringFeature("e2Lab", e2Lab));
            combFeatures.add(new StringFeature("e2Inst", e2Inst));
            combFeatures.add(new StringFeature("e2Lemma", e2.getTo().getClearedInstance()));
            combFeatures.add(new StringFeature("e2Depth", firstDepth));
            combFeatures.add(new StringFeature("e2OutSize", e2Out.size()));
            combFeatures.add(new ListFeature("e2OutLabels", e2Out.stream().map(e -> e.getLabel()).collect(Collectors.toList())));
            combFeatures.add(new ListFeature("e2OutLabelsPosTags", e2Out.stream().map(e -> e.getLabel() + (e.getTo().isPropbankEntry()?":PROP":e.getTo().getPos())).collect(Collectors.toList())));
            combFeatures.add(new StringFeature("e2Pos", e2Pos));

            combFeatures.add(new StringFeature("argsSize", args.size()));
            combFeatures.add(new StringFeature("limitedArgsSize", limitedArgs.size()));
            combFeatures.add(new ListFeature("args", args));
            combFeatures.add(new ListFeature("limitedArgs", limitedArgs));
            combFeatures.add(new ListFeature("argsPos", argsPos));
            combFeatures.add(new ListFeature("limitedArgsPos", limitedArgsPos));
            combFeatures.add(new StringFeature("e1Lab-e2Lab", e1Lab + e2Lab));

            combFeatures.add(new StringFeature("e1NrOfSwapsDowns", e1.getTo().annotation.nrOfSwapDowns));
            combFeatures.add(new StringFeature("e2NrOfSwapsDowns", e2.getTo().annotation.nrOfSwapDowns));

            combFeatures.add(new StringFeature("e1InitialConcept", e1.getTo().annotation.initialConcept));
            combFeatures.add(new StringFeature("e2InitialConcept", e2.getTo().annotation.initialConcept));

            combFeatures.add(new StringFeature("instance", inst));
            combFeatures.add(new StringFeature("instancePos", instPOS));
            combFeatures.add(new StringFeature("instanceWithPos", instWithPos));

            List<IndicatorFeature> newFeatures = new ArrayList<>();
            int i = 0;
            StringFeature e1LabFeature = new StringFeature("e1Lab", e1Lab);
            StringFeature e2LabFeature = new StringFeature("e2Lab", e2Lab);
            for(IndicatorFeature feature: combFeatures) {
                newFeatures.add(feature.composeWith(e1LabFeature, "*c1."+i));
                newFeatures.add(feature.composeWith(e2LabFeature, "*c2."+i));
                i++;
            }
            features.addAll(newFeatures);

        }

        featureManager.addAllUnaries(features);
        List<String> context = featureManager.toContext();

        this.usesRVF = true;

        Counter<String> counter = new ClassicCounter<>();

        for(String contextString: context) {
            counter.setCount(contextString, 1);
        }


        return new RVFDatum<>(counter, result);
    }

    public Edge getFirst(int compare, Edge e1, Edge e2) {
        if(compare <= 0) return e1;
        else return e2;
    }

    public Edge getSecond(int compare, Edge e1, Edge e2) {
        if(compare <= 0) return e2;
        else return e1;
    }

    public static String optionalInverse(int compare, String result) {
        if(compare <= 0) return result;
        else return inverse(result);
    }

    public static String inverse(String result) {
        if(result.equals(GoldTransitions.E1_BEFORE_E2)) return GoldTransitions.E2_BEFORE_E1;
        if(result.equals(GoldTransitions.E2_BEFORE_E1)) return GoldTransitions.E1_BEFORE_E2;
        throw new AssertionError("string " + result + " cannot be inverted");
    }

    public static int compare(Edge e1, Edge e2) {
        int labComp = e1.getLabel().compareTo(e2.getLabel());
        if(labComp != 0) return labComp;
        return e1.getTo().getInstance().compareTo(e2.getTo().getInstance());
    }

}
