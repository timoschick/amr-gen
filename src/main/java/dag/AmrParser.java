package dag;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Static class that provides functions for loading AMR graphs or additional information for AMR graphs from a file.
 */
public class AmrParser {

    private static final Pattern NAMED_VERTEX_PATTERN = Pattern.compile("\\(([^)]*?) / (.*?)[\\)\\s:]");
    private static final Pattern UNNAMED_NUMBER_PATTERN = Pattern.compile(":[^ ]+ ([0-9,\\.\\-\\+]+)(~e.[0-9,]+)?[ )]");
    private static final Pattern UNNAMED_WORD_PATTERN = Pattern.compile(":[^ ]+ (\"[^\"]+\")(~e.[0-9,]+)?[ )]");
    private static final Pattern UNNAMED_WORD_ALTERNATIVE_PATTERN = Pattern.compile(":[^ ]+ ([A-z][A-z][A-z][A-z]+)(~e.[0-9,]+)?[ )]");
    private static final Pattern ALIGNMENT_PATTERN = Pattern.compile("(.*?)~e\\.([0-9,]+)");

    // non-instantiable class
    private AmrParser() {}

    /**
     * Adds POS tags to each reference sentence contained within a list of AMR graphs.
     * @param amrs the list of AMR graphs
     * @param posTagFile the file in which the POS tags are stored. POS tags must be tab separated with one sentence per line
     *                   and the order of the sentences must be the same as in the list {@code amrs}.
     */
    public static void addPosTags(List<Amr> amrs, String posTagFile) throws IOException {
        try(BufferedReader br = new BufferedReader(new FileReader(posTagFile))) {
            int index=0;
            for(String line; (line = br.readLine()) != null; ) {
                if(amrs.size() <= index) return;
                amrs.get(index).posFromString(line);
                index++;
            }
        }
    }

    /**
     * Adds the corresponding dependency tree to each reference sentence contained within a list of AMR graphs.
     * @param amrs the list of AMR graphs
     * @param dependencyTreeFile the file in which the dependency trees are stored in CoNLL format.
     */
    public static void addDependencyTrees(List<Amr> amrs, String dependencyTreeFile) throws IOException {

        DependencyTreeParser parser = new DependencyTreeParser();

        try(BufferedReader br = new BufferedReader(new FileReader(dependencyTreeFile))) {
            int index=0;
            List<String> edgeSpec = new ArrayList<>();

            for(String line; (line = br.readLine()) != null; ) {
                if(amrs.size() <= index) return;

                if(!line.isEmpty()) {
                    edgeSpec.add(line);
                }
                else {
                    if(!edgeSpec.isEmpty()) {
                        amrs.get(index).dependencyTree = parser.fromString(edgeSpec, amrs.get(index).sentence);
                        amrs.get(index).dependencyTree.amr = amrs.get(index);
                        edgeSpec.clear();
                        do {
                            index++;
                        } while(index < amrs.size() && String.join(" ",amrs.get(index).sentence).equals("."));

                    }
                }
            }
        }
    }

    /**
     * Reads a list of AMR graphs from a file.
     * @param file the file in which the AMR graphs are stored in LDC2014T12 format. To also load the reference sentences, each AMR graph must be
     *             annotated with a # ::tok line containing a tokenized version of the reference sentence.
     * @param dependencyTreeFile the file in which the corresponding dependency trees are stored, see {@link AmrParser#addDependencyTrees(List, String)}
     * @param posTagFile the file in which the corresponding POS tags are stored, see {@link AmrParser#addPosTags(List, String)}
     * @param limit the maximum number of AMR graphs to load. Set this to some value &lt; 0 to load all AMR graphs found in the file.
     * @param format the format in which alignments are stored, see {@link AmrLineFormat}
     * @return the list of AMR graphs extracted from {@code file}, along with the dependency trees and POS tags of each reference sentence
     */
    public static List<Amr> fromFile(String file, String dependencyTreeFile, String posTagFile, int limit, AmrLineFormat format) throws IOException {

        List<Amr> amrs = new ArrayList<>();

        boolean collectingDagData = false;
        StringBuilder dagReprBuilder = new StringBuilder("");

        String[] currentSentence = null;
        String alignmentLine = null;

        try(BufferedReader br = new BufferedReader(new FileReader(file))) {
            for(String line; (line = br.readLine()) != null; ) {

                if(line.startsWith("# ::tok ")) {
                    currentSentence = getSentence(line);
                }
                else if(line.startsWith("# ::alignments ")) {
                    alignmentLine = line;
                }

                if(!collectingDagData) {
                    if(line.startsWith("(")) collectingDagData = true;
                }

                if(collectingDagData) {
                    if(!line.trim().isEmpty()) {
                        dagReprBuilder.append(line);
                    }
                    else {

                        Amr amr = fromString(dagReprBuilder.toString(), currentSentence);
                        if(alignmentLine != null && format != null) {
                            addAlignmentFromLine(amr, alignmentLine, format);
                            alignmentLine = null;
                        }
                        amrs.add(amr);
                        if(amrs.size() == limit) {
                            break;
                        }

                        collectingDagData = false;
                        dagReprBuilder.setLength(0);
                        currentSentence = null;
                    }
                }

            }
        }

        if(posTagFile != null) {
            addPosTags(amrs, posTagFile);
        }
        if(dependencyTreeFile != null) {
            addDependencyTrees(amrs, dependencyTreeFile);
        }
        return amrs;
    }

    /**
     * Adds alignments to each AMR graph contained within a list of AMR graphs.
     * @param amrs the list of AMR graphs
     * @param filePath the file in which the alignments are stored
     * @param format the format in which the alignments are represented, see {@link AmrLineFormat}
     */
    public static void addAlignmentsFromFile(List<Amr> amrs, String filePath, AmrLineFormat format) throws IOException {
        for(Amr amr: amrs) {
            amr.backupAlignment = amr.alignment;
            amr.alignment = new HashMap<>();
        }
        int i=0;
        try(BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            for(String line; (line = br.readLine()) != null; ) {
                if(amrs.size() == i) return;
                addAlignmentFromLine(amrs.get(i), line, format);
                i++;
            }
        }
    }

    private static void addAlignmentFromLine(Amr amr, String line, AmrLineFormat format) {
        if(format == AmrLineFormat.JAMR) {
            line = line.substring(15);
            int commentsStart = line.indexOf("::");
            if (commentsStart > 0) {
                line = line.substring(0, commentsStart);
            }
            line = line.trim();
        }

        String[] alignments = line.split("[ ]+");
        for(String alignmentString: alignments) {
            if(alignmentString.isEmpty()) continue;
            addAlignmentFromString(amr, alignmentString, format);
        }
    }

    private static void addAlignmentFromString(Amr amr, String alignment, AmrLineFormat format) {
        String[] comps = alignment.split(AmrLineFormat.getWordNodeSeparator(format));
        String[] span = comps[0].split("-");
        String[] edgeStrings = comps[1].split("\\+");

        List<Integer> wordIds = new ArrayList<>();

        if(format == AmrLineFormat.JAMR) {
            for (int i = Integer.valueOf(span[0]); i < Integer.valueOf(span[1]); i++) {
                wordIds.add(i);
            }
        }
        else if(format == AmrLineFormat.EXTERNAL) {
            wordIds.add(Integer.valueOf(span[0]));
        }

        for(String edgeString: edgeStrings) {
            for(int id: wordIds) {
                addAlignmentFromEdgeIdPair(amr, edgeString, id, format);
            }
        }

    }

    private static void addAlignmentFromEdgeIdPair(Amr amr, String edgeString, int id, AmrLineFormat format) {

        Edge e = getEdgeFromString(amr, edgeString, format);

        if(amr.alignment.containsKey(e)) {
            amr.alignment.get(e).add(id);
        }
        else {
            HashSet<Integer> idSet = new HashSet<>();
            idSet.add(id);
            amr.alignment.put(e, idSet);
        }
    }

    private static Edge getEdgeFromString(Amr amr,  String edgeString, AmrLineFormat format) {

        List<Integer> edgeLevels;
        boolean relationEdge = false;
        switch(format) {
            case JAMR:
                edgeLevels = Arrays.asList(edgeString.split("\\.")).stream().map(s -> Integer.valueOf(s)).collect(Collectors.toList());
                break;
            case EXTERNAL:
                if(edgeString.charAt(edgeString.length()-1) == 'r') {
                    relationEdge = true;
                    edgeString = edgeString.substring(0, edgeString.length()-2);
                }
                edgeLevels = Arrays.asList(edgeString.split("\\.")).stream().map(s -> Integer.valueOf(s)).collect(Collectors.toList());
                break;
            default:
                throw new AssertionError("invalid AMR line format");
        }

        edgeLevels.remove(0);
        Vertex currentVertex = amr.dag.getRoot();
        Edge currentEdge = null;

        while(!edgeLevels.isEmpty()) {
            int nextChildId = edgeLevels.remove(0);

            List<Edge> relevantEdges;
            switch(format) {
                case EXTERNAL: relevantEdges = currentVertex.getOutgoingEdges(); // eigentlich müsste der InstanceEdge entfernt werden UND nextChildId-=1 gerechnet werden, aber beides hebt sich gegenseitig auf!
                    break;
                case JAMR: relevantEdges = currentVertex.getOutgoingEdges().stream().filter(e -> !e.debugInfo.equals("link") && !e.isInstanceEdge()).collect(Collectors.toList());
                    break;
                default:
                    throw new AssertionError("invalid AMR line format");
            }
            currentEdge = relevantEdges.get(nextChildId);
            currentVertex = currentEdge.getTo();
        }

        if(!relationEdge) {
            return currentVertex.getInstanceEdge();
        }
        else {
            return currentEdge;
        }
    }

    private static String[] getSentence(String line) {
        line = line.substring(8);
        return line.split(" ");
    }

    /**
     * see {@link AmrParser#fromString(String, String[])}
     */
    public static Amr fromString(String dagRepr) {
        return fromString(dagRepr, null);
    }

    /**
     * Builds an AMR graph from a string representation of the graph in LDC2014T12 format and assigns a reference sentence to it.
     * @param dagRepr the string representation of the AMR graph
     * @param sentence the reference sentence
     * @return the built AMR graph
     */
    public static Amr fromString(String dagRepr, String[] sentence) {

        Amr result = new Amr();
        result.sentence = sentence;

        dagRepr = dagRepr.replaceAll("\\s+"," ");
        Map<String,Vertex> vertexMap = new HashMap<>();
        Map<Vertex,String> idMap = new HashMap<>();
        Set<Vertex> unnamedVertices = new HashSet<>();
        dagRepr = getVertices(dagRepr, result.dag, vertexMap, idMap, unnamedVertices);
        vertexMap.put(Vertex.EMPTY_VERTEX_ID, Vertex.EMPTY_VERTEX);
        for(Vertex v: vertexMap.values()) {
            if(!unnamedVertices.contains(v)) {
                dagRepr = dagRepr.replace(idMap.get(v) + " / " + v.instance, idMap.get(v) + "@L@");
            }
            else {

            }
            Edge e = new Edge(v, Vertex.EMPTY_VERTEX, v.instance);
            e.instanceEdge = true;
            v.instance = v.instance.split("~")[0];
        }

        Pattern unbracedInstances = Pattern.compile("(:[^ ]+ )([^(].*?)([) ])");
        Matcher uiMatcher = unbracedInstances.matcher(dagRepr);

        dagRepr = uiMatcher.replaceAll("$1($2)$3");

        generateEdges(dagRepr, vertexMap, result.dag);

        for(Vertex v: result.dag) {
            // vertex-based alignment
            /*Matcher vMatcher = ALIGNMENT_PATTERN.matcher(v.instance);
            while (vMatcher.find()) {
                v.instance = vMatcher.group(1);
                int alignment = Integer.valueOf(vMatcher.group(2));
                result.alignment.put(v, alignment);
            }*/

            // edge-based alignment
            for(Edge e: v.outgoingEdges) {
                Matcher eMatcher = ALIGNMENT_PATTERN.matcher(e.label);
                while (eMatcher.find()) {
                    e.label = eMatcher.group(1);

                    String[] alignStrings = eMatcher.group(2).split(",");
                    Set<Integer> alignment = new HashSet<>();
                    for(String alignString: alignStrings) {
                        alignment.add(Integer.valueOf(alignString));
                    }
                    result.alignment.put(e, alignment);
                }
            }

        }

        return result;
    }

    private static void generateEdges(String dagRepr, Map<String,Vertex> vertexMap, DirectedGraph dag) {

        String[] split;
        dagRepr = dagRepr.substring(1, dagRepr.length()-1);

        if(!dagRepr.contains(" ")) {
            return;
        }

        split = dagRepr.split(" ",2);
        String fromNodeId = split[0].replace("@L@","");
        Vertex fromNode = vertexMap.get(fromNodeId);
        dagRepr = split[1];

        while(!dagRepr.isEmpty()) {

            if(!dagRepr.contains(" ")) {
                break;
            }

            split = dagRepr.split(" ", 2);
            String toNodeId = split[1].replaceAll("[\\(\\)]", "").split(" ", 2)[0];
            toNodeId = toNodeId.split("~")[0];
            boolean isLink = true;
            if(toNodeId.contains("@L@")) {
                toNodeId = toNodeId.replace("@L@","");
                isLink = false;
            }
            Vertex toNode = vertexMap.get(toNodeId);

            if(toNode == null) {
                throw new AssertionError("toNodeId " + toNodeId + " not found!");
            }


            String edgeLabel = split[0];
            Edge e = new Edge(fromNode, toNode, edgeLabel);
            if(isLink && !toNode.isSpecialNode()) {
                e.debugInfo = "link";
            }
            dagRepr = split[1];

            int level = 0;
            int startIndex = 0, endIndex = 0;
            boolean inQuotes = false;

            if(dagRepr.charAt(0) == '(') {

                for (int i = 0; i < dagRepr.length(); i++) {
                    char currentChar = dagRepr.charAt(i);

                    if(currentChar == '"') {
                        inQuotes = !inQuotes;
                    }

                    if (currentChar == '(' && !inQuotes) {
                        if (level == 0) {
                            startIndex = i;
                        }
                        level++;
                    }
                    else if (currentChar == ')' && !inQuotes) {
                        level--;
                        if (level == 0) {
                            endIndex = i;
                            generateEdges(dagRepr.substring(startIndex, endIndex + 1), vertexMap, dag);
                            break;
                        }
                    }

                }
            }
            else {
                throw new AssertionError("substring should start with (");
            }
            dagRepr = dagRepr.substring(endIndex + 1).trim();
        }
    }

    private static String getVertices(String dagRepr, DirectedGraph dag, Map<String,Vertex> vertexMap, Map<Vertex,String> idMap, Set<Vertex> unnamedVertices) {

        vertexMap.clear();
        idMap.clear();
        unnamedVertices.clear();

        Matcher m = NAMED_VERTEX_PATTERN.matcher(dagRepr);
        boolean rootFound = false;
        while (m.find()) {
            String id = m.group(1);
            String instance = m.group(2);
            Vertex v = new Vertex(instance);
            if(!rootFound) {
                rootFound = true;
                dag.root = v;
            }
            vertexMap.put(id, v);
            idMap.put(v, id);
        }

        for(Pattern p: Arrays.asList(UNNAMED_NUMBER_PATTERN, UNNAMED_WORD_PATTERN, UNNAMED_WORD_ALTERNATIVE_PATTERN)) {
            Matcher unnamedMatcher = p.matcher(dagRepr);
            while (unnamedMatcher.find()) {
                String align = unnamedMatcher.group(2);
                String instance = unnamedMatcher.group(1);

                if(instance.contains("(") || instance.contains(")") || instance.contains(" ")) {
                    String newInstance = instance.replace("(","[").replace(")","]").replace(" ","_");
                    dagRepr = dagRepr.replace(instance, newInstance);
                    instance = newInstance;
                }

                String id = instance + ((align != null) ? align : "");
                if(!vertexMap.containsKey(id.split("~")[0])) {
                    String idKey = id.split("~")[0];
                    Vertex v = new Vertex(id);
                    vertexMap.put(idKey, v);
                    idMap.put(v, idKey);
                    unnamedVertices.add(v);
                }
            }
        }

        return dagRepr;
    }

}
