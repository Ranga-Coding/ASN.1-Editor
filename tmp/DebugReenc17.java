import java.nio.file.*;
import java.util.Arrays;
import java.util.ArrayList;
import com.asn1editor.parser.*;
import com.asn1editor.model.*;
import com.asn1editor.service.*;
import com.asn1editor.ui.TreeItemContent;
import javafx.scene.control.TreeItem;
import javafx.collections.FXCollections;

public class DebugReenc17 {
    public static void main(String[] args) throws Exception {
        String raw = new String(Files.readAllBytes(Paths.get("TEST.crmf")), java.nio.charset.StandardCharsets.ISO_8859_1);
        
        ASN1Service service = new ASN1Service();
        String decodedContent = service.decodeBase64IfNeeded(raw);
        byte[] originalBytes = service.fromHexString(decodedContent);
        
        ASN1Node originalRoot = ASN1BerDecoder.decode(originalBytes);
        ASN1Document originalDoc = new ASN1Document(originalRoot);
        
        // Schritt 1: buildTreeView
        TreeItem<TreeItemContent> treeRoot1 = buildTreeItems(originalDoc);
        
        // Schritt 2: getTreeViewDocument - holt Knoten aus TreeView
        ASN1Document docFromTreeView = getTreeViewDocument(treeRoot1);
        System.out.println("docFromTreeView[0] == originalDoc[0]: " + 
            (docFromTreeView.definitions().get(0) == originalDoc.definitions().get(0)));
        
        // Schritt 3: syncHexFromTree - encode und re-decode
        byte[] encoded = ASN1BerEncoder.encode(docFromTreeView.definitions().get(0));
        
        // Schritt 4: rebuildTreeViewWithEncodedRoot
        ASN1Node newRoot = ASN1BerDecoder.decode(encoded);
        ASN1Document newDoc = new ASN1Document(newRoot);
        TreeItem<TreeItemContent> treeRoot2 = buildTreeItems(newDoc);
        
        // Schritt 5: Finde den ersten UTF8String im NEUEN TreeView
        TreeItem<TreeItemContent> utf8Item = findFirstUtf8Item(treeRoot2);
        if (utf8Item != null) {
            TreeItemContent content = utf8Item.getValue();
            ASN1Node node = content.node();
            System.out.println("\nUTF8String im neu gebauten TreeView:");
            System.out.println("  node offset=" + node.offset());
            System.out.println("  node length=" + node.length());
            System.out.println("  node value=" + node.value());
            
            // Prüfe: die children des Nodes
            for (ASN1Node child : node.children()) {
                System.out.println("    child: " + child.name() + " = " + child.value());
            }
            
            // highlightBytes würde aufrufen:
            int start = node.offset();
            int end = start + node.length();
            System.out.println("\n  highlightRange [" + start + ", " + end + ")");
            System.out.println("  currentHexBytes.length=" + encoded.length);
            
            byte[] highlighted = Arrays.copyOfRange(encoded, start, end);
            System.out.println("  hex=" + HexUtils.toHexString(highlighted));
            System.out.println("  expected=0C 0E 53 4D 2D 54 65 73 74 2D 50 4B 49 2D 44 45");
            
            // Auch: was zeigt der ALTE TreeView?
            TreeItem<TreeItemContent> oldUtf8Item = findFirstUtf8Item(treeRoot1);
            if (oldUtf8Item != null) {
                ASN1Node oldNode = oldUtf8Item.getValue().node();
                System.out.println("\nUTF8String im alten TreeView:");
                System.out.println("  node offset=" + oldNode.offset());
                System.out.println("  node length=" + oldNode.length());
            }
        }
    }
    
    static TreeItem<TreeItemContent> buildTreeItems(ASN1Document doc) {
        TreeItem<TreeItemContent> root = new TreeItem<>(
                new TreeItemContent("ASN.1 Document", null));
        root.setExpanded(true);
        
        for (ASN1Node definition : doc.definitions()) {
            root.getChildren().add(buildTreeItem(definition));
        }
        return root;
    }
    
    static TreeItem<TreeItemContent> buildTreeItem(ASN1Node node) {
        String display = node.name() + (node.value() != null && !node.value().isEmpty() ? " = " + node.value() : "");
        for (ASN1Node child : node.children()) {
            if (child.isLeaf() && "value".equals(child.name())) {
                String v = child.value();
                if (v != null && !v.isEmpty()) {
                    display = node.name() + " = " + v;
                    break;
                }
            }
        }
        TreeItemContent content = new TreeItemContent(display, node);
        TreeItem<TreeItemContent> item = new TreeItem<>(content);
        
        for (ASN1Node child : node.children()) {
            item.getChildren().add(buildTreeItem(child));
        }
        return item;
    }
    
    static ASN1Document getTreeViewDocument(TreeItem<TreeItemContent> root) {
        ArrayList<ASN1Node> definitions = new ArrayList<>();
        for (TreeItem<TreeItemContent> item : root.getChildren()) {
            if (item.getValue() != null && item.getValue().node() != null) {
                definitions.add(item.getValue().node());
            }
        }
        return definitions.isEmpty() ? null : new ASN1Document(definitions);
    }
    
    static TreeItem<TreeItemContent> findFirstUtf8Item(TreeItem<TreeItemContent> root) {
        TreeItemContent content = root.getValue();
        if (content != null && content.node() != null && content.node().name().equals("UTF8String")) {
            return root;
        }
        for (TreeItem<TreeItemContent> child : root.getChildren()) {
            TreeItem<TreeItemContent> found = findFirstUtf8Item(child);
            if (found != null) return found;
        }
        return null;
    }
}
