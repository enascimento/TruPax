package coderslagoon.tclib.crypto;

import java.util.Arrays;

import coderslagoon.baselib.util.BinUtils;
import coderslagoon.tclib.util.TCLibException;

/**
 * XTS implementation, translated from TrueCrypt sources to get a compatible
 * (and verifiable) version.
 */
public class XTS implements Algorithm, Cloneable {
    BlockCipher cipher1;
    BlockCipher cipher2;

    private final static int BLOCK_SIZE = 16;

    /** Size of a block this XTS implementation deals with. */
    public final static int DATA_UNIT_SIZE = 512;

    private final static int BLOCKS_PER_DATA_UNIT = DATA_UNIT_SIZE / BLOCK_SIZE;
    private final static int SIZEOF_LONG = 8;

    // XTS instances are supposed to have a long life span, thus we don't
    // optimize or compact array allocation here (for now) ...

    private long[] w_vals  = new long[DATA_UNIT_SIZE / SIZEOF_LONG];
    private byte[] w_val   = new byte[BLOCK_SIZE];
    private byte[] unit_no = new byte[BLOCK_SIZE];

    /** Restricted for tests and cloning. Do not use. */
    XTS() {
    }

    /**
     * Default constructor.
     * @param cipher1 The first block cipher instance.
     * @param cipher2 The second block cipher instance.
     * @throws TCLibException If the ciphers are not of identical types.
     */
    public XTS(BlockCipher cipher1, BlockCipher cipher2) throws TCLibException {
        if (cipher1.blockSize() != BLOCK_SIZE  ||
           !cipher1.name().equals(cipher2.name())) {
            throw new TCLibException();
        }
        this.cipher1 = cipher1;
        this.cipher2 = cipher2;
    }

    @Override
    public Object clone() {
        XTS result = new XTS();
        result.cipher1 = (BlockCipher)this.cipher1.clone();
        result.cipher2 = (BlockCipher)this.cipher2.clone();
        return result;
    }

    @Override
    public String name() {
        return "XTS";
    }

    @Override
    public void erase() {
        Arrays.fill(this.w_vals , (byte)0);
        Arrays.fill(this.w_val  , (byte)0);
        Arrays.fill(this.unit_no, (byte)0);
        this.cipher2.erase();
        this.cipher1.erase();
    }

    /**
     * Process one block.
     * @param buf Data buffer.
     * @param ofs Where to start reading/writing in the buffer.
     * @param len How many bytes to process.
     * @param startDataUnit Block (as in volume) number.
     * @param startBlock Which sub-block to start at.
     * @throws TCLibException
     */
    public void process(byte[] buf, int ofs, int len,
            long startDataUnit, int startBlock) throws TCLibException {
        if (0 > ofs ||
            0 > len ||
            0 != len % BLOCK_SIZE ||
            0 > startDataUnit ||
            0 > startBlock) {
            throw new TCLibException();
        }

        long dataUnit = startDataUnit;

        final long[] w_vals  = this.w_vals;
        final byte[] unit_no = this.unit_no;

        final BlockCipher cipher1 = this.cipher1;
        final BlockCipher cipher2 = this.cipher2;

        final int w_vals_end = w_vals.length - 1;

        int blockCount = len / BLOCK_SIZE;

        while (blockCount > 0) {
            int endBlock = blockCount < BLOCKS_PER_DATA_UNIT ?
                    startBlock + blockCount :
                    BLOCKS_PER_DATA_UNIT;

            BinUtils.writeInt64LE(dataUnit, unit_no, 0);
            Arrays.fill(unit_no, SIZEOF_LONG, BLOCK_SIZE, (byte)0);

            cipher2.processBlock(unit_no, 0, unit_no, 0);

            long w_lo = BinUtils.readInt64LE(unit_no, 0);
            long w_hi = BinUtils.readInt64LE(unit_no, SIZEOF_LONG);

            for (int block = 0, wi = w_vals_end; block < endBlock; block++, wi -= 2) {
                if (block >= startBlock) {
                    w_vals[wi    ] = w_lo;
                    w_vals[wi - 1] = w_hi;
                }

                int finalCarry = 0 == (w_hi & 0x8000000000000000L) ? 0 : 135;

                w_hi <<= 1;
                w_hi |= w_lo >>> 63;
                w_lo <<= 1;
                w_lo ^= finalCarry;
            }

            for (int block = startBlock, wi = w_vals_end;
                 block < endBlock;
                 block++, wi -= 2, ofs += SIZEOF_LONG * 2) {
                BinUtils.xorInt64OverBytesLE(w_vals[wi    ], buf, ofs);
                BinUtils.xorInt64OverBytesLE(w_vals[wi - 1], buf, ofs + SIZEOF_LONG);

                cipher1.processBlock(buf, ofs, buf, ofs);

                BinUtils.xorInt64OverBytesLE(w_vals[wi    ], buf, ofs);
                BinUtils.xorInt64OverBytesLE(w_vals[wi - 1], buf, ofs + SIZEOF_LONG);
            }

            blockCount -= endBlock - startBlock;
            startBlock = 0;
            dataUnit++;
        }
    }

    ///////////////////////////////////////////////////////////////////////////

    // TODO: test vectors are a bit insufficient, multiple data units and/or
    //       block indexes different from zero should be tested as well...
    final static String[][] TEST_VECTORS = {
        // IEEE 1619 vector 10
        {
            "2718281828459045235360287471352662497757247093699959574966967627", // key 1
            "3141592653589793238462643383279502884197169399375105820974944592", // key 2
            "00000000000000ff",                                                 // data unit
            "0",                                                                // block #
            "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f" +// plain text
            "202122232425262728292a2b2c2d2e2f303132333435363738393a3b3c3d3e3f" +
            "404142434445464748494a4b4c4d4e4f505152535455565758595a5b5c5d5e5f" +
            "606162636465666768696a6b6c6d6e6f707172737475767778797a7b7c7d7e7f" +
            "808182838485868788898a8b8c8d8e8f909192939495969798999a9b9c9d9e9f" +
            "a0a1a2a3a4a5a6a7a8a9aaabacadaeafb0b1b2b3b4b5b6b7b8b9babbbcbdbebf" +
            "c0c1c2c3c4c5c6c7c8c9cacbcccdcecfd0d1d2d3d4d5d6d7d8d9dadbdcdddedf" +
            "e0e1e2e3e4e5e6e7e8e9eaebecedeeeff0f1f2f3f4f5f6f7f8f9fafbfcfdfeff" +
            "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f" +
            "202122232425262728292a2b2c2d2e2f303132333435363738393a3b3c3d3e3f" +
            "404142434445464748494a4b4c4d4e4f505152535455565758595a5b5c5d5e5f" +
            "606162636465666768696a6b6c6d6e6f707172737475767778797a7b7c7d7e7f" +
            "808182838485868788898a8b8c8d8e8f909192939495969798999a9b9c9d9e9f" +
            "a0a1a2a3a4a5a6a7a8a9aaabacadaeafb0b1b2b3b4b5b6b7b8b9babbbcbdbebf" +
            "c0c1c2c3c4c5c6c7c8c9cacbcccdcecfd0d1d2d3d4d5d6d7d8d9dadbdcdddedf" +
            "e0e1e2e3e4e5e6e7e8e9eaebecedeeeff0f1f2f3f4f5f6f7f8f9fafbfcfdfeff",
            "1c3b3a102f770386e4836c99e370cf9bea00803f5e482357a4ae12d414a3e63b" +// cipher text
            "5d31e276f8fe4a8d66b317f9ac683f44680a86ac35adfc3345befecb4bb188fd" +
            "5776926c49a3095eb108fd1098baec70aaa66999a72a82f27d848b21d4a741b0" +
            "c5cd4d5fff9dac89aeba122961d03a757123e9870f8acf1000020887891429ca" +
            "2a3e7a7d7df7b10355165c8b9a6d0a7de8b062c4500dc4cd120c0f7418dae3d0" +
            "b5781c34803fa75421c790dfe1de1834f280d7667b327f6c8cd7557e12ac3a0f" +
            "93ec05c52e0493ef31a12d3d9260f79a289d6a379bc70c50841473d1a8cc81ec" +
            "583e9645e07b8d9670655ba5bbcfecc6dc3966380ad8fecb17b6ba02469a020a" +
            "84e18e8f84252070c13e9f1f289be54fbc481457778f616015e1327a02b140f1" +
            "505eb309326d68378f8374595c849d84f4c333ec4423885143cb47bd71c5edae" +
            "9be69a2ffeceb1bec9de244fbe15992b11b77c040f12bd8f6a975a44a0f90c29" +
            "a9abc3d4d893927284c58754cce294529f8614dcd2aba991925fedc4ae74ffac" +
            "6e333b93eb4aff0479da9a410e4450e0dd7ae4c6e2910900575da401fc07059f" +
            "645e8b7e9bfdef33943054ff84011493c27b3429eaedb4ed5376441a77ed4385" +
            "1ad77f16f541dfd269d50d6a5f14fb0aab1cbb4c1550be97f7ab4066193c4caa" +
            "773dad38014bd2092fa755c824bb5e54c4f36ffda9fcea70b9c6e693e148c151"
        },
        // IEEE 1619 - Vector 14
        {
            "2718281828459045235360287471352662497757247093699959574966967627",
            "3141592653589793238462643383279502884197169399375105820974944592",
            "000000ffffffffff",
            "0",
            "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f" +
            "202122232425262728292a2b2c2d2e2f303132333435363738393a3b3c3d3e3f" +
            "404142434445464748494a4b4c4d4e4f505152535455565758595a5b5c5d5e5f" +
            "606162636465666768696a6b6c6d6e6f707172737475767778797a7b7c7d7e7f" +
            "808182838485868788898a8b8c8d8e8f909192939495969798999a9b9c9d9e9f" +
            "a0a1a2a3a4a5a6a7a8a9aaabacadaeafb0b1b2b3b4b5b6b7b8b9babbbcbdbebf" +
            "c0c1c2c3c4c5c6c7c8c9cacbcccdcecfd0d1d2d3d4d5d6d7d8d9dadbdcdddedf" +
            "e0e1e2e3e4e5e6e7e8e9eaebecedeeeff0f1f2f3f4f5f6f7f8f9fafbfcfdfeff" +
            "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f" +
            "202122232425262728292a2b2c2d2e2f303132333435363738393a3b3c3d3e3f" +
            "404142434445464748494a4b4c4d4e4f505152535455565758595a5b5c5d5e5f" +
            "606162636465666768696a6b6c6d6e6f707172737475767778797a7b7c7d7e7f" +
            "808182838485868788898a8b8c8d8e8f909192939495969798999a9b9c9d9e9f" +
            "a0a1a2a3a4a5a6a7a8a9aaabacadaeafb0b1b2b3b4b5b6b7b8b9babbbcbdbebf" +
            "c0c1c2c3c4c5c6c7c8c9cacbcccdcecfd0d1d2d3d4d5d6d7d8d9dadbdcdddedf" +
            "e0e1e2e3e4e5e6e7e8e9eaebecedeeeff0f1f2f3f4f5f6f7f8f9fafbfcfdfeff",
            "64497e5a831e4a932c09be3e5393376daa599548b816031d224bbf50a818ed23" +
            "50eae7e96087c8a0db51ad290bd00c1ac1620857635bf246c176ab463be30b80" +
            "8da548081ac847b158e1264be25bb0910bbc92647108089415d45fab1b3d2604" +
            "e8a8eff1ae4020cfa39936b66827b23f371b92200be90251e6d73c5f86de5fd4" +
            "a950781933d79a28272b782a2ec313efdfcc0628f43d744c2dc2ff3dcb66999b" +
            "50c7ca895b0c64791eeaa5f29499fb1c026f84ce5b5c72ba1083cddb5ce45434" +
            "631665c333b60b11593fb253c5179a2c8db813782a004856a1653011e93fb6d8" +
            "76c18366dd8683f53412c0c180f9c848592d593f8609ca736317d356e13e2bff" +
            "3a9f59cd9aeb19cd482593d8c46128bb32423b37a9adfb482b99453fbe25a41b" +
            "f6feb4aa0bef5ed24bf73c762978025482c13115e4015aac992e5613a3b5c2f6" +
            "85b84795cb6e9b2656d8c88157e52c42f978d8634c43d06fea928f2822e465aa" +
            "6576e9bf419384506cc3ce3c54ac1a6f67dc66f3b30191e698380bc999b05abc" +
            "e19dc0c6dcc2dd001ec535ba18deb2df1a101023108318c75dc98611a09dc48a" +
            "0acdec676fabdf222f07e026f059b672b56e5cbc8e1d21bbd867dd9272120546" +
            "81d70ea737134cdfce93b6f82ae22423274e58a0821cc5502e2d0ab4585e94de" +
            "6975be5e0b4efce51cd3e70c25a1fbbbd609d273ad5b0d59631c531f6a0a57b9"
        }
    };

    @Override
    public void test() throws Throwable {
        for (String[] tv : TEST_VECTORS) {
            byte[] key1   = BinUtils.hexStrToBytes(tv[0]);
            byte[] key2   = BinUtils.hexStrToBytes(tv[1]);
            long dataUnit = BinUtils.readInt64BE(BinUtils.hexStrToBytes(tv[2]), 0);
            int blockNO   = Integer.parseInt(tv[3], 10);
            byte[] ctxt   = BinUtils.hexStrToBytes(tv[5]);
            byte[] ptxt   = BinUtils.hexStrToBytes(tv[4]);
            byte[] ptxt2  = ptxt.clone();

            BlockCipher bc1 = new AES256();
            BlockCipher bc2 = new AES256();
            bc1.initialize(BlockCipher.Mode.ENCRYPT, key1, 0);
            bc2.initialize(BlockCipher.Mode.ENCRYPT, key2, 0);

            XTS xts = new XTS(bc1, bc2);

            xts.process(ptxt, 0, ptxt.length, dataUnit, blockNO);

            if (!BinUtils.arraysEquals(ptxt, ctxt)) {
                throw new Exception();
            }

            bc1 = new AES256();
            bc2 = new AES256();
            bc1.initialize(BlockCipher.Mode.DECRYPT, key1, 0);
            bc2.initialize(BlockCipher.Mode.ENCRYPT, key2, 0);

            xts = new XTS(bc1, bc2);

            xts.process(ptxt, 0, ptxt.length, dataUnit, blockNO);

            if (!BinUtils.arraysEquals(ptxt, ptxt2)) {
                throw new Exception();
            }
        }
    }
}
