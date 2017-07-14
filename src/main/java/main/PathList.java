package main;

import dag.Amr;
import dag.AmrLineFormat;
import dag.AmrParser;
import gen.Hyperparams;

import java.util.Arrays;
import java.util.List;

/**
 * Static class containing paths to files required by the generator.
 */
public class PathList {

    /**
     * The path relative to which all other paths are defined. For the project directory to be the base path, simply leave this
     * an empty string.
     */
    private static final String BASEPATH = "";

    /**
     * path to the directory which contains all AMR graphs used for training. Note that these AMR graphs are not to be stored
     * directly in the TRAINING_DIR, but instead inside the subdirectories specified by {@link PathList#AMR_SUBDIRECTORIES}.
     */
    public static final String TRAINING_DIR = PathList.BASEPATH + "corpus/training/";

    /**
     * path to the directory which contains all development AMR graphs. Note that these AMR graphs are not to be stored
     * directly in the DEVELOPMENT_DIR, but instead inside the subdirectories specified by {@link PathList#AMR_SUBDIRECTORIES}.
     */
    public static final String DEVELOPMENT_DIR = PathList.BASEPATH + "corpus/dev/";

    /**
     * path to the directory which contains all AMR graphs used for testing. Note that these AMR graphs are not to be stored
     * directly in the TEST_DIR, but instead inside the subdirectories specified by {@link PathList#AMR_SUBDIRECTORIES}.
     */
    public static final String TEST_DIR = PathList.BASEPATH + "corpus/test/";

    /**
     * subdirectories of the directories containing training, development and test AMR graphs
     */
    public static List<String> AMR_SUBDIRECTORIES = Arrays.asList("bolt/", "consensus/", "dfa/", "proxy/", "xinhua/");

    /**
     * the names of the files which contain the actual AMR graphs. Each subdirectory (see {@link PathList#AMR_SUBDIRECTORIES}) must
     * contain a file with this name.
     */
    public static final String AMR_FILENAME = "data.amr.tok.aligned";

    /**
     * the names of the files which contain the POS tags corresponding to the reference realizations of the AMR graphs stored
     * in file {@link PathList#AMR_FILENAME}. Each subdirectory (see {@link PathList#AMR_SUBDIRECTORIES}) must
     * contain a file with this name if the AMR graphs are to be used for training.
     */
    public static final String POS_FILENAME = "pos.txt";

    /**
     * the names of the files which contain the dependency trees corresponding to the reference realizations of the AMR graphs
     * stored in file {@link PathList#AMR_FILENAME}. Each subdirectory (see {@link PathList#AMR_SUBDIRECTORIES}) must
     * contain a file with this name if the AMR graphs are to be used for training.
     */
    public static final String DEPENDENCIES_FILENAME = "data.sent.tok.charniak.parse.dep";

    /**
     * the names of the files which contain the EM alignments corresponding to the reference realizations of the AMR graphs stored
     * in file {@link PathList#AMR_FILENAME}. Each subdirectory (see {@link PathList#AMR_SUBDIRECTORIES}) must
     * contain a file with this name if the AMR graphs are to be used for training.
     */
    public static final String EM_ALIGNMENTS_FILENAME = "alignments.txt";

    /**
     * path to the {@link edu.stanford.nlp.tagger.maxent.MaxentTagger} file to be used for POS tagging
     */
    public static final String POS_TAGGER_PATH = BASEPATH + "res/english-bidirectional-distsim.tagger";

    /**
     * path to the {@link edu.berkeley.nlp.lm.ArrayEncodedNgramLanguageModel} file to be used for scoring sentences
     */
    public static final String LANGUAGE_MODEL_PATH = BASEPATH + "res/lm.binary";

    /**
     * path to the parts of speech (POS) maximum entropy model to be used by the generator
     */
    public static final String POS_MAXENT_PATH = BASEPATH + "models/pos.model";

    /**
     * path to the denominator maximum entropy model to be used by the generator
     */
    public static final String DENOM_MAXENT_PATH = BASEPATH + "models/denom.model";

    /**
     * path to the number maximum entropy model to be used by the generator
     */
    public static final String NUMBER_MAXENT_PATH = BASEPATH + "models/number.model";

    /**
     * path to the tense maximum entropy model to be used by the generator
     */
    public static final String TENSE_MAXENT_PATH = BASEPATH + "models/tense.model";

    /**
     * path to the voice maximum entropy model to be used by the generator
     */
    public static final String VOICE_MAXENT_PATH = BASEPATH + "models/voice.model";

    /**
     * path to the &lt;<sub>*</sub> reordering maximum entropy model to be used by the generator (see Eq. 19, Section 4.2.1 Modeling)
     */
    public static final String REORDER_MAXENT_PATH = BASEPATH + "models/reorder.model";

    /**
     * path to the &lt;<sub>l</sub> reordering maximum entropy model to be used by the generator (see Eq. 19, Section 4.2.1 Modeling)
     */
    public static final String LEFT_REORDER_MAXENT_PATH = BASEPATH + "models/reorder_left.model";

    /**
     * path to the &lt;<sub>r</sub> reordering maximum entropy model to be used by the generator (see Eq. 19, Section 4.2.1 Modeling)
     */
    public static final String RIGHT_REORDER_MAXENT_PATH = BASEPATH + "models/reorder_right.model";

    /**
     * path to the INSERT-BEFORE maximum entropy model to be used by the generator for edges whose label matches :ARG[0-9]
     */
    public static final String ARG_INSERTION_MAXENT_PATH = BASEPATH + "models/arg_insertion.model";

    /**
     * path to the INSERT-BEFORE maximum entropy model to be used by the generator for edges whose label does not match :ARG[0-9]
     */
    public static final String OTHERS_INSERTION_MAXENT_PATH = BASEPATH + "models/others_insertion.model";

    /**
     * path to the INSERT-CHILD maximum entropy model to be used by the generator
     */
    public static final String CHILD_INSERTION_MAXENT_PATH = BASEPATH +  "models/child_ins.bin.gz";

    /**
     * path to the first stage maximum entropy model to be used by the generator
     */
    public static final String FIRST_STAGE_MAXENT_PATH = BASEPATH +  "models/first_stage.model";

    /**
     * path to the REALIZE maximum entropy model to be used by the generator
     */
    public static final String REALIZE_MAXENT_PATH = BASEPATH + "models/realize.bin.gz";

    /**
     * path to the map containing verbalizations like (developer → (person (:ARG1-of develop-02))).
     * The default version of this map is extracted from http://amr.isi.edu/download/lists/verbalization-list-v1.06.txt
     */
    public static final String VERBALIZATION_PATH = BASEPATH + "res/verbalization.txt";

    /**
     * path to the map containing corresponding noun pairs like (accept → acceptance).
     * The default version of this map is extracted from http://amr.isi.edu/download/lists/morph-verbalization-v1.01.txt
     */
    public static final String MORPH_VERBALIZATION_PATH = BASEPATH + "res/morph-verbalization.txt";

    /**
     * path to a map containing the most frequently observed POS tag for each concept that is not a PropBank frameset
     */
    public static final String BESTPOSTAGS_PATH = BASEPATH + "res/bestpostags.txt";

    /**
     * path to a list containing all (concept,POS tag) pairs observed in the training corpus
     */
    public static final String CONCEPT_LIST = BASEPATH + "res/concepts.txt";

    /**
     * path to a map containing (parent,child) pairs and the result of merging them
     */
    public static final String MERGEMAP_PATH = BASEPATH + "res/mergemap.txt";

    /**
     * paths to a map mapping all realizations for named entities to the number of times they have been observed in the training corpus
     */
    public static final String NAMED_ENTITIES_MAP = BASEPATH + "res/namedentities.txt";

    /**
     * path to a list containing the values for all hyperparameters in the order specified in {@link Hyperparams}
     */
    public static final String HYPERPARAMS_LIST = BASEPATH + "res/hyperparams.txt";

    // non-instantiable class
    private PathList() { }

}