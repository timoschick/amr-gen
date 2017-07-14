package dag;

import com.mxgraph.layout.hierarchical.mxHierarchicalLayout;
import com.mxgraph.layout.mxCompactTreeLayout;
import com.mxgraph.layout.mxGraphLayout;
import com.mxgraph.swing.mxGraphComponent;
import com.mxgraph.util.mxConstants;
import com.mxgraph.view.mxGraph;
import com.mxgraph.view.mxStylesheet;
import misc.Debugger;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.*;
import java.util.List;

/**
 * This class can be used to graphically represent a dependency tree.
 */
public class DependencyTreeFrame extends JFrame {

    private List<DependencyTree> trees;
    private int index = 0;
    private JScrollPane scrPane;

    /**
     * Creates a new frame for a sequence of dependency trees. This sequence can be browsed by pressing any key.
     * @param trees the dependency trees to show
     */
    public DependencyTreeFrame(List<DependencyTree> trees) {
        this.trees = trees;
        KeyboardFocusManager manager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        manager.addKeyEventDispatcher(new MyDispatcher());
        initialize();
    }

    /**
     * Creates a new frame showing a single dependency tree.
     * @param tree the dependency tree to show
     */
    public DependencyTreeFrame(DependencyTree tree) {
        this.trees = Collections.singletonList(tree);
        KeyboardFocusManager manager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        manager.addKeyEventDispatcher(new MyDispatcher());
        initialize();
    }

    private void next() {
        if(index < trees.size()-1) {
            Debugger.println("loading next amr...");
            index++;
            initialize();
        }
    }

    private void initialize() {
        DependencyTree tree = trees.get(index);
        JPanel contentPane = new JPanel();
        contentPane.add(toMxGraph(tree));
        if(scrPane != null) {
            this.remove(scrPane);
        }
        scrPane = new JScrollPane(contentPane);
        this.add(scrPane);
        this.pack();
        this.setVisible(true);
    }

    private mxGraphComponent toMxGraph(DependencyTree tree) {

        mxGraph graph = new mxGraph();
        Object parent = graph.getDefaultParent();

        mxStylesheet stylesheet = graph.getStylesheet();

        Hashtable<String, Object> nodeStyle = new Hashtable<>();
        nodeStyle.put(mxConstants.STYLE_FONTCOLOR, "#000000");
        nodeStyle.put(mxConstants.STYLE_FONTSTYLE, "1");
        nodeStyle.put(mxConstants.STYLE_FILLCOLOR, "#ffffff");
        nodeStyle.put(mxConstants.STYLE_FONTSIZE, "10");
        stylesheet.putCellStyle("NODE", nodeStyle);

        Hashtable<String, Object> unalignedNodeStyle = new Hashtable<>();
        unalignedNodeStyle.putAll(nodeStyle);
        unalignedNodeStyle.put(mxConstants.STYLE_FILLCOLOR, "#99FFFF");
        stylesheet.putCellStyle("UNALIGNED_NODE", unalignedNodeStyle);

        Hashtable<String, Object> edgeStyle = new Hashtable<>();
        edgeStyle.put(mxConstants.STYLE_FONTCOLOR, "#009900");
        edgeStyle.put(mxConstants.STYLE_FONTSTYLE, "1");
        stylesheet.putCellStyle("EDGE", edgeStyle);

        Hashtable<String, Object> alignmentStyle = new Hashtable<>();
        alignmentStyle.put(mxConstants.STYLE_STROKECOLOR, "#990000");
        alignmentStyle.put(mxConstants.STYLE_OPACITY, 40);
        alignmentStyle.put(mxConstants.STYLE_DASHED, true);
        stylesheet.putCellStyle("ALIGNMENT", alignmentStyle);

        Map<Vertex,Object> vertexToObjMap = new HashMap<>();
        Map<Edge,Object> edgeToObjMap = new HashMap<>();
        Map<Integer,Object> stringIndexToObjMap = new HashMap<>();

        Set<Integer> alignedWords = new HashSet<>();

        if(tree.amr != null) {
            tree.amr.alignment.values().forEach(s -> alignedWords.addAll(s));
        }

        graph.setHtmlLabels(true);
        graph.getModel().beginUpdate();
        try {
            for (Vertex vertex : tree.tree) {

                String style = "NODE";

                if(!alignedWords.contains(tree.alignment.get(vertex))) {
                    style = "UNALIGNED_NODE";
                }

                Object vObj = graph.insertVertex(parent, null, vertex.getInstance(), 0, 0, 45, 38, style);
                vertexToObjMap.put(vertex, vObj);
            }

            for(Vertex vertex: tree.tree) {
                for(Edge edge: vertex.outgoingEdges) {
                    Object eObj = graph.insertEdge(parent, null, edge.label, vertexToObjMap.get(edge.from), vertexToObjMap.get(edge.getTo()), "EDGE");
                    edgeToObjMap.put(edge, eObj);
                }
            }
        }
        finally { graph.getModel().endUpdate(); }

        mxGraphLayout layout;
        if(tree.tree.isTree()) {
            layout = new mxCompactTreeLayout(graph,false);
        }
        else {
            layout = new mxHierarchicalLayout(graph);
        }

        layout.execute(graph.getDefaultParent());

        graph.getModel().beginUpdate();
        try {
            // add string
            double heightOffset = graph.getGraphBounds().getHeight() + 80;
            double xPadding = 2;

            graph.insertVertex(parent, null, "", -30, heightOffset, 1, 1);
            graph.insertVertex(parent, null, "", tree.sentence.length * (45 + xPadding) + 20, heightOffset, 1,1);

            for (int i = 0; i < tree.sentence.length; i++) {
                Object edgeObj = graph.insertVertex(parent, null, tree.sentence[i], i * (45 + xPadding), heightOffset, 45, 30, "NODE");
                stringIndexToObjMap.put(i, edgeObj);
            }
        } finally {
            graph.getModel().endUpdate();
        }

        graph.getModel().beginUpdate();
        try {
            // add alignment
            for(Vertex vertex: tree.alignment.keySet()) {
                int stringIndex = tree.alignment.get(vertex);
                graph.insertEdge(parent, null, "", vertexToObjMap.get(vertex), stringIndexToObjMap.get(stringIndex), "ALIGNMENT");
            }

        } finally {
            graph.getModel().endUpdate();
        }

        graph.setBorder(0);
        mxGraphComponent graphComponent = new mxGraphComponent(graph);
        graphComponent.setEnabled(false);
        graphComponent.setBorder(null);
        graphComponent.setRequestFocusEnabled(false);
        return graphComponent;

    }

    private class MyDispatcher implements KeyEventDispatcher {
        @Override
        public boolean dispatchKeyEvent(KeyEvent e) {
            if (e.getID() == KeyEvent.KEY_RELEASED) {
                next();
            }
            return false;
        }
    }

}

