package coderslagoon.tclib.crypto;

import java.util.Arrays;

import coderslagoon.baselib.util.BinUtils;

public class PKCS5 {

    /**
     * PKCS5/PBKDF2 key derivation implementation.
     */
    public static class PBKDF2 implements Algorithm {

        private HMAC hmac;
        private Hash.Function hfunc;

        public PBKDF2() {
        }

        /**
         * Default constructor.
         * @param hfunc The hash function instance to use.
         */
        public PBKDF2(Hash.Function hfunc) {
            this.hfunc = hfunc;
            this.hmac = new HMAC();
        }

        /**
         * Derive a key from a password and some salt.
         * @param passw The password bytes.
         * @param salt The salt value.
         * @param iterations Number of iteration to make brute forcing harder.
         * @param keyLen The size of the key to generate.
         * @return The derived key material.
         */
        public byte[] deriveKey(
                byte[] passw, byte[] salt,
                int iterations,
                int keyLen) {
            byte[] result = new byte[keyLen];

            final int J = 0;
            final int K = this.hfunc.hashSize();
            final int U = this.hfunc.hashSize() << 1;
            final int B = K + U;
            final byte[] tmp = new byte[K + U + 4];

            HMAC hmac = this.hmac;
            hmac.initialize(this.hfunc, passw, 0, passw.length);

            for (int kpos = 0, blk = 1; kpos < keyLen; kpos += K, blk++) {
                BinUtils.writeInt32BE(blk, tmp, B);

                hmac.reset(null, 0, 0);
                hmac.update(salt, 0, salt.length);
                hmac.update(tmp, B, 4);
                hmac.hash(tmp, U);
                System.arraycopy(tmp, U, tmp, J, K);

                for (int i = 1, j = J, k = K; i < iterations; i++) {
                    hmac.reset(passw, 0, passw.length);
                    hmac.update(tmp, j, K);
                    hmac.hash(tmp, k);

                    for (int u = U, v = k; u < B; u++, v++) {
                        tmp[u] ^= tmp[v];
                    }

                    int swp = k;
                    k = j;
                    j = swp;
                }

                int tocpy = Math.min(keyLen - kpos, K);
                System.arraycopy(tmp, U, result, kpos, tocpy);
            }

            Arrays.fill(tmp, (byte)0);

            return result;
        }

        @Override
        public void erase() {
            this.hfunc.erase();
            this.hmac.erase();
        }

        @Override
        public String name() {
            return "PBKDF2";
        }

        @Override
        public void test() throws Throwable {
            PBKDF2 inst = new PBKDF2(new RIPEMD160());

            byte[] key = inst.deriveKey("password".getBytes(),
                    new byte[] { 0x12, 0x34, 0x56, 0x78, }, 5, 4);

            if (!BinUtils.arraysEquals(key, new byte[] { 0x7a, 0x3d, 0x7c, 3 })) {
                throw new Exception();
            }

            key = inst.deriveKey("password".getBytes(),
                    new byte[] { 0x12, 0x34, 0x56, 0x78, }, 5, 48);

            if (!BinUtils.arraysEquals(key, BinUtils.hexStrToBytes(
                    "7a3d7c03e7266bf83d78fb29d2641f56eaf0e5f5ccc43a31" +
                    "a88470bfbd6f8e78245ac00af6faf0f6e900475f73cee143"))) {
                throw new Exception();
            }

            final String[] REF_KEYS = {
                "39",
                "b9",
                "3905b2203f6f823071dbb61779e2e9048c991e17",
                "b97f282b35683b56eb464de6a36fb111ccb57f1c",
                "3905b2203f6f823071dbb61779e2e9048c991e1783b63277c77fdbaf84820c3297cbe8a670b243c8973bde20a69ccb41e17e220d7d0b64",
                "b97f282b35683b56eb464de6a36fb111ccb57f1ca56cb2d360679b7c4876f91ea19403e74592509639c85cf63ca32283e12eb7658ea6d0",
                "34",
                "cd",
                "347e35f3ecd1146864d9039850a71da943507a2d",
                "cd647ce2fbd2c1f5124459349626e31ecc5e089d",
                "347e35f3ecd1146864d9039850a71da943507a2d13d0ba971492e93c740aa4184b5bb0cf9ec2c448af8074376980d725af16b509755e0c",
                "cd647ce2fbd2c1f5124459349626e31ecc5e089d22e21b7b70ee0ed9bca89696f6ff442ee1fc84998e1bab056d3c20f8da1fb2a9560678",
                "7c",
                "11",
                "7c330174af3ca0eb3f6190620851ab9f2a53de59",
                "11373b32d7f6c1c2dfe4815c2752777c2da78118",
                "7c330174af3ca0eb3f6190620851ab9f2a53de5912a4d638a792295f1b9058f8a863e1285bb0d8622cbd6e49a67d2c0f4a41df67e95a71",
                "11373b32d7f6c1c2dfe4815c2752777c2da7811825e4189a43b63cc58f528663d104c5064e83caedd2fbac00881f12fa199502ac4bb2cd",
                "5b",
                "b9",
                "5bd3d71b36950818bb6789e2c51aefb1d2795245",
                "b95ecf5800c79b8dfdfd65e50e54f9008522ae1f",
                "5bd3d71b36950818bb6789e2c51aefb1d2795245246dd866909b8846827defd60bee7294b55589b0b714d89b18b943d04a344ada4d4c7f",
                "b95ecf5800c79b8dfdfd65e50e54f9008522ae1fa67d437c9464699c5d6e89b970b6515824cc732e5b3a50419ea74470e5dea753690ae6"
            };
            final String[] REF_SALT = {
                 "",
                 "deedee0001"
            };
            final String[] REF_PASSW = REF_SALT;
            final int[] REF_KEYSZS = {
                 1, 20, 55
            };
            final int[] REF_ITERS = {
                 0, 11
            };
            int rk = 0;
            for (int s = 0; s < REF_SALT.length; s++) {
                for (int p = 0; p < REF_PASSW.length; p++) {
                    for (int z = 0; z < 3; z++) {
                        for (int i = 0; i < 2; i++) {
                            key = inst.deriveKey(
                                    BinUtils.hexStrToBytes(REF_PASSW[p]),
                                    BinUtils.hexStrToBytes(REF_PASSW[s]),
                                    REF_ITERS[i],
                                    REF_KEYSZS[z]);

                            if (!BinUtils.arraysEquals(
                                    key,
                                    BinUtils.hexStrToBytes(REF_KEYS[rk++]))) {
                                throw new Exception();
                            }
                        }
                    }
                }
            }
            inst.erase();

            if (rk != REF_KEYS.length) {
                throw new Exception();
            }
        }
    }
}
