package coderslagoon.tclib.crypto;

import java.security.DigestException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import coderslagoon.baselib.util.BinUtils;

/**
 * SHA-512, simply wraps the JRE implementation (TODO: make our own)
 */
public class SHA512 implements Hash.Function {

    private MessageDigest md;

    public SHA512() {
        try {
            this.md = MessageDigest.getInstance("SHA-512");
        }
        catch (NoSuchAlgorithmException nsae) {
            throw new Error(nsae);
        }
    }

    @Override
    public String name() {
        return "SHA-512";
    }

    @Override
    public int hashSize() {
        return 64;
    }

    @Override
    public int blockSize() {
        return 128;
    }

    @Override
    public int recommededHMACIterations(boolean advanced) {
        return advanced ? 500000 : 1000;
    }

    @Override
    public void erase() {
        if (null == this.md)
            return;
        this.md.reset();
        this.md = null;
    }

    @Override
    public void hash(byte[] hash, int ofs) {
        try {
            this.md.digest(hash, ofs, this.hashSize());
        }
        catch (DigestException de) {
            throw new Error(de);
        }
    }

    @Override
    public void reset() {
        this.md.reset();
    }

    @Override
    public void update(final byte[] input, int ofs, final int len) {
        this.md.update(input, ofs, len);
    }

    @Override
    public void test() throws Throwable {

        for (String[] v : new String[][] {
            { "",
              "cf83e1357eefb8bdf1542850d66d8007d620e4050b5715dc83f4a921d36ce9ce47d0d13c5d85f2b0ff8318d2877eec2f63b931bd47417a81a538327af927da3e" },
            { "abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq",
              "204a8fc6dda82f0a0ced7beb8e08a41657c16ef468b228a8279be331a703c33596fd15c13b1b07f9aa1d3bea57789ca031ad85c7a71dd70354ec631238ca3445" }
        }) {
            final byte[] data = v[0].getBytes();
            final byte[] hash = BinUtils.hexStrToBytes(v[1]);
            SHA512 s = new SHA512();
            if (s.hashSize() != hash.length) {
                throw new Exception();
            }
            s.update(data, 0, data.length);
            byte[] hash2 = new byte[s.hashSize()];
            s.hash(hash2, 0);
            if (!BinUtils.arraysEquals(hash2, hash)) {
                throw new Exception();
            }
        }
    }
}
