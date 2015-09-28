package coderslagoon.tclib.crypto;

/**
 * Hash algorithm abstraction.
 */
public interface Hash {
    /**
     * Minimum interface for a hash value producer.
     */
    public interface Producer extends Algorithm {
        /**
         * @return size of the hash, in bytes.
         */
        int hashSize();
        /**
         * Feed data into the instance.
         * @param buf The data buffer.
         * @param ofs Where to start reading in the buffer.
         * @param len Number of bytes to read out.
         */
        void update(byte[] buf, int ofs, int len);
        /**
         * Produces the final hash. The output must be of sufficient size.
         * @param hash Where to store the hash value.
         * @param ofs Where to start writing out the hash value.
         */
        void hash(byte[] hash, int ofs);
    }
    /**
     * Hash function definition as needed for TrueCrypt functionality.
     */
    public interface Function extends Producer {
        /**
         * Reset the instance, can be reused due to that.
         */
        void reset();
        /**
         * @return Size of the blocks the function consumes at a time.
         */
        int blockSize();
        /**
         * How often the function should be repeated for HMAC purposes. This is
         * the value which makes brute force password guessing slow.
         * @param advanced True to get a higher number suitable for modern CPUs. 
         * @return Iteration number.
         */
        int recommededHMACIterations(boolean advanced);
    }
}
