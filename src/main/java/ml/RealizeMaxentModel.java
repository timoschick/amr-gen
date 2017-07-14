package ml;

import dag.Amr;
import dag.Edge;
import dag.Vertex;
import gen.DefaultRealizer;
import gen.GoldSyntacticAnnotations;
import gen.GoldTransitions;
import opennlp.tools.ml.model.Event;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This class implements a maximum entropy model for scoring REALIZE transitions. See {@link OpenNlpMaxentModelImplementation} for further details on the
 * implemented methods. The features used by {@link RealizeMaxentModel#toEvents(Amr, Vertex, boolean)} are explained in the thesis.
 */
public class RealizeMaxentModel extends OpenNlpMaxentModelImplementation {

    private static final Pattern NO_REALIZE = Pattern.compile("[0-9.,]*");
    private static final Matcher NO_REALIZE_MATCHER = NO_REALIZE.matcher("");

    @Override
    public List<Event> toEvents(Amr amr, Vertex vertex, boolean forTesting) {
        if(forTesting) throw new AssertionError("can only be called for testing with an additional information map!");
        else {
            return toEvents(amr, vertex, forTesting, null);
        }
    }

    public List<Event> toEvents(Amr amr, Vertex vertex, boolean forTesting, Map<String,Prediction> syntacticAnnotation) {
        Edge instanceEdge = vertex.getInstanceEdge();
        if(instanceEdge == null || vertex.isDeleted()) return Collections.emptyList();
        if(!vertex.name.isEmpty()) return Collections.emptyList();

        if(!forTesting) {
            String thisMergeResult = GoldTransitions.getGoldMerge(amr, vertex);
            if(thisMergeResult != null) {
                return Collections.emptyList();
            }
            for(Edge childEdge: vertex.getOutgoingEdges()) {
                if(childEdge == instanceEdge) continue;
                String childMergeResult = GoldTransitions.getGoldMerge(amr, childEdge.getTo());
                if(childMergeResult != null) {
                    return Collections.emptyList();
                }
            }
        }

        NO_REALIZE_MATCHER.reset(vertex.getInstance());
        if(vertex.getInstance().startsWith("\"") || NO_REALIZE_MATCHER.matches()) return Collections.emptyList();

        String result = GoldTransitions.getGoldRealization(amr, instanceEdge);
        if(result.isEmpty() && !forTesting) return Collections.emptyList();

        String pos = vertex.getPos();
        String instance = vertex.getInstance();
        String inLabel = vertex.getIncomingEdges().isEmpty()?":ROOT": vertex.getIncomingEdges().get(0).getLabel();

        List<IndicatorFeature> features = new ArrayList<>();

        String complexInfo;

        if(pos.equals("NN")) {
            String number;
            if(!forTesting) {
                number = GoldSyntacticAnnotations.getGoldNumber(amr, vertex);
            }
            else {
                number = syntacticAnnotation.get("number").getValue();
            }
            if(number.isEmpty()) number = GoldSyntacticAnnotations.SINGULAR;
            complexInfo = pos + number;
        }
        else if(pos.equals("VB")) {
            String mode = vertex.mode;
            String tense;
            boolean thirdPerson = DefaultRealizer.guessThirdPerson(amr, vertex, forTesting);

            String voice = "";
            if(!forTesting) {
                voice = GoldSyntacticAnnotations.getGoldVoice(amr, vertex);
            }
            else {
                voice = syntacticAnnotation.getOrDefault("voice", new Prediction(null, 0)).getValue();
            }
            if(voice == null || voice.isEmpty()) voice = GoldSyntacticAnnotations.ACTIVE;

            if(!forTesting) {
                tense = GoldSyntacticAnnotations.getGoldTense(amr, vertex);
            }
            else {
                tense = syntacticAnnotation.getOrDefault("tense", new Prediction(null, 0)).getValue();
                if(tense == null) {
                    tense = GoldSyntacticAnnotations.PAST;
                }
            }

            complexInfo = pos + "," + mode + "," + voice;
            if(!mode.equals("imperative") && tense.equals(GoldSyntacticAnnotations.PRESENT) && voice.equals(GoldSyntacticAnnotations.ACTIVE)) {
                complexInfo += "," + "tp="+thirdPerson;
            }
            if(mode.isEmpty() && voice.equals(GoldSyntacticAnnotations.ACTIVE)) {
                complexInfo += "," + tense;
            }

            if(voice.equals(GoldSyntacticAnnotations.PASSIVE)) {
                complexInfo = "VBN";
                pos = "VBN";
            }
        }
        else if(pos.equals("JJ")) {
            complexInfo = pos;
        }
        else if(pos.equals("VBN") || pos.equals("VBG")) {
            complexInfo = pos;
        }
        else {
            complexInfo = pos;
        }

        String instWithPos = instance + pos;

        features.add(new StringFeature("instWithComplexInfo", instance + complexInfo).makeMandatory());
        features.add(new StringFeature("instWithPos", instWithPos));
        features.add(new StringFeature("inst", instance));
        features.add(new StringFeature("instWithComplexInfo-inLabel-swaps", inLabel + instance + complexInfo + "," + vertex.annotation.nrOfSwapDowns));

        featureManager.addAllUnaries(features);
        List<String> context = featureManager.toContext();

        Event event = new Event(result, context.toArray(new String[context.size()]));
        return Collections.singletonList(event);
    }
}