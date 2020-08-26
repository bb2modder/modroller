package net.bb2.modroller.scenes;

import org.eclipse.jgit.util.FileUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;
import java.util.Set;

public class ModXmlApplicator {

	private final DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
	private final XPathFactory xPathFactory = XPathFactory.newInstance();
	private final TransformerFactory transformerFactory = TransformerFactory.newInstance();

	public void apply(File xmlFile, File modDir, Map<String, String> xpathToReplacementFiles) throws Exception {
		DocumentBuilder docBuilder = documentBuilderFactory.newDocumentBuilder();
		Document targetDoc = docBuilder.parse(xmlFile);

		for (Map.Entry<String, String> entry : xpathToReplacementFiles.entrySet()) {
			XPathExpression xPathExpression = xPathFactory.newXPath().compile(entry.getKey());

			String replacementXml = Files.readString(modDir.toPath().resolve(entry.getValue()));
			Node replacementNode = documentBuilderFactory.newDocumentBuilder()
					.parse(new ByteArrayInputStream(replacementXml.getBytes()))
					.getDocumentElement();
			replacementNode = targetDoc.importNode(replacementNode, true);

			NodeList nodeList = (NodeList)xPathExpression.evaluate(targetDoc, XPathConstants.NODESET);
			if (nodeList.getLength() == 0) {
				throw new Exception("Did not match " + entry.getKey() + " within " + xmlFile.getAbsolutePath());
			}
			for (int cursor = 0; cursor < nodeList.getLength(); cursor++) {
				Node targetNode = nodeList.item(cursor);
				targetNode.getParentNode().replaceChild(replacementNode, targetNode);
			}
		}

		writeDocumentToFile(targetDoc, xmlFile);
	}

	public void remove(File targetXmlFile, File originalXmlFile, Set<String> xpaths) throws Exception {
		Document originalDoc = documentBuilderFactory.newDocumentBuilder().parse(originalXmlFile);
		Document targetDoc = documentBuilderFactory.newDocumentBuilder().parse(targetXmlFile);

		for (String xpath : xpaths) {
			XPathExpression xPathExpression = xPathFactory.newXPath().compile(xpath);

			NodeList originalNodeList = (NodeList)xPathExpression.evaluate(originalDoc, XPathConstants.NODESET);
			NodeList targetNodeList = (NodeList)xPathExpression.evaluate(targetDoc, XPathConstants.NODESET);
			if (originalNodeList.getLength() == 0) {
				throw new Exception("Did not match " + xpath + " within " + originalXmlFile.getAbsolutePath());
			}
			if (targetNodeList.getLength() == 0) {
				throw new Exception("Did not match " + xpath + " within " + targetXmlFile.getAbsolutePath());
			}
			if (originalNodeList.getLength() != targetNodeList.getLength()) {
				throw new Exception("Different number of matches of " + xpath + " in " + originalXmlFile.getAbsolutePath() + " and " + targetXmlFile.getAbsolutePath());
			}

			for (int originalCursor = 0; originalCursor < originalNodeList.getLength(); originalCursor++) {
				Node originalNode = originalNodeList.item(originalCursor);
				originalNode = targetDoc.importNode(originalNode, true);
				Node targetNode = targetNodeList.item(originalCursor);

				targetNode.getParentNode().replaceChild(originalNode, targetNode);
			}

		}

		writeDocumentToFile(targetDoc, targetXmlFile);
	}

	private void writeDocumentToFile(Document targetDoc, File xmlFile) throws Exception {
		// Strip whitespace text nodes
		XPath xp = xPathFactory.newXPath();
		NodeList nl = (NodeList) xp.evaluate("//text()[normalize-space(.)='']", targetDoc, XPathConstants.NODESET);
		for (int i=0; i < nl.getLength(); ++i) {
			Node node = nl.item(i);
			node.getParentNode().removeChild(node);
		}

		Transformer transformer = transformerFactory.newTransformer();
		transformer.setOutputProperty(OutputKeys.INDENT, "yes");
		transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "3"); // Weirdo Cyanide using 3-space indentation

		DOMSource source = new DOMSource(targetDoc);
		FileWriter writer = new FileWriter(xmlFile);
		StreamResult result = new StreamResult(writer);
		try {
			transformer.transform(source, result);
		} finally {
			writer.close();
		}
	}
}
