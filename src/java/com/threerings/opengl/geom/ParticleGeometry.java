//
// $Id$

package com.threerings.opengl.geom;

import com.threerings.opengl.renderer.SimpleBatch.DrawCommand;
import com.threerings.opengl.renderer.state.ArrayState;

/**
 * Represents a particle geometry instance.
 */
public class ParticleGeometry extends Geometry
{
    @Override // documentation inherited
    public ArrayState getArrayState (int pass)
    {
        return null;
    }

    @Override // documentation inherited
    public DrawCommand getDrawCommand (int pass)
    {
        return null;
    }

    @Override // documentation inherited
    public boolean requiresUpdate ()
    {
        return true;
    }

    @Override // documentation inherited
    public void update ()
    {

    }
}