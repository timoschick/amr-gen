package dag;

import edu.stanford.nlp.tagger.maxent.MaxentTagger;
import misc.PosHelper;
import ml.Prediction;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Represents a vertex of a directed, acyclic graph.
 */
public class Vertex {

    public String instance;
    public String mode;
    public String name;

    List<Edge> incomingEdges;
    List<Edge> outgoingEdges;

    // stores n-best syntactic annotations and n-best transition predictions for this vertex
    public Map<String, List<Prediction>> predictions = new HashMap<>();

    // stores annotations actually assigned to this vertex
    public AnnotationFunction annotation;

    static final String EMPTY_VERTEX_ID = "__empty__";
    static final String EMPTY_VERTEX_INSTANCE = "UNKNOWN";
    public static final Vertex EMPTY_VERTEX = new Vertex(EMPTY_VERTEX_INSTANCE);
    static { EMPTY_VERTEX.setPos(EMPTY_VERTEX_INSTANCE); }

    /**
     * Creates a new vertex.
     * @param instance the label assigned to this vertex
     */
    public Vertex(String instance) {
        this.instance = instance;
        mode = "";
        name = "";
        incomingEdges = new ArrayList<>();
        outgoingEdges = new ArrayList<>();
        annotation = new AnnotationFunction(this);
    }

    /**
     * adds a new incoming edge to this vertex
     * @param e the edge to add
     */
    void addIncomingEdge(Edge e) {
        incomingEdges.add(e);
    }

    /**
     * adds a new outgoing edge to this vertex
     * @param e the edge to add
     */
    void addOutgoingEdge(Edge e) {
        outgoingEdges.add(e);
    }

    /** determines the POS tag of this vertex using a maximum entropy tagger on a sentence that consists only of a cleaned up version of the label of this vertex
     * @param tagger a maximum entropy tagger
     * @return the determined POS tag
     */
    public String getPos(MaxentTagger tagger) {
        String clearedInstance = instance.replaceAll("[\"\']","");
        if(clearedInstance.matches(".*-[0-9]+")) {
            clearedInstance = instance.substring(0, instance.lastIndexOf("-"));
        }
        String taggedInstance = tagger.tagString(clearedInstance).trim();
        return taggedInstance.substring(taggedInstance.lastIndexOf("_") + 1);
    }

    /**
     * sets the POS tag of this vertex to be the simplified version (according to {@link PosHelper#mapPos(String, boolean)}
     * of the one returned by {@link Vertex#getPos(MaxentTagger)}
     * @param tagger a maximum entropy tagger
     */
    public void setSimplifiedPos(MaxentTagger tagger) {
        setSimplifiedPos(getPos(tagger));
    }

    /**
     * sets the POS tag of this vertex to the simplified version (according to {@link PosHelper#mapPos(String, boolean)}) of the POS tag given as a string
     * @param pos the POS tag in unsimplified form
     */
    public void setSimplifiedPos(String pos) {
        if(pos == null) {
            this.setPos("UNKNOWN");
        }
        else {
            this.setPos(PosHelper.mapPos(pos, isPropbankEntry()));
        }
    }

    /**
     * @return the instance edge of this vertex as described in the thesis
     */
    public Edge getInstanceEdge() {
        for(Edge e: outgoingEdges) {
            if(e.label.equals(instance)) {
                return e;
            }
        }
        return null;
    }

    /**
     * @return the number of nodes in the subgraph induced by this vertex.
     */
    public int subtreeSize() {
        int subtreeSize = 1;
        for(Edge e: outgoingEdges) {
            if(e.getTo() != Vertex.EMPTY_VERTEX) {
                subtreeSize += e.getTo().subtreeSize();
            }
        }
        return subtreeSize;
    }

    /**
     * Removes all but one incoming edge from this vertex to ensure that the corresponding AMR graph is a tree as soon as this method has been applied
     * to all vertices. This method implements the DELETE-REENTRANCE transition described in the paper with the minor difference that it does not remove
     * edges one by one, but all at once.
     */
    public void convertToTree() {
        if(incomingEdges.size() <= 1) return;

        int minDistToRoot = 10;
        List<Edge> minDistEdges = new ArrayList<>();

        for(Edge parentEdge: incomingEdges) {
            Vertex currentVertex = parentEdge.getFrom();
            int distanceToRoot = 0;
            while(!currentVertex.getIncomingEdges().isEmpty()) {
                distanceToRoot++;
                currentVertex = currentVertex.getIncomingEdges().get(0).getFrom();
                if(distanceToRoot > minDistToRoot) break;
            }

            if(distanceToRoot < minDistToRoot) {
                minDistToRoot = distanceToRoot;
                minDistEdges.clear();
                minDistEdges.add(parentEdge);
            }
            else if(distanceToRoot == minDistToRoot){
                minDistEdges.add(parentEdge);
            }
        }

        Set<Edge> possEdges = minDistEdges.stream().filter(e -> Arrays.asList(":poss",":wiki").contains(e.label)).collect(Collectors.toSet());
        if(possEdges.size() != minDistEdges.size()) {
            minDistEdges.removeAll(possEdges);
        }

        Set<Edge> invEdges = minDistEdges.stream().filter(e -> e.label.endsWith("-of")).collect(Collectors.toSet());
        if(invEdges.size() != minDistEdges.size()) {
            minDistEdges.removeAll(invEdges);
        }

        if(minDistEdges.isEmpty()) {
            minDistEdges = new ArrayList<>(incomingEdges);
        }

        Edge goldParentEdge = minDistEdges.get(0);

        for (Edge edge : incomingEdges) {
            if (edge != goldParentEdge) {
                edge.uncouple();
            }
        }
        incomingEdges = new ArrayList<>();
        incomingEdges.add(goldParentEdge);
    }

    /**
     * Removes all outgoing edges with label :wiki.
     */
    public void removeWikiTags() {

        Set<Edge> removableEdges = new HashSet<>();

        for(Edge e: outgoingEdges) {
            if(e.getLabel().toLowerCase().equals(":wiki")) {
                removableEdges.add(e);
            }
        }
        outgoingEdges.removeAll(removableEdges);
    }

    /**
     * @return all vertices of the subgraph induced by this vertex in bottom-up order.
     */
    public List<Vertex> getVerticesBottomUp() {
        List<Vertex> ret = new ArrayList<>();
        for(Edge outEdge: getOutgoingEdges()) {
            if(!outEdge.isInstanceEdge()) {
                ret.addAll(outEdge.getTo().getVerticesBottomUp());
            }
        }
        ret.add(this);
        return ret;
    }

    /**
     * @return all edges of the subgraph induced by this vertex in bottom-up order.
     */
    public List<Edge> getEdgesBottomUp() {
        List<Edge> ret = new ArrayList<>();
        for(Edge outEdge: getOutgoingEdges()) {
            if(!outEdge.isInstanceEdge()) {
                ret.add(outEdge);
                ret.addAll(outEdge.getTo().getEdgesBottomUp());
            }
        }
        return ret;
    }

    public List<Edge> getOutgoingEdges() {
        return outgoingEdges;
    }

    public List<Edge> getIncomingEdges() {
        return incomingEdges;
    }

    public boolean isPropbankEntry() {
        return instance.matches(".*-[0-9]+");
    }

    public boolean isTranslatable() {
        return !(instance.matches("\"(.*)\"") || instance.matches("[0-9.,]+"));
    }

    public boolean isSpecialNode() {
        return !isTranslatable() || Arrays.asList("-", "+", "interrogative", "imperative", "expressive").contains(instance);
    }

    public boolean isLink() {
        return annotation.original != null;
    }

    public boolean isDeleted() {
        return annotation.delete;
    }

    public String getInstance() {
        return instance;
    }

    public String getClearedInstance() {
        return instance.replaceAll("-[0-9]+","");
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String toString() {
        return instance + (name.isEmpty()?"":" '"+name+"'") + "\n" + AmrFrame.toString(predictions) + (getPos() !=null?" {"+ getPos() +"}":"") + annotation.toString();
    }

    public String getPos() {
        return annotation.pos;
    }

    public void setPos(String pos) {
        annotation.pos = pos;
    }
}
