//
// $Id$

package com.threerings.tudey.client.util;

import org.lwjgl.opengl.GL11;

import com.threerings.math.Box;
import com.threerings.math.Ray3D;
import com.threerings.math.Triangle;
import com.threerings.math.Vector3f;

import com.threerings.opengl.compositor.RenderQueue;
import com.threerings.opengl.renderer.Color4f;
import com.threerings.opengl.renderer.state.AlphaState;
import com.threerings.opengl.renderer.state.ColorState;
import com.threerings.opengl.renderer.state.DepthState;
import com.threerings.opengl.renderer.state.RenderState;
import com.threerings.opengl.scene.SimpleSceneElement;
import com.threerings.opengl.util.GlContext;

import com.threerings.tudey.data.TudeySceneModel.Vertex;

/**
 * Displays a solid area.
 */
public class AreaElement extends SimpleSceneElement
{
    /**
     * Creates a new area element.
     */
    public AreaElement (GlContext ctx)
    {
        super(ctx, RenderQueue.TRANSPARENT);
    }

    /**
     * Sets the vertices of the area.
     */
    public void setVertices (Vertex[] vertices)
    {
        _vertices = vertices;
        updateBounds();
    }

    /**
     * Returns a reference to the outline color.
     */
    public Color4f getColor ()
    {
        ColorState cstate = (ColorState)_batch.getStates()[RenderState.COLOR_STATE];
        return cstate.getColor();
    }

    @Override // documentation inherited
    public boolean getIntersection (Ray3D ray, Vector3f result)
    {
        // make sure the ray intersects the bounds
        if (!_bounds.intersects(ray)) {
            return false;
        }
        // transform into model space and check against triangles
        // (and back if we get a hit)
        ray = ray.transform(_transform.invert());
        Vertex v0 = _vertices[0];
        _triangle.getFirstVertex().set(v0.x, v0.y, v0.z);
        for (int ii = 2; ii < _vertices.length; ii++) {
            Vertex v1 = _vertices[ii - 1], v2 = _vertices[ii];
            _triangle.getSecondVertex().set(v1.x, v1.y, v1.z);
            _triangle.getThirdVertex().set(v2.x, v2.y, v2.z);
            if (_triangle.getIntersection(ray, result)) {
                _transform.transformPointLocal(result);
                return true;
            }
        }
        return false;
    }

    @Override // documentation inherited
    protected RenderState[] createStates ()
    {
        RenderState[] states = super.createStates();
        states[RenderState.ALPHA_STATE] = AlphaState.PREMULTIPLIED;
        states[RenderState.COLOR_STATE] = new ColorState();
        states[RenderState.DEPTH_STATE] = DepthState.TEST;
        return states;
    }

    @Override // documentation inherited
    protected void computeBounds (Box result)
    {
        result.setToEmpty();
        if (_vertices == null) {
            return;
        }
        for (Vertex vertex : _vertices) {
            result.addLocal(new Vector3f(vertex.x, vertex.y, vertex.z));
        }
        result.getCenter(_center);
        result.transformLocal(_transform);
    }

    @Override // documentation inherited
    protected void draw ()
    {
        if (_vertices == null) {
            return;
        }
        GL11.glBegin(GL11.GL_POLYGON);
        for (Vertex vertex : _vertices) {
            GL11.glVertex3f(vertex.x, vertex.y, vertex.z);
        }
        GL11.glEnd();
    }

    @Override // documentation inherited
    protected Vector3f getCenter ()
    {
        return _center;
    }

    /** The vertices of the area. */
    protected Vertex[] _vertices;

    /** The model space center. */
    protected Vector3f _center = new Vector3f();

    /** Triangle used for intersection testing. */
    protected Triangle _triangle = new Triangle();
}