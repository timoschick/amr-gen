package dag;

import java.util.HashMap;
import java.util.Map;

/**
 * Simple wrapper class for a {@link DirectedGraph} to represent a dependency tree.
 */
public class DependencyTree {

    // the sentence corresponding to the dependency tree
    String[] sentence;
    // the alignment of vertices to indices in the sentence
    Map<Vertex,Integer> alignment;
    // the actual tree
    DirectedGraph tree;
    // the corresponding AMR graph
    Amr amr;

    /**
     * Creates a new dependency tree with no vertices.
     */
    public DependencyTree() {
        alignment = new HashMap<>();
    }

    public String[] getSentence() {
        return sentence;
    }

    public void setSentence(String[] sentence) {
        this.sentence = sentence;
    }

    public Map<Vertex, Integer> getAlignment() {
        return alignment;
    }

    public void setAlignment(Map<Vertex, Integer> alignment) {
        this.alignment = alignment;
    }

    public DirectedGraph getTree() {
        return tree;
    }

    public void setTree(DirectedGraph tree) {
        this.tree = tree;
    }

    public Amr getAmr() {
        return amr;
    }

    public void setAmr(Amr amr) {
        this.amr = amr;
    }
}