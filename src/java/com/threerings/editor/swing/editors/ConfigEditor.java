//
// $Id$

package com.threerings.editor.swing.editors;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JLabel;

import com.samskivert.util.ObjectUtil;

import com.threerings.config.ConfigGroup;
import com.threerings.config.swing.ConfigBox;

import com.threerings.editor.swing.PropertyEditor;

import static com.threerings.editor.Log.*;

/**
 * Editor for enumerated type properties.
 */
public class ConfigEditor extends PropertyEditor
    implements ActionListener
{
    // documentation inherited from interface ActionListener
    public void actionPerformed (ActionEvent event)
    {
        Object value = _box.getSelectedConfig();
        if (!ObjectUtil.equals(_property.get(_object), value)) {
            _property.set(_object, value);
            fireStateChanged();
        }
    }

    @Override // documentation inherited
    public void update ()
    {
        _box.setSelectedConfig((String)_property.get(_object));
    }

    @Override // documentation inherited
    protected void didInit ()
    {
        add(new JLabel(getPropertyLabel() + ":"));
        ConfigGroup group = _ctx.getConfigManager().getGroup(getMode());
        if (group == null) {
            log.warning("Missing group for config editor.", "group", getMode());
            return;
        }
        add(_box = new ConfigBox(_msgs, group, _property.getAnnotation().nullable()));
        _box.addActionListener(this);
    }

    /** The combo box. */
    protected ConfigBox _box;
}