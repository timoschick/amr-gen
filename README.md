# Transition-based AMR Generator

This is a Java-based implementation of the AMR-to-text generator introduced in "Transition-based Generation from Abstract Meaning Representations". For a detailed description of all relevant classes, please refer to the Javadoc documentation found in the `javadoc` subdirectory. Running the generator requires Java Version 8 or newer. 

# Generation

There are two ways of generating sentences from AMR graphs using this generator: You may either use the precompiled and pretrained (using the [LDC2014T12](https://catalog.ldc.upenn.edu/ldc2014t12) corpus) generator's command line interface, which requires almost no time to set up but is not very flexible, or you may set up the generator as described in section [Setup](#setup) and then use the methods `loadAmrGraphs(String directory, boolean forTesting)` and `generate(List<Amr> amrs)` of class `main.AmrMain`.

For using the command line interface, the following parameters may be specified:
- `--input` (`-i`): The file in which the AMR graphs are stored in official [AMR format](https://github.com/amrisi/amr-guidelines/blob/master/amr.md). The AMR graphs must be separated by empty lines and there must be *two* line breaks after the last graph. If this parameter is not specified, it is assumed that the required AMR graphs can be found in the subdirectories `bolt`, `consensus`, `dfa`, `proxy` and `xinhua` of `corpus/test` (as is the case for LDC2014T12).
- `--output` (`-o`): The file in which the generated sentences should be saved. This is the only required parameter.
- `--bleu` (`-b`): If this flag is set, the Bleu score achieved by the generator on the given data set is output to the standard output stream. This is only possible if the AMR graphs are stored with tokenized reference realizations (indicated by a line beginning with `# ::tok` right above each actual AMR graph) in the input file.
- `--show-output` (`-s`): If this flag is set, pairs of (reference realization, generated sentence) are printed to the standard output stream when the generator is finished. Again, this is only possible if the AMR graphs are stored with tokenized reference realizations in the input file.

**Important**: Note that the generation process requires around 8GB of RAM. Therefore, the generator should always be run with `-Xmx8g` or more.
 
### Examples

Following is the content of the file `in.txt` (line breaks are indicated through `↩`):

```
(v1 / want-01 ↩
      :ARG0 (v2 / person ↩
  	    	:ARG0-of (v4 / develop-02)) ↩
      :ARG1 (v3 / sleep-01 ↩
  	    	:ARG0 v2)) ↩
↩
```
It is an encoding (in official AMR format) of an AMR graph used extensively in the Master's thesis. The following command generates an English sentence from this graph:
```
java -jar -Xmx8g AmrGen.jar --input in.txt --output out.txt
```
Running this command creates a new file `out.txt` which contains only a single line with content "the developer wants to sleep".

The following command generates sentences from all AMR graphs found in `some/directory/input.txt`, writes them to `some/other/directory/output.txt` and outputs the obtained Bleu score to the standard output stream:
```
java -jar -Xmx8g AmrGen.jar --input some/directory/input.txt --output some/other/directory/output.txt --bleu
```
The following command generates sentences from all AMR graphs found in the subdirectories `bolt`, `consensus`, `dfa`, `proxy` and `xinhua` of `corpus/test`, writes them to `some/directory/output.txt` and outputs both the Bleu score and pairs of reference realizations and generated sentences to the standard output stream:
```
java -jar -Xmx8g AmrGen.jar -o some/directory/output.txt -b -s
```

# Setup

To set up the AMR generator, simply build the Maven project using `pom.xml`, which automatically loads all dependencies.

### Setup using IntelliJ IDEA

Using [IntelliJ IDEA](https://www.jetbrains.com/idea/) (tested with IntelliJ IDEA Ultimate 2016.3 under Ubuntu 16.10, Windows 10 and OS X 10.10.5), the project can be set up as follows:

- Select **File | New | Project from Existing Sources...**
- In the "Select File or Directory to Import" dialogue, select the root folder of the implementation and click **Ok**.
- In the "Import Project" dialogue, click **Next** several times and then **Finish**.

# Training

After performing the steps described above, the maximum entropy models required by the generator can be retrained using the `train()` method provided by `main.AmrMain`. This assumes that the development and training AMR graphs can be found in the subdirectories `bolt`, `consensus`, `dfa`, `proxy` and `xinhua` of `corpus/dev` and `corpus/training`, respectively. Each of these subfolders should contain the following four files: 
- **data.amr.tok.aligned**: A list of aligned and tokenized AMR graphs, separated by newlines. The file must end with two line breaks. To obtain the reported results, the alignments should be created using [JAMR](https://github.com/jflanigan/jamr).
Above each AMR graph, there should be a line starting with `# ::tok` containing a tokenized reference realization and a line starting with `# ::alignments` containing the alignments. For example, an AMR graph may be represented like this:

    ```
    # ::tok the developer wants to sleep
    # ::alignments 1-2|0.0+0.0.0 2-3|0 4-5|0.1	
    (v1 / want-01
          :ARG0 (v2 / person
      	    	:ARG0-of (v4 / develop-02))
          :ARG1 (v3 / sleep-01
      	    	:ARG0 v2))
    ```

- **data.amr.tok.charniak.parse.dep**: A list of dependency trees which correspond to the AMR graphs found in the above file in a one-to-one manner. The dependency trees must be separated by empty lines and encoded in [Stanford dependencies format](https://nlp.stanford.edu/software/stanford-dependencies.shtml). For example, the dependency tree corresponding to the sentence encoded by the above AMR graph may look like this:

    ```
    root(ROOT-0, wants-3)
    nsubj(wants-3, developer-2)
    xcomp(wants-3, sleep-5)
    det(developer-2, the-1)
    mark(sleep-5, to-4)
    ```

- **pos.txt**: A newline-separated list of POS sequences, where POS tags are separated by spaces and each sequence corresponds in a one-to-one manner to the reference realizations of the AMR graphs in the above file. The following entry corresponds to the sentence represented by the above AMR graph:

    ```
    DT NN VBZ PRT VB
    ```

- **alignments.txt**: A list of additional alignment sequences, where each sequence corresponds in a one-to-one manner to the AMR graphs in the above file. To obtain the reported results, these alignments must be encoded in the format used by the aligner of Pourdamghani et al. (2014) found [here](http://isi.edu/~damghani/papers/Aligner.zip) and should be obtained using this very aligner. For example, the alignment `1-2|0.0+0.0.0 2-3|0 4-5|0.1` shown above in JAMR format should be encoded as follows:

    ```
    1-1.1 1-1.1.1 2-1 4-1.2 
    ```

To change the naming conventions, edit the corresponding entries in `main.PathList`. To retrain only specific models, use the `setUp(List<Models> modelsToTrain, boolean stopAfterFirstStage)` method provided by `main.AmrMain`.

**Important**: Note that the training process requires around 8GB of RAM and may take several hours to days. Therefore, it should always be run with `-Xmx8g` or more.

**Important**: Note that retraining the AMR generator on a different dataset may also require you to rebuild some of the files described in section [External Resources](#external-resources). For these files, the functions required to rebuild them are given below. 

### Hyperparameter Optimization

After training the classifier, hyperparameter optimization may be performed using the `optimizeHyperparams()` method provided by `main.AmrMain`. This assumes that the development AMR graphs can be found in the subdirectories `bolt`, `consensus`, `dfa`, `proxy` and `xinhua` of `corpus/dev`.
For randomized hyperparameter optimization, the various kinds of update functions provided by `gen.Hyperparam` can be used.

# External Resources

All external resources used by our implementation of the transition-based generator can be found in the subdirectory `res`. The paths to all of these files are defined in `main.PathList`. The external resources have the following contents:

- **res/lm.binary**: The language model to be used by the generator. This language model should be compatible with the [Berkeley LM](https://github.com/adampauls/berkeleylm). For efficient generation, it should be in binary format. By default, this file contains a 3-gram language model trained on Gigaword (LDC2003T05) which can be found at [www.keithv.com/software/giga](https://www.keithv.com/software/giga/).
- **res/english-bidirectional-distim.tagger**: A model file for the [Stanford POS tagger](https://nlp.stanford.edu/software/tagger.shtml) used to annotate reference realizations and unknown words with POS tags.
- **res/morph-verbalization.txt**: A file containing tuples of verbs and corresponding nouns, e.g. (develop,development) or (pray,prayer). This file is obtained from [amr.isi.edu](http://amr.isi.edu/download/lists/morph-verbalization-v1.01.txt) and used for determining default realizations.
- **res/verbalization.txt**: A file containing nouns and corresponding AMR graph realizations using PropBank framesets, e.g. (`actor`, `person :ARG0-of act-01`). It is obtained from [amr.isi.edu](http://amr.isi.edu/download/lists/verbalization-list-v1.06.txt) and used during the preparation of AMR graphs. 
- **res/concepts.txt**: This file contains all concepts observed during training. It can be refilled using the `getConceptList(List<Amr> amrs)` method provided by `misc.StaticHelper`.
- **res/bestpostags.txt**: This file maps each non-PropBank concept to the POS tag observed most often in the training data of LDC2014T12. It was obtained using the `getBestPosTagsMap(List<Amr> amrs)` method of `misc.StaticHelper`.
- **res/mergemap.txt**: For each pair of vertices that has been merged during training, this file contains the resulting (realization,pos)-tuple observed most often, e.g. `(long, more) → (longer, JJ)`. It was obtained using the `getMergeMap(List<Amr> amrs)` method of `misc.StaticHelper`.
- **res/namedentities.txt**: This file stores realizations observed for named entities during training along with the number of times these realizations have been observed.
- **res/hyperparams.txt**: This file contains the current configuration for all hyperparameters. For more details, please refer to the Javadoc documentation of `gen.Hyperparam` and `gen.Hyperparams`.