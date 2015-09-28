package coderslagoon.trupax.tc;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.Arrays;



import coderslagoon.baselib.io.BlockDevice;
import coderslagoon.tclib.container.Header;
import coderslagoon.tclib.util.TCLibException;

public class TCInvalidate {
    public abstract static class HeaderChange {
        public abstract void change(byte[] hdr, boolean isBackup) throws TCLibException;
        public boolean needsOriginal() { return false; }

        public static HeaderChange random() {
            return new HeaderChange() {
                public void change(byte[] hdr, boolean isBackup) {
                    new SecureRandom().nextBytes(hdr);
                }
            };
        }

        public static HeaderChange zeros() {
            return new HeaderChange() {
                public void change(byte[] orig, boolean isBackup) {
                    Arrays.fill(orig, "\0".getBytes()[0]);
                }
            };
        }
    }

    private TCInvalidate() { }

    public interface Progress {
        public boolean onStart(long blocks, int blockSize);
        public boolean onBlock();
    }

    public static boolean destroy(BlockDevice bdev, HeaderChange hc, Progress pg) throws TCLibException {
        if (bdev.readOnly()) {
            throw new TCLibException("block device is not writeable");
        }
        if (bdev.writeOnly() && hc.needsOriginal()) {
            throw new TCLibException("block device does not support reading");
        }
        final long bsz = bdev.size();
        if (bsz < Header.BLOCK_COUNT) {
            throw new TCLibException("not enough blocks (volume too small)");
        }
        final long[] nums;
        if (bsz < Header.BLOCK_COUNT * 2) {
            nums = new long[] { 0L };
        }
        else {
            nums = new long[] { 0L, bsz - Header.BLOCK_COUNT };
        }
        if (!pg.onStart(Header.BLOCK_COUNT * nums.length, Header.BLOCK_SIZE)) {
            return false;
        }
        final byte[] buf = new byte[Header.SIZE];
        try {
            for (int i = 0; i < nums.length; i++) {
                final long num = nums[i];
                final boolean isBackup = i > 0;
                if (hc.needsOriginal()) {
                    for (int j = 0, ofs = 0;
                         j < Header.BLOCK_COUNT;
                         j++, ofs += Header.BLOCK_SIZE) {
                        bdev.read(num + j, buf, ofs);
                    }
                }
                else if (isBackup) {
                    Arrays.fill(buf, (byte)0);
                }
                hc.change(buf, isBackup);
                for (int j = 0, ofs = 0;
                     j < Header.BLOCK_COUNT;
                     j++, ofs += Header.BLOCK_SIZE) {
                    if (!pg.onBlock()) {
                        return false;
                    }
                    bdev.write(num + j, buf, ofs);
                }
            }
            return true;
        }
        catch (IOException ioe) {
            throw new TCLibException(ioe);
        }
        finally {
            Arrays.fill(buf, (byte)0);
        }
    }
}
