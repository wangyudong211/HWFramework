package org.apache.http.util;

@Deprecated
public final class ByteArrayBuffer {
    private byte[] buffer;
    private int len;

    public ByteArrayBuffer(int capacity) {
        if (capacity >= 0) {
            this.buffer = new byte[capacity];
            return;
        }
        throw new IllegalArgumentException("Buffer capacity may not be negative");
    }

    private void expand(int newlen) {
        byte[] newbuffer = new byte[Math.max(this.buffer.length << 1, newlen)];
        System.arraycopy(this.buffer, 0, newbuffer, 0, this.len);
        this.buffer = newbuffer;
    }

    public void append(byte[] b, int off, int len2) {
        if (b != null) {
            if (off < 0 || off > b.length || len2 < 0 || off + len2 < 0 || off + len2 > b.length) {
                throw new IndexOutOfBoundsException();
            } else if (len2 != 0) {
                int newlen = this.len + len2;
                if (newlen > this.buffer.length) {
                    expand(newlen);
                }
                System.arraycopy(b, off, this.buffer, this.len, len2);
                this.len = newlen;
            }
        }
    }

    public void append(int b) {
        int newlen = this.len + 1;
        if (newlen > this.buffer.length) {
            expand(newlen);
        }
        this.buffer[this.len] = (byte) b;
        this.len = newlen;
    }

    public void append(char[] b, int off, int len2) {
        if (b != null) {
            if (off < 0 || off > b.length || len2 < 0 || off + len2 < 0 || off + len2 > b.length) {
                throw new IndexOutOfBoundsException();
            } else if (len2 != 0) {
                int oldlen = this.len;
                int newlen = oldlen + len2;
                if (newlen > this.buffer.length) {
                    expand(newlen);
                }
                int i1 = off;
                for (int i2 = oldlen; i2 < newlen; i2++) {
                    this.buffer[i2] = (byte) b[i1];
                    i1++;
                }
                this.len = newlen;
            }
        }
    }

    public void append(CharArrayBuffer b, int off, int len2) {
        if (b != null) {
            append(b.buffer(), off, len2);
        }
    }

    public void clear() {
        this.len = 0;
    }

    public byte[] toByteArray() {
        byte[] b = new byte[this.len];
        if (this.len > 0) {
            System.arraycopy(this.buffer, 0, b, 0, this.len);
        }
        return b;
    }

    public int byteAt(int i) {
        return this.buffer[i];
    }

    public int capacity() {
        return this.buffer.length;
    }

    public int length() {
        return this.len;
    }

    public byte[] buffer() {
        return this.buffer;
    }

    public void setLength(int len2) {
        if (len2 < 0 || len2 > this.buffer.length) {
            throw new IndexOutOfBoundsException();
        }
        this.len = len2;
    }

    public boolean isEmpty() {
        return this.len == 0;
    }

    public boolean isFull() {
        return this.len == this.buffer.length;
    }
}
