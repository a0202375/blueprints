package com.tinkerpop.blueprints.pgm.parser;

import com.tinkerpop.blueprints.pgm.Edge;
import com.tinkerpop.blueprints.pgm.Graph;
import com.tinkerpop.blueprints.pgm.TransactionalGraph;
import com.tinkerpop.blueprints.pgm.TransactionalGraph.Conclusion;
import com.tinkerpop.blueprints.pgm.TransactionalGraph.Mode;
import com.tinkerpop.blueprints.pgm.Vertex;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.events.XMLEvent;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class GraphMLReader {

    public static void inputGraph(final Graph graph, final InputStream graphMLInputStream, final int bufferSize) throws XMLStreamException {
        XMLInputFactory inputFactory = XMLInputFactory.newInstance();
        XMLStreamReader reader = inputFactory.createXMLStreamReader(graphMLInputStream);

        Map<String, String> keyIdMap = new HashMap<String, String>();
        Map<String, String> keyTypesMaps = new HashMap<String, String>();
        Map<String, Object> vertexIdMap = new HashMap<String, Object>();

        Vertex currentVertex = null;
        Edge currentEdge = null;

        Mode transactionMode = null;
        boolean isTransactionalGraph = false;
        Integer transactionBufferSize = 0;
        if (graph instanceof TransactionalGraph) {
            transactionMode = ((TransactionalGraph) graph).getTransactionMode();
            ((TransactionalGraph) graph).setTransactionMode(Mode.MANUAL);
            ((TransactionalGraph) graph).startTransaction();
            isTransactionalGraph = true;
        }

        while (reader.hasNext()) {

            Integer eventType = reader.next();
            if (eventType.equals(XMLEvent.START_ELEMENT)) {
                String elementName = reader.getName().getLocalPart();
                if (elementName.equals(GraphMLTokens.KEY)) {
                    String id = reader.getAttributeValue(null, GraphMLTokens.ID);
                    String attributeName = reader.getAttributeValue(null, GraphMLTokens.ATTR_NAME);
                    String attributeType = reader.getAttributeValue(null, GraphMLTokens.ATTR_TYPE);
                    keyIdMap.put(id, attributeName);
                    keyTypesMaps.put(attributeName, attributeType);
                } else if (elementName.equals(GraphMLTokens.NODE)) {
                    String vertexStringId = reader.getAttributeValue(null, GraphMLTokens.ID);

                    Object vertexObjectId = vertexIdMap.get(vertexStringId);
                    if (vertexObjectId != null)
                        currentVertex = graph.getVertex(vertexObjectId);
                    else {
                        currentVertex = graph.addVertex(vertexStringId);
                        transactionBufferSize++;
                        vertexIdMap.put(vertexStringId, currentVertex.getId());
                    }

                } else if (elementName.equals(GraphMLTokens.EDGE)) {
                    String edgeId = reader.getAttributeValue(null, GraphMLTokens.ID);
                    String edgeLabel = reader.getAttributeValue(null, GraphMLTokens.LABEL);
                    edgeLabel = edgeLabel == null ? GraphMLTokens._DEFAULT : edgeLabel;
                    String outStringId = reader.getAttributeValue(null, GraphMLTokens.SOURCE);
                    String inStringId = reader.getAttributeValue(null, GraphMLTokens.TARGET);

                    // TODO: current edge retrieve by id first?
                    Object outObjectId = vertexIdMap.get(outStringId);
                    Object inObjectId = vertexIdMap.get(inStringId);

                    Vertex outVertex = null;
                    if (null != outObjectId)
                        outVertex = graph.getVertex(outObjectId);

                    Vertex inVertex = null;
                    if (null != inObjectId)
                        inVertex = graph.getVertex(inObjectId);

                    if (null == outVertex) {
                        outVertex = graph.addVertex(outStringId);
                        transactionBufferSize++;
                        vertexIdMap.put(outStringId, outVertex.getId());
                    }
                    if (null == inVertex) {
                        inVertex = graph.addVertex(inStringId);
                        transactionBufferSize++;
                        vertexIdMap.put(inStringId, inVertex.getId());
                    }

                    currentEdge = graph.addEdge(edgeId, outVertex, inVertex, edgeLabel);
                    transactionBufferSize++;

                } else if (elementName.equals(GraphMLTokens.DATA)) {
                    String key = reader.getAttributeValue(null, GraphMLTokens.KEY);
                    String attributeName = keyIdMap.get(key);
                    if (attributeName != null) {
                        String value = reader.getElementText();
                        if (currentVertex != null) {
                            currentVertex.setProperty(key, typeCastValue(key, value, keyTypesMaps));
                            transactionBufferSize++;
                        } else if (currentEdge != null) {
                            currentEdge.setProperty(key, typeCastValue(key, value, keyTypesMaps));
                            transactionBufferSize++;
                        }
                    }
                }
            } else if (eventType.equals(XMLEvent.END_ELEMENT)) {
                String elementName = reader.getName().getLocalPart();
                if (elementName.equals(GraphMLTokens.NODE))
                    currentVertex = null;
                else if (elementName.equals(GraphMLTokens.EDGE))
                    currentEdge = null;

            }

            if (isTransactionalGraph && (transactionBufferSize > bufferSize)) {
                ((TransactionalGraph) graph).stopTransaction(Conclusion.SUCCESS);
                ((TransactionalGraph) graph).startTransaction();
                transactionBufferSize = 0;
            }

        }
        reader.close();

        if (isTransactionalGraph) {
            ((TransactionalGraph) graph).stopTransaction(Conclusion.SUCCESS);
            ((TransactionalGraph) graph).setTransactionMode(transactionMode);
        }

    }

    public static void inputGraph(final Graph graph, final InputStream graphMLInputStream) throws XMLStreamException {
        GraphMLReader.inputGraph(graph, graphMLInputStream, 1000);
    }

    public static Object typeCastValue(String key, String value, Map<String, String> keyTypes) {
        String type = keyTypes.get(key);
        if (null == type || type.equals(GraphMLTokens.STRING))
            return value;
        else if (type.equals(GraphMLTokens.FLOAT))
            return Float.valueOf(value);
        else if (type.equals(GraphMLTokens.INT))
            return Integer.valueOf(value);
        else if (type.equals(GraphMLTokens.DOUBLE))
            return Double.valueOf(value);
        else if (type.equals(GraphMLTokens.BOOLEAN))
            return Boolean.valueOf(value);
        else if (type.equals(GraphMLTokens.LONG))
            return Long.valueOf(value);
        else
            return value;
    }
}
