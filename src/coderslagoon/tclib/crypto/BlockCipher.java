package coderslagoon.tclib.crypto;

/**
 * Block cipher abstraction.
 */
public abstract class BlockCipher implements Algorithm, Cloneable {
    /** Operation modes. */
    public enum Mode {
        /** Encrypt data. Plaintext to ciphertext. */
        ENCRYPT,
        /** Decrypt data. Ciphertext to plaintext. */
        DECRYPT
    }
    /** @return Size of a a block in bytes. Usually 8, 16 or 32. */
    public abstract int blockSize();
    /** @return Size of the key material, in bytes. */
    public abstract int keySize();
    /**
     * Initialize the instance.
     * @param mode The operation mode.
     * @param key Buffer to the key material.
     * @param ofs Where the key material starts in the buffer.
     */
    public void initialize(Mode mode, byte[] key, int ofs) {
        this.mode = mode;
    }
    /**
     * Processes one block of plain- or ciphertext.
     * @param in The input buffer.
     * @param ofs_i Where the block is read from in the input buffer.
     * @param out The output buffer.
     * @param ofs_o Where the processed block is written to the output buffer.
     */
    public abstract void processBlock(byte[] in, int ofs_i, byte[] out, int ofs_o);
    /**
     * @return The operation mode.
     */
    public Mode mode() {
        return this.mode;
    }
    /** The operation mode. Accessible for cloning purposes. */
    protected Mode mode;
    /**
     * @return Deep copy of the instance.
     */
    public abstract Object clone();
}
