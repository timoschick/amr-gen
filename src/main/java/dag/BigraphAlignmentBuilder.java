package dag;

import misc.WordLists;
import misc.StaticHelper;

import java.util.*;
import java.util.stream.Collectors;

/**
 * This class can be used to build type alignments (i.e. alignments for e.g. articles, INSERT-CHILD and INSERT-BETWEEN transitions)
 */
public class BigraphAlignmentBuilder {

    private BABState state;

    /**
     * Builds type alignments from a bigraph, represented by the corresponding AMR graph and the corresponding dependency tree.
     * The resulting alignment is stored in {@link Amr#typeAlignment}.
     * @param amr the AMR graph
     * @param tree the dependency tree
     */
    public void buildTypeAlignment(Amr amr, DependencyTree tree) {

        state = new BABState();
        state.amr = amr;
        state.tree = tree;
        state.unalignedIndices = new HashSet<>();
        for(int i=0; i < amr.sentence.length; i++) {
            state.unalignedIndices.add(i);
        }
        amr.alignment.remove(null);
        amr.alignment.values().forEach(a -> state.unalignedIndices.removeAll(a));

        if(tree != null && tree.alignment != null) {
            for (Vertex v : tree.alignment.keySet()) {
                state.treeMap.put(tree.alignment.get(v), v);
            }
        }

        for(Edge e: amr.alignment.keySet()) {
            if(e.isInstanceEdge()) {
                for(int alignmentIndex: amr.alignment.get(e)) {
                    if(!state.amrMap.containsKey(alignmentIndex)) {
                        state.amrMap.put(alignmentIndex, new HashSet<>());
                    }
                    state.amrMap.get(alignmentIndex).add(e.getFrom());
                }
            }
        }

        if(tree != null && tree.alignment != null) {
            buildTypeAlignmentFromDependencyTree();
        }
        state.amr.typeAlignment.putAll(state.typeAlignment);
    }

    private void buildTypeAlignmentFromDependencyTree() {

        // align articles
        for(Vertex depVertex: state.tree.tree) {
            if(WordLists.articles.contains(depVertex.getInstance().toLowerCase())) {
                if (depVertex.getIncomingEdges().size() > 0) {
                    Vertex depParent = depVertex.getIncomingEdges().get(0).getFrom();

                    for (Vertex amrVertex : getVerticesInAmr(depParent)) {

                        if(amrVertex.getInstanceEdge() == null) {
                            continue;
                        }

                        EdgeTypePair eat = new EdgeTypePair(amrVertex.getInstanceEdge(), AlignmentType.DT_ARTICLE);
                        if (!state.typeAlignment.containsKey(eat)) {
                            state.typeAlignment.put(eat, new HashSet<>());
                        }
                        state.unalignedIndices.remove(state.tree.alignment.get(depVertex));
                        state.typeAlignment.get(eat).add(state.tree.alignment.get(depVertex));
                    }
                }
            }
        }

        // align child- and between-insertions
        nextVertex: for (Vertex v : state.tree.tree) {
            // check if this node needs to be aligned
            if (state.unalignedIndices.contains(state.tree.alignment.get(v))) {

                boolean beforeInsertionCandidate = WordLists.beforeInsertableWords.contains(v.getInstance().toLowerCase());
                boolean afterInsertionCandidate = WordLists.afterInsertableWords.contains(v.getInstance().toLowerCase());

                final Vertex parent, grandparent;
                List<Vertex> children, grandchildren, neighbours, parentNeighbours;
                Set<Vertex> amrParents, amrGrandparents, amrChildren, amrGrandchildren, amrNeighbours, amrParentNeighbours;

                // calculate some properties of the vertex
                if(!v.getIncomingEdges().isEmpty()) {
                    parent = v.getIncomingEdges().get(0).getFrom();
                }
                else {
                    parent = null;
                }

                if(parent != null) {
                    amrParents = getVerticesInAmr(parent);
                    if(!parent.getIncomingEdges().isEmpty()) {
                        grandparent = parent.getIncomingEdges().get(0).getFrom();
                        amrGrandparents = getVerticesInAmr(grandparent);
                        parentNeighbours = grandparent.getOutgoingEdges().stream().filter(e -> e.getTo() != parent && !e.isInstanceEdge()).map(Edge::getTo).collect(Collectors.toList());
                        amrParentNeighbours = getVerticesInAmr(parentNeighbours);
                    }
                    else {
                        grandparent = null;
                        amrGrandparents = Collections.emptySet();
                        amrParentNeighbours = Collections.emptySet();
                    }

                    neighbours = parent.getOutgoingEdges().stream().filter(e -> e.getTo() != v && !e.isInstanceEdge()).map(Edge::getTo).collect(Collectors.toList());
                    amrNeighbours = getVerticesInAmr(neighbours);

                }
                else {
                    grandparent = null;
                    amrParents = Collections.emptySet();
                    amrGrandparents = Collections.emptySet();
                    amrNeighbours = Collections.emptySet();
                    amrParentNeighbours = Collections.emptySet();
                }

                children = v.getOutgoingEdges().stream().map(Edge::getTo).collect(Collectors.toList());
                amrChildren = getVerticesInAmr(children);

                grandchildren = new ArrayList<>();
                children.forEach(c -> grandchildren.addAll(c.getOutgoingEdges().stream().map(Edge::getTo).collect(Collectors.toList())));
                amrGrandchildren = getVerticesInAmr(grandchildren);

                // if the vertex has a parent and a child, try to align it to the parent->child edge
                if (parent != null) {

                    if(beforeInsertionCandidate || afterInsertionCandidate) {

                        if (!children.isEmpty()) {

                            // check if the parent and some child of v are also in a parent-child relationship in the amr
                            if (checkForCorrespondenceAndAlign(v, amrParents, amrChildren)) continue;

                            // check if the parent and some grandchild of v are in a parent-child relationship in the amr
                            if (checkForCorrespondenceAndAlign(v, amrParents, amrGrandchildren)) continue;

                            // check if the relationship is swapped in the amr
                            if (checkForCorrespondenceAndAlign(v, amrChildren, amrParents)) continue;

                            // check if some grandparent and some child of v are in a parent-child relationship in the amr
                            if (grandparent != null) {
                                if (checkForCorrespondenceAndAlign(v, amrGrandparents, amrChildren)) continue;
                                if (checkForCorrespondenceAndAlign(v, amrGrandparents, amrGrandchildren)) continue;
                            }

                            // check if some neighbour and some child of v are in a parent-child relationship in the amr
                            if (checkForCorrespondenceAndAlign(v, amrNeighbours, amrChildren)) continue;

                            // check if some neighbour of a parent of v and some child of v are in a parent-child relationship in the amr
                            if (grandparent != null) {
                                if (checkForCorrespondenceAndAlign(v, amrParentNeighbours, amrChildren)) continue;
                            }

                            // if a child of v is connected to the root and the root has no alignment, we assign the insertion to the edge root->child
                            Vertex root = state.amr.dag.getRoot();
                            if (!state.amr.alignment.containsKey(root.getInstanceEdge()) || root.getPos().equals("CC")) {
                                if (checkForCorrespondenceAndAlign(v, Collections.singleton(root), amrChildren))
                                    continue;
                            }

                        }

                        // if the vertex has a parent and a grandparent but no child
                        else if (!parent.getIncomingEdges().isEmpty()) {
                            // the parent of the vertex is now the new parent, e.g. (want(see(to)) -> see ist child, want ist parent
                            if (checkForCorrespondenceAndAlign(v, amrGrandparents, amrParents)) continue;

                            // check if the relationship is swapped in the amr
                            if (checkForCorrespondenceAndAlign(v, amrParents, amrGrandparents)) continue;

                            if (checkForCorrespondenceAndAlign(v, amrGrandparents, amrNeighbours)) continue;
                            if (checkForCorrespondenceAndAlign(v, amrParentNeighbours, amrParents)) continue;

                        }
                    }

                    // make a child insertion

                    if(WordLists.articles.contains(v.getInstance())) {
                        // articles are handled separately
                        continue;
                    }

                    int parentPosition = state.tree.alignment.get(parent);
                    int insertionPosition = state.tree.alignment.get(v);

                    AlignmentType type = (parentPosition < insertionPosition)?AlignmentType.DT_CHILD_INSERTION_RIGHT:AlignmentType.DT_CHILD_INSERTION_LEFT;

                    nextParent: for(Vertex p: amrParents) {

                        if(!p.getIncomingEdges().isEmpty()) {
                            if(p.getIncomingEdges().get(0).getLabel().equals(":domain")) continue;
                        }
                        for(Edge e: p.getOutgoingEdges()) {
                            if(e.getLabel().equals(":domain")) continue nextParent;
                        }

                        if(p.getInstanceEdge() != null) {
                            EdgeTypePair eat = new EdgeTypePair(p.getInstanceEdge(), type);
                            if (!state.typeAlignment.containsKey(eat)) {
                                state.typeAlignment.put(eat, new HashSet<>());
                            }
                            state.unalignedIndices.remove(state.tree.alignment.get(v));
                            state.typeAlignment.get(eat).add(state.tree.alignment.get(v));
                            continue nextVertex;
                        }
                    }

                }
            }
        }
    }

    /**
     * Checks if two collections of vertices contain a parent-child pair. If so, the function alignes the unaligned vertex {@code unalignedVertex} to this connection.
     * @param unalignedVertex the unaligned vertex
     * @param amrParentCandidates the parent candidates
     * @param amrChildrenCandidates the child candidates
     * @return true iff a parent-child pair was found
     */
    private boolean checkForCorrespondenceAndAlign(Vertex unalignedVertex, Collection<Vertex> amrParentCandidates, Collection<Vertex> amrChildrenCandidates) {
        for(Vertex amrParent: amrParentCandidates) {

            // if e.getTo() has no alignment or POS tag CC (and/or/...), we consider all of its children instead
            List<Edge> edgesToCheck = amrParent.getOutgoingEdges().stream().filter(e -> !e.isInstanceEdge()).collect(Collectors.toList());
            List<Edge> addables = new ArrayList<>();
            Map<Edge,Edge> originals = new HashMap<>();
            for(Edge e: edgesToCheck) {

                if(!state.amr.alignment.containsKey(e.getTo().getInstanceEdge()) || (e.getTo().getPos() != null && e.getTo().getPos().equals("CC"))) {
                    for(Edge outEdge: e.getTo().getOutgoingEdges()) {
                        if(e.getTo().getPos().equals("CC") && !outEdge.getLabel().startsWith(":op")) continue;
                        addables.add(outEdge);
                        originals.put(outEdge, e);
                    }

                }
            }
            edgesToCheck.addAll(addables);

            for(Edge e: edgesToCheck) {
                Edge alignmentEdge = originals.getOrDefault(e,e);
                if(e.isInstanceEdge() || WordLists.NO_ALIGNMENT_EDGES.contains(alignmentEdge.getLabel())) continue;
                if(WordLists.INSERTION_CONSTRAINTS.containsKey(unalignedVertex.getInstance())) {
                    if(!WordLists.INSERTION_CONSTRAINTS.get(unalignedVertex.getInstance()).contains(alignmentEdge.getLabel())) continue;
                }

                if(amrChildrenCandidates.contains(e.getTo()) || (e.getTo().isLink() && amrChildrenCandidates.contains(e.getTo().annotation.original))) {

                    // check whether the insertion is left or right
                    Vertex original = e.getTo().isLink()?e.getTo().annotation.original:e.getTo();
                    AlignmentType alignmentType;

                    int insertionPos = state.tree.alignment.get(unalignedVertex);
                    double toPos;
                    try {
                        toPos = StaticHelper.median(state.amr.alignment.get(original.getInstanceEdge()));
                    }
                    catch (NullPointerException ex) {
                        toPos = 0;
                    }
                    double fromPos = 0;
                    if(state.amr.alignment.containsKey(e.getFrom().getInstanceEdge())) {
                        fromPos = StaticHelper.median(state.amr.alignment.get(e.getFrom().getInstanceEdge()));
                    }

                    double val = insertionPos - toPos;
                    double valFrom = insertionPos - fromPos;
                    if(val < 0) alignmentType = AlignmentType.DT_HEAD_INSERTION_LEFT;
                    else alignmentType = AlignmentType.DT_HEAD_INSERTION_RIGHT;

                    // check whether the insertion is actually in between the two considered words
                    if(fromPos != 0 && ((val > 0 && valFrom > 0) || (val < 0 && valFrom < 0))) continue;

                    if(alignmentType == AlignmentType.DT_HEAD_INSERTION_LEFT && !WordLists.beforeInsertableWords.contains(unalignedVertex.getInstance())) continue;

                    // align the nodes
                    EdgeTypePair eat = new EdgeTypePair(alignmentEdge, alignmentType);
                    if(!state.typeAlignment.containsKey(eat)) {
                        state.typeAlignment.put(eat, new HashSet<>());
                    }
                    state.unalignedIndices.remove(state.tree.alignment.get(unalignedVertex));
                    state.typeAlignment.get(eat).add(state.tree.alignment.get(unalignedVertex));
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * This is an implementation of the bigraph mapping Pi_B^1 as defined in the thesis, i.e. it returns all vertices in the AMR graph that correspond to a vertex of the dependency tree.
     * @param dependencyTreeVertex the dependency tree vertex
     * @return the set of corresponding AMR vertices
     */
    private Set<Vertex> getVerticesInAmr(Vertex dependencyTreeVertex) {
        return state.amrMap.getOrDefault(state.tree.alignment.get(dependencyTreeVertex), new HashSet<>());
    }

    /**
     * This is an implementation of the bigraph mapping Pi_B^2 as defined in the thesis, i.e. it returns all vertices in the dependency tree that correspond to a vertex of the AMR graph.
     * @param amrVertex the AMR vertex
     * @return the set of corresponding dependency tree vertices
     */
    private Set<Vertex> getVerticesInDependencyTree(Vertex amrVertex) {
        Set<Vertex> ret = new HashSet<>();
        for(int i: state.amr.alignment.get(amrVertex.getInstanceEdge())) {
            ret.add(state.treeMap.get(i));
        }
        return ret;
    }

    /**
     * A generalisation of {@link BigraphAlignmentBuilder#getVerticesInAmr(Vertex)} that supports a collection of dependency tree vertices.
     * @param dependencyTreeVertices the collection of dependency tree vertices
     * @return the set of corresponding AMR vertices
     */
    private Set<Vertex> getVerticesInAmr(Collection<Vertex> dependencyTreeVertices) {
        Set<Vertex> ret = new HashSet<>();
        for(Vertex dtv: dependencyTreeVertices) {
            ret.addAll(getVerticesInAmr(dtv));
        }
        return ret;
    }

    private class BABState {
        Amr amr;
        DependencyTree tree;
        Set<Integer> unalignedIndices = new HashSet<>();
        Map<Integer,Vertex> treeMap = new HashMap<>();
        Map<Integer,Set<Vertex>> amrMap = new HashMap<>();
        Map<EdgeTypePair,Set<Integer>> typeAlignment = new HashMap<>();
    }

}
