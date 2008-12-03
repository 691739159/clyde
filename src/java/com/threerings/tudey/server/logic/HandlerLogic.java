//
// $Id$

package com.threerings.tudey.server.logic;

import com.threerings.math.Vector2f;

import com.threerings.tudey.config.HandlerConfig;
import com.threerings.tudey.server.TudeySceneManager;

/**
 * Handles the server-side processing for an event handler type.
 */
public abstract class HandlerLogic extends Logic
{
    /**
     * Handles the startup event.
     */
    public static class Startup extends HandlerLogic
    {
        @Override // documentation inherited
        protected void didInit ()
        {
            execute(_scenemgr.getTimestamp());
        }
    }

    /**
     * Handles the tick event.
     */
    public static class Tick extends HandlerLogic
        implements TudeySceneManager.TickParticipant
    {
        // documentation inherited from interface TudeySceneManager.TickParticipant
        public boolean tick (int timestamp)
        {
            execute(timestamp);
            return true;
        }

        @Override // documentation inherited
        protected void didInit ()
        {
            _scenemgr.addTickParticipant(this);
        }

        @Override // documentation inherited
        protected void wasRemoved ()
        {
            _scenemgr.removeTickParticipant(this);
        }
    }

    /**
     * Initializes the logic.
     */
    public void init (TudeySceneManager scenemgr, HandlerConfig config, Logic source)
    {
        super.init(scenemgr);
        _config = config;
        _source = source;
        _action = (config.action == null) ? null : createAction(config.action, source);

        // give subclasses a chance to initialize
        didInit();
    }

    /**
     * Notes that the logic has been removed.
     */
    public void removed ()
    {
        wasRemoved();
    }

    @Override // documentation inherited
    public Vector2f getTranslation ()
    {
        return _source.getTranslation();
    }

    @Override // documentation inherited
    public float getRotation ()
    {
        return _source.getRotation();
    }

    /**
     * Override to perform custom initialization.
     */
    protected void didInit ()
    {
        // nothing by default
    }

    /**
     * Override to perform custom cleanup.
     */
    protected void wasRemoved ()
    {
        // nothing by default
    }

    /**
     * Executes the handler's action.
     */
    protected void execute (int timestamp)
    {
        if (_action != null) {
            _action.execute(timestamp);
        }
    }

    /** The handler configuration. */
    protected HandlerConfig _config;

    /** The action source. */
    protected Logic _source;

    /** The action to execute in response to the event. */
    protected ActionLogic _action;
}