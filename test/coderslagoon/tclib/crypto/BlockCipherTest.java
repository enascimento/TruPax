package coderslagoon.tclib.crypto;

import static org.junit.Assert.assertTrue;
import coderslagoon.tclib.crypto.BlockCipher;

public class BlockCipherTest {


    public void test0() {


        BlockCipher bc = new BlockCipher() {
            @Override
            public String name() {
                return null;
            }
            @Override
            public void erase() {
            }
            @Override
            public void test() throws Throwable {
            }
            @Override
            public int blockSize() {
                return 0;
            }
            @Override
            public int keySize() {
                return 0;
            }
            @Override
            public void processBlock(byte[] in, int ofs_i, byte[] out, int ofs_o) {
            }
            @Override
            public Object clone() {
                return null;
            }
        };
        bc.initialize(BlockCipher.Mode.ENCRYPT, null,  0);
        assertTrue(BlockCipher.Mode.ENCRYPT == bc.mode());
    }
}
