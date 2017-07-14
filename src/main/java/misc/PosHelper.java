package misc;

import net.sf.extjwnl.data.POS;

import java.util.*;

/**
 * This class contains static methods to handle part of speech (POS) tags.
 * The set of all POS tags can be found at <a href="http://www.ling.upenn.edu/courses/Fall_2003/ling001/penn_treebank_pos.html">www.ling.upenn.edu/courses/Fall_2003/ling001/penn_treebank_pos.html</a>.
 */
public class PosHelper {

    // the POS tag assigned if no unique POS tag could be determined
    public static final String POS_ANY = "-*-";

    // the list of all POS tags allowed for PropBank framesets
    public static List<String> posCategories;

    // the mapping of POS tags to simplified POS tags
    static Map<String,List<String>> posMapper = new HashMap<>();
    static {
        posMapper.put("NN", Arrays.asList("NN", "NNS", "NNP", "NNPS", "FW"));
        posMapper.put("VB", Arrays.asList("VB", "VBD", "VBP", "VBZ"));
        posMapper.put("VBN", Arrays.asList("VBN"));
        posMapper.put("VBG", Arrays.asList("VBG"));
        posMapper.put("JJ", Arrays.asList("JJ", "JJR", "JJS", "WRB", "RB", "RBR", "RBS"));
        posCategories = new ArrayList<>(posMapper.keySet());
        posCategories.add(POS_ANY);
    }

    /**
     * This function maps a POS tag string to a simplified version.
     * @param pos the POS tag string
     * @param propbankEntry wheter the corresponding instance is a PropBank frameset. If so,
     *                      any unmapped POS tag gets mapped to {@link PosHelper#POS_ANY}. Otherwise,
     *                      it is returned as is.
     * @return the simplified POS tag
     */
    public static String mapPos(String pos, boolean propbankEntry) {
        for(String mappedPos: posMapper.keySet()) {
            if(posMapper.get(mappedPos).contains(pos)) {
                return mappedPos;
            }
        }
        if(propbankEntry) return POS_ANY;
        else return pos;
    }

    /**
     * This function maps a POS tag string to the corresponding WordNet POS instance
     * @param pos the POS tag string
     * @return the corresponding WordNet POS instance or null if no such instance is found
     */
    public static POS mapPosToWordNet(String pos) {
        if(posMapper.get("NN").contains(pos)) return POS.NOUN;
        if(posMapper.get("VB").contains(pos) || posMapper.get("VBC").contains(pos) || pos.equals("VBN") || pos.equals("VBG")) return POS.VERB;
        if(pos.startsWith("RB")) return POS.ADVERB;
        if(pos.startsWith("JJ")) return POS.ADJECTIVE;
        return null;
    }
}
