//
// $Id$

package com.threerings.editor.swing.editors;

import javax.swing.BorderFactory;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import com.samskivert.swing.GroupLayout;
import com.samskivert.swing.VGroupLayout;
import com.samskivert.util.StringUtil;

import com.threerings.math.Vector2f;

import com.threerings.editor.Editable;
import com.threerings.editor.swing.PropertyEditor;
import com.threerings.editor.swing.Vector2fPanel;

/**
 * Editor for vector properties.
 */
public class Vector2fEditor extends PropertyEditor
    implements ChangeListener
{
    // documentation inherited from interface ChangeListener
    public void stateChanged (ChangeEvent event)
    {
        Vector2f value = _panel.getValue();
        if (!_property.get(_object).equals(value)) {
            _property.set(_object, value);
            fireStateChanged();
        }
    }

    @Override // documentation inherited
    public void update ()
    {
        _panel.setValue((Vector2f)_property.get(_object));
    }

    @Override // documentation inherited
    protected void didInit ()
    {
        setLayout(new VGroupLayout(GroupLayout.NONE, GroupLayout.STRETCH, 5, GroupLayout.TOP));
        setBorder(BorderFactory.createTitledBorder(getPropertyLabel()));
        Editable annotation = _property.getAnnotation();
        String mstr = getMode();
        Vector2fPanel.Mode mode = Vector2fPanel.Mode.CARTESIAN;
        try {
            mode = Enum.valueOf(Vector2fPanel.Mode.class, StringUtil.toUSUpperCase(mstr));
        } catch (IllegalArgumentException e) { }
        add(_panel = new Vector2fPanel(_msgs, mode, (float)getStep(), (float)getScale()));
        _panel.setBackground(getDarkerBackground(_lineage.length));
        _panel.addChangeListener(this);
    }

    /** The vector panel. */
    protected Vector2fPanel _panel;
}