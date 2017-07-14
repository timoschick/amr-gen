package dag;

import java.util.*;

/**
 * Class to represent a rooted, directed and acyclic graph (DAG).
 */
public class DirectedGraph implements Iterable<Vertex> {

    Vertex root;

    /**
     * Creates a new rooted DAG without any vertices or edges.
     */
    public DirectedGraph() { }

    public Vertex getRoot() {
        return root;
    }

    /**
     * @return all vertices reachable from the root in some unspecified order
     */
    public List<Vertex> getVertices() {
        List<Vertex> vertices = new ArrayList<>();
        for(Vertex v: this) {
            vertices.add(v);
        }
        return vertices;
    }

    /**
     * @return all vertices reachable from the root in bottom-up order
     */
    public List<Vertex> getVerticesBottomUp() {
        return root.getVerticesBottomUp();
    }

    public Set<Edge> getEdges() {
        Set<Edge> edges = new HashSet<>();
        for(Vertex v: this) {
            edges.addAll(v.outgoingEdges);
        }
        return edges;
    }

    /**
     * An implementation of Kahn's algorithm (Kahn, 1962) that checks if a directed graph contains any cycles.
     * @return true if the graph contains at least one cycle, false otherwise
     */
    public boolean isCyclic() {

        List<Vertex> L = new ArrayList<>();
        Stack<Vertex> Q = new Stack<>();
        Set<Edge> removedEdges = new HashSet<>();

        for(Vertex v : getVertices()){
            if(v.getIncomingEdges().isEmpty()){
                Q.add(v);
            }
        }

        while(!Q.isEmpty()) {

            Vertex n = Q.pop();
            L.add(n);

            for(Edge e: n.getOutgoingEdges()) {
                if(!removedEdges.contains(e)) {
                    removedEdges.add(e);
                    Vertex m = e.getTo();
                    if (m.equals(Vertex.EMPTY_VERTEX)) {
                        continue;
                    }
                    if(removedEdges.containsAll(m.getIncomingEdges())) {
                        Q.add(m);
                    }

                }
            }
        }

        if(removedEdges.size() != getEdges().size()) return true;
        return false;

    }

    /**
     * @return true iff each vertex reachable from the root has exactly one parent vertex and is
     * connected to it by exactly one edge
     */
    public boolean isTree() {
        for(Vertex v: this) {
            if(v.incomingEdges.size() > 1) {
                return false;
            }
        }
        return true;
    }

    /**
     * Converts this DAG into a tree by successively removing incoming edges from nodes with more
     * than one incoming edge using the method described in the thesis (Sect. Training).
     */
    public void convertToTree() {
        for(Vertex v: this) {
            v.convertToTree();
        }
    }

    @Override
    public Iterator<Vertex> iterator() {
        return new VertexIterator();
    }

    class VertexIterator implements Iterator<Vertex> {

        Queue<Vertex> frontier;
        Set<Vertex> visited;

        private VertexIterator() {
            frontier = new LinkedList<>();
            visited = new HashSet<>();
            if(root == null) {
                throw new AssertionError("root cannot be null");
            }
            frontier.add(root);
            visited.add(root);
        }

        @Override
        public boolean hasNext() {
            return !frontier.isEmpty();
        }

        @Override
        public Vertex next() {
            Vertex next = frontier.poll();

            for(Edge e: next.outgoingEdges) {
                if(e.getTo() != Vertex.EMPTY_VERTEX) {
                    if (!visited.contains(e.getTo())) {
                        frontier.add(e.getTo());
                        visited.add(e.getTo());
                    }
                }
            }
            return next;
        }
    }
}
