//
// $Id$

package com.threerings.opengl.mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import com.google.common.collect.Maps;

import com.threerings.expr.Bound;
import com.threerings.expr.Function;
import com.threerings.expr.Scope;
import com.threerings.expr.Scoped;
import com.threerings.expr.SimpleScope;
import com.threerings.math.Matrix4f;
import com.threerings.math.Ray;
import com.threerings.math.Transform3D;
import com.threerings.math.Vector3f;

import com.threerings.opengl.mat.Surface;
import com.threerings.opengl.material.config.MaterialConfig;
import com.threerings.opengl.model.config.ArticulatedConfig;
import com.threerings.opengl.model.config.ArticulatedConfig.AnimationMapping;
import com.threerings.opengl.model.config.ArticulatedConfig.Attachment;
import com.threerings.opengl.model.config.ModelConfig.Imported.MaterialMapping;
import com.threerings.opengl.model.config.ModelConfig.VisibleMesh;
import com.threerings.opengl.renderer.state.TransformState;
import com.threerings.opengl.util.GlContext;

import static com.threerings.opengl.Log.*;

/**
 * An articulated model implementation.
 */
public class Articulated extends Model.Implementation
{
    /**
     * A node in the model.
     */
    public static class Node extends SimpleScope
    {
        /**
         * Creates a new node.
         */
        public Node (
            Scope parentScope, ArticulatedConfig.Node config, Transform3D parentViewTransform)
        {
            super(parentScope);
            _viewTransform = new Transform3D();
            setConfig(config, parentViewTransform);
        }

        /**
         * Sets the configuration of this node.
         */
        public void setConfig (ArticulatedConfig.Node config, Transform3D parentViewTransform)
        {
            _config = config;
            _localTransform.set(config.transform);
            _parentViewTransform = parentViewTransform;
        }

        /**
         * Returns a reference to the configuration of this node.
         */
        public ArticulatedConfig.Node getConfig ()
        {
            return _config;
        }

        /**
         * Returns a reference to this node's view transform.
         */
        public Transform3D getViewTransform ()
        {
            return _viewTransform;
        }

        /**
         * Returns this node's bone matrix (and flags it as a bone, if not already flagged).
         */
        public Matrix4f getBoneMatrix ()
        {
            if (_boneTransform == null) {
                _boneTransform = _viewTransform.compose(_config.invRefTransform);
                _boneTransform.update(Transform3D.AFFINE);
            }
            return _boneTransform.getMatrix();
        }

        /**
         * Creates the surfaces of this node.
         */
        public void createSurfaces (
            GlContext ctx, MaterialMapping[] materialMappings,
            Map<String, MaterialConfig> materialConfigs)
        {
            // nothing by default
        }

        /**
         * Enqueues this node for rendering.
         */
        public void enqueue ()
        {
            // compose parent view transform with local transform
            _parentViewTransform.compose(_localTransform, _viewTransform);

            // update bone transform if necessary
            if (_boneTransform != null) {
                _viewTransform.compose(_config.invRefTransform, _boneTransform);
                _boneTransform.update(Transform3D.AFFINE);
            }
        }

        @Override // documentation inherited
        public String getScopeName ()
        {
            return "node";
        }

        /**
         * Constructor for subclasses.
         */
        protected Node (Scope parentScope)
        {
            super(parentScope);
        }

        /** The node configuration. */
        protected ArticulatedConfig.Node _config;

        /** A reference to the parent view transform. */
        protected Transform3D _parentViewTransform;

        /** The node's local transform. */
        protected Transform3D _localTransform = new Transform3D();

        /** The node's view transform. */
        @Scoped
        protected Transform3D _viewTransform;

        /** The bone transform, for nodes used as bones. */
        protected Transform3D _boneTransform;
    }

    /**
     * A node that contains a (visible and/or collision) mesh.
     */
    public static class MeshNode extends Node
    {
        /**
         * Creates a new mesh node.
         */
        public MeshNode (
            Scope parentScope, ArticulatedConfig.MeshNode config, Transform3D parentViewTransform)
        {
            super(parentScope);
            _viewTransform = _transformState.getModelview();
            setConfig(config, parentViewTransform);
        }

        @Override // documentation inherited
        public void createSurfaces (
            GlContext ctx, MaterialMapping[] materialMappings,
            Map<String, MaterialConfig> materialConfigs)
        {
            VisibleMesh mesh = ((ArticulatedConfig.MeshNode)_config).visible;
            if (mesh != null) {
                _surface = createSurface(ctx, this, mesh, materialMappings, materialConfigs);
            }
        }

        @Override // documentation inherited
        public void enqueue ()
        {
            super.enqueue();
            if (_surface != null) {
                _transformState.setDirty(true);
                _surface.enqueue();
            }
        }

        /** The surface transform state. */
        @Scoped
        protected TransformState _transformState = new TransformState();

        /** The surface. */
        protected Surface _surface;
    }

    /**
     * Creates a new articulated implementation.
     */
    public Articulated (GlContext ctx, Scope parentScope, ArticulatedConfig config)
    {
        super(parentScope);
        _ctx = ctx;
        setConfig(config);
    }

    /**
     * Sets the configuration of this model.
     */
    public void setConfig (ArticulatedConfig config)
    {
        _config = config;

        // create the node list
        ArrayList<Node> nnodes = new ArrayList<Node>();
        config.root.getArticulatedNodes(this, _nodes, nnodes, _viewTransform);
        _nodes = nnodes.toArray(new Node[nnodes.size()]);

        // populate the name map
        _nodesByName.clear();
        for (Node node : _nodes) {
            _nodesByName.put(node.getConfig().name, node);
        }

        // create the node surfaces
        Map<String, MaterialConfig> materialConfigs = Maps.newHashMap();
        for (Node node : _nodes) {
            node.createSurfaces(_ctx, config.materialMappings, materialConfigs);
        }

        // create the skinned surfaces
        _surfaces = createSurfaces(
            _ctx, this, config.skin.visible, config.materialMappings, materialConfigs);

        // create the configured attachments
        Model[] omodels = _configAttachments;
        _configAttachments = new Model[config.attachments.length];
        for (int ii = 0; ii < _configAttachments.length; ii++) {
            Model model = (omodels == null || omodels.length <= ii) ?
                new Model(_ctx) : omodels[ii];
            _configAttachments[ii] = model;
            Attachment attachment = config.attachments[ii];
            model.setParentScope(getNode(attachment.node));
            model.setConfig(attachment.model);
        }
    }

    /**
     * Attaches the specified model at the given point.
     */
    public void attach (String point, Model model)
    {
        attach(point, model, true);
    }

    /**
     * Attaches the specified model at the given point.
     *
     * @param replace if true, replace any existing attachments at the point.
     */
    public void attach (String point, Model model, boolean replace)
    {
        Node node = getNode(point);
        if (node == null) {
            return;
        }
        if (replace) {
            detachAll(node);
        }
        model.setParentScope(node);
        _userAttachments.add(model);
    }

    /**
     * Detaches any models attached to the specified point.
     */
    public void detachAll (String point)
    {
        detachAll(getNode(point));
    }

    /**
     * Detaches an attached model.
     */
    public void detach (Model model)
    {
        for (int ii = 0, nn = _userAttachments.size(); ii < nn; ii++) {
            if (_userAttachments.get(ii) == model) {
                _userAttachments.remove(ii);
                return;
            }
        }
        log.warning("Missing attachment to remove.", "model", model);
    }

    /**
     * Returns a reference to the bone matrix for the named node.
     */
    @Scoped
    public Matrix4f getBoneMatrix (String name)
    {
        Node node = _nodesByName.get(name);
        return (node == null) ? (Matrix4f)_parentGetBoneMatrix.call(name) : node.getBoneMatrix();
    }

    // documentation inherited from interface Renderable
    public void enqueue ()
    {
        // update the view transform
        _parentViewTransform.compose(_localTransform, _viewTransform);

        // update/enqueue the nodes
        for (Node node : _nodes) {
            node.enqueue();
        }

        // enqueue the surfaces
        for (Surface surface : _surfaces) {
            surface.enqueue();
        }

        // enqueue the configured attachments
        for (Model model : _configAttachments) {
            model.enqueue();
        }

        // and the user attachments
        for (int ii = 0, nn = _userAttachments.size(); ii < nn; ii++) {
            _userAttachments.get(ii).enqueue();
        }
    }

    @Override // documentation inherited
    public boolean getIntersection (Ray ray, Vector3f result)
    {
        return false;
    }

    /**
     * Returns a reference to the node with the specified name, logging a warning and returning
     * <code>null</code> if no such node exists.
     */
    protected Node getNode (String name)
    {
        Node node = _nodesByName.get(name);
        if (node == null) {
            log.warning("Missing node.", "node", name);
        }
        return node;
    }

    /**
     * Detaches all models attached to the specified node.
     */
    protected void detachAll (Node node)
    {
        for (int ii = _userAttachments.size() - 1; ii >= 0; ii--) {
            if (_userAttachments.get(ii).getParentScope() == node) {
                _userAttachments.remove(ii);
            }
        }
    }

    /** The application context. */
    protected GlContext _ctx;

    /** The model configuration. */
    protected ArticulatedConfig _config;

    /** The model nodes, in the order of a preorder depth-first traversal. */
    protected Node[] _nodes;

    /** Maps node names to nodes. */
    protected HashMap<String, Node> _nodesByName = new HashMap<String, Node>();

    /** The skinned surfaces. */
    protected Surface[] _surfaces;

    /** The attachments created from the configuration. */
    protected Model[] _configAttachments;

    /** The local transform. */
    @Bound
    protected Transform3D _localTransform;

    /** The parent view transform. */
    @Bound("viewTransform")
    protected Transform3D _parentViewTransform;

    /** The parent implementation of {@link #getBoneMatrix}. */
    @Bound("getBoneMatrix")
    protected Function _parentGetBoneMatrix = Function.NULL;

    /** The view transform. */
    @Scoped
    protected Transform3D _viewTransform = new Transform3D();

    /** User attachments (their parent scopes are the nodes to which they're attached). */
    protected ArrayList<Model> _userAttachments = new ArrayList<Model>();
}