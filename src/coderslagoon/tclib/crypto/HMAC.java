package coderslagoon.tclib.crypto;

import java.util.Arrays;

import coderslagoon.baselib.util.BinUtils;

/**
 * HMAC implementation.
 */
public class HMAC implements Hash.Producer {

    private Hash.Function hfnc;
    private byte[] k_opad, k_ipad;
    private String name;

    /**
     * Initializes the instance.
     * @param hfnc The hash function to use.
     * @param key The key material.
     * @param ofs Where the key material is stored.
     * @param len Length of the key material in bytes.
     */
    public void initialize(Hash.Function hfnc, byte[] key, int ofs, int len) {
        this.hfnc = hfnc;
        this.name = "HMAC-" + hfnc.name();
        this.k_opad = new byte[hfnc.blockSize()];
        this.k_ipad = new byte[this.k_opad.length];
        reset(key, ofs, len);
    }

    @Override
    public String name() {
        return this.name;
    }

    @Override
    public void erase() {
        if (null != this.k_opad) Arrays.fill(this.k_opad, (byte)0);
        if (null != this.k_ipad) Arrays.fill(this.k_ipad, (byte)0);
        if (null != this.hfnc) this.hfnc.erase();
    }

    private final static int OPAD_VALUE = 0x5c;
    private final static int IPAD_VALUE = 0x36;

    /**
     * Reset the instance with a new key.
     * @param key The key material.
     * @param ofs Where the key material is stored.
     * @param len Length of the key material in bytes.
     */
    public void reset(byte[] key, int ofs, int len) {
        if (null != key) {
            if (len > this.k_opad.length) {
                this.hfnc.reset();
                this.hfnc.update(key, ofs, len);
                this.hfnc.hash(this.k_opad, 0);
                len = this.hfnc.hashSize();

                for (int i = 0; i < len; i++) {
                    this.k_ipad[i] = (byte)(this.k_opad[i] ^ IPAD_VALUE);
                    this.k_opad[i] ^= OPAD_VALUE;
                }
            }
            else {
                for (int i = 0; i < len; i++) {
                    int ki = key[ofs + i];
                    this.k_opad[i] = (byte)(ki ^ OPAD_VALUE);
                    this.k_ipad[i] = (byte)(ki ^ IPAD_VALUE);
                }
            }
            for (int i = len; i < this.k_opad.length; i++) {
                this.k_opad[i] = OPAD_VALUE;
                this.k_ipad[i] = IPAD_VALUE;
            }
        }

        this.hfnc.reset();
        this.hfnc.update(this.k_ipad, 0, this.k_opad.length);
    }

    @Override
    public void hash(byte[] hash, int ofs) {
        this.hfnc.hash(hash, ofs);
        this.hfnc.reset();
        this.hfnc.update(this.k_opad, 0, this.k_opad.length);
        this.hfnc.update(hash, ofs, this.hfnc.hashSize());
        this.hfnc.hash(hash, ofs);
    }

    @Override
    public int hashSize() {
        return this.hfnc.hashSize();
    }

    @Override
    public void update(byte[] buf, int ofs, int len) {
        this.hfnc.update(buf, ofs, len);
    }

    @Override
    public void test() throws Throwable {
        final byte[][] REF_KEYS_RIPEMD160 = {
            BinUtils.hexStrToBytes("cc00112233445566778899aabbccddeeff01234567"),
            BinUtils.hexStrToBytes("cc0123456789abcdeffedcba987654321000112233"),
        };
        final byte[][] REF_DATA_RIPEMD160 = {
            "message digest".getBytes(),
            "12345678901234567890123456789012345678901234567890123456789012345678901234567890".getBytes()
        };
        final byte[][] REF_HASHES_RIPEMD160 = {
            BinUtils.hexStrToBytes("f83662cc8d339c227e600fcd636c57d2571b1c34"),
            BinUtils.hexStrToBytes("85f164703e61a63131be7e45958e0794123904f9")
        };

        HMAC hmac = new HMAC();

        for (int i = 0; i < REF_KEYS_RIPEMD160.length; i++) {
            byte[] key = REF_KEYS_RIPEMD160[i];
            if (0 == i) {
                hmac.initialize(new RIPEMD160(), key, 1, key.length - 1);
            }
            else {
                hmac.reset(key, 1, key.length - 1);
            }

            hmac.update(
                    REF_DATA_RIPEMD160[i], 0,
                    REF_DATA_RIPEMD160[i].length);

            byte[] hash = new byte[1 + hmac.hashSize() + 1];
            hash[0              ] = (byte)0xbb;
            hash[hash.length - 1] = (byte)0xee;

            hmac.hash(hash, 1);

            if (!BinUtils.arraysEquals(hash, 1,
                    REF_HASHES_RIPEMD160[i], 0,
                    REF_HASHES_RIPEMD160[i].length)) {
                throw new Exception();
            }

            if ((hash[0              ] & 0x0ff) != 0xbb) throw new Exception();
            if ((hash[hash.length - 1] & 0x0ff) != 0xee) throw new Exception();
        }

        ///////////////////////////////////////////////////////////////////////

        final byte[][] REFEX_HASHES = {
            BinUtils.hexStrToBytes("44d86b658a3e7cbc1a2010848b53e35c917720ca"),
            BinUtils.hexStrToBytes("090e9e59645d4b2677a487ae0576ba41364d6d7b"),
            BinUtils.hexStrToBytes("f0fb1ef40ef18501db4d1e74105d782d5a0f2688"),
            BinUtils.hexStrToBytes("8744eca2319946d146b61173b92808c026bab5c4"),
            BinUtils.hexStrToBytes("3995fe711516f0e52080d205de1a793a10c06460"),
            BinUtils.hexStrToBytes("ecb2e5ca0eeffd84f5566b5de1d037ef1f9689ef"),
            BinUtils.hexStrToBytes("1c7fd123bdd208a27d476c25acea4c9355b50aac"),
            BinUtils.hexStrToBytes("268d6d609d49c7c605d4da79f6aa9344f086ec16"),
            BinUtils.hexStrToBytes("4c8b992def60b5d0361121064bd1c5936bcc5aff"),
            BinUtils.hexStrToBytes("85a2424ac1c779240d82e7c78825c26258243835"),
            BinUtils.hexStrToBytes("ca88124eb7db4a1c46ae3671d162cb5ef42bb99d"),
            BinUtils.hexStrToBytes("326b4ba19fb378ad4d7eaa91864a10dd5ebc51b9"),
            BinUtils.hexStrToBytes("e29956c86aeef20e07c909ec71975a421b7cd6ac"),
            BinUtils.hexStrToBytes("304695177e0e644c5edf81fe2c3f76099b7aa12d"),
            BinUtils.hexStrToBytes("e9e073955a79054f1d564ead2be67557963b13e1"),
            BinUtils.hexStrToBytes("9721814c01bd9ee16422cc738385c7113511fe09")
        };
        final byte[][] REFEX_DATA = {
            "".getBytes(),
            "a".getBytes(),
            "0123456789\u0000\u0000\u0000".getBytes(),
            BinUtils.hexStrToBytes("baadbeef")
        };
        final byte[][] REFEX_KEYS = {
            "".getBytes(),
            "a".getBytes(),
            "\n0123456789a0123456789a0123456789a0123456789a0123456789a0123456789a".getBytes(),
            BinUtils.hexStrToBytes("c0ffee")
        };

        int hidx = 0;
        for (byte[] rxdata : REFEX_DATA) {
            for (byte[] rxkey : REFEX_KEYS) {
                hmac.reset(rxkey, 0, rxkey.length);
                hmac.update(rxdata, 0, rxdata.length);

                byte[] hash = new byte[hmac.hashSize()];
                hmac.hash(hash, 0);

                if (!BinUtils.arraysEquals(
                        hash, 0,
                        REFEX_HASHES[hidx++], 0, hash.length)) {
                    throw new Exception();
                }
            }
        }
    }
}
