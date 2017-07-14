package dag;

/**
 * This class represents a pair consisting of an edge and an alignment type. It is used as a key for the alignment map built by the {@link BigraphAlignmentBuilder} where
 * pairs of (edge, alignmentType) are mapped to words of the corresponding sentence.
 */
public class EdgeTypePair {

    Edge e;
    AlignmentType type;

    /**
     * Creates a new EdgeTypePair
     * @param e the edge
     * @param t the alignment type
     */
    public EdgeTypePair(Edge e, AlignmentType t) {
        this.e = e;
        this.type = t;
    }

    @Override
    public boolean equals(Object o) {
        if(o instanceof EdgeTypePair) {
            EdgeTypePair eat = (EdgeTypePair) o;
            return e.equals(eat.e) && type.equals(eat.type);
        }
        return false;
    }

    @Override
    public int hashCode() {
        int hash = 1;
        hash = hash * 17 + e.hashCode();
        hash = hash * 31 + type.hashCode();
        return hash;
    }
}
