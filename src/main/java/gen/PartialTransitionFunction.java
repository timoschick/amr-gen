package gen;

import dag.Edge;
import dag.Vertex;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A naive implementation of a partial transition function as described in the thesis. This implementation simply contains mappings for every vertex and every class of transitions
 * as well as every syntactic annotation.
 */
public class PartialTransitionFunction {

    /**
     * This map contains the POS tags assigned to each vertex by this partial transition function.
     */
    public Map<Vertex,String> pos;

    /**
     * This map contains the number assigned to each vertex by this partial transition function.
     */
    public Map<Vertex,String> number;

    /**
     * This map contains the tense assigned to each vertex by this partial transition function.
     */
    public Map<Vertex,String> tense;

    /**
     * This map contains the voice assigned to each vertex by this partial transition function.
     */
    public Map<Vertex,String> voice;

    /**
     * This map contains the realization assigned to each vertex by this partial transition function.
     */
    public Map<Vertex,String> realization;

    /**
     * This map contains the denominator assigned to each vertex by this partial transition function.
     */
    public Map<Vertex,String> denominator;

    /**
     * This map contains the INSERT-BETWEEN-(w,l) transition assigned to each vertex by this partial transition function, if such a transition needs to be applied.
     */
    public Map<Vertex,String> beforeIns;

    /**
     * This map contains the INSERT-BETWEEN-(w,r) transition assigned to each vertex by this partial transition function, if such a transition needs to be applied.
     */
    public Map<Vertex,String> afterIns;

    /**
     * This map contains the reordering assigned to each vertex by this partial transition function.
     */
    public Map<Vertex,List<Edge>> reordering;

    /**
     * This map contains the INSERT-CHILD transitions assigned to each vertex by this partial transition function.
     */
    public Map<Vertex,List<Edge>> childInsertions;

    /**
     * This map contains the punctuation assigned to each vertex. While this is not a part of the partial transition function defined in the thesis, it simplifies
     * the implementation to view punctuations as parts of partial transition functions.
     */
    public Map<Vertex,String> punctuation;

    /**
     * Creates a new partial transition functions with empty mappings.
     */
    public PartialTransitionFunction() {
        pos = new HashMap<>();
        realization = new HashMap<>();
        denominator = new HashMap<>();
        beforeIns = new HashMap<>();
        afterIns = new HashMap<>();
        reordering = new HashMap<>();
        punctuation = new HashMap<>();
        childInsertions = new HashMap<>();
        number = new HashMap<>();
        voice = new HashMap<>();
        tense = new HashMap<>();
    }

    /**
     * Adds all entries of a given partial transition function to the entries of this partial transition function.
     * @param partialTransitionFunction the given partial transition function
     */
    public void addCopy(PartialTransitionFunction partialTransitionFunction) {
        pos.putAll(partialTransitionFunction.pos);
        realization.putAll(partialTransitionFunction.realization);
        denominator.putAll(partialTransitionFunction.denominator);
        beforeIns.putAll(partialTransitionFunction.beforeIns);
        afterIns.putAll(partialTransitionFunction.afterIns);
        reordering.putAll(partialTransitionFunction.reordering);
        punctuation.putAll(partialTransitionFunction.punctuation);
        childInsertions.putAll(partialTransitionFunction.childInsertions);
        number.putAll(partialTransitionFunction.number);
        voice.putAll(partialTransitionFunction.voice);
        tense.putAll(partialTransitionFunction.tense);
    }

}
