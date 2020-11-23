package com.cvt.iri.model;

import com.cvt.iri.hash.Curl;
import com.cvt.iri.hash.Sponge;
import com.cvt.iri.hash.SpongeFactory;
import com.cvt.iri.storage.Indexable;
import com.cvt.iri.utils.Converter;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Objects;

public final class Hash implements Serializable, Indexable, HashId {

    public static final int SIZE_IN_TRITS = 243;
    public static final int SIZE_IN_BYTES = 49;

    public static final Hash NULL_HASH = new Hash(new byte[Curl.HASH_LENGTH]);

    private final Object lock = new Object();
    private ByteSafe byteSafe;
    private TritSafe tritSafe;

    private final class ByteSafe {
        private final byte[] bytes;
        private final Integer hashcode;

        private ByteSafe(byte[] bytes) {
            Objects.requireNonNull(bytes, "ByteSafe is attempted to be initialized with a null byte array");
            this.bytes = bytes;
            this.hashcode = Arrays.hashCode(bytes);
        }
    }

    private final class TritSafe {
        private final byte[] trits;

        private TritSafe(byte[] trits) {
            this.trits = Objects.requireNonNull(trits, "TritSafe is attempted to be initialized with a null int array");
        }
    }


    public static Hash calculate(SpongeFactory.Mode mode, byte[] trits) {
        return calculate(trits, 0, trits.length, SpongeFactory.create(mode));
    }

    public static Hash calculate(byte[] bytes, int tritsLength, final Sponge curl) {
        byte[] trits = new byte[tritsLength];
        Converter.getTrits(bytes, trits);
        return calculate(trits, 0, tritsLength, curl);
    }

    public static Hash calculate(final byte[] tritsToCalculate, int offset, int length, final Sponge curl) {
        byte[] hashTrits = new byte[SIZE_IN_TRITS];
        curl.reset();
        curl.absorb(tritsToCalculate, offset, length);
        curl.squeeze(hashTrits, 0, SIZE_IN_TRITS);
        return new Hash(hashTrits);
    }

    public Hash() {
    }

    public Hash(final byte[] source, final int sourceOffset, final int sourceSize) {
        if(sourceSize < SIZE_IN_TRITS) {
            byte[] dest = new byte[SIZE_IN_BYTES];
            System.arraycopy(source, sourceOffset, dest, 0, sourceSize - sourceOffset > source.length ? source.length - sourceOffset : sourceSize);
            this.byteSafe = new ByteSafe(dest);
        } else {
            byte[] dest = new byte[SIZE_IN_TRITS];
            System.arraycopy(source, sourceOffset, dest, 0, dest.length);
            this.tritSafe = new TritSafe(dest);
        }
    }

    public Hash(final byte[] bytes) {
        this(bytes, 0, bytes.length == 243 ? SIZE_IN_TRITS : SIZE_IN_BYTES);
    }

    public Hash(final byte[] trits, final int offset) {
        this(trits, offset, SIZE_IN_TRITS);
    }

    public Hash(final String trytes) {
        this.tritSafe = new TritSafe(new byte[SIZE_IN_TRITS]);
        Converter.trits(trytes, this.tritSafe.trits, 0);
    }

    private void fullRead(byte[] src) {
        if (src != null) {
            synchronized (lock) {
                if (byteSafe != null || tritSafe != null) {
                    throw new IllegalStateException("I cannot be initialized with data twice.");
                }
                byte[] dest = new byte[SIZE_IN_BYTES];
                System.arraycopy(src, 0, dest, 0, Math.min(dest.length, src.length));
                byteSafe = new ByteSafe(dest);
            }
        }
    }

    public int trailingZeros() {
        final byte[] trits = trits();
        int index = SIZE_IN_TRITS;
        int zeros = 0;
        while (index-- > 0 && trits[index] == 0) {
            zeros++;
        }
        return zeros;
    }

    public byte[] trits() {
        TritSafe safe = tritSafe;
        if (safe == null) {
            synchronized (lock) {
                if (tritSafe == null) {
                    Objects.requireNonNull(byteSafe, "I need my bytes to be initialized in order to construct trits.");
                    byte[] src = bytes();
                    byte[] dest = new byte[Curl.HASH_LENGTH];
                    Converter.getTrits(src, dest);
                    tritSafe = new TritSafe(dest);
                }
                safe = tritSafe;
            }
        }
        return safe.trits;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Hash hash = (Hash) o;
        return Arrays.equals(bytes(), hash.bytes());
    }

    @Override
    public int hashCode() {
        bytes();
        return byteSafe.hashcode;
    }

    @Override
    public String toString() {
        return Converter.trytes(trits());
    }

    public byte[] bytes() {
        ByteSafe safe = byteSafe;
        if (safe == null) {
            synchronized (lock) {
                if (byteSafe == null) {
                    Objects.requireNonNull(tritSafe, "I need my trits to be initialized in order to construct bytes.");
                    byte[] src = trits();
                    byte[] dest = new byte[SIZE_IN_BYTES];
                    Converter.bytes(src, 0, dest, 0, src.length);
                    byteSafe = new ByteSafe(dest);
                }
                safe = byteSafe;
            }
        }
        return safe.bytes;
    }


    @Override
    public void read(byte[] src) {
        fullRead(src);
    }

    @Override
    public Indexable incremented() {
        return null;
    }

    @Override
    public Indexable decremented() {
        return null;
    }

    @Override
    public int compareTo(Indexable indexable) {
        Hash hash = (indexable instanceof Hash) ? (Hash) indexable : new Hash(indexable.bytes());
        if (this.equals(hash)) {
            return 0;
        }
        long diff = Converter.longValue(hash.trits(), 0, SIZE_IN_TRITS) - Converter.longValue(trits(), 0, SIZE_IN_TRITS);
        if (Math.abs(diff) > Integer.MAX_VALUE) {
            return diff > 0L ? Integer.MAX_VALUE : Integer.MIN_VALUE + 1;
        }
        return (int) diff;
    }
}