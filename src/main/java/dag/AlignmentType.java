package dag;

import java.util.*;

/**
 * An enum that stores all types of alignments extracted during the training process.
 * AlignmentTypes with prefix DT are extracted from the dependency tree, all others are extracted from the string only.
 */
public enum AlignmentType {

    ARTICLE, BEFORE_INSERTION, BE, AFTER_INSERTION, PUNCTUATION, TRANSLATION,
    DT_HEAD_INSERTION_LEFT, DT_HEAD_INSERTION_RIGHT, DT_CHILD_INSERTION_LEFT, DT_CHILD_INSERTION_RIGHT, DT_ARTICLE;

    // the set of AlignmentTypes to show in the graphical representation of an AMR graph
    public static Set<AlignmentType> filter = new HashSet<>(Arrays.asList(
            //ARTICLE, BEFORE_INSERTION, BE, AFTER_INSERTION, PUNCTUATION,
            DT_HEAD_INSERTION_LEFT, DT_HEAD_INSERTION_RIGHT, DT_CHILD_INSERTION_LEFT, DT_CHILD_INSERTION_RIGHT, DT_ARTICLE,
            TRANSLATION));

    // the colors for the AlignmentTypes in the graphical representation of an AMR graph
    public static Map<AlignmentType,String> colors = new HashMap<>();

    static {
        colors.put(AlignmentType.BEFORE_INSERTION, "#FF00FF");
        colors.put(AlignmentType.ARTICLE, "#00FF00");
        colors.put(AlignmentType.DT_ARTICLE, "#3333FF");
        colors.put(AlignmentType.AFTER_INSERTION, "#00BBBB");
        colors.put(AlignmentType.DT_HEAD_INSERTION_LEFT, "#00FF00");
        colors.put(AlignmentType.DT_HEAD_INSERTION_RIGHT, "#FFFF00");
        colors.put(AlignmentType.DT_CHILD_INSERTION_LEFT, "#5555AA");
        colors.put(AlignmentType.DT_CHILD_INSERTION_RIGHT, "#999999");
        colors.put(AlignmentType.BE, "#FFF000");
    }

}

