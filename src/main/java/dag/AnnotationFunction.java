package dag;

/**
 * This class represents an annotation function for a single {@link Vertex}.
 */
public class AnnotationFunction {

    public Vertex original;
    public boolean delete;

    public String pos;
    public String initialConcept;

    public int nrOfSwapDowns;

    public AnnotationFunction(Vertex v) {
        initialConcept = v.getInstance();
        original = null;
        delete = false;
        nrOfSwapDowns = 0;
        pos = null;
    }

    public String toString() {
        return pos + " " + (original!=null?"L ":"") + (delete?"D ":"");
    }

}
