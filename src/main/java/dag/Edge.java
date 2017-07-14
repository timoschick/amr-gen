package dag;

/**
 * Represents an edge of a directed acyclic graph.
 */
public class Edge {

    Vertex from, to;
    String label;

    public boolean instanceEdge = false;
    public boolean inserted = false;

    String debugInfo = "";

    /**
     * Creates a new edge (from, label, to).
     * @param from the node from which the edge starts
     * @param to the node at which the edge ends
     * @param label the label of the edge
     */
    public Edge(Vertex from, Vertex to, String label) {
        this(from,to,label,true);
    }

    /**
     * Creates a new edge (from, label, to).
     * @param from the node from which the edge starts
     * @param to the node at which the edge ends
     * @param label the label of the edge
     * @param addToOutgoing whether the edge should be added to the list of outgoing edges of {@code from}
     */
    public Edge(Vertex from, Vertex to, String label, boolean addToOutgoing) {
        this.from = from;
        if(addToOutgoing) {
            from.addOutgoingEdge(this);
        }
        this.to = to;
        to.addIncomingEdge(this);
        this.label = label;
    }

    /**
     * This function uncouples the edge from its to-node, creates a link to the latter and
     * reattaches the edge to the newly created link. It is required to implement the
     * DELETE-REENTRANCE transition as described in the thesis.
     */
    public void uncouple() {
        Vertex old = to;
        to = new Vertex(to.instance);
        to.addIncomingEdge(this);
        to.annotation.original = old;
    }

    public String toString() {
        return label;
    }

    public String getLabel() {
        return label;
    }

    public Vertex getTo() {
        return to;
    }

    public Vertex getFrom() {
        return from;
    }

    public boolean isInstanceEdge() {
        return instanceEdge;
    }

    public boolean isInserted() {
        return inserted;
    }
}
