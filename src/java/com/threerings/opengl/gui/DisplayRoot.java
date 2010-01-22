//
// $Id$
//
// Clyde library - tools for developing networked games
// Copyright (C) 2005-2010 Three Rings Design, Inc.
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

package com.threerings.opengl.gui;

import java.awt.Toolkit;

import org.lwjgl.LWJGLException;
import org.lwjgl.input.Controller;
import org.lwjgl.input.Controllers;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;

import com.threerings.opengl.util.GlContext;

import com.threerings.opengl.gui.event.ControllerEvent;
import com.threerings.opengl.gui.event.InputEvent;
import com.threerings.opengl.gui.event.KeyEvent;
import com.threerings.opengl.gui.event.MouseEvent;

import static com.threerings.opengl.gui.Log.*;

/**
 * A root for {@link Display}-based apps.
 */
public class DisplayRoot extends Root
{
    public DisplayRoot (GlContext ctx)
    {
        super(ctx);
        _clipboard = Toolkit.getDefaultToolkit().getSystemSelection();
    }

    /**
     * Polls the input system for events and dispatches them.
     */
    public void poll ()
    {
        // update the modifiers
        _modifiers = 0;
        int bcount = Mouse.getButtonCount();
        if (bcount >= 1 && Mouse.isButtonDown(0)) {
            _modifiers |= InputEvent.BUTTON1_DOWN_MASK;
        }
        if (bcount >= 2 && Mouse.isButtonDown(1)) {
            _modifiers |= InputEvent.BUTTON2_DOWN_MASK;
        }
        if (bcount >= 3 && Mouse.isButtonDown(2)) {
            _modifiers |= InputEvent.BUTTON3_DOWN_MASK;
        }
        if (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT)) {
            _modifiers |= InputEvent.SHIFT_DOWN_MASK;
        }
        if (Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) ||
                Keyboard.isKeyDown(Keyboard.KEY_RCONTROL)) {
            _modifiers |= InputEvent.CTRL_DOWN_MASK;
        }
        if (Keyboard.isKeyDown(Keyboard.KEY_LMETA) || Keyboard.isKeyDown(Keyboard.KEY_RMETA)) {
            _modifiers |= InputEvent.META_DOWN_MASK;
        }

        // process mouse events
        while (Mouse.next()) {
            int button = Mouse.getEventButton();
            if (button != -1) {
                if (Mouse.getEventButtonState()) {
                    mousePressed(_tickStamp, button, Mouse.getEventX(), Mouse.getEventY(), false);
                } else {
                    mouseReleased(_tickStamp, button, Mouse.getEventX(), Mouse.getEventY(), false);
                }
            }
            int delta = Mouse.getEventDWheel();
            if (delta != 0) {
                mouseWheeled(_tickStamp, Mouse.getEventX(), Mouse.getEventY(),
                    (delta > 0) ? +1 : -1, false);
            }
            if (button == -1 && delta == 0) {
                mouseMoved(_tickStamp, Mouse.getEventX(), Mouse.getEventY(), false);
            }
        }

        // process keyboard events
        while (Keyboard.next()) {
            if (Keyboard.getEventKeyState()) {
                keyPressed(_tickStamp, Keyboard.getEventCharacter(),
                    Keyboard.getEventKey(), false);
            } else {
                keyReleased(_tickStamp, Keyboard.getEventCharacter(),
                    Keyboard.getEventKey(), false);
            }
        }

        // process controller events
        while (Controllers.next()) {
            Controller controller = Controllers.getEventSource();
            if (Controllers.isEventButton()) {
                int index = Controllers.getEventControlIndex();
                if (controller.isButtonPressed(index)) {
                    controllerPressed(controller, _tickStamp, index);
                } else {
                    controllerReleased(controller, _tickStamp, index);
                }
            } else if (Controllers.isEventAxis()) {
                int index = Controllers.getEventControlIndex();
                controllerMoved(
                    controller, _tickStamp, index, Controllers.isEventXAxis(),
                    Controllers.isEventYAxis(), controller.getAxisValue(index));

            } else if (Controllers.isEventPovX()) {
                controllerPovXMoved(controller, _tickStamp, controller.getPovX());

            } else if (Controllers.isEventPovY()) {
                controllerPovYMoved(controller, _tickStamp, controller.getPovY());
            }
        }
    }

    @Override // documentation inherited
    public int getDisplayWidth ()
    {
        return Display.getDisplayMode().getWidth();
    }

    @Override // documentation inherited
    public int getDisplayHeight ()
    {
        return Display.getDisplayMode().getHeight();
    }

    @Override // documentation inherited
    public void setMousePosition (int x, int y)
    {
        Mouse.setCursorPosition(x, y);
        super.setMousePosition(x, y);
    }

    @Override // documentation inherited
    protected void updateCursor (Cursor cursor)
    {
        if (cursor == null) {
            cursor = getDefaultCursor();
        }
        try {
            Mouse.setNativeCursor(cursor == null ? null : cursor.getLWJGLCursor());
        } catch (LWJGLException e) {
            log.warning("Failed to set cursor.", "cursor", cursor, e);
        }
    }

    /**
     * Notes that a controller button has been pressed.
     */
    protected void controllerPressed (Controller controller, long when, int index)
    {
        ControllerEvent event = new ControllerEvent(
            controller, when, _modifiers, ControllerEvent.CONTROLLER_PRESSED, index);
        dispatchEvent(getFocus(), event);
    }

    /**
     * Notes that a controller button has been released.
     */
    protected void controllerReleased (Controller controller, long when, int index)
    {
        ControllerEvent event = new ControllerEvent(
            controller, when, _modifiers, ControllerEvent.CONTROLLER_RELEASED, index);
        dispatchEvent(getFocus(), event);
    }

    /**
     * Notes that a controller has moved on an axis.
     */
    protected void controllerMoved (
        Controller controller, long when, int index, boolean xAxis, boolean yAxis, float value)
    {
        ControllerEvent event = new ControllerEvent(
            controller, when, _modifiers, ControllerEvent.CONTROLLER_MOVED,
            index, xAxis, yAxis, value);
        dispatchEvent(getFocus(), event);
    }

    /**
     * Notes that a controller has moved on the pov x axis.
     */
    protected void controllerPovXMoved (Controller controller, long when, float value)
    {
        ControllerEvent event = new ControllerEvent(
            controller, when, _modifiers, ControllerEvent.CONTROLLER_POV_X_MOVED, value);
        dispatchEvent(getFocus(), event);
    }

    /**
     * Notes that a controller has moved on the pov y axis.
     */
    protected void controllerPovYMoved (Controller controller, long when, float value)
    {
        ControllerEvent event = new ControllerEvent(
            controller, when, _modifiers, ControllerEvent.CONTROLLER_POV_Y_MOVED, value);
        dispatchEvent(getFocus(), event);
    }
}
