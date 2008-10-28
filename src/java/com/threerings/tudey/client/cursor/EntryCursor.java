//
// $Id$

package com.threerings.tudey.client.cursor;

import com.threerings.opengl.util.GlContext;

import com.threerings.tudey.client.TudeySceneView;
import com.threerings.tudey.data.TudeySceneModel.Entry;

/**
 * Represents an entry.
 */
public abstract class EntryCursor extends Cursor
{
    /**
     * Creates a new entry cursor.
     */
    public EntryCursor (GlContext ctx, TudeySceneView view)
    {
        super(ctx, view);
    }

    /**
     * Returns a reference to the most recently set entry state.
     */
    public abstract Entry getEntry ();

    /**
     * Updates the cursor with new entry state.
     */
    public abstract void update (Entry entry);
}