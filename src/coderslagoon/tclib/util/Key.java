package coderslagoon.tclib.util;

import java.util.Arrays;

/**
 * Key holder. To ensure that sensitive key material gets erased safely.
 */
public interface Key extends Erasable {

    /**
     * @return The key material. Not a copy, so be careful.
     * @throws ErasedException If the key has been erased already.
     */
    public byte[] data() throws ErasedException;

    /** To detect already-erased issue. */
    public static class ErasedException extends TCLibException {
        private static final long serialVersionUID = 2600402929643683456L;
    }

    /**
     * Key which is wrapping a byte array
     */
    public static class ByteArray implements Key {
        protected byte[] data;

        protected ByteArray() {
        }

        public ByteArray(byte[] key) {
            this.data = key;
        }

        @Override
        public byte[] data() throws ErasedException {
            if (null == this.data) {
                throw new ErasedException();
            }
            return this.data;
        }

        @Override
        public void erase() {
            if (null != this.data) {
                Arrays.fill(this.data, (byte)0);
                this.data = null;
            }
        }
    }
}
