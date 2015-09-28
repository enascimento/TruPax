package coderslagoon.tclib.crypto;

import java.util.Arrays;

import coderslagoon.baselib.util.BinUtils;

/**
 * RIPEMD-160 implementation
 */
public class RIPEMD160 implements Hash.Function {
    private final static int HASH_SIZE = 20;
    private final static int BLOCK_SIZE = 64;

    public RIPEMD160() {
        reset();
    }

    @Override
    public String name() {
        return "RIPEMD-160";
    }

    @Override
    public int hashSize() {
        return HASH_SIZE;
    }

    @Override
    public int blockSize() {
        return BLOCK_SIZE;
    }

    @Override
    public int recommededHMACIterations(boolean advanced) {
        return advanced ? 655331 : 2000;
    }

    @Override
    public void erase() {
        Arrays.fill(this.state, 0);
    }

    @Override
    public void hash(byte[] hash, int ofs) {
        final byte[] size = new byte[8];
        BinUtils.writeInt64LE(this.count, size, 0);

        int padlen = BLOCK_SIZE - (((int)this.count >> 3) & 0x3f);
        if (padlen < 1 + 8) {
            padlen += BLOCK_SIZE;
        }
        update(PADDING, 0, padlen - 8);
        update(size, 0, size.length);

        for (int i = 0; i < 5; i++) {
            BinUtils.writeInt32LE(this.state[i], hash, ofs + (i << 2));
        }
    }

    @Override
    public void reset() {
        this.count = 0L;

        this.state[0] = 0x67452301;
        this.state[1] = 0xefcdab89;
        this.state[2] = 0x98badcfe;
        this.state[3] = 0x10325476;
        this.state[4] = 0xc3d2e1f0;
    }

    @Override
    public void update(final byte[] input, int ofs, final int len) {
        int rest = ((int)this.count >> 3) & 0x3f;
        final int c = BLOCK_SIZE - rest;
        final int end = len + ofs;
        this.count += len << 3;

        if (len >= c) {
            if (0 != rest) {
                System.arraycopy(input, ofs, this.buf, rest, c);
                transform(this.buf, 0);
                ofs += c;
                rest = 0;
            }
            while (ofs + BLOCK_SIZE <= end) {
                transform(input, ofs);
                ofs += BLOCK_SIZE;
            }
        }
        if (ofs < end) {
            System.arraycopy(input, ofs, this.buf, rest, end - ofs);
        }
    }

    ///////////////////////////////////////////////////////////////////////////

    private final int[] state = new int[5];
    private final int[] block = new int[16];
    private long count;
    private final byte[] buf = new byte[BLOCK_SIZE];

    private final static byte[] PADDING = new byte[BLOCK_SIZE]; {
        PADDING[0] = (byte)0x80;
    }

    ///////////////////////////////////////////////////////////////////////////

    final static int KT_1 = 0x5a827999;
    final static int KT_2 = 0x6ed9eba1;
    final static int KT_3 = 0x8f1bbcdc;
    final static int KT_4 = 0xa953fd4e;
    final static int KT_5 = 0x50a28be6;
    final static int KT_6 = 0x5c4dd124;
    final static int KT_7 = 0x6d703ef3;
    final static int KT_8 = 0x7a6d76e9;

    final static int F0(int x, int y, int z) { return x ^ y ^ z; }
    final static int F1(int x, int y, int z) { return (x & y) | (~x & z); }
    final static int F2(int x, int y, int z) { return (x | ~y) ^ z; }
    final static int F3(int x, int y, int z) { return (x & z) | (y & ~z); }
    final static int F4(int x, int y, int z) { return x ^ (y | ~z); }

    final static int rol(int n, int x) { return (x << n) | (x >>> (32 - n)); }

    final private void transform(final byte[] block, final int blockOfs) {
        final int[] state = this.state;

        int a = state[0];
        int b = state[1];
        int c = state[2];
        int d = state[3];
        int e = state[4];

        // NOTE: flattening the array into single integers didn't help, even
        //       on x64 where there should be more registers available...
        final int[] blk = this.block;
        for (int i = 0; i < 16; i++) {
            blk[i] = BinUtils.readInt32LE(block, (i << 2) + blockOfs);
        }

        // TODO: save a couple of assignments by role-change a, b, c, d and e
        { final int tmp = rol(11, a + blk[ 0] + F0(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol(14, a + blk[ 1] + F0(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol(15, a + blk[ 2] + F0(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol(12, a + blk[ 3] + F0(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol( 5, a + blk[ 4] + F0(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol( 8, a + blk[ 5] + F0(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol( 7, a + blk[ 6] + F0(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol( 9, a + blk[ 7] + F0(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol(11, a + blk[ 8] + F0(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol(13, a + blk[ 9] + F0(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol(14, a + blk[10] + F0(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol(15, a + blk[11] + F0(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol( 6, a + blk[12] + F0(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol( 7, a + blk[13] + F0(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol( 9, a + blk[14] + F0(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol( 8, a + blk[15] + F0(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }

        { final int tmp = rol( 7, a + blk[ 7] + KT_1 + F1(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol( 6, a + blk[ 4] + KT_1 + F1(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol( 8, a + blk[13] + KT_1 + F1(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol(13, a + blk[ 1] + KT_1 + F1(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol(11, a + blk[10] + KT_1 + F1(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol( 9, a + blk[ 6] + KT_1 + F1(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol( 7, a + blk[15] + KT_1 + F1(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol(15, a + blk[ 3] + KT_1 + F1(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol( 7, a + blk[12] + KT_1 + F1(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol(12, a + blk[ 0] + KT_1 + F1(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol(15, a + blk[ 9] + KT_1 + F1(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol( 9, a + blk[ 5] + KT_1 + F1(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol(11, a + blk[ 2] + KT_1 + F1(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol( 7, a + blk[14] + KT_1 + F1(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol(13, a + blk[11] + KT_1 + F1(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol(12, a + blk[ 8] + KT_1 + F1(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }

        { final int tmp = rol(11, a + blk[ 3] + KT_2 + F2(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol(13, a + blk[10] + KT_2 + F2(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol( 6, a + blk[14] + KT_2 + F2(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol( 7, a + blk[ 4] + KT_2 + F2(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol(14, a + blk[ 9] + KT_2 + F2(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol( 9, a + blk[15] + KT_2 + F2(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol(13, a + blk[ 8] + KT_2 + F2(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol(15, a + blk[ 1] + KT_2 + F2(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol(14, a + blk[ 2] + KT_2 + F2(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol( 8, a + blk[ 7] + KT_2 + F2(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol(13, a + blk[ 0] + KT_2 + F2(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol( 6, a + blk[ 6] + KT_2 + F2(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol( 5, a + blk[13] + KT_2 + F2(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol(12, a + blk[11] + KT_2 + F2(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol( 7, a + blk[ 5] + KT_2 + F2(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol( 5, a + blk[12] + KT_2 + F2(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }

        { final int tmp = rol(11, a + blk[ 1] + KT_3 + F3(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol(12, a + blk[ 9] + KT_3 + F3(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol(14, a + blk[11] + KT_3 + F3(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol(15, a + blk[10] + KT_3 + F3(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol(14, a + blk[ 0] + KT_3 + F3(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol(15, a + blk[ 8] + KT_3 + F3(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol( 9, a + blk[12] + KT_3 + F3(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol( 8, a + blk[ 4] + KT_3 + F3(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol( 9, a + blk[13] + KT_3 + F3(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol(14, a + blk[ 3] + KT_3 + F3(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol( 5, a + blk[ 7] + KT_3 + F3(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol( 6, a + blk[15] + KT_3 + F3(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol( 8, a + blk[14] + KT_3 + F3(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol( 6, a + blk[ 5] + KT_3 + F3(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol( 5, a + blk[ 6] + KT_3 + F3(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol(12, a + blk[ 2] + KT_3 + F3(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }

        { final int tmp = rol( 9, a + blk[ 4] + KT_4 + F4(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol(15, a + blk[ 0] + KT_4 + F4(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol( 5, a + blk[ 5] + KT_4 + F4(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol(11, a + blk[ 9] + KT_4 + F4(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol( 6, a + blk[ 7] + KT_4 + F4(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol( 8, a + blk[12] + KT_4 + F4(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol(13, a + blk[ 2] + KT_4 + F4(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol(12, a + blk[10] + KT_4 + F4(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol( 5, a + blk[14] + KT_4 + F4(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol(12, a + blk[ 1] + KT_4 + F4(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol(13, a + blk[ 3] + KT_4 + F4(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol(14, a + blk[ 8] + KT_4 + F4(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol(11, a + blk[11] + KT_4 + F4(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol( 8, a + blk[ 6] + KT_4 + F4(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol( 5, a + blk[15] + KT_4 + F4(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol( 6, a + blk[13] + KT_4 + F4(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }


        // NOTE: this split is necessary since the JIT unfortunately stopped
        // optimizing right before the second last round (possibly because of a
        // limit of inlined code per method or something similar) ...

        transform2(blk, a, b, c, d, e);
    }

    final private void transform2(final int[] blk,
            final int a2, final int b2, final int c2, final int d2, final int e2) {
        final int[] state = this.state;

        int a = state[0];
        int b = state[1];
        int c = state[2];
        int d = state[3];
        int e = state[4];

        { final int tmp = rol( 8, a + blk[ 5] + KT_5 + F4(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol( 9, a + blk[14] + KT_5 + F4(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol( 9, a + blk[ 7] + KT_5 + F4(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol(11, a + blk[ 0] + KT_5 + F4(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol(13, a + blk[ 9] + KT_5 + F4(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol(15, a + blk[ 2] + KT_5 + F4(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol(15, a + blk[11] + KT_5 + F4(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol( 5, a + blk[ 4] + KT_5 + F4(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol( 7, a + blk[13] + KT_5 + F4(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol( 7, a + blk[ 6] + KT_5 + F4(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol( 8, a + blk[15] + KT_5 + F4(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol(11, a + blk[ 8] + KT_5 + F4(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol(14, a + blk[ 1] + KT_5 + F4(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol(14, a + blk[10] + KT_5 + F4(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol(12, a + blk[ 3] + KT_5 + F4(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol( 6, a + blk[12] + KT_5 + F4(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }

        { final int tmp = rol( 9, a + blk[ 6] + KT_6 + F3(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol(13, a + blk[11] + KT_6 + F3(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol(15, a + blk[ 3] + KT_6 + F3(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol( 7, a + blk[ 7] + KT_6 + F3(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol(12, a + blk[ 0] + KT_6 + F3(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol( 8, a + blk[13] + KT_6 + F3(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol( 9, a + blk[ 5] + KT_6 + F3(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol(11, a + blk[10] + KT_6 + F3(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol( 7, a + blk[14] + KT_6 + F3(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol( 7, a + blk[15] + KT_6 + F3(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol(12, a + blk[ 8] + KT_6 + F3(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol( 7, a + blk[12] + KT_6 + F3(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol( 6, a + blk[ 4] + KT_6 + F3(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol(15, a + blk[ 9] + KT_6 + F3(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol(13, a + blk[ 1] + KT_6 + F3(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol(11, a + blk[ 2] + KT_6 + F3(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }

        { final int tmp = rol( 9, a + blk[15] + KT_7 + F2(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol( 7, a + blk[ 5] + KT_7 + F2(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol(15, a + blk[ 1] + KT_7 + F2(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol(11, a + blk[ 3] + KT_7 + F2(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol( 8, a + blk[ 7] + KT_7 + F2(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol( 6, a + blk[14] + KT_7 + F2(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol( 6, a + blk[ 6] + KT_7 + F2(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol(14, a + blk[ 9] + KT_7 + F2(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol(12, a + blk[11] + KT_7 + F2(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol(13, a + blk[ 8] + KT_7 + F2(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol( 5, a + blk[12] + KT_7 + F2(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol(14, a + blk[ 2] + KT_7 + F2(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol(13, a + blk[10] + KT_7 + F2(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol(13, a + blk[ 0] + KT_7 + F2(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol( 7, a + blk[ 4] + KT_7 + F2(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol( 5, a + blk[13] + KT_7 + F2(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }

        { final int tmp = rol(15, a + blk[ 8] + KT_8 + F1(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol( 5, a + blk[ 6] + KT_8 + F1(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol( 8, a + blk[ 4] + KT_8 + F1(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol(11, a + blk[ 1] + KT_8 + F1(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol(14, a + blk[ 3] + KT_8 + F1(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol(14, a + blk[11] + KT_8 + F1(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol( 6, a + blk[15] + KT_8 + F1(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol(14, a + blk[ 0] + KT_8 + F1(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol( 6, a + blk[ 5] + KT_8 + F1(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol( 9, a + blk[12] + KT_8 + F1(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol(12, a + blk[ 2] + KT_8 + F1(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol( 9, a + blk[13] + KT_8 + F1(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol(12, a + blk[ 9] + KT_8 + F1(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol( 5, a + blk[ 7] + KT_8 + F1(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol(15, a + blk[10] + KT_8 + F1(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol( 8, a + blk[14] + KT_8 + F1(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }

        { final int tmp = rol( 8, a + blk[12] + F0(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol( 5, a + blk[15] + F0(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol(12, a + blk[10] + F0(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol( 9, a + blk[ 4] + F0(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol(12, a + blk[ 1] + F0(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol( 5, a + blk[ 5] + F0(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol(14, a + blk[ 8] + F0(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol( 6, a + blk[ 7] + F0(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol( 8, a + blk[ 6] + F0(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol(13, a + blk[ 2] + F0(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol( 6, a + blk[13] + F0(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol( 5, a + blk[14] + F0(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol(15, a + blk[ 0] + F0(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol(13, a + blk[ 3] + F0(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol(11, a + blk[ 9] + F0(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }
        { final int tmp = rol(11, a + blk[11] + F0(b, c, d)) + e; a = e; e = d; d = rol(10, c); c = b; b = tmp; }


        final int tmp = state[1] + c2 + d;
        state[1] = state[2] + d2 + e;
        state[2] = state[3] + e2 + a;
        state[3] = state[4] + a2 + b;
        state[4] = state[0] + b2 + c;
        state[0] = tmp;
    }

    @Override
    public void test() throws Throwable {
        // test vector from TrueCrypt
        final byte[] REF_DATA_1 = "hash this!".getBytes();
        final byte[] REF_HASH_1 = BinUtils.hexStrToBytes("51c0f8b3e53dcbdc6219c056698811d41cebcb0b");
        if (HASH_SIZE != REF_HASH_1.length) {
            throw new Exception();
        }
        RIPEMD160 re = new RIPEMD160();
        re.update(REF_DATA_1, 0, REF_DATA_1.length);
        byte[] hash = new byte[re.hashSize()];
        re.hash(hash, 0);
        if (!BinUtils.arraysEquals(hash, REF_HASH_1)) {
            throw new Exception();
        }
        // other vectors created with TrueCrypt source code
        final int[] REF_DATA_2_SIZES = {
                0, 1, 63, 64, 65, 127, 128, 129, 255, 256, 258
        };
        final String[] REF_HASHES_2 = new String[] {
                "9c1185a5c5e9fc54612808977ee8f548b2258d31",
                "c81b94933420221a7ac004a90242d8b1d3e5070d",
                "6d31d3d634b4a7aa15914c239576eb1956f2d9a4",
                "2581f5e9f957b44b0fa24d31996de47409dd1e0f",
                "109949b95341eeea7365e8ac4d0d3883d98f709a",
                "2be8e565e24a87171f0700ecafa3c2942c97023e",
                "7c4d36070c1e1176b2960a1b0dd2319d547cf8eb",
                "1f15f104f445db8ef02bb601a67e60c373377fa6",
                "258a3b50d3df2564dd57d3dfa39d684650a05450",
                "9c4fa072db2c871a5635e37f791e93ab45049676",
                "ce92f227284cadfccfae7e752d63ec3ceeeb430c"
        };
        int c = REF_DATA_2_SIZES[REF_DATA_2_SIZES.length - 1];
        byte[] refData = new byte[c];
        for (int i = 0; i < c; i++) {
            refData[i] = (byte)i;
        }
        for (int i = 0; i < REF_DATA_2_SIZES.length; i++) {
            re = new RIPEMD160();

            final int CSZ = 11;
            int ofs = 0, sz = REF_DATA_2_SIZES[i];
            while (ofs < sz) {
                int tohash = Math.min(sz - ofs, CSZ);
                re.update(refData, ofs, tohash);
                ofs += tohash;
            }

            re.hash(hash, 0);

            byte[] refDigest = BinUtils.hexStrToBytes(REF_HASHES_2[i]);

            if (!BinUtils.arraysEquals(hash, refDigest)) {
                throw new Exception();
            }
        }
        // vectors found on Wikipedia
        final byte[][] REF_DATA_3 = {
                "The quick brown fox jumps over the lazy dog".getBytes(),
                "The quick brown fox jumps over the lazy cog".getBytes()
        };
        final byte[][] REF_HASHES_3 = {
                BinUtils.hexStrToBytes("37f332f68db77bd9d7edd4969571ad671cf9dd3b"),
                BinUtils.hexStrToBytes("132072df690933835eb8b6ad0b77e7b6f14acad7")
        };
        for (int i = 0; i < REF_DATA_3.length; i++) {
            re.reset();
            re.update(REF_DATA_3[i], 0, REF_DATA_3[i].length);
            re.hash(hash, 0);
            if (!BinUtils.arraysEquals(REF_HASHES_3[i], hash)) {
                throw new Exception();
            }
        }
    }
}
