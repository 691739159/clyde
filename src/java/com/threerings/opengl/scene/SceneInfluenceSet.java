//
// $Id$

package com.threerings.opengl.scene;

import java.util.ArrayList;
import java.util.HashSet;

import com.threerings.math.Box;

import com.threerings.opengl.renderer.Color4f;
import com.threerings.opengl.renderer.Light;
import com.threerings.opengl.renderer.state.FogState;
import com.threerings.opengl.renderer.state.LightState;

/**
 * A set of scene influences.
 */
public class SceneInfluenceSet extends HashSet<SceneInfluence>
{
    /**
     * Returns the fog state for this influence set.
     *
     * @param bounds the bounds used to resolve conflicts.
     * @param state an existing state to reuse, if possible.
     */
    public FogState getFogState (Box bounds, FogState state)
    {
        FogState closest = FogState.DISABLED;
        float cdist = Float.MAX_VALUE;
        for (SceneInfluence influence : this) {
            state = influence.getFogState();
            if (state != null) {
                float distance = influence.getBounds().getExtentDistance(bounds);
                if (distance < cdist) {
                    closest = state;
                    cdist = distance;
                }
            }
        }
        return closest;
    }

    /**
     * Returns the light state for this influence set.
     *
     * @param bounds the bounds used to resolve conflicts.
     * @param state an existing state to reuse, if possible.
     */
    public LightState getLightState (Box bounds, LightState state)
    {
        Color4f closestAmbient = null;
        float cdist = Float.MAX_VALUE;
        ArrayList<Light> lights = new ArrayList<Light>();
        for (SceneInfluence influence : this) {
            Color4f ambient = influence.getAmbientLight();
            if (ambient != null) {
                float distance = influence.getBounds().getExtentDistance(bounds);
                if (distance < cdist) {
                    closestAmbient = ambient;
                    cdist = distance;
                }
            }
            Light light = influence.getLight();
            if (light != null) {
                lights.add(light);
            }
        }
        return (closestAmbient == null) ? LightState.DISABLED :
            new LightState(lights.toArray(new Light[lights.size()]), closestAmbient);
    }
}