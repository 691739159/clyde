//
// $Id$

package com.threerings.opengl.renderer.config;

import java.nio.FloatBuffer;

import org.lwjgl.opengl.GL11;

import com.threerings.export.Exportable;
import com.threerings.util.DeepObject;

import com.threerings.opengl.renderer.ClientArray;

/**
 * Contains the configuration of a single client array.
 */
public class ClientArrayConfig extends DeepObject
    implements Exportable
{
    /** Type constants. */
    public enum Type
    {
        BYTE(GL11.GL_BYTE, 1),
        UNSIGNED_BYTE(GL11.GL_UNSIGNED_BYTE, 1),
        SHORT(GL11.GL_SHORT, 2),
        UNSIGNED_SHORT(GL11.GL_UNSIGNED_SHORT, 2),
        INT(GL11.GL_INT, 4),
        UNSIGNED_INT(GL11.GL_UNSIGNED_INT, 4),
        FLOAT(GL11.GL_FLOAT, 4),
        DOUBLE(GL11.GL_DOUBLE, 8);

        public int getConstant ()
        {
            return _constant;
        }

        public int getBytes ()
        {
            return _bytes;
        }

        Type (int constant, int bytes)
        {
            _constant = constant;
            _bytes = bytes;
        }

        protected int _constant, _bytes;
    }

    /** The number of components in each element. */
    public int size;

    /** The type of the components. */
    public Type type;

    /** Whether or not to normalize the components. */
    public boolean normalized;

    /** The stride between adjacent elements. */
    public int stride;

    /** The offset of the first component. */
    public int offset;

    /** The float array, if using one. */
    public FloatBuffer floatArray;

    public ClientArrayConfig (int size)
    {
        this(size, null);
    }

    public ClientArrayConfig (int size, FloatBuffer floatArray)
    {
        this(size, 0, 0, floatArray);
    }

    public ClientArrayConfig (int size, int stride, int offset, FloatBuffer floatArray)
    {
        this.size = size;
        this.type = Type.FLOAT;
        this.stride = stride;
        this.offset = offset;
        this.floatArray = floatArray;
    }

    public ClientArrayConfig ()
    {
    }

    /**
     * Returns the number of bytes in each element.
     */
    public int getElementBytes ()
    {
        return size * type.getBytes();
    }

    /**
     * Creates a client array from this config.
     */
    public ClientArray createClientArray ()
    {
        return new ClientArray(
            size, type.getConstant(), normalized, stride, offset, null, floatArray);
    }

    /**
     * Populates the supplied client array with the data in this config.
     */
    public void populateClientArray (ClientArray array)
    {
        FloatBuffer src = floatArray, dest = array.floatArray;
        int sstride = stride / 4, dstride = array.stride / 4;
        int sidx = offset / 4, didx = (int)array.offset / 4;
        float[] value = new float[size];
        for (int ii = 0, nn = src.capacity() / sstride; ii < nn; ii++) {
            src.position(sidx);
            src.get(value);

            dest.position(didx);
            dest.put(value);

            sidx += sstride;
            didx += dstride;
        }
        src.rewind();
        dest.rewind();
    }

    /**
     * Extracts the contents of this array into the specified float array.
     *
     * @param doffset the offset within the array at which to place the first element.
     * @param dstride the stride between adjacent elements within the array.
     */
    public void populateFloatArray (float[] array, int doffset, int dstride)
    {
        FloatBuffer src = floatArray;
        int sstride = stride / 4;
        int sidx = offset / 4, didx = doffset;
        for (int ii = 0, nn = src.capacity() / sstride; ii < nn; ii++) {
            src.position(sidx);
            src.get(array, didx, size);

            sidx += sstride;
            didx += dstride;
        }
        src.rewind();
    }

    /**
     * Extracts the contents of this array into the specified int array.
     *
     * @param doffset the offset within the array at which to place the first element.
     * @param dstride the stride between adjacent elements within the array.
     */
    public void populateIntArray (int[] array, int doffset, int dstride)
    {
        FloatBuffer src = floatArray;
        int sstride = stride / 4;
        int sidx = offset / 4, didx = doffset;
        for (int ii = 0, nn = src.capacity() / sstride; ii < nn; ii++) {
            for (int jj = 0; jj < size; jj++) {
                array[didx + jj] = (int)src.get(sidx + jj);
            }
            sidx += sstride;
            didx += dstride;
        }
        src.rewind();
    }
}
