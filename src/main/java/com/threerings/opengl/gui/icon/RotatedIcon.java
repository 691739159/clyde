//
// $Id$
//
// Clyde library - tools for developing networked games
// Copyright (C) 2005-2012 Three Rings Design, Inc.
// http://code.google.com/p/clyde/
//
// Redistribution and use in source and binary forms, with or without modification, are permitted
// provided that the following conditions are met:
//
// 1. Redistributions of source code must retain the above copyright notice, this list of
//    conditions and the following disclaimer.
// 2. Redistributions in binary form must reproduce the above copyright notice, this list of
//    conditions and the following disclaimer in the documentation and/or other materials provided
//    with the distribution.
//
// THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR IMPLIED WARRANTIES,
// INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
// PARTICULAR PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT,
// INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED
// TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
// INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
// LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
// SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

package com.threerings.opengl.gui.icon;

import org.lwjgl.opengl.GL11;

import com.threerings.opengl.renderer.Renderer;

/**
 * Rotates a sub-icon.
 */
public class RotatedIcon extends Icon
{
    /**
     * Creates a new rotated icon.
     *
     * @param rotation the rotation amount in degrees.
     */
    public RotatedIcon (Icon icon, float rotation)
    {
        _icon = icon;
        _rotation = rotation;
    }

    /**
     * Returns the width of this icon.
     */
    public int getWidth ()
    {
        return _icon.getWidth();
    }

    /**
     * Returns the height of this icon.
     */
    public int getHeight ()
    {
        return _icon.getHeight();
    }

    /**
     * Renders this icon.
     */
    public void render (Renderer renderer, int x, int y, float alpha)
    {
        int hwidth = _icon.getWidth()/2, hheight = _icon.getHeight()/2;
        GL11.glPushMatrix();
        GL11.glTranslatef(x + hwidth, y + hheight, 0f);
        GL11.glRotatef(_rotation, 0f, 0f, 1f);
        try {
            _icon.render(renderer, -hwidth, -hheight, alpha);
        } finally {
            GL11.glPopMatrix();
        }
    }

    /** The sub-icon to rotate. */
    protected Icon _icon;

    /** The rotation amount in degrees. */
    protected float _rotation;
}
