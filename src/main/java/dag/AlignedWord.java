package dag;

import java.util.List;
import java.util.stream.Collectors;

/**
 * This class represents an aligned word for the post-processing process,
 * consisting of the word itself, the vertex it is aligned to (i.e. the vertex whose realization it is),
 * and the type of alignment.
 */
public class AlignedWord {

    public String word;
    public Vertex alignment;
    public AlignmentType type;

    public AlignedWord(String word, Vertex alignment, AlignmentType type) {
        this.word = word;
        this.alignment = alignment;
        this.type = type;
    }

    public String toString() {
        return word + " (" + type + "," + alignment + ")";
    }

    /**
     * Filters a list of aligned words by removing all empty words and, optionally, all punctuation.
     * @param words the list of words to process
     * @param withPunctuation whether punctuation should be kept
     * @return the filtered list of strings
     */
    public static List<String> filterEmpty(List<AlignedWord> words, boolean withPunctuation) {
        return words.stream().filter(s -> !s.word.isEmpty() && (withPunctuation || s.type != AlignmentType.PUNCTUATION)).map(s -> s.word).collect(Collectors.toList());
    }

    /**
     * Returns the yield of a sequence of aligned words (i.e. the space-separated concatenation of the corresponding strings), removing all empty words and, optionally, all punctuation.
     * @param words the list of words to process
     * @param withPunctuation whether punctuation should be kept
     * @return the resulting string
     */
    public static String yield(List<AlignedWord> words, boolean withPunctuation) {
        return String.join(" ", filterEmpty(words, withPunctuation));
    }
}
