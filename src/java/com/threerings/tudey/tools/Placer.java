//
// $Id$

package com.threerings.tudey.tools;

import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;

import com.threerings.config.ConfigReference;
import com.threerings.editor.Editable;
import com.threerings.math.FloatMath;
import com.threerings.math.Transform3D;
import com.threerings.math.Vector3f;

import com.threerings.tudey.client.cursor.PlaceableCursor;
import com.threerings.tudey.client.sprite.PlaceableSprite;
import com.threerings.tudey.client.sprite.Sprite;
import com.threerings.tudey.config.PlaceableConfig;
import com.threerings.tudey.data.TudeySceneModel.PlaceableEntry;

/**
 * The placeable placer tool.
 */
public class Placer extends ConfigTool<PlaceableConfig>
{
    /**
     * Creates the placer tool.
     */
    public Placer (SceneEditor editor)
    {
        super(editor, PlaceableConfig.class, new PlaceableReference());
        _entry.transform.setType(Transform3D.UNIFORM);
    }

    @Override // documentation inherited
    public void init ()
    {
        _cursor = new PlaceableCursor(_editor, _editor.getView(), _entry);
    }

    @Override // documentation inherited
    public void tick (float elapsed)
    {
        if (_editor.isThirdButtonDown() && !_editor.isControlDown()) {
            _editor.deleteMouseObject(SceneEditor.PLACEABLE_SPRITE_FILTER);
        }
        updateCursor();
        if (_cursorVisible) {
            _cursor.tick(elapsed);
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
        if (event.getButton() == MouseEvent.BUTTON1 && _cursorVisible) {
            placeEntry();
        }
    }

    @Override // documentation inherited
    public void mouseWheelMoved (MouseWheelEvent event)
    {
        // adjust in terms of coarse (ninety degree) or fine increments
        if (_cursorVisible) {
            float increment = event.isShiftDown() ? FINE_ROTATION_INCREMENT : FloatMath.HALF_PI;
            _angle = (Math.round(_angle / increment) + event.getWheelRotation()) * increment;
        }
    }

    /**
     * Updates the entry transform and cursor visibility based on the location of the mouse cursor.
     */
    protected void updateCursor ()
    {
        if (!(_cursorVisible = _entry.placeable != null &&
                getMousePlaneIntersection(_isect) && !_editor.isControlDown())) {
            return;
        }
        // snap to tile grid if shift not held down
        if (!_editor.isShiftDown()) {
            _isect.x = FloatMath.floor(_isect.x) + 0.5f;
            _isect.y = FloatMath.floor(_isect.y) + 0.5f;
        }
        Transform3D transform = _entry.transform;
        transform.getTranslation().set(_isect.x, _isect.y, _editor.getGrid().getZ());
        transform.getRotation().fromAngleAxis(_angle, Vector3f.UNIT_Z);
        _cursor.update(_entry);

        // if we are dragging, consider performing another placement
        if (_editor.isFirstButtonDown() &&
                transform.getTranslation().distance(_lastPlacement) >= MIN_SPACING) {
            placeEntry();
        }
    }

    /**
     * Places the current entry.
     */
    protected void placeEntry ()
    {
        _scene.addEntry((PlaceableEntry)_entry.clone());
        _lastPlacement.set(_entry.transform.getTranslation());
    }

    @Override // documentation inherited
    protected void referenceChanged (ConfigReference<PlaceableConfig> ref)
    {
        _entry.placeable = ref;
    }

    /**
     * Allows us to edit the placeable reference.
     */
    protected static class PlaceableReference extends EditableReference<PlaceableConfig>
    {
        /** The placeable reference. */
        @Editable(nullable=true)
        public ConfigReference<PlaceableConfig> placeable;

        @Override // documentation inherited
        public ConfigReference<PlaceableConfig> getReference ()
        {
            return placeable;
        }

        @Override // documentation inherited
        public void setReference (ConfigReference<PlaceableConfig> ref)
        {
            placeable = ref;
        }
    }

    /** The prototype entry. */
    protected PlaceableEntry _entry = new PlaceableEntry();

    /** The cursor. */
    protected PlaceableCursor _cursor;

    /** Whether or not the cursor is in the window. */
    protected boolean _cursorVisible;

    /** The angle about the z axis. */
    protected float _angle;

    /** The location at which we last placed. */
    protected Vector3f _lastPlacement = new Vector3f();

    /** Holds the result on an intersection test. */
    protected Vector3f _isect = new Vector3f();

    /** The fine rotation increment. */
    protected static final float FINE_ROTATION_INCREMENT = FloatMath.toRadians(5f);

    /** The minimum spacing between placements when dragging. */
    protected static final float MIN_SPACING = 0.5f;
}