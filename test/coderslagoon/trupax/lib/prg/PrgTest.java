package coderslagoon.trupax.lib.prg;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import coderslagoon.baselib.io.IOUtils;
import coderslagoon.baselib.io.LocalFileSystem;
import coderslagoon.baselib.util.Prp;
import coderslagoon.baselib.util.VarInt;
import coderslagoon.test.util.TestUtils;
import coderslagoon.trupax.lib.prg.Prg;
import coderslagoon.trupax.lib.prg.PrgImpl;
import coderslagoon.trupax.lib.prg.PrgProps;
import coderslagoon.trupax.lib.prg.Prg.Result;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class PrgTest {
    @Before
    public void setUp() {
        new PrgProps.TrimPath().set(Prp.global(), true);
        assertTrue(PrgImpl.init().isSuccess());
    }

    @After
    public void tearDown() {
        delPrg();
        assertTrue(PrgImpl.cleanup().isSuccess());
        LocalFileSystem.__test_FAKE_READ_ERROR  = false;
        PrgImpl.__TEST_write_error = false;
        PrgImpl.__TEST_make_npe = false;
        Prp.global().clear();
    }

    ///////////////////////////////////////////////////////////////////////////

    private Prg prgi;

    Prg newPrg() {
        assertNull(this.prgi);
        return (this.prgi = new PrgImpl());
    }

    void delPrg() {
        if (null != this.prgi) {
            assertTrue(this.prgi.dtor().isSuccess());
            this.prgi = null;
        }
    }

    ///////////////////////////////////////////////////////////////////////////

    @Test
    public void testProperties() throws Exception {
        final Prg.Setup setup = new Prg.Setup();

        setup.args = new String[0];

        assertTrue(newPrg().ctor(Prp.global(), setup).isSuccess());
        Prg prg = this.prgi;
        assertNotNull(prg);

        Prg.PropertyInfo pinf;
        assertNotNull(pinf = prg.getPropertyInfo(Prg.Prop.RECURSIVE_SEARCH));
        assertTrue(pinf.type == Prg.PropertyInfo.Type.FLAG);
        assertNotNull(pinf = prg.getPropertyInfo(Prg.Prop.HASH_FUNCTION));
        assertTrue(pinf.type == Prg.PropertyInfo.Type.SELECT);
        assertTrue(1 <= pinf.selection.length);

        for (Field fld : Prg.Prop.class.getFields()) {
            int mod = fld.getModifiers();
            if (!Modifier.isFinal (mod) ||
                !Modifier.isStatic(mod) ||
                !Modifier.isPublic(mod)) {
                continue;
            }
            String pname = fld.get(null).toString();
            assertNotNull(pinf = prg.getPropertyInfo(pname));
            switch(pinf.type) {
                case FLAG:
                case STRING: {
                    assertNull(pinf.max);
                    assertNull(pinf.min);
                    assertNull(pinf.step);
                    assertNull(pinf.selection);
                    break;
                }
                case NUMBER: {
                    assertNull(pinf.selection);
                    break;
                }
                case SELECT: {
                    assertNull(pinf.max);
                    assertNull(pinf.min);
                    assertNull(pinf.step);
                    assertTrue(pinf.selection.length > 0);
                    break;
                }
                default: fail();
            }
        }

        delPrg();
    }

    ///////////////////////////////////////////////////////////////////////////

    interface Failure {
        void provoke(File fl) throws IOException;
    }

    void testFailure(Failure failure, String expectInMsg, boolean early,
                     Class<? extends Exception> uncaught) throws IOException {
        for (int keepBrokenVolume = 0; keepBrokenVolume < 2; keepBrokenVolume++) {
            final VarInt c = new VarInt();
            final VarInt d = new VarInt();

            assertTrue(newPrg().ctor(Prp.global(), new Prg.Setup()).isSuccess());
            Prg prg = this.prgi;

            Prg.NamedString prop = new Prg.NamedString(
                    Prg.Prop.KEEP_BROKEN_VOLUME,
                    Boolean.valueOf(0 != keepBrokenVolume).toString());
            assertTrue(prg.setProperty(prop).isSuccess());

            File fl = TestUtils.createTempFile("testFailureMissingFile", 100);
            assertTrue(fl.exists());

            assertTrue(prg.addObject(fl.getAbsolutePath()).isSuccess());

            assertTrue(prg.registerObjects(new Prg.RegisterObjectsCallback() {
                public Result onDirectory(String dir) {
                    c.v++;
                    return Result.ok();
                }
                public void configLocked() {
                }
            }).isSuccess());
            assertTrue(0 == c.v);
            assertTrue(prg.resolve().isSuccess());

            if (null != failure) {
                failure.provoke(fl);
            }
            File vol = TestUtils.createTempFile("testFailureMissingFile_vol", -1);
            assertFalse(vol.exists());

            final char[] passw = "abc".toCharArray();
            final Prg.MakeCallback mkcb = new Prg.MakeCallback() {
                public void onFile(String fileName, long fileSize) {
                    c.v++;
                }
                public Result onVolumeWrite(long pos) {
                    d.v++;
                    return Result.ok();
                }
                public Result onFreeSpace() {
                    return Result.ok();
                }
            };

            c.v = 0;
            assertTrue(prg.make(passw, mkcb).code == Prg.Result.Code.MAKE_REJECT);
            assertTrue(c.v == 0);
            assertTrue(d.v == 0);
            assertTrue(prg.setVolumeFile(vol.getAbsolutePath()).isSuccess());

            try {
                Result res = prg.make(passw, mkcb);
                assertTrue(null == uncaught);
                if (!early) {
                    assertTrue(0 < c.v);
                    assertTrue(0 < d.v);
                }
                assertTrue(res.isFailure());

                if (null != expectInMsg) {
                    assertTrue(-1 != res.msg.indexOf(expectInMsg));
                }

                //System.err.println(res.msg);
            }
            catch (Exception e) {
                assertTrue(uncaught.isInstance(e));
                if (null != expectInMsg) {
                    assertTrue(-1 != e.getMessage().indexOf(expectInMsg));
                }
            }
            assertTrue((0 == keepBrokenVolume) ^ vol.exists());

            delPrg();
        }
    }

    @Test
    public void testFailureMissingFile() throws IOException {
        testFailure(new Failure() {
            public void provoke(File fl) {
                assertTrue(fl.delete());
            }
        }, null, false, null);
    }


    @Test
    public void testFailureFileSizeChanged() throws IOException {
        testFailure(new Failure() {
            public void provoke(File fl) throws IOException {
                long len = fl.length();

                RandomAccessFile raf = new RandomAccessFile(fl, "rw");

                raf.seek(len);
                raf.write(0);
                raf.close();

                assertTrue(fl.length() == 1 + len);
            }
        }, "small size mismatch for file", false, null);
    }

    @Test
    public void testFailureIOReadError() throws IOException {
        LocalFileSystem.__test_FAKE_READ_ERROR = true;
        testFailure(null, "FAKE_ERROR_", false, null);
    }

    @Test
    public void testFailureIOWriteError() throws IOException {
        PrgImpl.__TEST_write_error = true;
        testFailure(null, "FAKE_WRITE_", true, null);
    }

    @Test
    public void testFailureNPE() throws IOException {
        PrgImpl.__TEST_make_npe = true;
        testFailure(null, "MAKE_NPE", false, NullPointerException.class);
        // TODO: proof that all of the auxiliary threads are down at this point
    }

    ///////////////////////////////////////////////////////////////////////////

    @Test
    public void testPropRecursiveSearchAndViewing() throws IOException {
        final long now = TestUtils.adjustFileTimestamp(System.currentTimeMillis());

        File baseDir = TestUtils.createTempDir("prgtest.testPropRecursiveSearch");

        File subDir = new File(baseDir, "A");
        assertTrue(!subDir.exists() && subDir.mkdir());

        File subFl = new File(subDir, "2.txt");
        TestUtils.fillFile123(subFl, 14);

        subDir = new File(baseDir, "emptyDir");
        assertTrue(!subDir.exists() && subDir.mkdir());

        final int[][] expects = new int[][] {
                { 0, 15, 29 },  // bytes
                { 1,  1,  3 },  // directories
                { 0,  1,  2 },  // files
                { 1,  1,  3 }   // viewables
        };

        for (int i = 0; i < 3; i++) {
            assertTrue(newPrg().ctor(Prp.global(), new Prg.Setup()).isSuccess());
            Prg prg = this.prgi;

            if (i == 1) {
                TestUtils.fillFile123(new File(baseDir, "1.txt"), 15);
            }

            final boolean rec = 2 == i;

            assertTrue(prg.setProperty(new Prg.NamedString(
                    Prg.Prop.RECURSIVE_SEARCH,
                    Boolean.valueOf(rec).toString())).isSuccess());

            final VarInt dirs = new VarInt();

            assertTrue(prg.addObject(baseDir.getAbsolutePath()).isSuccess());
            assertTrue(prg.registerObjects(new Prg.RegisterObjectsCallback() {
                public Result onDirectory(String dir) {
                    dirs.v++;
                    return Result.ok();
                }
                public void configLocked() {
                }
            }).isSuccess());
            assertTrue(dirs.v == expects[1][i]);

            Prg.RegSum rs = prg.registerSummary();
            assertTrue(rs.bytesTotal          == expects[0][i]);
            assertTrue(rs.numberOfDirectories == expects[1][i]);
            assertTrue(rs.numberOfFiles       == expects[2][i]);

            int vc = prg.registerViewCount();
            assertTrue(vc == expects[3][i]);
            assertTrue(vc == expects[3][i]);
            assertTrue(0 == prg.registerView(0, 0).length);

            Prg.RegObj[] ros = prg.registerView(0, vc);
            assertTrue(vc == ros.length);
            for (Prg.RegObj ro : ros) {
                assertTrue(ro.timestamp >= now);
                boolean isFile = ro.name.endsWith(".txt");
                assertTrue(14 <= ro.length ^ !isFile);
                assertTrue(ro.isDir()      ^  isFile);
            }

            delPrg();
        }
        TestUtils.removeDir(baseDir, true);
    }

    ///////////////////////////////////////////////////////////////////////////

    @Test
    public void testPropStoreFullPath() throws IOException {
        File baseDir = TestUtils.createTempDir("prgtest.testPropStoreFullPath");

        File fl = new File(baseDir, "1.txt");
        TestUtils.fillFile123(fl, 123);

        for (int sfp = 0; sfp < 2; sfp++) {
            assertTrue(newPrg().ctor(Prp.global(), new Prg.Setup()).isSuccess());
            Prg prg = this.prgi;

            Prg.NamedString prop = new Prg.NamedString(
                Prg.Prop.STORE_FULL_PATH,
                Boolean.valueOf(0 != sfp).toString());
            assertTrue(prg.setProperty(prop).isSuccess());

            final VarInt dirs = new VarInt();

            assertTrue(prg.addObject(baseDir.getAbsolutePath()).isSuccess());
            assertTrue(prg.registerObjects(new Prg.RegisterObjectsCallback() {
                public Result onDirectory(String dir) {
                    dirs.v++;
                    return Result.ok();
                }
                public void configLocked() {
                }
            }).isSuccess());
            assertTrue(dirs.v == 1);

            int depth = 0;
            if (0 != sfp) {
                File p = baseDir.getParentFile();
                while (null != p && !IOUtils.isRoot(p)) {
                    depth++;
                    p = p.getParentFile();
                }
                assertTrue(0 < depth);
            }

            Prg.RegSum rs = prg.registerSummary();
            assertEquals(rs.bytesTotal         , 123);
            assertEquals(rs.numberOfDirectories, 1 + depth);
            assertEquals(rs.numberOfFiles      , 1);

            delPrg();
        }
        TestUtils.removeDir(baseDir, true);
    }

    ///////////////////////////////////////////////////////////////////////////

    @Test
    public void testPropSkipEmptyDirs() throws IOException {
        File baseDir = TestUtils.createTempDir("prgtest.testPropStoreEmptyDirs");
        for (int sed = 0; sed < 2; sed++) {
            assertTrue(newPrg().ctor(Prp.global(), new Prg.Setup()).isSuccess());
            Prg prg = this.prgi;

            Prg.NamedString prop = new Prg.NamedString(
                    Prg.Prop.SKIP_EMPTY_DIRS,
                    Boolean.valueOf(0 != sed).toString());
            assertTrue(prg.setProperty(prop).isSuccess());

            final VarInt dirs = new VarInt();

            assertTrue(prg.addObject(baseDir.getAbsolutePath()).isSuccess());
            assertTrue(prg.registerObjects(new Prg.RegisterObjectsCallback() {
                public Result onDirectory(String dir) {
                    dirs.v++;
                    return Result.ok();
                }
                public void configLocked() {
                }
            }).isSuccess());
            assertTrue(dirs.v == 1);

            Prg.RegSum rs = prg.registerSummary();
            assertTrue(rs.bytesTotal          == 0);
            assertTrue(rs.numberOfDirectories == (sed ^ 1));
            assertTrue(rs.numberOfFiles       == 0);

            delPrg();
        }
        TestUtils.removeDir(baseDir, true);
    }

    ///////////////////////////////////////////////////////////////////////////

    @Test
    public void testPropAllowMerge() throws IOException {
        File baseDir = TestUtils.createTempDir("prgtest.testPropAllowMerge");

        File fl = new File(baseDir, "456.dat");
        TestUtils.fillFile123(fl, 456);

        for (int am = 0; am < 2; am++) {
            boolean allowMerge = 0 != am;

            assertTrue(newPrg().ctor(Prp.global(), new Prg.Setup()).isSuccess());
            Prg prg = this.prgi;

            Prg.NamedString prop = new Prg.NamedString(
                    Prg.Prop.ALLOW_MERGE,
                    Boolean.valueOf(allowMerge).toString());
            assertTrue(prg.setProperty(prop).isSuccess());

            for (int i = 0; i < 2; i++) {
                assertTrue(prg.addObject(baseDir.getAbsolutePath()).isSuccess());
            }
            Prg.Result res = prg.registerObjects(new Prg.RegisterObjectsCallback() {
                public Result onDirectory(String dir) {
                    return Result.ok();
                }
                public void configLocked() {
                }
            });
            if (allowMerge) {
                assertTrue(res.isSuccess());
                Prg.RegSum rs = prg.registerSummary();
                assertTrue(rs.bytesTotal          == 456);
                assertTrue(rs.numberOfDirectories == 1);
                assertTrue(rs.numberOfFiles       == 1);
            }
            else {
                assertTrue(res.isFailure());
                assertTrue(res.code == Prg.Result.Code.FILE_COLLISION);
                assertNotNull(res.msg);
            }

            delPrg();
        }
        TestUtils.removeDir(baseDir, true);
    }

    ///////////////////////////////////////////////////////////////////////////

    @Test
    public void testUnknownAlgorithms() {
        for (String pname : new String[] { Prg.Prop.HASH_FUNCTION,
                                           Prg.Prop.BLOCK_CIPHER }) {
            assertTrue(newPrg().ctor(Prp.global(), new Prg.Setup()).isSuccess());
            Prg prg = this.prgi;

            Prg.NamedString prop = new Prg.NamedString(pname, "not-an-algorithm");
            Result res = prg.verifyProperty(prop);
            assertTrue(res.isFailure());
            assertTrue(res.code == Prg.Result.Code.INVALID_PROPERTY);
            assertFalse(-1 == res.msg.indexOf(prop.name));
            assertFalse(-1 == res.msg.indexOf(prop.value));

            delPrg();
        }
    }

    ///////////////////////////////////////////////////////////////////////////

    @Test
    public void testFileMasking() throws IOException {
        File baseDir = TestUtils.createTempDir("prgtest.testFileMasking");

        File dr0 = new File(baseDir, "dadir"); assertTrue(dr0.mkdirs());

        File fl0 = new File(baseDir, "x.dat"); TestUtils.fillFile123(fl0, 1);
        File fl1 = new File(baseDir, "x.txt"); TestUtils.fillFile123(fl1, 2);
        File fl2 = new File(dr0    , "y.bmp"); TestUtils.fillFile123(fl2, 3);
        File fl3 = new File(dr0    , "y.txt"); TestUtils.fillFile123(fl3, 4);

        assertTrue(newPrg().ctor(Prp.global(), new Prg.Setup()).isSuccess());
        Prg prg = this.prgi;

        assertTrue(prg.setProperty(new Prg.NamedString(
                Prg.Prop.RECURSIVE_SEARCH, Boolean.TRUE.toString())).isSuccess());

        String maskedPath = baseDir.getAbsolutePath() + File.separatorChar + "*.txt";

        assertTrue(prg.addObject(maskedPath).isSuccess());

        final VarInt dirs = new VarInt();
        assertTrue(prg.registerObjects(new Prg.RegisterObjectsCallback() {
            public Result onDirectory(String dir) {
                dirs.v++;
                return Result.ok();
            }
            public void configLocked() {
            }
        }).isSuccess());
        assertTrue(2 == dirs.v);

        Prg.RegSum rs = prg.registerSummary();
        assertTrue(rs.bytesTotal          == 6);
        assertTrue(rs.numberOfDirectories == 2);
        assertTrue(rs.numberOfFiles       == 2);

        assertTrue(prg.clearObjects().isSuccess());

        assertTrue(prg.addObject("*.*").isSuccess());

        assertTrue(prg.registerObjects(new Prg.RegisterObjectsCallback() {
            public Result onDirectory(String dir) {
                return Result.ok();
            }
            public void configLocked() {
            }
        }).isSuccess());

        // usually this should work because there is always something in the
        // test environment's current path, although it is not guaranteed...
        assertTrue(prg.registerSummary().numberOfFiles > 0);

        delPrg();
    }
}
