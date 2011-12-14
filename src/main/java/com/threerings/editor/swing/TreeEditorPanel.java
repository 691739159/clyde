//
// $Id$
//
// Clyde library - tools for developing networked games
// Copyright (C) 2005-2011 Three Rings Design, Inc.
// http://code.google.com/p/clyde/
//
// Redistribution and use in source and binary forms, with or without modification, are permitted
// provided that the following conditions are met:
//
// 1. Redistributions of source code must retain the above copyright notice, this list of
//    conditions and the following disclaimer.
// 2. Redistributions in binary form must reproduce the above copyright notice, this list of
//    conditions and the following disclaimer in the documentation and/or other materials provided
//    with the distribution.
//
// THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR IMPLIED WARRANTIES,
// INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
// PARTICULAR PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT,
// INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED
// TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
// INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
// LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
// SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

package com.threerings.editor.swing;

import java.awt.Point;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.FlavorEvent;
import java.awt.datatransfer.FlavorListener;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import java.lang.reflect.Array;

import java.util.List;

import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.TransferHandler;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.filechooser.FileFilter;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import com.samskivert.util.ListUtil;
import com.samskivert.util.StringUtil;

import com.threerings.config.ArgumentMap;
import com.threerings.config.ConfigReference;
import com.threerings.config.ManagedConfig;
import com.threerings.config.Parameter;
import com.threerings.config.ParameterizedConfig;
import com.threerings.export.ObjectMarshaller;
import com.threerings.export.XMLExporter;
import com.threerings.export.XMLImporter;
import com.threerings.export.util.SerializableWrapper;
import com.threerings.math.Quaternion;
import com.threerings.math.Transform2D;
import com.threerings.math.Transform3D;
import com.threerings.math.Vector2f;
import com.threerings.math.Vector3f;
import com.threerings.util.DeepUtil;
import com.threerings.util.ToolUtil;

import com.threerings.opengl.renderer.Color4f;

import com.threerings.editor.Introspector;
import com.threerings.editor.Property;
import com.threerings.editor.util.EditorContext;

import static com.threerings.editor.Log.*;

/**
 * Allows editing properties of an object in tree mode.
 */
public class TreeEditorPanel extends BaseEditorPanel
    implements ClipboardOwner, FlavorListener
{
    /**
     * Creates an empty editor panel.
     */
    public TreeEditorPanel (EditorContext ctx)
    {
        this(ctx, null, false);
    }

    /**
     * Creates an empty editor panel.
     */
    public TreeEditorPanel (EditorContext ctx, Property[] ancestors, boolean omitColumns)
    {
        super(ctx, ancestors, omitColumns);

        _tree = new JTree(new Object[0]);
        add(isEmbedded() ? _tree : new JScrollPane(_tree));

        // add selection listener to update context items
        _tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        _tree.addTreeSelectionListener(new TreeSelectionListener() {
            public void valueChanged (TreeSelectionEvent event) {
                boolean selected = (getSelectedNode() != null);
                _import.setEnabled(selected);
                _export.setEnabled(selected);
                _cut.setEnabled(selected);
                _copy.setEnabled(selected);
                _paste.setEnabled(shouldEnablePaste());
                _delete.setEnabled(selected);
            }
        });

        // add transfer handler for drag'n'drop, clipboard operations
        _tree.setDragEnabled(true);
        _tree.setTransferHandler(new TransferHandler() {
            @Override public boolean canImport (JComponent comp, DataFlavor[] flavors) {
                return ListUtil.contains(flavors, ToolUtil.SERIALIZED_WRAPPED_FLAVOR);
            }
            @Override public boolean importData (JComponent comp, Transferable t) {
                if (!canImport(comp, t.getTransferDataFlavors())) {
                    return false; // this isn't checked automatically for paste
                }
                boolean local = t.isDataFlavorSupported(LOCAL_NODE_TRANSFER_FLAVOR);
                Object data;
                try {
                    data = t.getTransferData(local ?
                        LOCAL_NODE_TRANSFER_FLAVOR : ToolUtil.SERIALIZED_WRAPPED_FLAVOR);
                } catch (Exception e) {
                    log.warning("Failure importing data.", e);
                    return false;
                }
                DefaultMutableTreeNode node = null;
                Object value;
                if (local) {
                    NodeTransfer transfer = (NodeTransfer)data;
                    node = transfer.node;
                    value = transfer.value;
                } else {
                    value = ((SerializableWrapper)data).getObject();
                }
                // no copying nodes onto themselves
                DefaultMutableTreeNode snode = getSelectedNode();
                if (node == snode) {
                    return false;
                }

                // have to clone the value in case we are going to paste it multiple times
                value = DeepUtil.copy(value);

                // perhaps copy the value onto a compatible property
                NodeObject snobj = (NodeObject)snode.getUserObject();
                if (snobj.property != null && snobj.property.isLegalValue(value)) {
                    setNodeValue(snode, value);
                    populateNode(snode, getLabel(snobj.property), value,
                        snobj.property.getSubtypes(), snobj.property, snobj.comp);
                    ((DefaultTreeModel)_tree.getModel()).reload(snode);
                    fireStateChanged();
                    return true;
                }

                // or into a compatible array/list
                DefaultMutableTreeNode pnode = (DefaultMutableTreeNode)snode.getParent();
                NodeObject pnobj = (NodeObject)pnode.getUserObject();
                NodeObject dnobj;
                DefaultMutableTreeNode dnode;
                int idx;
                if (snobj.comp instanceof Integer && pnobj.property != null &&
                        pnobj.property.isLegalComponentValue(value)) {
                    dnobj = pnobj;
                    dnode = pnode;
                    idx = (Integer)snobj.comp;

                } else if (snobj.property != null && snobj.property.getComponentType() != null &&
                        snobj.property.isLegalComponentValue(value)) {
                    dnobj = snobj;
                    dnode = snode;
                    idx = Integer.MAX_VALUE;

                } else {
                    return false;
                }
                // find out if we're moving within the list (and from where)
                int oidx = -1;
                pnode = (node == null) ? null : (DefaultMutableTreeNode)node.getParent();
                if (pnode != null) {
                    pnobj = (NodeObject)pnode.getUserObject();
                    if (pnobj.value == dnobj.value) {
                        NodeObject nobj = (NodeObject)node.getUserObject();
                        if (nobj.comp instanceof Integer) {
                            oidx = (Integer)nobj.comp;
                        }
                    }
                }

                // add/move element in list/array
                if (dnobj.value instanceof List) {
                    @SuppressWarnings("unchecked") List<Object> list =
                        (List<Object>)dnobj.value;
                    idx = Math.min(idx, list.size());
                    if (oidx != -1) {
                        if (idx == oidx) {
                            return false;
                        }
                        list.remove(oidx);
                    }
                    list.add(idx, value);

                } else {
                    int len = Array.getLength(dnobj.value);
                    idx = Math.min(idx, len);
                    if (oidx != -1) {
                        if (idx == oidx) {
                            return false;
                        }
                        // store value, shift elements up/down, replace
                        Object tmp = Array.get(dnobj.value, oidx);
                        if (oidx < idx) {
                            System.arraycopy(dnobj.value, oidx + 1, dnobj.value, oidx, idx - oidx);
                        } else {
                            System.arraycopy(dnobj.value, idx, dnobj.value, idx + 1, oidx - idx);
                        }
                        Array.set(dnobj.value, idx, value);

                    } else {
                        Object narray = Array.newInstance(
                            dnobj.value.getClass().getComponentType(), len + 1);
                        System.arraycopy(dnobj.value, 0, narray, 0, idx);
                        Array.set(narray, idx, value);
                        System.arraycopy(dnobj.value, idx, narray, idx + 1, len - idx);
                        setNodeValue(dnode, narray);
                    }
                }
                populateNode(dnode, getLabel(dnobj.property), dnobj.value,
                    dnobj.property.getSubtypes(), dnobj.property, dnobj.comp);
                ((DefaultTreeModel)_tree.getModel()).reload(dnode);
                fireStateChanged();
                return true;
            }
            @Override public int getSourceActions (JComponent comp) {
                return COPY_OR_MOVE;
            }
            @Override protected Transferable createTransferable (JComponent comp) {
                DefaultMutableTreeNode node = getSelectedNode();
                return (node == null) ? null : new NodeTransfer(node, false);
            }
            @Override protected void exportDone (
                    JComponent source, Transferable data, int action) {
                if (action == MOVE) {
                    deleteNode(((NodeTransfer)data).node, false);
                }
            }
        });

        // add popup menu with context items
        JPopupMenu popup = new JPopupMenu();
        _tree.setComponentPopupMenu(popup);
        popup.add(new JMenuItem(_import = createAction("import", KeyEvent.VK_I, -1)));
        popup.add(new JMenuItem(_export = createAction("export", KeyEvent.VK_E, -1)));
        popup.addSeparator();
        popup.add(new JMenuItem(_cut = createAction("cut", KeyEvent.VK_T,  KeyEvent.VK_X)));
        popup.add(new JMenuItem(_copy = createAction("copy", KeyEvent.VK_C,  KeyEvent.VK_C)));
        popup.add(new JMenuItem(_paste = createAction("paste", KeyEvent.VK_P,  KeyEvent.VK_V)));
        popup.add(new JMenuItem(_delete = createAction(
            "delete", KeyEvent.VK_D, KeyEvent.VK_DELETE, 0)));
    }

    /**
     * Returns a reference to the cut action object.
     */
    public Action getCutAction ()
    {
        return _cut;
    }

    /**
     * Returns a reference to the cut action object.
     */
    public Action getCopyAction ()
    {
        return _copy;
    }

    /**
     * Returns a reference to the cut action object.
     */
    public Action getPasteAction ()
    {
        return _paste;
    }

    /**
     * Returns a reference to the cut action object.
     */
    public Action getDeleteAction ()
    {
        return _delete;
    }

    // documentation inherited from interface ClipboardOwner
    public void lostOwnership (Clipboard clipboard, Transferable contents)
    {
        // no-op
    }

    // documentation inherited from interface FlavorListener
    public void flavorsChanged (FlavorEvent event)
    {
        _paste.setEnabled(shouldEnablePaste());
    }

    @Override // documentation inherited
    public void addNotify ()
    {
        super.addNotify();
        _tree.getToolkit().getSystemClipboard().addFlavorListener(this);
    }

    @Override // documentation inherited
    public void removeNotify ()
    {
        _tree.getToolkit().getSystemClipboard().removeFlavorListener(this);
        super.removeNotify();
    }

    @Override // documentation inherited
    public void setObject (Object object)
    {
        // make sure it's not the same object
        if (object == _object) {
            return;
        }
        super.setObject(object);
        update();
    }

    @Override // documentation inherited
    public void update ()
    {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode(
            new NodeObject(null, _object, null, null));
        if (_object != null) {
            addPropertyNodes(root, _object);
        }
        _tree.setModel(new DefaultTreeModel(root, true));
    }

    @Override // documentation inherited
    public void actionPerformed (ActionEvent event)
    {
        String action = event.getActionCommand();
        if ("import".equals(action)) {
            JFileChooser chooser = createFileChooser();
            if (chooser.showOpenDialog(_tree) == JFileChooser.APPROVE_OPTION) {
                File file = chooser.getSelectedFile();
                DefaultMutableTreeNode snode = getSelectedNode();
                NodeObject snobj = (NodeObject)snode.getUserObject();
                Object value;
                try {
                    XMLImporter in = new XMLImporter(new FileInputStream(file));
                    value = in.readObject();
                    in.close();

                } catch (IOException e) {
                    log.warning("Failed to import value.", e);
                    return;
                }
                _tree.getTransferHandler().importData(_tree, new NodeTransfer(value));
            }
        } else if ("export".equals(action)) {
            JFileChooser chooser = createFileChooser();
            if (chooser.showSaveDialog(_tree) == JFileChooser.APPROVE_OPTION) {
                File file = chooser.getSelectedFile();
                NodeObject nodeobj = (NodeObject)getSelectedNode().getUserObject();
                try {
                    XMLExporter out = new XMLExporter(new FileOutputStream(file));
                    out.writeObject(nodeobj.value);
                    out.close();

                } catch (IOException e) {
                    log.warning("Failed to export value.", "value", e);
                }
            }
        } else if ("cut".equals(action)) {
            copySelectedNode();
            deleteNode(getSelectedNode(), true);

        } else if ("copy".equals(action)) {
            copySelectedNode();

        } else if ("paste".equals(action)) {
            _tree.getTransferHandler().importData(
                _tree, _tree.getToolkit().getSystemClipboard().getContents(this));

        } else if ("delete".equals(action)) {
            deleteNode(getSelectedNode(), true);

        } else {
            super.actionPerformed(event);
       }
    }

    @Override // documentation inherited
    protected String getMousePath (Point pt)
    {
        pt = _tree.getMousePosition();
        TreePath path = (pt == null) ? null : _tree.getPathForLocation(pt.x, pt.y);
        if (path == null) {
            return "";
        }
        StringBuilder buf = new StringBuilder();
        for (int ii = 1, nn = path.getPathCount(); ii < nn; ii++) {
            NodeObject obj = (NodeObject)((DefaultMutableTreeNode)
                path.getPathComponent(ii)).getUserObject();
            if (obj.comp instanceof Integer) {
                buf.append('[').append(obj.comp).append(']');
            } else if (obj.comp instanceof String) {
                buf.append("[\"").append(((String)obj.comp).replace("\"", "\\\"")).append("\"]");
            } else { // obj.comp == null
                buf.append('.').append(obj.property.getName());
            }
        }
        return buf.toString();
    }

    /**
     * Creates an action for the popup menu.
     */
    protected Action createAction (String command, int mnemonic, int accelerator)
    {
        return createAction(command, mnemonic, accelerator, KeyEvent.CTRL_MASK);
    }

    /**
     * Creates an action for the popup menu.
     */
    protected Action createAction (String command, int mnemonic, int accelerator, int modifiers)
    {
        Action action = ToolUtil.createAction(
            this, _msgs, command, mnemonic, accelerator, modifiers);
        _tree.getActionMap().put(command, action);
        if (accelerator != -1) {
            _tree.getInputMap().put(KeyStroke.getKeyStroke(accelerator, modifiers), command);
        }
        action.setEnabled(false);
        return action;
    }

    /**
     * Creates a chooser for XML export files.
     */
    protected JFileChooser createFileChooser ()
    {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileFilter() {
            @Override public boolean accept (File file) {
                return file.isDirectory() || file.toString().endsWith(".xml");
            }
            @Override public String getDescription () {
                return _msgs.get("m.xml_files");
            }
        });
        return chooser;
    }

    /**
     * Copies the selected node to the clipboard.
     */
    protected void copySelectedNode ()
    {
        Clipboard clipboard = _tree.getToolkit().getSystemClipboard();
        clipboard.setContents(new NodeTransfer(getSelectedNode(), true), this);
    }

    /**
     * Deletes the specified node, if it is not already deleted.
     *
     * @param revertToDefaults if true, revert non-array nodes to default values as a way of
     * "deleting" them; otherwise, do not modify non-array nodes.
     */
    protected void deleteNode (DefaultMutableTreeNode node, boolean revertToDefaults)
    {
        DefaultMutableTreeNode parent = (DefaultMutableTreeNode)node.getParent();
        if (parent == null) {
            return;
        }
        NodeObject nodeobj = (NodeObject)node.getUserObject();
        NodeObject pnobj = (NodeObject)parent.getUserObject();
        if (!(nodeobj.comp instanceof Integer)) {
            if (!revertToDefaults) {
                return;
            }
            if (nodeobj.comp instanceof String) {
                ArgumentMap args = ((ConfigReference)pnobj.value).getArguments();
                args.remove((String)nodeobj.comp);
                nodeobj.value = nodeobj.property.get(args);
            } else {
                nodeobj.value = nodeobj.property.get(
                    ObjectMarshaller.getObjectMarshaller(pnobj.value.getClass()).getPrototype());
                nodeobj.property.set(pnobj.value, nodeobj.value);
            }
            populateNode(node, getLabel(nodeobj.property), nodeobj.value,
                nodeobj.property.getSubtypes(), nodeobj.property, nodeobj.comp);
            ((DefaultTreeModel)_tree.getModel()).reload(node);
            fireStateChanged();
            return;
        }
        int idx = (Integer)nodeobj.comp;
        if (pnobj.value instanceof List) {
            ((List)pnobj.value).remove(idx);
        } else {
            int len = Array.getLength(pnobj.value);
            Object narray = Array.newInstance(
                pnobj.value.getClass().getComponentType(), len - 1);
            System.arraycopy(pnobj.value, 0, narray, 0, idx);
            System.arraycopy(pnobj.value, idx + 1, narray, idx, len - 1 - idx);
            setNodeValue(parent, narray);
        }
        populateNode(parent, getLabel(pnobj.property), pnobj.value,
            pnobj.property.getSubtypes(), pnobj.property, pnobj.comp);
        ((DefaultTreeModel)_tree.getModel()).reload(parent);
        fireStateChanged();
    }

    /**
     * Sets the value contained in the specified node through its parent.
     */
    protected void setNodeValue (DefaultMutableTreeNode node, Object value)
    {
        NodeObject nodeobj = (NodeObject)node.getUserObject();
        DefaultMutableTreeNode parent = (DefaultMutableTreeNode)node.getParent();
        NodeObject pnobj = (NodeObject)parent.getUserObject();
        Object object = pnobj.value;
        if (nodeobj.comp instanceof String) {
            object = ((ConfigReference)object).getArguments();
        }
        nodeobj.property.set(object, nodeobj.value = value);
    }

    /**
     * Checks whether we should enable the paste operation, based on the selection
     * and clipboard state.
     */
    protected boolean shouldEnablePaste ()
    {
        return getSelectedNode() != null &&
            _tree.getToolkit().getSystemClipboard().isDataFlavorAvailable(
                ToolUtil.SERIALIZED_WRAPPED_FLAVOR);
    }

    /**
     * Returns the selected node, or <code>null</code> for none.
     */
    protected DefaultMutableTreeNode getSelectedNode ()
    {
        TreePath path = _tree.getSelectionPath();
        return (path == null) ? null : (DefaultMutableTreeNode)path.getLastPathComponent();
    }

    /**
     * Adds child nodes for the specified object's properties to the specified parent node.
     */
    protected void addPropertyNodes (DefaultMutableTreeNode parent, Object object)
    {
        Property[] properties = Introspector.getProperties(object);
        if (properties.length == 0) {
            return;
        }
        parent.setAllowsChildren(true);
        for (Property property : properties) {
            addNode(parent, getLabel(property), property.get(object),
                property.getSubtypes(), property, null);
        }
    }

    /**
     * Adds a child node for the specified labeled value.
     */
    protected void addNode (
        DefaultMutableTreeNode parent, String label, Object value,
        Class<?>[] subtypes, Property property, Object comp)
    {
        DefaultMutableTreeNode child = new DefaultMutableTreeNode();
        populateNode(child, label, value, subtypes, property, comp);
        parent.add(child);
    }

    /**
     * (Re)populates the specified node.
     */
    protected void populateNode (
        DefaultMutableTreeNode node, String label, Object value,
        Class<?>[] subtypes, Property property, Object comp)
    {
        if (value == null || value instanceof Boolean || value instanceof Number ||
                value instanceof Color4f || value instanceof File ||
                value instanceof Quaternion || value instanceof String ||
                value instanceof Transform2D || value instanceof Transform3D ||
                value instanceof Vector2f || value instanceof Vector3f ||
                value instanceof Enum) {
            Object dval = value;
            if (value == null) {
                dval = _msgs.get("m.null_value");

            } else if (value instanceof String) {
                dval = "\"" + value + "\"";

            } else if (value instanceof Enum) {
                Enum<?> eval = (Enum)value;
                dval = getLabel(eval, _msgmgr.getBundle(
                    Introspector.getMessageBundle(eval.getDeclaringClass())));
            }
            node.setUserObject(new NodeObject(label + ": " + dval, value, property, comp));
            node.setAllowsChildren(false);

        } else if (value instanceof ConfigReference) {
            ConfigReference<?> ref = (ConfigReference)value;
            String name = ref.getName();
            node.setUserObject(new NodeObject(label + ": " + name, value, property, comp));
            node.setAllowsChildren(false);
            if (property != null) {
                @SuppressWarnings("unchecked") Class<ManagedConfig> clazz =
                    (Class<ManagedConfig>)property.getArgumentType(ConfigReference.class);
                ManagedConfig config = _ctx.getConfigManager().getConfig(clazz, name);
                if (config instanceof ParameterizedConfig) {
                    ParameterizedConfig pconfig = (ParameterizedConfig)config;
                    if (pconfig.parameters.length > 0) {
                        for (Parameter param : pconfig.parameters) {
                            Property aprop = param.getArgumentProperty(pconfig);
                            if (aprop != null) {
                                node.setAllowsChildren(true);
                                addNode(node, param.name, aprop.get(ref.getArguments()),
                                    aprop.getSubtypes(), aprop, param.name);
                            }
                        }
                    }
                }
            }
        } else if (value instanceof List || value.getClass().isArray()) {
            node.setUserObject(new NodeObject(label, value, property, comp));
            node.removeAllChildren();
            node.setAllowsChildren(true);
            Class<?>[] componentSubtypes = (property != null) ?
                property.getComponentSubtypes() : new Class[0];
            if (value instanceof List) {
                List<?> list = (List)value;
                for (int ii = 0, nn = list.size(); ii < nn; ii++) {
                    addNode(node, String.valueOf(ii), list.get(ii), componentSubtypes, null, ii);
                }
            } else {
                for (int ii = 0, nn = Array.getLength(value); ii < nn; ii++) {
                    addNode(node, String.valueOf(ii), Array.get(value, ii),
                        componentSubtypes, null, ii);
                }
            }
        } else {
            if (subtypes.length > 1) {
                label = label + ": " + getLabel(value.getClass());
            }
            node.setUserObject(new NodeObject(label, value, property, comp));
            node.setAllowsChildren(false);
            addPropertyNodes(node, value);
        }
    }

    /**
     * A user object for a tree node.
     */
    protected static class NodeObject
    {
        /** The object's string representation. */
        public final String label;

        /** The object's value. */
        public Object value;

        /** The node property. */
        public final Property property;

        /** The array index or the parameter name, if applicable. */
        public final Object comp;

        /**
         * Creates a new node object.
         */
        public NodeObject (String label, Object value, Property property, Object comp)
        {
            this.label = label;
            this.value = value;
            this.property = property;
            this.comp = comp;
        }

        @Override // documentation inherited
        public String toString ()
        {
            return label;
        }
    }

    /**
     * Contains a node for transfer.
     */
    protected static class NodeTransfer
        implements Transferable
    {
        /** The node being transferred. */
        public DefaultMutableTreeNode node;

        /** The contained value. */
        public Object value;

        public NodeTransfer (DefaultMutableTreeNode node, boolean clipboard)
        {
            this.node = clipboard ? null : node;
            value = DeepUtil.copy(((NodeObject)node.getUserObject()).value);
        }

        public NodeTransfer (Object value)
        {
            this.value = value;
        }

        // documentation inherited from interface Transferable
        public DataFlavor[] getTransferDataFlavors ()
        {
            return NODE_TRANSFER_FLAVORS;
        }

        // documentation inherited from interface Transferable
        public boolean isDataFlavorSupported (DataFlavor flavor)
        {
            return ListUtil.contains(NODE_TRANSFER_FLAVORS, flavor);
        }

        // documentation inherited from interface Transferable
        public Object getTransferData (DataFlavor flavor)
        {
            return flavor.equals(LOCAL_NODE_TRANSFER_FLAVOR) ?
                this : new SerializableWrapper(value);
        }
    }

    /** The tree component. */
    protected JTree _tree;

    /** The various context actions. */
    protected Action _import, _export, _cut, _copy, _paste, _delete;

    /** A data flavor that provides access to the actual transfer object. */
    protected static final DataFlavor LOCAL_NODE_TRANSFER_FLAVOR =
        ToolUtil.createLocalFlavor(NodeTransfer.class);

    /** The flavors available for node transfer. */
    protected static final DataFlavor[] NODE_TRANSFER_FLAVORS =
        { LOCAL_NODE_TRANSFER_FLAVOR, ToolUtil.SERIALIZED_WRAPPED_FLAVOR };
}
