package dag;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This class can be used to parse dependency trees stored in CoNLL format.
 */
public class DependencyTreeParser {

    private static final String ROOT_ID = "ROOT-0";
    private static final Pattern edgePattern = Pattern.compile("([^(]+)\\(([^,]+), ([^)]+)\\)");
    private static Matcher edgeMatcher = edgePattern.matcher("");

    private Map<String,Vertex> nodeMap;
    private Map<Vertex,Integer> alignment;
    private String[] sentence;
    private Vertex root;

    /**
     * Turns a list of edge specifications in CoNLL format and a sentence into a {@link DependencyTree}.
     * @param edgeSpecs the list of edge specifications
     * @param sentence the sentence, represented as an array of words
     * @return the corresponding dependency tree
     */
    public DependencyTree fromString(List<String> edgeSpecs, String[] sentence) {

        setUp(sentence);

        for(String edgeSpec: edgeSpecs) {
            addEdge(edgeSpec, nodeMap);
        }

        DirectedGraph directedGraph = new DirectedGraph();
        directedGraph.root = root;

        DependencyTree ret = new DependencyTree();
        ret.tree = directedGraph;
        ret.sentence = sentence;
        ret.alignment = alignment;

        for(Vertex node: ret.tree) {
            node.instance = node.instance.substring(0, node.instance.lastIndexOf("-"));
        }

        return ret;
    }

    private void setUp(String[] sentence) {
        this.sentence = sentence;
        nodeMap = new HashMap<>();
        alignment = new HashMap<>();
        root = null;
    }

    private void addEdge(String edgeSpec, Map<String,Vertex> nodeMap) {

        edgeMatcher.reset(edgeSpec);
        if (edgeMatcher.find())
        {
            String edgeLabel = edgeMatcher.group(1);
            String from = edgeMatcher.group(2);
            String to = edgeMatcher.group(3);

            registerNode(from);
            registerNode(to);

            if(!from.equals(ROOT_ID)) {
                new Edge(nodeMap.get(from), nodeMap.get(to), edgeLabel);
            }
            else {
                root = nodeMap.get(to);
            }
        }

    }

    private void registerNode(String nodeSpec) {

        if(nodeSpec.equals(ROOT_ID)) return;

        if(!nodeMap.containsKey(nodeSpec)) {
            nodeMap.put(nodeSpec, new Vertex(nodeSpec));
            int alignmentId = Integer.valueOf(nodeSpec.substring(nodeSpec.lastIndexOf("-")+1))-1;
            if(sentence.length <= alignmentId) {
                throw new AssertionError("Error in parsing the dependency tree. Found alignment id " + alignmentId + " but sentence length is only " + sentence.length);
            }
            else {
                alignment.put(nodeMap.get(nodeSpec), alignmentId);
            }
        }
    }
}
