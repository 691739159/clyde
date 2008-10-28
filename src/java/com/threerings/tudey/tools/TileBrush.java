//
// $Id$

package com.threerings.tudey.tools;

import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;

import java.util.ArrayList;

import com.samskivert.util.Predicate;

import com.threerings.config.ConfigReference;
import com.threerings.editor.Editable;
import com.threerings.math.FloatMath;
import com.threerings.math.Vector3f;

import com.threerings.opengl.gui.util.Rectangle;

import com.threerings.tudey.client.cursor.TileCursor;
import com.threerings.tudey.client.sprite.TileSprite;
import com.threerings.tudey.client.sprite.Sprite;
import com.threerings.tudey.config.TileConfig;
import com.threerings.tudey.data.TudeySceneModel.TileEntry;
import com.threerings.tudey.util.Coord;

/**
 * Places individual tiles.
 */
public class TileBrush extends ConfigTool<TileConfig>
{
    /**
     * Creates the tile brush tool.
     */
    public TileBrush (SceneEditor editor)
    {
        super(editor, TileConfig.class, new TileReference());
    }

    @Override // documentation inherited
    public void init ()
    {
        _cursor = new TileCursor(_editor, _editor.getView(), _entry);
    }

    @Override // documentation inherited
    public void tick (float elapsed)
    {
        updateCursor();
        if (_cursorVisible) {
            _cursor.tick(elapsed);
        } else if (_editor.isThirdButtonDown() && !_editor.isControlDown()) {
            _editor.deleteMouseEntry(SceneEditor.TILE_ENTRY_FILTER);
        }
    }

    @Override // documentation inherited
    public void enqueue ()
    {
        if (_cursorVisible) {
            _cursor.enqueue();
        }
    }

    @Override // documentation inherited
    public void mousePressed (MouseEvent event)
    {
        int button = event.getButton();
        boolean paint = (button == MouseEvent.BUTTON1), erase = (button == MouseEvent.BUTTON3);
        if ((paint || erase) && _cursorVisible) {
            paintTile(erase);
        }
    }

    @Override // documentation inherited
    public void mouseWheelMoved (MouseWheelEvent event)
    {
        if (_cursorVisible) {
            _entry.rotation = (_entry.rotation + event.getWheelRotation()) & 0x03;
        }
    }

    /**
     * Updates the entry transform and cursor visibility based on the location of the mouse cursor.
     */
    protected void updateCursor ()
    {
        if (!(_cursorVisible = (_entry.tile != null) &&
                getMousePlaneIntersection(_isect) && !_editor.isControlDown())) {
            return;
        }
        TileConfig.Original config = _entry.getConfig(_editor.getConfigManager());
        int width = _entry.getWidth(config), height = _entry.getHeight(config);
        Coord location = _entry.getLocation();
        location.set(Math.round(_isect.x - width*0.5f), Math.round(_isect.y - height*0.5f));
        _entry.elevation = _editor.getGrid().getElevation();
        _cursor.update(_entry);

        // if we are dragging, consider performing another placement
        boolean paint = _editor.isFirstButtonDown(), erase = _editor.isThirdButtonDown();
        if ((paint || erase) && !location.equals(_lastPlacement)) {
            if (erase) {
                paintTile(true);
            } else {
                // make sure we've moved at least one tile length in one direction
                if (Math.abs(location.x - _lastPlacement.x) >= width ||
                        Math.abs(location.y - _lastPlacement.y) >= height) {
                    paintTile(false);
                }
            }
        }
    }

    /**
     * Paints the current tile.
     *
     * @param erase if true, just erase the tiles under the entry.
     */
    protected void paintTile (boolean erase)
    {
        // remove any tiles underneath
        Rectangle region = new Rectangle();
        _entry.getRegion(_entry.getConfig(_editor.getConfigManager()), region);
        ArrayList<TileEntry> underneath = new ArrayList<TileEntry>();
        _scene.getTileEntries(region, underneath);
        for (TileEntry entry : underneath) {
            _editor.removeEntry(entry.getKey());
        }
        // add the tile if we're not erasing
        if (!erase) {
            _editor.addEntry((TileEntry)_entry.clone());
        }
        _lastPlacement.set(_entry.getLocation());
    }

    @Override // documentation inherited
    protected void referenceChanged (ConfigReference<TileConfig> ref)
    {
        _entry.tile = ref;
    }

    /**
     * Allows us to edit the tile reference.
     */
    protected static class TileReference extends EditableReference<TileConfig>
    {
        /** The tile reference. */
        @Editable(nullable=true)
        public ConfigReference<TileConfig> tile;

        @Override // documentation inherited
        public ConfigReference<TileConfig> getReference ()
        {
            return tile;
        }

        @Override // documentation inherited
        public void setReference (ConfigReference<TileConfig> ref)
        {
            tile = ref;
        }
    }

    /** The prototype tile. */
    protected TileEntry _entry = new TileEntry();

    /** The cursor. */
    protected TileCursor _cursor;

    /** Whether or not the cursor is in the window. */
    protected boolean _cursorVisible;

    /** The location at which we last placed. */
    protected Coord _lastPlacement = new Coord();

    /** Holds the result on an intersection test. */
    protected Vector3f _isect = new Vector3f();
}
