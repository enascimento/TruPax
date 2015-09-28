package coderslagoon.trupax.tc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.Arrays;

import org.junit.Before;
import org.junit.Test;


import coderslagoon.baselib.io.BlockDevice;
import coderslagoon.baselib.io.BlockDeviceImpl;
import coderslagoon.baselib.util.BytePtr;
import coderslagoon.baselib.util.VarBool;
import coderslagoon.baselib.util.VarInt;
import coderslagoon.tclib.util.TCLibException;
import coderslagoon.test.util.TestUtils;
import coderslagoon.trupax.tc.TCInvalidate;

public class TCInvalidateTest {
    byte[]      buf;
    BlockDevice bd;

    @Before
    public void setUp() throws Exception {
        BlockDeviceImpl.MemoryBlockDevice mbd = new
        BlockDeviceImpl.MemoryBlockDevice(512, 256 * 2 + 1, false, false);
        this.bd = mbd;
        this.buf = mbd.buffer();
    }

    private static void prepareBuffer(byte[] buf, int head, int middle, int tail) {
        Arrays.fill(buf, 0        , 512 * 256, (byte)head);
        Arrays.fill(buf, 512 * 256, 512 * 257, (byte)middle);
        Arrays.fill(buf, 512 * 257, 512 * 513, (byte)tail);
    }

    ///////////////////////////////////////////////////////////////////////////

    static class TestProgress implements TCInvalidate.Progress {
        public int stopAt = -1;
        public int starts;
        public int writes;
        public int lastBlockSize = -1;

        public boolean onStart(long blocks, int blockSize) {
            this.starts++;
            this.lastBlockSize = blockSize;
            return 0 != this.stopAt;
        }
        public boolean onBlock() {
            this.writes++;
            return 0 >= this.stopAt;
        }
    };

    ///////////////////////////////////////////////////////////////////////////

    @Test
    public void test0() throws Exception {
        byte[] buf = this.buf;

        prepareBuffer(buf, 20, -1, 20);

        TestProgress tpg = new TestProgress();

        assertTrue(TCInvalidate.destroy(this.bd, TCInvalidate.HeaderChange.zeros(), tpg));
        assertTrue(1   == tpg.starts);
        assertTrue(512 == tpg.writes);
        assertTrue(512 == tpg.lastBlockSize);

        assertTrue(TestUtils.checkFill(new BytePtr(buf, 0        , 512 * 256), (byte) 0));
        assertTrue(TestUtils.checkFill(new BytePtr(buf, 512 * 256, 512      ), (byte)-1));
        assertTrue(TestUtils.checkFill(new BytePtr(buf, 512 * 257, 512 * 256), (byte) 0));

        prepareBuffer(buf, -1, 20, -1);

        assertTrue(TCInvalidate.destroy(this.bd, TCInvalidate.HeaderChange.random(), tpg));
        int nonZeros = 0;
        for (int i = 0; i < 512 * 256; i++) nonZeros += 0 == buf[i] ? 0 : 1;
        assertTrue(0 < nonZeros);
        assertTrue(TestUtils.checkFill(new BytePtr(buf, 512 * 256, 512), (byte)20));
        nonZeros = 0;
        for (int i = 512 * 257; i < buf.length; i++) nonZeros += 0 == buf[i] ? 0 : 1;
        assertTrue(0 < nonZeros);

        tpg = new TestProgress();
        tpg.stopAt = 0;
        assertFalse(TCInvalidate.destroy(this.bd, TCInvalidate.HeaderChange.random(), tpg));
        assertTrue(1 == tpg.starts);
        assertTrue(0 == tpg.writes);

        tpg = new TestProgress();
        tpg.stopAt = 1;
        assertFalse(TCInvalidate.destroy(this.bd, TCInvalidate.HeaderChange.random(), tpg));
        assertTrue(1 == tpg.starts);
        assertTrue(1 == tpg.writes);
    }

    @Test
    public void testHeaderChange() throws Exception {
        byte[] buf = this.buf;

        TCInvalidate.HeaderChange hc = new TCInvalidate.HeaderChange() {
            public void change(byte[] hdr, boolean isBackup) throws TCLibException {
                throw new TCLibException("change_problem");
            }
        };
        try {
            assertTrue(TCInvalidate.destroy(this.bd, hc, new TestProgress()));
            fail();
        }
        catch (TCLibException tle) {
            assertEquals("change_problem", tle.getMessage());
        }

        prepareBuffer(buf, 5, 3, 6);
        final VarInt calls = new VarInt(0);
        hc = new TCInvalidate.HeaderChange() {
            public boolean needsOriginal() {
                return true;
            }
            public void change(byte[] hdr, boolean isBackup) throws TCLibException {
                assertTrue(0 == calls.v ^ isBackup);
                assertTrue(256 * 512 == hdr.length);
                assertTrue(TestUtils.checkFill(new BytePtr(hdr, 0, hdr.length), (byte)(0 == calls.v++ ? 5 : 6)));
            }
        };
        assertTrue(TCInvalidate.destroy(this.bd, hc, new TestProgress()));
        assertTrue(2 == calls.v);
        calls.v = 0;
        hc = new TCInvalidate.HeaderChange() {
            public boolean needsOriginal() {
                return false;
            }
            public void change(byte[] hdr, boolean isBackup) throws TCLibException {
                assertTrue(0 == calls.v++ ^ isBackup);
                assertTrue(256 * 512 == hdr.length);
                assertTrue(TestUtils.checkFill(new BytePtr(hdr, 0, hdr.length), (byte)0));
            }
        };
        prepareBuffer(buf, 8, 99, 7);
        assertTrue(TCInvalidate.destroy(this.bd, hc, new TestProgress()));
        assertTrue(2 == calls.v);
    }

    @Test
    public void testSingleHeader() throws Exception {
        BlockDeviceImpl.MemoryBlockDevice mbd = new
        BlockDeviceImpl.MemoryBlockDevice(512, 256L, false, false);
        Arrays.fill(mbd.buffer(), (byte)'a');
        BlockDevice bd2 = mbd;
        final VarInt calls = new VarInt(0);
        TCInvalidate.HeaderChange hc = new TCInvalidate.HeaderChange() {
            public boolean needsOriginal() {
                return true;
            }
            public void change(byte[] hdr, boolean isBackup) throws TCLibException {
                assertFalse(isBackup);
                assertTrue(256 * 512 == hdr.length);
                assertTrue(TestUtils.checkFill(new BytePtr(hdr, 0, hdr.length), (byte)'a'));
                Arrays.fill(hdr, (byte)'b');
                calls.v++;
            }
        };
        calls.v = 0;
        assertTrue(TCInvalidate.destroy(bd2, hc, new TestProgress()));
        assertTrue(1 == calls.v);
        byte[] buf2 = mbd.buffer();
        assertTrue(TestUtils.checkFill(new BytePtr(buf2, 0, buf2.length), (byte)'b'));
    }

    @Test
    public void testBadBDevs() throws Exception {
        final VarInt calls = new VarInt(0);
        final VarBool needsOriginal = new VarBool(true);
        TCInvalidate.HeaderChange hc = new TCInvalidate.HeaderChange() {
            public boolean needsOriginal() {
                return needsOriginal.v;
            }
            public void change(byte[] hdr, boolean isBackup) throws TCLibException {
                assertTrue(0 == calls.v++ ^ isBackup);
                assertTrue(256 * 512 == hdr.length);
                assertTrue(TestUtils.checkFill(new BytePtr(hdr, 0, hdr.length), (byte)0));
            }
        };
        try {
            assertTrue(TCInvalidate.destroy(new BlockDeviceImpl.MemoryBlockDevice(512, 255, false, false), hc, new TestProgress()));
            fail();
        }
        catch (TCLibException tle) {
            assertEquals("not enough blocks (volume too small)", tle.getMessage());
            assertTrue(0 == calls.v);
        }
        try {
            assertTrue(TCInvalidate.destroy(new BlockDeviceImpl.MemoryBlockDevice(512, 513, false, true), hc, new TestProgress()));
            fail();
        }
        catch (TCLibException tle) {
            assertEquals("block device does not support reading", tle.getMessage());
            assertTrue(0 == calls.v);
        }
        try {
            assertTrue(TCInvalidate.destroy(new BlockDeviceImpl.MemoryBlockDevice(512, 513, true, false), hc, new TestProgress()));
            fail();
        }
        catch (TCLibException tle) {
            assertEquals("block device is not writeable", tle.getMessage());
            assertTrue(0 == calls.v);
        }

        needsOriginal.v = true;
        calls.v = 0;
        BlockDevice fbd = new FailingBlockDevice(this.bd, true, false, false);
        try {
            assertTrue(TCInvalidate.destroy(fbd, hc, new TestProgress()));
            fail();
        }
        catch (TCLibException tle) {
            assertTrue(tle.getMessage().contains("__fail_read__"));
            assertTrue(0 == calls.v);
        }
        fbd = new FailingBlockDevice(this.bd, true, true, false);
        needsOriginal.v = false;
        try {
            assertTrue(TCInvalidate.destroy(fbd, hc, new TestProgress()));
            fail();
        }
        catch (TCLibException tle) {
            assertTrue(tle.getMessage().contains("__fail_write__"));
            assertTrue(1 == calls.v);
        }
    }

    static class FailingBlockDevice implements BlockDevice {
        final BlockDevice bdev;
        final boolean failRead, failWrite, failClose;
        public FailingBlockDevice(BlockDevice bdev,
                boolean failRead, boolean failWrite, boolean failClose) {
            this.bdev = bdev;
            this.failRead  = failRead;
            this.failWrite = failWrite;
            this.failClose = failClose;
        }
        public int     blockSize  () { return this.bdev.blockSize(); }
        public long    size       () { return this.bdev.size(); }
        public boolean readOnly   () { return this.bdev.readOnly(); }
        public boolean writeOnly  () { return this.bdev.writeOnly(); }
        public boolean serialWrite() { return this.bdev.serialWrite(); }
        public void write(long num, byte[] block, int ofs) throws IOException {
            if (this.failWrite) throw new IOException("__fail_write__");
            this.bdev.write(num, block, ofs);
        }
        public void read(long num, byte[] block, int ofs) throws IOException {
            if (this.failRead) throw new IOException("__fail_read__");
            this.bdev.read(num, block, ofs);
        }
        public void close(boolean err) throws IOException {
            if (this.failClose) throw new IOException("__fail_close__");
            this.bdev.close(err);
        }
    }
}
