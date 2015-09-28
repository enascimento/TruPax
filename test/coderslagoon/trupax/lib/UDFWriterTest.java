package coderslagoon.trupax.lib;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.security.SecureRandom;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.Set;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import coderslagoon.baselib.io.BlockDevice;
import coderslagoon.baselib.io.BlockDeviceImpl;
import coderslagoon.baselib.io.DbgFileSystem;
import coderslagoon.baselib.io.FileNode;
import coderslagoon.baselib.io.FileRegistrar;
import coderslagoon.baselib.io.FileSystem;
import coderslagoon.baselib.io.LocalFileSystem;
import coderslagoon.baselib.io.BlockDeviceImpl.HookBlockDevice;
import coderslagoon.baselib.io.BlockDeviceImpl.NullWriteDevice;
import coderslagoon.baselib.io.FileRegistrar.Directory;
import coderslagoon.baselib.io.FileRegistrar.InMemory.DefCmp;
import coderslagoon.baselib.util.Clock;
import coderslagoon.baselib.util.Combo;
import coderslagoon.baselib.util.Log;
import coderslagoon.baselib.util.MiscUtils;
import coderslagoon.baselib.util.Routine;
import coderslagoon.baselib.util.VarBool;
import coderslagoon.baselib.util.VarInt;
import coderslagoon.baselib.util.VarLong;
import coderslagoon.baselib.util.VarRef;
import coderslagoon.test.util.FileNameMaker;
import coderslagoon.test.util.TestUtils;
import coderslagoon.trupax.lib.UDFWriter;
import coderslagoon.trupax.lib.Writer;
import coderslagoon.trupax.lib.io.filesystem.udf.Browser;
import coderslagoon.trupax.lib.io.filesystem.udf.UDF;

public class UDFWriterTest
    extends WriterTest
    implements Browser.Listener, FileRegistrar.Callback {
    File tmpDir;
    File rootDir;

    void makeRootDir() {
        this.tmpDir = new File(System.getProperty("java.io.tmpdir", null));

        final String rdname = String.format("%s_%d_%08x",
                this.getClass().getName(),
                System.currentTimeMillis(),
                new SecureRandom().nextInt());

        this.rootDir = new File(this.tmpDir, rdname);

        assertTrue(TestUtils.removeDir(this.rootDir, true));

        if (!this.rootDir.mkdirs()) {
            fail(String.format("cannot make root directory '%s'",
                               this.rootDir.getAbsolutePath()));
        }
    }

    void delayClock(final long delay) {
        UDFWriter._clock = new Clock() {
            public long now() {
                return Clock._system.now() - delay;
            }
        };
    }

    ///////////////////////////////////////////////////////////////////////////

    final static Log.Level DEF_LOG_LVL = Log.Level.TRACE;

    @Before
    public void setUp() {
        Log.addPrinter(System.out);
        Log.level(DEF_LOG_LVL);
        makeRootDir();
    }

    @After
    public void tearDown() {
        assertTrue(TestUtils.removeDir(this.rootDir, true));
        UDFWriter.__TEST_limitDirectorySize(-1L);
        UDFWriter.__TEST_noPathLengthCheck = false;
        UDFWriter._clock = Clock._system;
        this.verify = null;
        this.captureOutput = false;
        this.dirs = null;
        this.files = null;
        Browser._bulkReadProgress = false;
        Log.reset();
    }

    ///////////////////////////////////////////////////////////////////////////

    final static int BLOCK_SZ = 512;

    ///////////////////////////////////////////////////////////////////////////

    static int registerDirectory(FileRegistrar freg,
                                 FileSystem fs,
                                 FileNode dir,
                                 FileNode bottom) throws IOException {
        Iterator<FileNode> ifn = fs.list(dir, new FileSystem.Filter() {
            public boolean matches(FileNode file) {
                return true;
            }
        });

        ArrayList<FileNode> files = new ArrayList<>();

        int result = 0;

        while (ifn.hasNext()) {
            FileNode fn = ifn.next();

            if (fn.hasAttributes(FileNode.ATTR_DIRECTORY)) {
                result += registerDirectory(freg, fs, fn, bottom);
            }
            else {
                result++;
            }
            files.add(fn);
        }

        freg.add(files, bottom, null, new FileRegistrar.Callback() {
            public Merge onMerge(FileNode[] nd0, FileNode nd1) {
                if (nd0[0].hasAttributes(FileNode.ATTR_DIRECTORY) &&
                    nd0[0].hasAttributes(FileNode.ATTR_DIRECTORY)) {
                    return Merge.IGNORE;
                }
                fail();
                return null;
            }
        });

        return result;
    }

    ///////////////////////////////////////////////////////////////////////////

    @Test
    public void test0() throws IOException {
        File singleFile = new File(this.tmpDir,
                String.format("%08x.dat", new SecureRandom().nextInt()));

        assertTrue(singleFile.delete() || !singleFile.exists());
        TestUtils.fillFile123(singleFile, 0);

        TestUtils.fillFile123(new File(this.rootDir, "x.dat"), 10);
        TestUtils.fillFile123(new File(this.rootDir, "y.dat"), 299);

        File subDir = new File(this.rootDir, "folder");
        assertTrue(subDir.mkdirs());

        TestUtils.fillFile123(new File(subDir, "z.dat"), 1001);

        FileRegistrar freg = new FileRegistrar.InMemory(new DefCmp(false));

        LocalFileSystem lfs = new LocalFileSystem(false);

        FileNode fn = lfs.nodeFromString(this.rootDir.getAbsolutePath());
        int fcount = registerDirectory(freg, lfs, fn, fn);
        assertTrue(3 == fcount);

        List<FileNode> files = new ArrayList<>();
        fn = lfs.nodeFromString(singleFile.getAbsolutePath());
        files.add(fn);
        freg.add(files, fn, null, new FileRegistrar.Callback() {
            public Merge onMerge(FileNode[] nd0, FileNode nd1) {
                fail();
                return null;
            }
        });
        fcount++;

        //FileRegistrar.dump(freg.root(), 0, System.out);

        Properties props = new Properties();

        Writer w = new UDFWriter(freg, props);

        long bcount = w.resolve(new Writer.Layout() {
            public int     blockSize   () { return BLOCK_SZ; }
            public long    freeBlocks  () { return 23; }
            public String  label       () { return " mylabel\n"; }
            public boolean writeProtect() { return false; }
        });
        assertTrue(0L < bcount);
        assertTrue(0L == (bcount & (1L << 32)));

        System.out.printf("%d blocks\n", bcount);

        ByteArrayOutputStream baos = new ByteArrayOutputStream(BLOCK_SZ * (int)bcount);

        BlockDevice bd = new BlockDeviceImpl.OutputStreamBlockDevice(baos, bcount, BLOCK_SZ, false);

        w.make(bd, Writer.newDebugProgress(System.out));

        baos.flush();
        baos.close();
        byte[] data = baos.toByteArray();

        assertTrue(data.length == bcount * BLOCK_SZ);

        File dump = TestUtils.dumpToFile(data, "UDFWriterTest.test0.dump", true);
        assertNotNull(dump);

        if (UDFTest.available()) {
            assertTrue(UDFTest.exec(dump, BLOCK_SZ, true, false, false, null));
        }
        BlockDevice bdev = new BlockDeviceImpl.MemoryBlockDevice(BLOCK_SZ, data, false, false);

        Browser b = browse(bdev, Log.Level.WARN, true);
        assertEquals(b.logicalVolumeDescriptor.logicalVolumeIdentifier, "mylabel_");
        assertEquals(b.fileSetDescriptor.logicalVolumeIdentifier      , "mylabel_");

        assertTrue(this.dirs.size () == 2);
        assertTrue(this.files.size() == 4);
        assertTrue(this.files.size() == fcount);

        assertTrue(this.dirs.contains(PATH_SEPA + this.rootDir.getName()));
        assertTrue(this.dirs.contains(PATH_SEPA + this.rootDir.getName() + PATH_SEPA + "folder"));

        String path = PATH_SEPA + this.rootDir.getName() + PATH_SEPA;
        FileCapture fc = this.files.get(path + "x.dat");
        data = fc.baos.toByteArray();
        assertTrue(10 == fc.length);
        assertTrue(10 == data.length);
        assertTrue(TestUtils.checkPattern123(data, 0, data.length));

        fc = this.files.get(path + "y.dat");
        data = fc.baos.toByteArray();
        assertTrue(299 == fc.length);
        assertTrue(299 == data.length);
        assertTrue(TestUtils.checkPattern123(data, 0, data.length));

        path = PATH_SEPA + this.rootDir.getName() + PATH_SEPA + "folder" + PATH_SEPA;
        fc = this.files.get(path + "z.dat");
        data = fc.baos.toByteArray();
        assertTrue(1001 == fc.length);
        assertTrue(1001 == data.length);
        assertTrue(TestUtils.checkPattern123(data, 0, data.length));

        fc = this.files.get(PATH_SEPA + singleFile.getName());
        data = fc.baos.toByteArray();
        assertTrue(0 == fc.length);
        assertTrue(0 == data.length);
    }

    ///////////////////////////////////////////////////////////////////////////

    boolean captureOutput;

    static class FileCapture {
        long                  length;
        ByteArrayOutputStream baos;
    };

    Set<String>              dirs  = new HashSet<>();
    Map<String, FileCapture> files = new HashMap<>();

    public void onDirectory(String name, long time) {
        //System.out.println("onDirectory '" + name + "'");
        assertFalse(this.dirs.contains(name));
        this.dirs.add(name);
        if (null != this.verify) {
            verifyDirectory(name, time);
        }
    }

    public OutputStream onFile(String name, long time, long length) throws IOException {
        //System.out.println("onFile '" + name + "', " + length + " bytes");
        FileCapture fc = new FileCapture();
        fc.length = length;
        assertNull(this.files.get(name));
        this.files.put(name, fc);
        if (null != this.verify) {
            return verifyFile(name, time, length);
        }
        if (this.captureOutput) {
            return fc.baos = new ByteArrayOutputStream();
        }
        return TestUtils.newNulOutputStream();
    }

    ///////////////////////////////////////////////////////////////////////////

    @Test
    public void testComputeUnallocatedSpaceBitmapLength() {
        final int BSZ = 512;
        UDFWriter uw = new UDFWriter(null, null);
        uw.layout = new Writer.Layout() {
            public int     blockSize   () { return BSZ; }
            public long    freeBlocks  () { return -1; }
            public String  label       () { return null; }
            public boolean writeProtect() { return false; }
        };

        assertTrue(26        == uw.computeUnallocatedSpaceBitmapLength(24, 13));
        assertTrue(25        == uw.computeUnallocatedSpaceBitmapLength(24, 0));
        assertTrue(125030549 == uw.computeUnallocatedSpaceBitmapLength(24, 1000000001));
        assertTrue(367       == uw.computeUnallocatedSpaceBitmapLength(24, 2739));

        final int COUNT = 100000;
        Random rnd = new Random(0xbaadf00d);

        long maxslack = 0, minslack = Long.MAX_VALUE;

        for (long MAX_PARTITION_BYTES : new long[] {
                2L * 1024L * 1024L * 1024L * 1024L,
                2L * 1024L * 1024L * 1024L,
                2L * 1024L * 1024L,
                2L * 1024L,
                512
        }) {
            for (int i = 0; i < COUNT; i++) {
                final long MOD = MAX_PARTITION_BYTES / 512;  // an ubit represents a block!
                long ubits = Math.abs(rnd.nextLong()) % MOD;
                int res = uw.computeUnallocatedSpaceBitmapLength(24, ubits);
                assertTrue(res > 0);
                int bres = (res / BSZ) + ((0 == (res % BSZ)) ? 0 : 1);
                long allbits = 24 * 8 + ubits + bres;
                long resbits = bres * 512L * 8L;
                long slack = resbits - allbits;
                if (0 > slack || slack > 4097) {
                    System.err.printf(
                            "ubits=%d res=%d allbits=%d resbits=%d slack=%d\n",
                            ubits, res, allbits, resbits, slack);
                    fail();
                }
                maxslack = Math.max(maxslack, slack);
                minslack = Math.min(minslack, slack);
            }
        }
        System.out.printf("maxslack=%d\nminslack=%d\n", maxslack, minslack);
        assertTrue(maxslack <= 4097L);
        assertTrue(minslack ==    0L);
    }

    ///////////////////////////////////////////////////////////////////////////

    @Test
    public void testWithDbgFS() throws IOException {
        DbgFileSystem dfs = new DbgFileSystem(true, null);

        final String ROOT = "C";

        assertNotNull(dfs.createFile("0.dat", new String[] { ROOT },
                1311, 0x100001, FileNode.ATTR_READONLY, false));

        assertNotNull(dfs.createFile("test", new String[] { ROOT, "1", "2" },
                0, 0x100002, 0, false));

        assertNotNull(dfs.createFile(null, new String[] { ROOT, "1", "New Folder" },
                0, 0, 0, false));

        FileRegistrar freg = new FileRegistrar.InMemory(new DefCmp(false));

        Iterator<FileNode> roots = dfs.roots();
        FileNode root = roots.next();
        assertFalse(roots.hasNext());
        int res = registerDirectory(freg, dfs, root, root);
        assertTrue(2 == res);

        FileRegistrar.dump(freg.root(), 0, System.out);

        Properties props = new Properties();
        Writer w = new UDFWriter(freg, props);

        long bcount = w.resolve(new Writer.Layout() {
            public int     blockSize   () { return BLOCK_SZ; }
            public long    freeBlocks  () { return 0; }
            public String  label       () { return null; }
            public boolean writeProtect() { return false; }
        });
        assertTrue(0L < bcount);
        assertTrue(0L == (bcount & 0xffffffff00000000L));

        System.out.printf("%d blocks\n", bcount);

        ByteArrayOutputStream baos = new ByteArrayOutputStream(BLOCK_SZ * (int)bcount);
        BlockDevice bd = new BlockDeviceImpl.OutputStreamBlockDevice(baos, bcount, BLOCK_SZ, false);

        w.make(bd, Writer.newDebugProgress(System.out));

        byte[] data = baos.toByteArray();

        //TestUtils.dumpToFile(data, "C:\\tmp\\test1.dat", false);

        BlockDevice bdev = new BlockDeviceImpl.MemoryBlockDevice(BLOCK_SZ, data, false, false);

        browse(bdev, Log.Level.WARN, true);

        assertTrue(this.dirs.size() == 3);

        FileCapture fc = this.files.get(PATH_SEPA + "0.dat");
        assertTrue(1311 == fc.length);

        assertTrue(TestUtils.inputStreamsEqual(
                new ByteArrayInputStream(fc.baos.toByteArray()),
                DbgFileSystem.createInputStream("0.dat", 1311, true),
                true));

        fc = this.files.get(PATH_SEPA + "1" + PATH_SEPA + "2" + PATH_SEPA + "test");
        assertTrue(0 == fc.length);
    }


    ///////////////////////////////////////////////////////////////////////////

    final static long ONE_GB = 1024L * 1024L * 1024L;

    @Test
    public void testOversizedFile() throws IOException {
        DbgFileSystem dfs = new DbgFileSystem(true, null);

        final String ROOT = "X";

        assertNotNull(dfs.createFile("maxi.file", new String[] { ROOT },
                ONE_GB * 20000, 0x100001, FileNode.ATTR_NONE, false));

        FileRegistrar freg = new FileRegistrar.InMemory(new DefCmp(false));
        FileNode root = dfs.roots().next();

        assertTrue(1 == registerDirectory(freg, dfs, root, root));

        Properties props = new Properties();
        Writer w = new UDFWriter(freg, props);

        try {
            w.resolve(new Writer.Layout() {
                public int    blockSize    () { return BLOCK_SZ; }
                public long   freeBlocks   () { return 0; }
                public String label        () { return null; }
                public boolean writeProtect() { return false; }
            });
            fail();
        }
        catch (Writer.Exception we) {
            assertTrue(we.error == Writer.ERROR_FILE_TOO_LARGE);
            assertTrue(TestUtils.resStrValid(we.getMessage()));
        }
    }

    ///////////////////////////////////////////////////////////////////////////

    boolean isValidFileSize(long fsz) throws IOException {
        final String ROOT = "X";

        DbgFileSystem dfs = new DbgFileSystem(true, null);

        assertNotNull(dfs.createFile("largest.file", new String[] { ROOT },
                fsz, 123, FileNode.ATTR_NONE, false));

        FileRegistrar freg = new FileRegistrar.InMemory(new DefCmp(false));
        FileNode root = dfs.roots().next();

        assertTrue(1 == registerDirectory(freg, dfs, root, root));

        Properties props = new Properties();
        Writer w = new UDFWriter(freg, props);

        try {
            w.resolve(new Writer.Layout() {
                public int     blockSize   () { return BLOCK_SZ; }
                public long    freeBlocks  () { return 0; }
                public String  label       () { return null; }
                public boolean writeProtect() { return false; }
            });
            return true;
        }
        catch (Writer.Exception we) {
            assertTrue(we.error == Writer.ERROR_FILE_TOO_LARGE);
            return false;
        }
    }

    @Test
    public void testLargestFile() throws IOException {
        long fsz = ONE_GB * 2048;
        long fsz2 = -1L;

        for (long d = fsz >> 1; 0 < d; d >>= 1) {
            if (isValidFileSize(fsz)) {
                fsz2 = fsz;
                fsz += d;
            }
            else {
                fsz -= d;
            }
        }

        assertFalse(-1L == fsz2);
        assertFalse(isValidFileSize(fsz2 + 1));

        System.out.println("largest file size: " + fsz2);

        // make sure that we can actually write this
        DbgFileSystem dfs = new DbgFileSystem(false, null);

        assertNotNull(dfs.createFile("largest.file", new String[] { "B" },
                fsz2, 987654321, FileNode.ATTR_NONE, false));

        FileRegistrar freg = new FileRegistrar.InMemory(new DefCmp(false));
        FileNode root = dfs.roots().next();

        assertTrue(1 == registerDirectory(freg, dfs, root, root));

        Properties props = new Properties();
        Writer w = new UDFWriter(freg, props);

        final long bcount = w.resolve(new Writer.Layout() {
            public int     blockSize   () { return BLOCK_SZ; }
            public long    freeBlocks  () { return 0; }
            public String  label       () { return null; }
            public boolean writeProtect() { return false; }
        });
        assertTrue(0L < bcount);

        if (coderslagoon.test.Control.quick()) {
            return;
        }

        final VarInt fcount = new VarInt();
        final VarLong total = new VarLong();
        final VarLong last = new VarLong(System.currentTimeMillis());

        w.make(new HookBlockDevice(new NullWriteDevice(BLOCK_SZ)) {
                protected boolean onRead(long num) {
                    return false;
                }
                protected boolean onWrite(long num) {
                    total.v = num;
                    long now = System.currentTimeMillis();
                    if (now - last.v > 1000) {
                        System.out.println(this.df.format(num));
                        System.out.flush();
                        last.v = now;
                    }
                    return true;
                }
                final DecimalFormat df = new DecimalFormat();
            },
            new Writer.Progress() {
                public void onFile(Directory dir, FileNode node) {
                    fcount.v += null == dir || node == null ? 0 : 1;
                }
            });

        assertTrue(total.v + 1 == bcount);  // (progress reports 0..N-1)
        assertTrue(fcount.v == 1);
    }

    ///////////////////////////////////////////////////////////////////////////

    @Test
    public void testLargestDirectory() throws IOException {
        final int MAXDIRSZ = 68000;

        UDFWriter.__TEST_limitDirectorySize(MAXDIRSZ);

        DbgFileSystem dfs = new DbgFileSystem(false, null);

        final String FMT   = MiscUtils.fillString(200, 'a') + "%06d";
        final int    COUNT = MAXDIRSZ >> 7;

        for (int i = 0; i < COUNT; i++) {
            assertNotNull(dfs.createFile(String.format(FMT, i),
                    new String[] { "D" }, 1, 777, FileNode.ATTR_NONE, false));
        }

        FileRegistrar freg = new FileRegistrar.InMemory(new DefCmp(false));
        FileNode root = dfs.roots().next();

        assertTrue(COUNT == registerDirectory(freg, dfs, root, root));

        Properties props = new Properties();
        Writer w = new UDFWriter(freg, props);

        try {
            w.resolve(new Writer.Layout() {
                public int     blockSize   () { return BLOCK_SZ; }
                public long    freeBlocks  () { return 0; }
                public String  label       () { return null; }
                public boolean writeProtect() { return false; }
            });
            fail();
        }
        catch (Writer.Exception we) {
            assertNotNull(TestUtils.resStrValid(we.getMessage()));
        }
    }

    ///////////////////////////////////////////////////////////////////////////

    @Test
    public void testOversizing() throws Exception {
        final VarBool resolved = new VarBool();
        final VarLong freeblocks = new VarLong(0);

        delayClock(45000L);

        Routine.Arg2E<Boolean, DbgFileSystem, Boolean> r = new Routine.Arg2E<Boolean, DbgFileSystem, Boolean>() {
            public Boolean call(DbgFileSystem dfs, Boolean expected) throws Exception {
                resolved.v = false;

                FileRegistrar freg = new FileRegistrar.InMemory(new DefCmp(false));

                FileNode root = dfs.roots().next();

                assertTrue(1 == registerDirectory(freg, dfs, root, root));

                Properties props = new Properties();
                Writer w = new UDFWriter(freg, props);

                long bcount = w.resolve(new Writer.Layout() {
                    public int     blockSize   () { return BLOCK_SZ; }
                    public long    freeBlocks  () { return freeblocks.v; }
                    public String  label       () { return null; }
                    public boolean writeProtect() { return false; }
                });
                assertTrue(0 < bcount);

                resolved.v = true;

                File tmp = new File(UDFWriterTest.this.tmpDir,
                                    "UDFWriterTest.testOversizing.dat");
                assertTrue(!tmp.exists() || tmp.delete());

                FileOutputStream fos = new FileOutputStream(tmp);
                try {
                    BlockDevice bd = new BlockDeviceImpl.OutputStreamBlockDevice(fos, bcount, BLOCK_SZ, false);
                    w.make(bd, Writer.newDebugProgress(System.out));
                }
                finally {
                    fos.close();
                }

                boolean result = UDFTest.available() ?
                    UDFTest.exec(tmp, BLOCK_SZ, false, false, false, null) :
                    expected;
                assertTrue(tmp.delete());

                return result;
            }
        };

        final int MXL = UDF.MAX_FILENAME_DSTRLEN - 1;

        // testing file names...

        DbgFileSystem dfs = new DbgFileSystem(false, null);
        assertNotNull(dfs.createFile(MiscUtils.fillChars(MXL, 'a').toString(),
                new String[] { "D" }, 1, 1970L, FileNode.ATTR_NONE, false));

        assertTrue(r.call(dfs, true));
        assertTrue(resolved.v);

        dfs = new DbgFileSystem(false, null);
        assertNotNull(dfs.createFile(MiscUtils.fillChars(MXL + 1, 'a').toString(),
                new String[] { "D" }, 1, 1970L, FileNode.ATTR_NONE, false));
        try {
            r.call(dfs, true);
            fail();
        }
        catch (Writer.Exception we) {
            assertTrue(we.error == Writer.ERROR_NAME_TOO_LONG);
        }
        assertFalse(resolved.v);

        // testing directory names...

        dfs = new DbgFileSystem(false, null);
        assertNotNull(dfs.createFile("1.txt",
                new String[] { "D", MiscUtils.fillChars(MXL, 'd').toString() },
                1, 1970L, FileNode.ATTR_NONE, false));

        assertTrue(r.call(dfs, true));
        assertTrue(resolved.v);

        dfs = new DbgFileSystem(false, null);
        assertNotNull(dfs.createFile("1.txt",
                new String[] { "D", MiscUtils.fillChars(MXL + 1, 'd').toString() },
                1, 1970L, FileNode.ATTR_NONE, false));
        try {
            r.call(dfs, true);
            fail();
        }
        catch (Writer.Exception we) {
            assertTrue(we.error == Writer.ERROR_NAME_TOO_LONG);
        }
        assertFalse(resolved.v);

        // testing Unicode extremes...

        dfs = new DbgFileSystem(false, null);
        assertNotNull(dfs.createFile(MiscUtils.fillChars(MXL / 2, (char)0x1234).toString(),
                new String[] { "D" }, 1, 1970L, FileNode.ATTR_NONE, false));

        assertTrue(r.call(dfs, true));
        assertTrue(resolved.v);

        dfs = new DbgFileSystem(false, null);
        assertNotNull(dfs.createFile(MiscUtils.fillChars(MXL / 2  +1, (char)0x1234).toString(),
                new String[] { "D" }, 1, 1970L, FileNode.ATTR_NONE, false));
        try {
            r.call(dfs, true);
            fail();
        }
        catch (Writer.Exception we) {
            assertTrue(we.error == Writer.ERROR_NAME_TOO_LONG);
        }
        assertFalse(resolved.v);

        // testing maximum path length and also the deepest nesting possible...

        dfs = new DbgFileSystem(false, null);

        final String[] path = new String[UDF.MAX_PATH_LEN >> 1];
        path[0] = "root1";
        Arrays.fill(path, 1, path.length, "d");

        assertNotNull(dfs.createFile("ff", path, 1, 555L, FileNode.ATTR_NONE, false));
        assertTrue(r.call(dfs, true));

        dfs = new DbgFileSystem(false, null);

        assertNotNull(dfs.createFile("fff", path, 1, 555L, FileNode.ATTR_NONE, false));

        StringBuilder sb = new StringBuilder();
        for (final String p : path) {
            sb.append(p);
            sb.append('/');
        }
        sb.append("fff");

        final String oversized = sb.toString();
        assertNotNull(dfs.nodeFromString(oversized));

        UDFWriter.__TEST_noPathLengthCheck = true;

        assertFalse(r.call(dfs, false));

        UDFWriter.__TEST_noPathLengthCheck = false;

        try {
            r.call(dfs, false);
            fail();
        }
        catch (Writer.Exception we) {
            assertTrue(Writer.ERROR_PATH_TOO_LONG == we.error);
            assertFalse(resolved.v);
        }
    }

    ///////////////////////////////////////////////////////////////////////////

    @Test
    public void testFreeBlocksMax() throws Exception {
        Properties props = new Properties();
        DbgFileSystem dfs = new DbgFileSystem(false, null);
        dfs.addRoot("nofiles");

        FileRegistrar freg = new FileRegistrar.InMemory(new DefCmp(false));
        FileNode root = dfs.roots().next();
        assertTrue(0 == registerDirectory(freg, dfs, root, root));

        final VarLong freeblocks = new VarLong(0x100000000L);
        long delta = freeblocks.v >> 1;
        long last = -1L;
        for (;0L < delta; delta >>= 1) {
            try {
                Writer w = new UDFWriter(freg, props);

                assertTrue(0 < w.resolve(new Writer.Layout() {
                    public int     blockSize   () { return BLOCK_SZ; }
                    public long    freeBlocks  () { return freeblocks.v; }
                    public String  label       () { return null; }
                    public boolean writeProtect() { return false; }
                }));

                last = freeblocks.v;
                freeblocks.v += delta;
            }
            catch (Writer.Exception we) {
                assertTrue(we.error == Writer.ERROR_TOO_MUCH_DATA);
                freeblocks.v -= delta;
            }
        }
        assertTrue(0L == delta);
        assertTrue(last <= freeblocks.v);

        // give the perfect size a run
        Writer w = new UDFWriter(freg, props);
        freeblocks.v = last;

        long blocks = w.resolve(new Writer.Layout() {
            public int     blockSize   () { return BLOCK_SZ; }
            public long    freeBlocks  () { return freeblocks.v; }
            public String  label       () { return null; }
            public boolean writeProtect() { return false; }
        });
        assertTrue(Integer.MAX_VALUE == blocks);

        if (coderslagoon.test.Control.quick()) {
            return;
        }

        final VarBool unexpected = new VarBool(false);

        long tm = System.currentTimeMillis();

        w.make(new BlockDeviceImpl.NullWriteDevice(BLOCK_SZ), new Writer.Progress() {
            public void onFile(Directory d, FileNode n) {
                unexpected.v |= null != d || n != null;
            }
        });

        System.out.printf("make time: %.1f seconds\n",
        		(System.currentTimeMillis() - tm) / 1000.0);

        assertFalse(unexpected.v);
    }

    ///////////////////////////////////////////////////////////////////////////

    @Test
    public void testRandomVolume0() throws Exception {
        final FileNameMaker[] fnmks = new FileNameMaker[] {
                null,
                new FileNameMaker.Numbered(),
                new FileNameMaker.RandomASCII(),
                new FileNameMaker.RandomUnicode(),
        };
        fnmks[0] = new FileNameMaker.Mixer(fnmks);

        for (final FileNameMaker fnmk : fnmks) {
            final int MAX_FILES = 100; // this limit is the winner below

            Combo.Two<FileRegistrar, DbgFileSystem> r = make(
                new MakeLayout() {
                    public long maxData         () { return 7654321L; }
                    public int  maxDirNameBytes () { return 32; }
                    public int  maxFileNameBytes() { return 64; }
                    public int  maxDirs         () { return 30; }
                    public int  maxFileSize     () { return 100000; }
                    public int  maxFiles        () { return MAX_FILES; }
                    public int  maxFilesPerDir  () { return 10; }
                    public int  maxPathLen      () { return UDF.MAX_PATH_LEN; }
                },
                new MakeEnv() {
                    public FileNameMaker fnmk   () { return fnmk; }
                    public int           rndBase() { return 0xd0debabe; }
                });

            FileRegistrar.Walker.Counting fwc = new FileRegistrar.Walker.Counting();
            FileRegistrar.walk(r.t.root(), fwc, true, false);
            assertTrue(11        == fwc.directories);
            assertTrue(MAX_FILES == fwc.files);

            //System.out.println(MiscUtils.fillString(132, '_'));
            //FileRegistrar.dump(r.t.root(), 0, System.out);

            Writer w = new UDFWriter(r.t, new Properties());

            long blocks = w.resolve(new Writer.Layout() {
                public int     blockSize   () { return BLOCK_SZ; }
                public long    freeBlocks  () { return 0; }
                public String  label       () { return null; }
                public boolean writeProtect() { return false; }
            });
            assertTrue(0 < blocks);

            final VarInt fcount = new VarInt(0);

            ByteArrayOutputStream baos = new ByteArrayOutputStream((int)blocks * BLOCK_SZ);
            BlockDevice bdev = new BlockDeviceImpl.OutputStreamBlockDevice(baos, blocks, BLOCK_SZ, true);

            w.make(bdev, new Writer.Progress() {
                public void onFile(Directory dir, FileNode fn) {
                    if (fn == null && dir == null) {
                        return;
                    }
                    fcount.v++;
                }
            });
            assertTrue(MAX_FILES == fcount.v);

            byte[] data = baos.toByteArray();
            baos.close();
            bdev = new BlockDeviceImpl.MemoryBlockDevice(BLOCK_SZ, data, true, false);

            browse(bdev, Log.Level.WARN, false);

            assertTrue(this.dirs.size () == 11);
            assertTrue(this.files.size() == MAX_FILES);

            File dump = TestUtils.dumpToFile(data, "UDFWriterTest.testRandomVolume0.dump", true);
            try {
                if (UDFTest.available()) {
                    assertTrue(UDFTest.exec(dump, BLOCK_SZ, true, false, false, null));
                }
            }
            finally {
                assertTrue(!dump.exists() || dump.delete());
            }
        }
    }

    ///////////////////////////////////////////////////////////////////////////

    public void runRndVol(final String name, final MakeLayout mlo,
            final int loops, final boolean verify) throws Exception {
        final FileNameMaker fnmk = new FileNameMaker.Mixer(new FileNameMaker[] {
                new FileNameMaker.RandomASCII(),
                new FileNameMaker.RandomUnicode(),
                new FileNameMaker.Numbered(),
        });

        final Random seeder = new Random(0xb00bbebe);

        Log.reset();
        final long startTm = System.currentTimeMillis();

        for (int i = 0; i < loops; i++) {
            System.out.printf("%s - loop %d...\n", name, i);

            Combo.Two<FileRegistrar, DbgFileSystem> r = make(mlo, new MakeEnv() {
                public FileNameMaker fnmk() { return fnmk; }
                public int  rndBase      () { return seeder.nextInt(); }
            });

            //FileRegistrar.dump(r.t.root(), 0, System.out);

            FileRegistrar.Walker.Counting fwc = new FileRegistrar.Walker.Counting();
            FileRegistrar.walk(r.t.root(), fwc, true, false);
            final int fcount = fwc.files;

            final Writer w = new UDFWriter(r.t, new Properties());

            final long blocks = w.resolve(new Writer.Layout() {
                public int     blockSize   () { return BLOCK_SZ; }
                public long    freeBlocks  () { return 0; }
                public String  label       () { return null; }
                public boolean writeProtect() { return false; }
            });
            assertTrue(0 < blocks);

            final VarInt fcount2 = new VarInt(0);

            File tmp = new File(System.getProperty("java.io.tmpdir"),  String.format(
                                "UDFWriterTest.testRandomVolumeMedium%06d.dump", i));
            assertTrue(!tmp.exists() || tmp.delete());

            FileOutputStream fos = new FileOutputStream(tmp);

            BlockDevice bdev = new BlockDeviceImpl.OutputStreamBlockDevice(fos, blocks, BLOCK_SZ, false);

            w.make(bdev, new Writer.Progress() {
                public void onFile(Directory dir, FileNode fn) {
                    if (fn == null && dir == null) {
                        return;
                    }
                    fcount2.v++;
                }
            });
            assertTrue(fcount == fcount2.v);
            fos.close();

            RandomAccessFile raf = new RandomAccessFile(tmp, "r");

            bdev = new BlockDeviceImpl.FileBlockDevice(raf, BLOCK_SZ, -1L, true, false);

            if (verify) {
                this.verify = r.t;
                verifyPrepare(r.t.root());
            }

            browse(bdev, Log.Level.WARN, false);

            assertTrue(this.files.size() == fcount);
            raf.close();

            if (verify) {
                verifyFinal(null);
            }

            if (UDFTest.available()) {
                assertTrue(UDFTest.exec(tmp, BLOCK_SZ, true, false, false, null));
            }
            assertTrue(tmp.delete());
        }
        System.out.printf("test total time: %.3f seconds\n",
        		(System.currentTimeMillis() - startTm) / 1000.0);
    }

    @Test
    public void testRandomVolume16MB() throws Exception {
       runRndVol(
            MiscUtils.currentMethod(),
            new MakeLayout() {
                public long maxData         () { return 16 * 1024 * 1024; }
                public int  maxDirNameBytes () { return 30; }
                public int  maxFileNameBytes() { return 100; }
                public int  maxDirs         () { return 5000; }
                public int  maxFileSize     () { return 1000 * 100; }
                public int  maxFiles        () { return 1000 * 1000; }
                public int  maxFilesPerDir  () { return 100; }
                public int  maxPathLen      () { return UDF.MAX_PATH_LEN; }
            },
            coderslagoon.test.Control.quick() ? 3 : 100,
            true);
    }

    ///////////////////////////////////////////////////////////////////////////

    @Test
    public void testLargeFile() throws Exception {
        if (coderslagoon.test.Control.quick()) {
            return;
        }

        DbgFileSystem dfs = new DbgFileSystem(true, null);

        final long FSIZE = 5 * ONE_GB + 1;

        FileNode fn = dfs.createFile(
                "biggg", new String[] { "root0", "somedir" },
                FSIZE, 1234567, FileNode.ATTR_NONE, false);

        assertNotNull(fn);

        FileRegistrar freg = new FileRegistrar.InMemory(new DefCmp(false));

        List<FileNode> fnlst = new ArrayList<>();
        fnlst.add(fn);

        assertTrue(freg.add(fnlst, null, freg.root(), new FileRegistrar.Callback() {
            public Merge onMerge(FileNode[] fn0, FileNode fn1) {
                return FileRegistrar.Callback.Merge.ABORT;
            }
        }));

        FileRegistrar.dump(freg.root(), 0, System.out);

        Properties props = new Properties();

        Writer w = new UDFWriter(freg, props);

        long bcount = w.resolve(new Writer.Layout() {
            public int     blockSize   () { return BLOCK_SZ; }
            public long    freeBlocks  () { return 0; }
            public String  label       () { return null; }
            public boolean writeProtect() { return false; }
        });
        assertTrue(0L < bcount);
        assertTrue(0L == (bcount & (1L << 32)));

        System.out.printf("%d blocks, %d bytes total\n", bcount, bcount * BLOCK_SZ);

        File tmp = new File(System.getProperty("java.io.tmpdir"),
                TestUtils.createTempFileName(MiscUtils.currentMethod() + "_out"));

        assertTrue(!tmp.exists() || tmp.delete());

        RandomAccessFile raf = new RandomAccessFile(tmp, "rw");

        BlockDevice bdev = new BlockDeviceImpl.FileBlockDevice(raf, BLOCK_SZ, bcount, false, false);

        long tm = System.currentTimeMillis();

        w.make(bdev, Writer.newDebugProgress(System.out));

        System.out.printf("volume with one (%d bytes) large file created in %.3f seconds\n",
                          FSIZE, (double)(System.currentTimeMillis() - tm) / 1000L);

        this.verify = freg;
        verifyPrepare(freg.root());

        Browser._bulkReadProgress = true;

        browse(bdev, Log.Level.WARN, false);
        raf.close();

        assertTrue(1 == this.files.size());

        verifyFinal(null);

        if (UDFTest.available()) {
            assertTrue(UDFTest.exec(tmp, BLOCK_SZ, true, false, false, null));
        }
        assertTrue(tmp.delete());
    }

    ///////////////////////////////////////////////////////////////////////////

    public Merge onMerge(FileNode[] nd0, FileNode nd1) {
        return nd0[0].hasAttributes(FileNode.ATTR_DIRECTORY) &&
               nd1   .hasAttributes(FileNode.ATTR_DIRECTORY) ?
                   FileRegistrar.Callback.Merge.IGNORE :
                   FileRegistrar.Callback.Merge.ABORT;
    }

    public boolean onProgress(FileNode current) {
        return true;
    }

    ///////////////////////////////////////////////////////////////////////////

    FileRegistrar verify;

    static class VerifyTag {
        final static String NAME = "verify.udfwritertest";
        public int count;
    }

    private void verifyPrepare(FileRegistrar.Directory dir) {
        if (null == dir.nodes()) {
            assertNull(dir.parent());
        }
        else {
            dir.nodes()[0].setTag(VerifyTag.NAME, new VerifyTag());
        }
        Iterator<FileRegistrar.Directory> dirs = dir.dirs();
        while (dirs.hasNext()) {
            verifyPrepare(dirs.next());
        }
        Iterator<FileNode> files = dir.files();
        while (files.hasNext()) {
            files.next().setTag(VerifyTag.NAME, new VerifyTag());
        }
    }

    private static String[] verifyPathItems(final String name) {
        // NOTE: the name shall not end with a separator!
        final String sepa = Character.toString(Browser.Listener.PATH_SEPA);

        assertTrue(name.length() > 0 &&
                   name.startsWith(sepa));

        final String[] result = name.substring(1).split(sepa);
        assertTrue(0 < result.length && result[0].length() > 0);
        return result;
    }

    private FileRegistrar.Directory verifyFindDir(String name, VarRef<String> fname) {
        FileRegistrar.Directory result = this.verify.root();
        final String[] pis = verifyPathItems(name);
        for (int i = 0, c = pis.length - (null == fname ? 0 : 1); i < c; i++) {
            Iterator<FileRegistrar.Directory> dirs = result.dirs();
            boolean found = false;
            while (dirs.hasNext()) {
                FileRegistrar.Directory dir = dirs.next();
                if (dir.nodes()[0].name().equals(pis[i])) {
                    result = dir;
                    found = true;
                    break;
                }
            }
            assertTrue(found);
        }
        if (null != fname) {
            fname.v = pis[pis.length - 1];
        }
        return result;
    }

    private void verifyDirectory(String name, long time) {
        assertNotNull(this.verify);

        FileRegistrar.Directory dir = verifyFindDir(name, null);

        assertTrue(time == dir.nodes()[0].timestamp());

        ((VerifyTag)dir.nodes()[0].getTag(VerifyTag.NAME)).count++;
    }

    private OutputStream verifyFile(final String name, long time, final long length) {
        assertNotNull(this.verify);

        // path exists?
        VarRef<String> fname = new VarRef<>();
        FileRegistrar.Directory dir = verifyFindDir(name, fname);
        assertNotNull(fname.v);

        // file's there and has the right size and time?
        final Iterator<FileNode> files = dir.files();
        FileNode fn = null;
        while (files.hasNext()) {
            final FileNode fn2 = files.next();
            if (fn2.name().equals(fname.v)) {
                fn = fn2;
                break;
            }
        }
        if (null == fn) {
            fail();
        }
        if (fn.size() != length) {
            fail();
        }
        if (fn.timestamp() != time) {
            fail();
        }
        ((VerifyTag)fn.getTag(VerifyTag.NAME)).count++;

        // prepare content matching
        final InputStream dis = DbgFileSystem.createInputStream(fname.v, length, true);
        return new OutputStream() {
            @Override
            public void close() throws IOException {
                super.close();
                assertTrue(this.pos == length);
            }
            long pos;
            @Override
            public void write(final int b) throws IOException {
                final int d = dis.read();
                if (d != (255 & b)) {
                    throw new IOException(String.format(
                            "difference encountered for '%s' at position %d (%d!=%d)",
                            name, this.pos, d, 255 & b));
                }
                this.pos++;
            }
        };
    }

    private void verifyFinal(FileRegistrar.Directory dir) {
        if (null == dir) {
            assertNotNull(dir = this.verify.root());
        }

        Iterator<FileRegistrar.Directory> dirs = dir.dirs();
        while (dirs.hasNext()) {
            FileRegistrar.Directory dir2 = dirs.next();
            Object tag = dir2.nodes()[0].getTag(VerifyTag.NAME);
            assertTrue(tag instanceof VerifyTag);
            assertTrue(1 == ((VerifyTag)tag).count);
            verifyFinal(dir2);
        }

        Iterator<FileNode> files = dir.files();
        while (files.hasNext()) {
            FileNode fn = files.next();
            Object tag = fn.getTag(VerifyTag.NAME);
            assertTrue(tag instanceof VerifyTag);
            assertTrue(1 == ((VerifyTag)tag).count);
        }
    }

    ///////////////////////////////////////////////////////////////////////////

    public Browser browse(BlockDevice bdev, Log.Level loglvl, boolean capOut) {
        Log.level(loglvl);

        Browser result = new Browser(bdev, this);

        this.captureOutput = capOut;
        this.dirs.clear();
        this.files.clear();

        try {
            result.exec();
        }
        catch (Throwable err) {
            err.printStackTrace(System.err);
            fail();
        }

        Log.level(Log.Level.TRACE);

        return result;
    }

    ///////////////////////////////////////////////////////////////////////////

    @Test
    public void testNormalizeVolumeID() {
        for (String[] vid : new String[][] {
                { "", "" },
                { " ", "" },
                { "\u1999", "_" },
                { "a", "a" },
                { "0123456789ABCDE" , "0123456789ABCDE" },
                { "0123456789ABCDEF", "0123456789ABCDE" },
                { "\nx\ty\rZ", "_x_y_Z" },
                { " th is\0 ", "th is_" },
                { "!\"$%&/()=?`@;:_",
                  "!\"$%&/()=?`@;:_" },
        }) {
            System.out.println(vid[0]);
            assertEquals(UDFWriter.normalizeVolumeID(vid[0]), vid[1]);
        }
    }
}
