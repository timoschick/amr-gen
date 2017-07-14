package gen;

import dag.*;
import edu.stanford.nlp.tagger.maxent.MaxentTagger;
import misc.PosHelper;
import misc.WordLists;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A static class that contains functions to get the gold syntactic annotations for vertices.
 */
public class GoldSyntacticAnnotations {

    // voice syntactic annotation values
    public static final String PASSIVE = "passive";
    public static final String ACTIVE = "active";

    // number syntactic annotation values
    public static final String SINGULAR = "singular";
    public static final String PLURAL = "plural";

    // tense syntactic annotation values
    public static final String FUTURE = "future";
    public static final String PRESENT = "present";
    public static final String PAST = "past";
    public static final String NONE = "no_tense";

    // non-instantiable class
    private GoldSyntacticAnnotations() {}

    /**
     * This function returns the gold part of speech (POS) tag assigned to a vertex. For a list of possible POS tags, see {@link PosHelper}.
     * @param amr the AMR graph to which the vertex belongs
     * @param vertex the vertex
     * @return the gold POS tag
     */
    public static String getGoldPos(Amr amr, Vertex vertex) {
        String pos = PosHelper.mapPos(vertex.getPos(), true);
        if(pos.equals("VBN") || pos.equals("VBG")) {
            if(amr.typeAlignment.containsKey(new EdgeTypePair(vertex.getInstanceEdge(), AlignmentType.BE))) return "VB";
        }
        return pos;
    }

    /**
     * This function returns the gold denominator (one of "the", "a" and "-") assigned to a vertex. It makes use of the type alignment
     * constructed by {@link Amr#prepareForTraining(MaxentTagger)} and {@link dag.BigraphAlignmentBuilder#buildTypeAlignment(Amr, DependencyTree)}.
     * @param amr the AMR graph to which the vertex belongs
     * @param vertex the vertex
     * @return the gold denominator
     */
    public static String getGoldDenominator(Amr amr, Vertex vertex) {

        EdgeTypePair eat = new EdgeTypePair(vertex.getInstanceEdge(), AlignmentType.ARTICLE);

        if(amr.typeAlignment.containsKey(eat)) {
            int index = new ArrayList<>(amr.typeAlignment.get(eat)).get(0);
            if(amr.sentence[index].toLowerCase().equals("the")) {
                return "the";
            }
            else {
                return "a";
            }
        }
        else {
            return "-";
        }
    }

    /**
     * This function returns the gold voice (either "passive" or "active") assigned to a vertex.
     * @param amr the AMR graph to which the vertex belongs
     * @param vertex the vertex
     * @return the gold voice
     */
    public static String getGoldVoice(Amr amr, Vertex vertex) {

        String mappedPos = PosHelper.mapPos(vertex.getPos(), true);
        if(mappedPos.equals("VBN")) {
            EdgeTypePair eat = new EdgeTypePair(vertex.getInstanceEdge(), AlignmentType.BE);
            if (amr.typeAlignment.containsKey(eat)) {
                for(int i: amr.typeAlignment.get(eat)) {
                    if(WordLists.beForms.contains(amr.sentence[i])) {
                        return PASSIVE;
                    }
                }
            }
        }
        return ACTIVE;
    }

    /**
     * This function returns the gold number (either "singular" or "plural") assigned to a vertex. If the vertex has no number (e.g. because
     * it represents a verb), an empty string is returned.
     * @param amr the AMR graph to which the vertex belongs
     * @param vertex the vertex
     * @return the gold number
     */
    public static String getGoldNumber(Amr amr, Vertex vertex) {
        if(amr.alignment.containsKey(vertex.getInstanceEdge())) {

            List<Integer> sortedAlignment = amr.alignment.get(vertex.getInstanceEdge()).stream().sorted().collect(Collectors.toList());

            for(int i: sortedAlignment) {
                if (amr.pos[i].equals("NNS") || amr.pos[i].equals("NNPS")) {
                    return PLURAL;
                }
                else if (PosHelper.mapPos(amr.pos[i], true).equals("NN")) return SINGULAR;
            }
        }
        return "";
    }

    /**
     * This function returns the gold tense (one of "past", "present", "future") assigned to a vertex. If the vertex has no tense, an instance
     * of {@link GoldSyntacticAnnotations#NONE} is returned.
     * @param amr the AMR graph to which the vertex belongs
     * @param vertex the vertex
     * @return the gold tense
     */
    public static String getGoldTense(Amr amr, Vertex vertex) {
        if(amr.alignment.containsKey(vertex.getInstanceEdge())) {

            List<Integer> sortedAlignment = amr.alignment.get(vertex.getInstanceEdge()).stream().sorted().collect(Collectors.toList());

            for(int i: sortedAlignment) {
                String pos = amr.pos[i];
                if(pos.equals("VBD")) return PAST;
                else if(pos.equals("VBP") || pos.equals("VBZ")) return PRESENT;
                else if(pos.equals("VB") && ( (i > 0 && amr.sentence[i-1].equals("will")) ||
                        (i > 1 && amr.sentence[i-2].equals("will") && PosHelper.mapPos(amr.pos[i-1],true).equals("JJ")))) return FUTURE;
                else if(pos.equals("VB")) return NONE;
            }
        }
        return "";
    }
}
