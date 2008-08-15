//
// $Id$

package com.threerings.math;

import com.threerings.export.Exportable;

/**
 * An axis-aligned rectangle.
 */
public final class Rect
    implements Exportable
{
    /**
     * Creates a rectangle with the values contained in the supplied minimum and maximum extents.
     */
    public Rect (Vector2f minExtent, Vector2f maxExtent)
    {
        set(minExtent, maxExtent);
    }

    /**
     * Copy constructor.
     */
    public Rect (Rect other)
    {
        set(other);
    }

    /**
     * Creates an empty rectangle.
     */
    public Rect ()
    {
        setToEmpty();
    }

    /**
     * Returns a reference to the rectangle's minimum extent.
     */
    public Vector2f getMinimumExtent ()
    {
        return _minExtent;
    }

    /**
     * Returns a reference to the rectangle's maximum extent.
     */
    public Vector2f getMaximumExtent ()
    {
        return _maxExtent;
    }

    /**
     * Determines whether the rectangle is empty (whether it contains the special values
     * {@link Vector2f#MAX_VALUE} and {@link Vector2f#MIN_VALUE} for its minimum
     * and maximum extents, respectively).
     */
    public boolean isEmpty ()
    {
        return _minExtent.equals(Vector2f.MAX_VALUE) && _maxExtent.equals(Vector2f.MIN_VALUE);
    }

    /**
     * Initializes this rectangle with the extents of an array of points.
     *
     * @return a reference to this rectangle, for chaining.
     */
    public Rect fromPoints (Vector2f[] points)
    {
        set(Vector2f.MAX_VALUE, Vector2f.MIN_VALUE);
        for (Vector2f point : points) {
            addLocal(point);
        }
        return this;
    }

    /**
     * Expands this rectangle in-place to include the specified point.
     *
     * @return a reference to this rectangle, for chaining.
     */
    public Rect addLocal (Vector2f point)
    {
        return add(point, this);
    }

    /**
     * Expands this rectangle to include the specified point.
     *
     * @return a new rectangle containing the result.
     */
    public Rect add (Vector2f point)
    {
        return add(point, new Rect());
    }

    /**
     * Expands this rectangle to include the specified point, placing the result in the object
     * provided.
     *
     * @return a reference to the result rectangle, for chaining.
     */
    public Rect add (Vector2f point, Rect result)
    {
        result.getMinimumExtent().set(
            Math.min(_minExtent.x, point.x),
            Math.min(_minExtent.y, point.y));
        result.getMaximumExtent().set(
            Math.max(_maxExtent.x, point.x),
            Math.max(_maxExtent.y, point.y));
        return result;
    }

    /**
     * Expands this rectangle to include the bounds of another rectangle.
     *
     * @return a reference to this rectangle, for chaining.
     */
    public Rect addLocal (Rect other)
    {
        return add(other, this);
    }

    /**
     * Expands this rectangle to include the bounds of another rectangle.
     *
     * @return a new rectangle containing the result.
     */
    public Rect add (Rect other)
    {
        return add(other, new Rect());
    }

    /**
     * Expands this rectangle to include the bounds of another rectangle, placing the result in the
     * object provided.
     *
     * @return a reference to the result rectangle, for chaining.
     */
    public Rect add (Rect other, Rect result)
    {
        Vector2f omin = other.getMinimumExtent(), omax = other.getMaximumExtent();
        result.getMinimumExtent().set(
            Math.min(_minExtent.x, omin.x),
            Math.min(_minExtent.y, omin.y));
        result.getMaximumExtent().set(
            Math.max(_maxExtent.x, omax.x),
            Math.max(_maxExtent.y, omax.y));
        return result;
    }

    /**
     * Transforms this rectangle in-place.
     *
     * @return a reference to this rectangle, for chaining.
     */
    public Rect transformLocal (Transform2D transform)
    {
        return transform(transform, this);
    }

    /**
     * Transforms this rectangle.
     *
     * @return a new rectangle containing the result.
     */
    public Rect transform (Transform2D transform)
    {
        return transform(transform, new Rect());
    }

    /**
     * Transforms this rectangle, placing the result in the provided object.
     *
     * @return a reference to the result rectangle, for chaining.
     */
    public Rect transform (Transform2D transform, Rect result)
    {
        // the corners of the rectangle cover the four permutations of ([minX|maxX], [minY|maxY]).
        // to find the new minimum and maximum for each element, we transform selecting either the
        // minimum or maximum for each component based on whether it will increase or decrease the
        // total (which depends on the sign of the matrix element).
        transform.update(Transform3D.AFFINE);
        Matrix3f matrix = transform.getMatrix();
        float minx =
            matrix.m00 * (matrix.m00 > 0f ? _minExtent.x : _maxExtent.x) +
            matrix.m10 * (matrix.m10 > 0f ? _minExtent.y : _maxExtent.y) + matrix.m20;
        float miny =
            matrix.m01 * (matrix.m01 > 0f ? _minExtent.x : _maxExtent.x) +
            matrix.m11 * (matrix.m11 > 0f ? _minExtent.y : _maxExtent.y) + matrix.m21;
        float maxx =
            matrix.m00 * (matrix.m00 < 0f ? _minExtent.x : _maxExtent.x) +
            matrix.m10 * (matrix.m10 < 0f ? _minExtent.y : _maxExtent.y) + matrix.m20;
        float maxy =
            matrix.m01 * (matrix.m01 < 0f ? _minExtent.x : _maxExtent.x) +
            matrix.m11 * (matrix.m11 < 0f ? _minExtent.y : _maxExtent.y) + matrix.m21;
        result.getMinimumExtent().set(minx, miny);
        result.getMaximumExtent().set(maxx, maxy);
        return result;
    }

    /**
     * Expands the rectangle in-place by the specified amounts.
     *
     * @return a reference to this rectangle, for chaining.
     */
    public Rect expandLocal (float x, float y)
    {
        return expand(x, y, this);
    }

    /**
     * Expands the rectangle by the specified amounts.
     *
     * @return a new rectangle containing the result.
     */
    public Rect expand (float x, float y)
    {
        return expand(x, y, new Rect());
    }

    /**
     * Expands the rectangle by the specified amounts, placing the result in the object provided.
     *
     * @return a reference to the result rectangle, for chaining.
     */
    public Rect expand (float x, float y, Rect result)
    {
        result.getMinimumExtent().set(_minExtent.x - x, _minExtent.y - y);
        result.getMaximumExtent().set(_maxExtent.x + x, _maxExtent.y + y);
        return result;
    }

    /**
     * Sets the parameters of the rectangle to the empty values ({@link Vector2f#MAX_VALUE} and
     * {@link Vector2f#MIN_VALUE} for the minimum and maximum, respectively).
     *
     * @return a reference to this rectangle, for chaining.
     */
    public Rect setToEmpty ()
    {
        return set(Vector2f.MAX_VALUE, Vector2f.MIN_VALUE);
    }

    /**
     * Copies the parameters of another rectangle.
     *
     * @return a reference to this rectangle, for chaining.
     */
    public Rect set (Rect other)
    {
        return set(other.getMinimumExtent(), other.getMaximumExtent());
    }

    /**
     * Sets the rectangle parameters to the values contained in the supplied vectors.
     *
     * @return a reference to this rectangle, for chaining.
     */
    public Rect set (Vector2f minExtent, Vector2f maxExtent)
    {
        _minExtent.set(minExtent);
        _maxExtent.set(maxExtent);
        return this;
    }

    /**
     * Retrieves one of the four vertices of the rectangle.  The code parameter identifies the
     * vertex with flags indicating which values should be selected from the minimum extent, and
     * which from the maximum extent.  For example, the code 01b selects the vertex with the
     * minimum x and maximum y.
     *
     * @return a reference to the result, for chaining.
     */
    public Vector2f getVertex (int code, Vector2f result)
    {
        return result.set(
            ((code & (1 << 1)) == 0) ? _minExtent.x : _maxExtent.x,
            ((code & (1 << 0)) == 0) ? _minExtent.y : _maxExtent.y);
    }

    /**
     * Determines whether this rectangle contains the specified point.
     */
    public boolean contains (Vector2f point)
    {
        return contains(point.x, point.y);
    }

    /**
     * Determines whether this rectangle contains the specified point.
     */
    public boolean contains (float x, float y)
    {
        return x >= _minExtent.x && x <= _maxExtent.x &&
            y >= _minExtent.y && y <= _maxExtent.y;
    }

    /**
     * Determines whether this rectangle intersects the specified other rectangle.
     */
    public boolean intersects (Rect other)
    {
        return _maxExtent.x >= other._minExtent.x && _minExtent.x <= other._maxExtent.x &&
            _maxExtent.y >= other._minExtent.y && _minExtent.y <= other._maxExtent.y;
    }

    @Override // documentation inherited
    public String toString ()
    {
        return "[min=" + _minExtent + ", max=" + _maxExtent + "]";
    }

    /** The rectangle's minimum extent. */
    protected Vector2f _minExtent = new Vector2f();

    /** The rectangle's maximum extent. */
    protected Vector2f _maxExtent = new Vector2f();
}