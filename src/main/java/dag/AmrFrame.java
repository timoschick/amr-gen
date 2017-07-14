package dag;

import com.mxgraph.layout.hierarchical.mxHierarchicalLayout;
import com.mxgraph.layout.mxCompactTreeLayout;
import com.mxgraph.layout.mxGraphLayout;
import com.mxgraph.swing.mxGraphComponent;
import com.mxgraph.util.mxConstants;
import com.mxgraph.view.mxGraph;
import com.mxgraph.view.mxStylesheet;
import misc.Debugger;
import gen.GoldTransitions;
import gen.PartialTransitionFunction;
import misc.PosHelper;
import ml.Prediction;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.text.DecimalFormat;
import java.util.*;
import java.util.List;

/**
 * This class can be used to graphically represent an AMR graph.
 */
public class AmrFrame extends JFrame {

    private List<Amr> amrs;
    private int index = 0;
    private JScrollPane scrPane;

    private static DecimalFormat df = new DecimalFormat("#.000");


    /**
     * Creates a new frame for a sequence of AMR graphs. This sequence can be browsed by pressing any key.
     * @param amrs the AMR graphs to show
     */
    public AmrFrame(List<Amr> amrs) {
        this.amrs = amrs;
        KeyboardFocusManager manager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        manager.addKeyEventDispatcher(new MyDispatcher());
        initialize();
    }

    /**
     * Creates a new frame showing a single AMR graph.
     * @param amr the AMR graph to show
     */
    public AmrFrame(Amr amr) {
        this.amrs = Collections.singletonList(amr);
        KeyboardFocusManager manager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        manager.addKeyEventDispatcher(new MyDispatcher());
        initialize();
    }

    private void next() {
        if(index < amrs.size()-1) {
            Debugger.println("loading next amr...");
            index++;
            initialize();
        }
    }

    private void initialize() {
        Amr amr = amrs.get(index);
        JPanel contentPane = new JPanel();
        contentPane.add(toMxGraph(amr));
        if(scrPane != null) {
            this.remove(scrPane);
        }
        scrPane = new JScrollPane(contentPane);
        this.add(scrPane);

        this.pack();
        this.setVisible(true);
    }

    private mxGraphComponent toMxGraph(Amr amr) {

        mxGraph graph = new mxGraph();

        graph.setHtmlLabels(true);
        Object parent = graph.getDefaultParent();

        mxStylesheet stylesheet = graph.getStylesheet();

        Hashtable<String, Object> nodeStyle = new Hashtable<>();
        nodeStyle.put(mxConstants.STYLE_FONTCOLOR, "#000000");
        nodeStyle.put(mxConstants.STYLE_FONTSTYLE, "1");
        nodeStyle.put(mxConstants.STYLE_FILLCOLOR, "#ffffff");
        nodeStyle.put(mxConstants.STYLE_FONTSIZE, "10");
        stylesheet.putCellStyle("NODE", nodeStyle);

        Hashtable<String, Object> edgeStyle = new Hashtable<>();
        edgeStyle.put(mxConstants.STYLE_FONTCOLOR, "#009900");
        edgeStyle.put(mxConstants.STYLE_FONTSTYLE, "1");
        stylesheet.putCellStyle("EDGE", edgeStyle);

        Hashtable<String, Object> instanceEdgeStyle = new Hashtable<>();
        instanceEdgeStyle.put(mxConstants.STYLE_STROKECOLOR, "#555555");
        instanceEdgeStyle.put(mxConstants.STYLE_FONTCOLOR, "#000099");
        instanceEdgeStyle.put(mxConstants.STYLE_OPACITY, 40);
        instanceEdgeStyle.put(mxConstants.STYLE_FONTSTYLE, "1");
        stylesheet.putCellStyle("INSTANCEEDGE", instanceEdgeStyle);

        Hashtable<String, Object> alignmentStyle = new Hashtable<>();
        alignmentStyle.put(mxConstants.STYLE_STROKECOLOR, "#990000");
        alignmentStyle.put(mxConstants.STYLE_OPACITY, 40);
        alignmentStyle.put(mxConstants.STYLE_DASHED, true);
        stylesheet.putCellStyle("ALIGNMENT", alignmentStyle);

        for(AlignmentType type: AlignmentType.values()) {
            Hashtable<String, Object> typeStyle = new Hashtable<>();
            typeStyle.put(mxConstants.STYLE_STROKECOLOR, AlignmentType.colors.getOrDefault(type, "#000000"));
            typeStyle.put(mxConstants.STYLE_OPACITY, 100);
            typeStyle.put(mxConstants.STYLE_DASHED, true);
            stylesheet.putCellStyle(type.name(), typeStyle);
        }

        Hashtable<String, Object> linkStyle = new Hashtable<>();
        linkStyle.put(mxConstants.STYLE_STROKECOLOR, "#550055");
        linkStyle.put(mxConstants.STYLE_OPACITY, 40);
        linkStyle.put(mxConstants.STYLE_DASHED, true);
        stylesheet.putCellStyle("LINK", linkStyle);

        Hashtable<String, Object> invisibleStyle = new Hashtable<>();
        invisibleStyle.put(mxConstants.STYLE_STROKEWIDTH, 0);
        invisibleStyle.put(mxConstants.STYLE_OPACITY, 0);
        stylesheet.putCellStyle("INVISIBLE", invisibleStyle);

        Map<Vertex,Object> vertexToObjMap = new HashMap<>();
        Map<Edge,Object> edgeToObjMap = new HashMap<>();
        Map<Integer,Object> stringIndexToObjMap = new HashMap<>();

        graph.setHtmlLabels(true);
        graph.getModel().beginUpdate();
        try {
            for (Vertex vertex : amr.dag) {
                Object vObj = graph.insertVertex(parent, null, toHtmlString(amr, vertex), 0, 0, 80, 60, "NODE");
                vertexToObjMap.put(vertex, vObj);
            }

            for(Vertex vertex: amr.dag) {
                for(Edge edge: vertex.outgoingEdges) {
                    if(edge.getTo().equals(Vertex.EMPTY_VERTEX)) {
                        Object instanceNode = graph.insertVertex(parent, null, " ", 0,0,1,1, "INVISIBLE");
                        Object eObj = graph.insertEdge(parent, null, edge.label, vertexToObjMap.get(edge.from), instanceNode, "INSTANCEEDGE");
                        edgeToObjMap.put(edge, eObj);
                    }
                    else {
                        Object eObj = graph.insertEdge(parent, null, edge.label, vertexToObjMap.get(edge.from), vertexToObjMap.get(edge.getTo()), "EDGE");
                        edgeToObjMap.put(edge, eObj);
                    }
                }
            }
        }
        finally { graph.getModel().endUpdate(); }

        mxGraphLayout layout;
        if(amr.dag.isTree()) {
            layout = new mxCompactTreeLayout(graph,false);
        }
        else {
            layout = new mxHierarchicalLayout(graph);
        }

        layout.execute(graph.getDefaultParent());

        // add links to original vertices
        graph.getModel().beginUpdate();
        try {

            for(Vertex vertex: amr.dag) {
                if(vertex.isLink()) {
                    graph.insertEdge(parent, null, "", vertexToObjMap.get(vertex), vertexToObjMap.get(vertex.annotation.original), "LINK");
                }
            }

        }
        finally { graph.getModel().endUpdate(); }

        if(amr.sentence != null && amr.sentence.length > 0) {

            graph.getModel().beginUpdate();
            try {
                // add string
                double heightOffset = graph.getGraphBounds().getHeight() + 80;
                double xPadding = 2;

                graph.insertVertex(parent, null, "", -30, heightOffset, 1, 1);
                graph.insertVertex(parent, null, "", amr.sentence.length * (45 + xPadding) + 20, heightOffset, 1,1);

                for (int i = 0; i < amr.sentence.length; i++) {
                    Object edgeObj = graph.insertVertex(parent, null, amr.sentence[i], i * (45 + xPadding), heightOffset, 45, 30, "NODE");
                    if(amr.pos != null) {
                        Object posObj = graph.insertVertex(parent, null, amr.pos[i], i * (45 + xPadding), heightOffset + 40, 45, 30, "NODE");
                    }
                    stringIndexToObjMap.put(i, edgeObj);
                }
            } finally {
                graph.getModel().endUpdate();
            }

            graph.getModel().beginUpdate();
            try {
                // add alignment
                for(Edge edge: amr.alignment.keySet()) {
                    Set<Integer> stringIndices = amr.alignment.get(edge);
                    for(int stringIndex: stringIndices) {
                        graph.insertEdge(parent, null, "", edgeToObjMap.get(edge), stringIndexToObjMap.get(stringIndex), "ALIGNMENT");
                    }
                }

                for(EdgeTypePair eat: amr.typeAlignment.keySet()) {

                    if(!AlignmentType.filter.contains(eat.type)) continue;

                    Edge edge = eat.e;
                    Set<Integer> stringIndices = amr.typeAlignment.get(eat);

                    String style = eat.type.name();


                    for(int stringIndex: stringIndices) {
                        graph.insertEdge(parent, null, "", edgeToObjMap.get(edge), stringIndexToObjMap.get(stringIndex), style);
                    }
                }

            } finally {
                graph.getModel().endUpdate();
            }
        }

        graph.setBorder(0);
        mxGraphComponent graphComponent = new mxGraphComponent(graph);
        graphComponent.setEnabled(false);
        graphComponent.setBorder(null);
        graphComponent.setRequestFocusEnabled(false);
        return graphComponent;

    }

    /**
     * Returns a HTML representation of a vertex and the modifications applied to it according to some partial transition function
     * @param amr the corresponding AMR graph
     * @param v the vertex
     * @param ptf the partial transition function
     * @return the HTML representation
     */
    private String toHtmlString(Amr amr, Vertex v, PartialTransitionFunction ptf) {
        StringBuilder ret =  new StringBuilder();
        ret.append("<html>");

        if(ptf.beforeIns.containsKey(v)) {
            if(!ptf.beforeIns.get(v).isEmpty()) {
                ret.append("[").append(ptf.beforeIns.get(v)).append("] ");
            }
        }
        if(ptf.denominator.containsKey(v)) {
            if(!ptf.denominator.get(v).isEmpty()) {
                ret.append("[").append(ptf.denominator.get(v)).append("] ");
            }
        }

        ret.append("<b>").append(ptf.realization.getOrDefault(v, "-")).append("</b>");

        if(ptf.afterIns.containsKey(v)) {
            if(!ptf.afterIns.get(v).isEmpty()) {
                ret.append(" [").append(ptf.afterIns.get(v)).append("]");
            }
        }

        if(ptf.punctuation.containsKey(v)) {
            if(!ptf.punctuation.get(v).isEmpty()) {
                ret.append(" [").append(ptf.punctuation.get(v)).append("]");
            }
        }

        String denom = ptf.denominator.getOrDefault(v, "-");
        if(denom.isEmpty()) denom ="-";

        ret.append("<br/>");
        ret.append("POS: ").append(ptf.pos.getOrDefault(v, "-")).append(",");
        ret.append("NUM: ").append(ptf.number.getOrDefault(v, "-")).append("<br/>");
        ret.append("TNS: ").append(ptf.tense.getOrDefault(v, "-").replace("no_tense", "-")).append(", ");
        ret.append("VOI: ").append(ptf.voice.getOrDefault(v, "-")).append(", ");
        ret.append("DEN: ").append(denom);

        if(ptf.reordering.containsKey(v)) {
            List<Edge> reordering = ptf.reordering.get(v);
            List<String> indices = new ArrayList<>();
            for(Edge e: reordering) {
                indices.add(v.getOutgoingEdges().indexOf(e)+"");
            }
            ret.append("<br/>");
            ret.append("(").append(String.join(",", indices)).append(")");
        }

        ret.append("</html>");
        return ret.toString();
    }

    /**
     * Returns a HTML representation of a vertex.
     * @param amr the corresponding AMR graph
     * @param v the vertex
     * @return the HTML representation
     */
    private String toHtmlString(Amr amr, Vertex v) {

        if(amr.partialTransitionFunction != null) {
            return toHtmlString(amr, v, amr.partialTransitionFunction);
        }

        String swapResult = "";

        try{
            swapResult = GoldTransitions.getGoldSwap(amr, v)?"(swap)":"";
        }
        catch(Exception e) {}

        return "<html>" +
                "<b>" + v.instance + "</b>" + (v.name.isEmpty()?"":" '"+v.name+"'") +
                //"<br/>" + toString(v.predictions) +
                ((v.getPos() !=null && !v.isLink() && !v.getPos().equals(PosHelper.POS_ANY) )?"<br/>POS: "+ v.getPos() +"":"") +
                "<br/>" + "span: " + ((amr.span != null && amr.span.containsKey(v.getInstanceEdge()))?(amr.span.get(v.getInstanceEdge()).min + "-" + amr.span.get(v.getInstanceEdge()).max):"-") +
                "</html>";
    }

    /**
     * Returns a String representation of a map of prediction keys and corresponding predictions.
     * @param predictions the prediction map
     * @return the String representation
     */
    public static String toString(Map<String, List<Prediction>> predictions) {
        StringBuilder builder = new StringBuilder();

        for (String key : predictions.keySet()) {
            if (key.equals("realization")) continue;
            builder.append(key + "= {");
            for (Prediction p : predictions.get(key)) {
                builder.append(p.getValue());
                builder.append(":");
                builder.append(df.format(p.getScore()));
                builder.append(", ");
            }
            builder.append("}, ");

        }
        return builder.toString();
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

