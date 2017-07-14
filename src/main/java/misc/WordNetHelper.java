package misc;

import net.sf.extjwnl.JWNLException;
import net.sf.extjwnl.data.IndexWord;
import net.sf.extjwnl.data.POS;
import net.sf.extjwnl.data.Synset;
import net.sf.extjwnl.data.Word;
import net.sf.extjwnl.dictionary.Dictionary;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This class provides functions to access WordNet (see <a href="http://wordnet.princeton.edu/">wordnet.princeton.edu</a>).
 * It makes use of the Extended Java WordNet Library (extJWNL, see <a href="http://extjwnl.sourceforge.net/">extjwnl.sourceforge.net</a>) as an interface to WordNet.
 */
public class WordNetHelper {

    private final Dictionary dictionary;

    /**
     * Creates a new WordNetHelper.
     */
    public WordNetHelper() throws JWNLException {
        this.dictionary = Dictionary.getDefaultResourceInstance();
    }

    /**
     * Given an English word in its base form, this function returns all POS tags that can be assigned to this word.
     * For example, {@code getAllPosTags("run")} returns the list {@code [POS.VERB, POS.NOUN]}.
     * @param word the English word
     * @return the list of POS tags
     */
    public List<POS> getAllPOSTags(String word) {
        List<POS> ret = new ArrayList<>();
        for(POS pos: POS.getAllPOS()) {
            IndexWord iw = null;
            try {
                iw = dictionary.getIndexWord(pos, word);
            } catch (JWNLException e) {
                System.err.println(e);
            }
            if(iw != null) {
                ret.add(pos);
            }
        }
        return ret;
    }

    /**
     * Given an English word in its base form, this function returns all POS tags that can be assigned to this word
     * along with a count estimating how likely each POS tag is. This count is extracted from the WordNet use counts.
     * @param word the English word
     * @param useMax if set to true, the maximum of all use counts observed for each (word, POS)-pair is assigned to it.
     *               Otherwise, the sum of all use counts is taken.
     * @return a map from POS tags to integers where each POS tag is mapped to the calcualted count.
     */
    public Map<POS, Integer> getAllPOSTagsWithCount(String word, boolean useMax) {
        Map<POS, Integer> ret = new HashMap<>();
        for(POS pos: POS.getAllPOS()) {
            IndexWord iw = null;
            try {
                iw = dictionary.getIndexWord(pos, word);
            } catch (JWNLException e) {
                System.err.println(e);
            }
            if(iw != null) {
                ret.put(pos, getWordCount(iw, useMax));
            }
        }
        return ret;
    }

    /**
     * This function computes the observed count of an IndexWord as required by {@link WordNetHelper#getAllPOSTagsWithCount(String, boolean)}.
     * @param word the IndexWord
     * @param useMax if set to true, the maximum of all use counts observed for each (word, POS)-pair is assigned to it.
     *               Otherwise, the sum of all use counts is taken.
     * @return the use count of {@code word} according to WordNet
     */
    private int getWordCount(IndexWord word, boolean useMax) {
        int sum = 0;
        for(Synset synset: word.getSenses()) {

            int count = synset.getWords().stream().filter(w -> w.getLemma().equals(word.getLemma())).mapToInt(Word::getUseCount).sum();
            if(!useMax) {
                sum += count;
            }
            else {
                sum = Math.max(sum, count);
            }
        }
        return sum;
    }
}