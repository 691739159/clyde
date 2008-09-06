//
// $Id$

package com.threerings.opengl.scene;

import java.util.ArrayList;
import java.util.Collection;

import com.samskivert.util.Predicate;

import com.threerings.math.Box;
import com.threerings.math.Frustum;
import com.threerings.math.Ray;
import com.threerings.math.Vector3f;

import com.threerings.opengl.util.GlContext;

/**
 * A simple, "flat" scene implementation.
 */
public class SimpleScene extends Scene
{
    /**
     * Creates a new simple scene.
     */
    public SimpleScene (GlContext ctx)
    {
        super(ctx);
    }

    // documentation inherited from interface Renderable
    public void enqueue ()
    {
        enqueue(_elements, _ctx.getCompositor().getCamera().getWorldVolume());
    }

    @Override // documentation inherited
    public SceneElement getIntersection (
        Ray ray, Vector3f location, Predicate<SceneElement> filter)
    {
        return getIntersection(_elements, ray, location, filter);
    }

    @Override // documentation inherited
    public void getElements (Box bounds, Collection<SceneElement> results)
    {
        getIntersecting(_elements, bounds, results);
    }

    @Override // documentation inherited
    public void getInfluences (Box bounds, Collection<SceneInfluence> results)
    {
        getIntersecting(_influences, bounds, results);
    }

    @Override // documentation inherited
    protected void addToSpatial (SceneElement element)
    {
        _elements.add(element);
    }

    @Override // documentation inherited
    protected void removeFromSpatial (SceneElement element)
    {
        _elements.remove(element);
    }

    @Override // documentation inherited
    protected void addToSpatial (SceneInfluence influence)
    {
        _influences.add(influence);
    }

    @Override // documentation inherited
    protected void removeFromSpatial (SceneInfluence influence)
    {
        _influences.remove(influence);
    }

    /** The list of all scene elements. */
    protected ArrayList<SceneElement> _elements = new ArrayList<SceneElement>();

    /** The list of all scene influences. */
    protected ArrayList<SceneInfluence> _influences = new ArrayList<SceneInfluence>();
}