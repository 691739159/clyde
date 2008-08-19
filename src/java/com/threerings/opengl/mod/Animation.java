//
// $Id$

package com.threerings.opengl.mod;

import java.util.ArrayList;

import com.samskivert.util.ObserverList;

import com.threerings.config.ConfigEvent;
import com.threerings.config.ConfigReference;
import com.threerings.config.ConfigUpdateListener;
import com.threerings.expr.Function;
import com.threerings.expr.ObjectExpression.Evaluator;
import com.threerings.expr.Scope;
import com.threerings.expr.SimpleScope;
import com.threerings.expr.util.ScopeUtil;
import com.threerings.math.Transform3D;

import com.threerings.opengl.model.config.AnimationConfig;
import com.threerings.opengl.util.GlContext;

/**
 * An animation for an {@link Articulated} model.
 */
public class Animation extends SimpleScope
    implements ConfigUpdateListener<AnimationConfig>
{
    /**
     * The actual animation implementation.
     */
    public static abstract class Implementation extends SimpleScope
    {
        /**
         * Creates a new implementation.
         */
        public Implementation (Scope parentScope)
        {
            super(parentScope);
        }

        /**
         * Starts the animation.
         */
        public void start ()
        {
            // blend in
            blendToWeight(_config.weight, _config.blendIn);

            // notify containing animation
            ((Animation)_parentScope).started(
                (_config.override && _config.weight == 1f) ? _config.blendIn : -1f);
        }

        /**
         * Stops the animation.
         */
        public void stop ()
        {
            stop(_config.blendOut);
        }

        /**
         * Stops the animation, blending out over the specified interval.
         */
        public void stop (float blendOut)
        {
            // blend out
            blendToWeight(0f, blendOut);
        }

        /**
         * Determines whether the animation is currently playing.
         */
        public boolean isPlaying ()
        {
            return _weight > 0f || _targetWeight > 0f;
        }

        /**
         * Returns the priority of this animation.
         */
        public int getPriority ()
        {
            return _config.priority;
        }

        /**
         * Updates this animation based on the elapsed time in seconds.
         *
         * @return true if the animation has completed.
         */
        public boolean tick (float elapsed)
        {
            // update the weight
            if (_weight < _targetWeight) {
                _weight = Math.min(_weight + elapsed*_weightRate, _targetWeight);
            } else if (_weight > _targetWeight) {
                _weight = Math.max(_weight + elapsed*_weightRate, _targetWeight);
            }
            // if the weight is zero, we're done
            if (_weight == 0f && _targetWeight == 0f) {
                ((Animation)_parentScope).stopped(false);
            }
            return false;
        }

        /**
         * Checks whether this animation has completed.
         */
        public boolean hasCompleted ()
        {
            return false;
        }

        /**
         * Updates the transforms directly from this animation.
         */
        public void updateTransforms ()
        {
            // nothing by default
        }

        /**
         * Blends in the influence of this animation.
         *
         * @param update the current value of the update counter (used to determine which nodes
         * have been touched on this update).
         */
        public void blendTransforms (int update)
        {
            // nothing by default
        }

        @Override // documentation inherited
        public String getScopeName ()
        {
            return "impl";
        }

        /**
         * (Re)configures the implementation.
         */
        protected void setConfig (AnimationConfig.Original config)
        {
            _config = config;
        }

        /**
         * Blends to a target weight over an interval specified in seconds.
         */
        protected void blendToWeight (float weight, float interval)
        {
            _targetWeight = weight;
            if (interval > 0f) {
                _weightRate = (_targetWeight - _weight) / interval;
            } else {
                _weight = _targetWeight;
            }
        }

        /** The implementation configuration. */
        protected AnimationConfig.Original _config;

        /** The current weight of the animation. */
        protected float _weight;

        /** The target weight of the animation. */
        protected float _targetWeight;

        /** The weight's current rate of change. */
        protected float _weightRate;
    }

    /**
     * An imported implementation.
     */
    public static class Imported extends Implementation
    {
        /**
         * Creates a new imported implementation.
         */
        public Imported (Scope parentScope, AnimationConfig.Imported config)
        {
            super(parentScope);
            setConfig(config);
        }

        /**
         * (Re)configures the implementation.
         */
        public void setConfig (AnimationConfig.Imported config)
        {
            super.setConfig(_config = config);

            // resolve the targets and initialize the snapshot array
            _targets = new Articulated.Node[config.targets.length];
            _snapshot = new Transform3D[_targets.length];
            Function getNode = ScopeUtil.resolve(_parentScope, "getNode", Function.NULL);
            for (int ii = 0; ii < _targets.length; ii++) {
                _targets[ii] = (Articulated.Node)getNode.call(config.targets[ii]);
                if (_targets[ii] != null) {
                    _snapshot[ii] = new Transform3D();
                }
            }
        }

        @Override // documentation inherited
        public void start ()
        {
            // initialize frame counter
            _fidx = 0;
            _accum = 0f;
            _completed = false;

            // if transitioning, take a snapshot of the current transforms
            if (_transitioning = (_config.transition > 0f)) {
                for (int ii = 0; ii < _targets.length; ii++) {
                    Articulated.Node target = _targets[ii];
                    if (target != null) {
                        _snapshot[ii].set(target.getLocalTransform());
                    }
                }
            }

            // if blending out, store countdown time
            if (_counting = (!_config.loop && _config.blendOut > 0f)) {
                _countdown = _config.getDuration() - _config.blendOut;
            }

            // blend in
            super.start();
        }

        @Override // documentation inherited
        public boolean isPlaying ()
        {
            return super.isPlaying() && !hasCompleted();
        }

        @Override // documentation inherited
        public boolean tick (float elapsed)
        {
            // see if we need to start blending out
            if (_counting && (_countdown -= elapsed) <= 0f) {
                blendToWeight(0f, _config.blendOut);
            }

            // update the weight
            super.tick(elapsed);
            if (!isPlaying()) {
                return false;
            }

            // if we're transitioning, update the accumulated portion based on transition time
            if (_transitioning) {
                _accum += (elapsed / _config.transition);
                if (_accum < 1f) {
                    return false; // still transitioning
                }
                // done transitioning; fix accumulated frames and clear transition flag
                _accum = (_accum - 1f) * _config.transition * _config.getScaledRate();
                _transitioning = false;

            // otherwise, based on frame rate
            } else {
                _accum += (elapsed * _config.getScaledRate());
            }
            int frames = (int)_accum;
            _accum -= frames;

            // advance the frame index
            int fcount = _config.transforms.length;
            if (_config.loop) {
                _fidx = (_fidx + frames) % (fcount - (_config.skipLastFrame ? 1 : 0));

            } else if ((_fidx += frames) >= fcount - 1) {
                _fidx = fcount - 1;
                _accum = 0f;
                _completed = true;
                ((Animation)_parentScope).stopped(true);
                return true;
            }
            return false;
        }

        @Override // documentation inherited
        public boolean hasCompleted ()
        {
            return _completed;
        }

        @Override // documentation inherited
        public void updateTransforms ()
        {
            Transform3D[][] transforms = _config.transforms;
            Transform3D[] t1, t2;
            if (_transitioning) {
                t1 = _snapshot;
                t2 = transforms[0];
            } else {
                t1 = transforms[_fidx];
                t2 = transforms[(_fidx + 1) % transforms.length];
            }
            for (int ii = 0; ii < _targets.length; ii++) {
                // lerp into the target transform
                Articulated.Node target = _targets[ii];
                if (target != null) {
                    t1[ii].lerp(t2[ii], _accum, target.getLocalTransform());
                }
            }
        }

        @Override // documentation inherited
        public void blendTransforms (int update)
        {
            Transform3D[][] transforms = _config.transforms;
            Transform3D[] t1, t2;
            if (_transitioning) {
                t1 = _snapshot;
                t2 = transforms[0];
            } else {
                t1 = transforms[_fidx];
                t2 = transforms[(_fidx + 1) % transforms.length];
            }
            for (int ii = 0; ii < _targets.length; ii++) {
                // first make sure the target exists
                Articulated.Node target = _targets[ii];
                if (target == null) {
                    continue;
                }
                // then see if we're the first to touch it, in which case we can lerp directly
                if (target.lastUpdate != update) {
                    t1[ii].lerp(t2[ii], _accum, target.getLocalTransform());
                    target.lastUpdate = update;
                    target.totalWeight = _weight;
                    continue;
                }
                // if the total weight is less than one, we can add our contribution
                if (target.totalWeight >= 1f) {
                    continue;
                }
                float weight = Math.min(_weight, 1f - target.totalWeight);
                t1[ii].lerp(t2[ii], _accum, _xform);
                target.getLocalTransform().lerpLocal(
                    _xform, weight / (target.totalWeight += weight));
            }
        }

        @Override // documentation inherited
        protected void blendToWeight (float weight, float interval)
        {
            super.blendToWeight(weight, interval);
            if (weight == 0f) {
                _counting = false; // cancel any plans to blend out
            }
        }

        /** The implementation configuration. */
        protected AnimationConfig.Imported _config;

        /** The targets of the animation. */
        protected Articulated.Node[] _targets;

        /** A snapshot of the original transforms of the targets, for transitioning. */
        protected Transform3D[] _snapshot;

        /** Whether we are currently transitioning into the first frame. */
        protected boolean _transitioning;

        /** Whether we are counting down until we must blend out. */
        protected boolean _counting;

        /** The time remaining until we have to start blending the animation out. */
        protected float _countdown;

        /** The index of the current animation frame. */
        protected int _fidx;

        /** The progress towards the next frame. */
        protected float _accum;

        /** Set when the animation has completed. */
        protected boolean _completed;

        /** A temporary transform for interpolation. */
        protected Transform3D _xform = new Transform3D();
    }

    /**
     * A procedural implementation.
     */
    public static class Procedural extends Implementation
    {
        /**
         * Creates a new procedural implementation.
         */
        public Procedural (Scope parentScope, AnimationConfig.Procedural config)
        {
            super(parentScope);
            setConfig(config);
        }

        /**
         * (Re)configures the implementation.
         */
        public void setConfig (AnimationConfig.Procedural config)
        {
            super.setConfig(_config = config);

            // create the target transforms
            Function getNode = ScopeUtil.resolve(_parentScope, "getNode", Function.NULL);
            _transforms = new TargetTransform[config.transforms.length];
            for (int ii = 0; ii < _transforms.length; ii++) {
                AnimationConfig.TargetTransform transform = config.transforms[ii];
                ArrayList<Articulated.Node> targets = new ArrayList<Articulated.Node>();
                for (String target : transform.targets) {
                    Articulated.Node node = (Articulated.Node)getNode.call(target);
                    if (node != null) {
                        targets.add(node);
                    }
                }
                _transforms[ii] = new TargetTransform(
                    targets.toArray(new Articulated.Node[targets.size()]),
                    transform.expression.createEvaluator(this));
            }
        }

        /**
         * Pairs a node with its transform evaluator.
         */
        protected static class TargetTransform
        {
            /** The nodes to update. */
            public Articulated.Node[] targets;

            /** The expression evaluator for the transform. */
            public Evaluator<Transform3D> evaluator;

            public TargetTransform (Articulated.Node[] targets, Evaluator<Transform3D> evaluator)
            {
                this.targets = targets;
                this.evaluator = evaluator;
            }
        }

        /** The implementation configuration. */
        protected AnimationConfig.Procedural _config;

        /** The target transforms. */
        protected TargetTransform[] _transforms;
    }

    /** An empty array of animations. */
    public static final Animation[] EMPTY_ARRAY = new Animation[0];

    /**
     * Creates a new animation.
     */
    public Animation (GlContext ctx, Scope parentScope)
    {
        super(parentScope);
        _ctx = ctx;
    }

    /**
     * Sets the configuration of this animation.
     */
    public void setConfig (String name, ConfigReference<AnimationConfig> ref)
    {
        setConfig(name, _ctx.getConfigManager().getConfig(AnimationConfig.class, ref));
    }

    /**
     * Sets the configuration of this animation.
     */
    public void setConfig (String name, AnimationConfig config)
    {
        _name = name;
        if (_config != null) {
            _config.removeListener(this);
        }
        if ((_config = config) != null) {
            _config.addListener(this);
        }
        updateFromConfig();
    }

    /**
     * Returns the name of the animation.
     */
    public String getName ()
    {
        return _name;
    }

    /**
     * Starts playing this animation.
     */
    public void start ()
    {
        _impl.start();
    }

    /**
     * Stops playing this animation.
     */
    public void stop ()
    {
        _impl.stop();
    }

    /**
     * Stops playing this animation, blending it out over the specified interval
     * (as opposed to its default interval).
     */
    public void stop (float blendOut)
    {
        _impl.stop(blendOut);
    }

    /**
     * Determines whether this animation is playing.
     */
    public boolean isPlaying ()
    {
        return _impl.isPlaying();
    }

    /**
     * Adds an observer to this animation.
     */
    public void addObserver (AnimationObserver observer)
    {
        if (_observers == null) {
            _observers = ObserverList.newFastUnsafe();
        }
        _observers.add(observer);
    }

    /**
     * Removes an observer from this animation.
     */
    public void removeObserver (AnimationObserver observer)
    {
        if (_observers == null) {
            return;
        }
        _observers.remove(observer);
        if (_observers.isEmpty()) {
            _observers = null;
        }
    }

    /**
     * Returns the priority of this animation.
     */
    public int getPriority ()
    {
        return _impl.getPriority();
    }

    /**
     * Updates this animation based on the elapsed time in seconds.
     *
     * @return true if the animation has completed.
     */
    public boolean tick (float elapsed)
    {
        return _impl.tick(elapsed);
    }

    /**
     * Checks whether the animation has just completed, and thus should be removed from the
     * playing list after a final update has been performed.
     */
    public boolean hasCompleted ()
    {
        return _impl.hasCompleted();
    }

    /**
     * Updates the transforms directly from this animation.
     */
    public void updateTransforms ()
    {
        _impl.updateTransforms();
    }

    /**
     * Blends in the influence of this animation.
     *
     * @param update the current value of the update counter (used to determine which nodes have
     * been touched on this update).
     */
    public void blendTransforms (int update)
    {
        _impl.blendTransforms(update);
    }

    // documentation inherited from interface ConfigUpdateListener
    public void configUpdated (ConfigEvent<AnimationConfig> event)
    {
        updateFromConfig();
    }

    @Override // documentation inherited
    public String getScopeName ()
    {
        return "animation";
    }

    @Override // documentation inherited
    public String toString ()
    {
        return _name;
    }

    /**
     * Updates the model to match its new or modified configuration.
     */
    protected void updateFromConfig ()
    {
        Implementation nimpl = (_config == null) ?
            null : _config.getAnimationImplementation(_ctx, this, _impl);
        _impl = (nimpl == null) ? NULL_IMPLEMENTATION : nimpl;
    }

    /**
     * Notes that the animation started.
     *
     * @param overrideBlendOut if non-negative, an interval over which to blend out all
     * animations currently playing at the same priority level as this one.
     */
    protected void started (float overrideBlendOut)
    {
        // notify the containing implementation
        ((Articulated)_parentScope).animationStarted(this, overrideBlendOut);

        // notify observers
        applyStartedOp(_observers, this);
    }

    /**
     * Notes that the animation stopped.
     */
    protected void stopped (boolean completed)
    {
        // notify the containing implementation
        ((Articulated)_parentScope).animationStopped(this, completed);

        // notify observers
        applyStoppedOp(_observers, this, completed);
    }

    /**
     * Applies the {@link #_startedOp} to the supplied list of observers.
     */
    protected static void applyStartedOp (
        ObserverList<AnimationObserver> observers, Animation animation)
    {
        if (observers != null) {
            _startedOp.init(animation);
            observers.apply(_startedOp);
        }
    }

    /**
     * Applies the {@link #_stoppedOp} to the supplied list of observers.
     */
    protected static void applyStoppedOp (
        ObserverList<AnimationObserver> observers, Animation animation, boolean completed)
    {
        if (observers != null) {
            _stoppedOp.init(animation, completed);
            observers.apply(_stoppedOp);
        }
    }

    /**
     * An {@link ObserverList.ObserverOp} that calls {@link AnimationObserver#animationStarted}.
     */
    protected static class StartedOp
        implements ObserverList.ObserverOp<AnimationObserver>
    {
        /**
         * (Re)initializes this op.
         */
        public void init (Animation animation)
        {
            _animation = animation;
        }

        // documentation inherited from interface ObserverOp
        public boolean apply (AnimationObserver observer)
        {
            return observer.animationStarted(_animation);
        }

        /** The started animation. */
        protected Animation _animation;
    }

    /**
     * An {@link ObserverList.ObserverOp} that calls {@link AnimationObserver#animationStopped}.
     */
    protected static class StoppedOp
        implements ObserverList.ObserverOp<AnimationObserver>
    {
        /**
         * (Re)initializes this op.
         */
        public void init (Animation animation, boolean completed)
        {
            _animation = animation;
            _completed = completed;
        }

        // documentation inherited from interface ObserverOp
        public boolean apply (AnimationObserver observer)
        {
            return observer.animationStopped(_animation, _completed);
        }

        /** The started animation. */
        protected Animation _animation;

        /** Whether or not the animation completed. */
        protected boolean _completed;
    }

    /** The application context. */
    protected GlContext _ctx;

    /** The name of the animation. */
    protected String _name;

    /** The configuration of this animation. */
    protected AnimationConfig _config;

    /** The animation implementation. */
    protected Implementation _impl = NULL_IMPLEMENTATION;

    /** The lazily-initialized list of animation observers. */
    protected ObserverList<AnimationObserver> _observers;

    /** Started op to reuse. */
    protected static StartedOp _startedOp = new StartedOp();

    /** Stopped op to reuse. */
    protected static StoppedOp _stoppedOp = new StoppedOp();

    /** An implementation that does nothing. */
    protected static final Implementation NULL_IMPLEMENTATION = new Implementation(null) {
    };
}