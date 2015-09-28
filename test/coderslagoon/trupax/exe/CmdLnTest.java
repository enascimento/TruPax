package coderslagoon.trupax.exe;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.LineNumberReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.RandomAccessFile;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;


import coderslagoon.baselib.io.IOUtils;
import coderslagoon.baselib.io.NulOutputStream;
import coderslagoon.baselib.util.Combo;
import coderslagoon.baselib.util.MiscUtils;
import coderslagoon.baselib.util.Prp;
import coderslagoon.baselib.util.Shutdown;
import coderslagoon.baselib.util.VarBool;
import coderslagoon.baselib.util.VarInt;
import coderslagoon.baselib.util.VarRef;
import coderslagoon.tclib.container.Header;
import coderslagoon.tclib.crypto.Registry;
import coderslagoon.tclib.util.Password;
import coderslagoon.test.util.FileNameMaker;
import coderslagoon.test.util.FilePathWalker;
import coderslagoon.test.util.TestUtils;
import coderslagoon.trupax.exe.CmdLn;
import coderslagoon.trupax.exe.CmdLnProps;
import coderslagoon.trupax.exe.Exe;
import coderslagoon.trupax.exe.NLS;
import coderslagoon.trupax.exe.Exe.ExitError;
import coderslagoon.trupax.lib.UDFTest;
import coderslagoon.trupax.lib.io.filesystem.udf.Browser;
import coderslagoon.trupax.lib.prg.Prg;
import coderslagoon.trupax.lib.prg.PrgImpl;
import coderslagoon.trupax.lib.prg.PrgProps;
import coderslagoon.trupax.test.util.TCBrowser;
import coderslagoon.trupax.test.util.Verifier;

public class CmdLnTest extends TestUtils {
    final static int BLOCK_SIZE = PrgImpl.BLOCK_SIZE;

    TestConsole tcon;
    InputStream stdinBak;
    boolean runMain;

    ///////////////////////////////////////////////////////////////////////////

    protected static boolean removePropFiles() {
        File pf = MiscUtils.determinePropFile(Exe.class, Exe._propFileName, false);
        if (pf.exists() && !pf.delete()) {
            return false;
        }
        pf = new File(System.getProperty("user.home"), pf.getName());
        if (pf.exists() && !pf.delete()) {
            return false;
        }
        return true;
    }

    ///////////////////////////////////////////////////////////////////////////

    @Before
    public void setUp() throws Exception {
        this.runMain = false;
        this.stdinBak = System.in;
        CmdLn.__TEST_exit_error = new VarRef<>();
        CmdLn.__TEST_console = this.tcon = new TestConsole();
        assertTrue(removePropFiles());
        Registry.setup(false);
    }

    @After
    public void tearDown() {
        MiscUtils.__TEST_uncaught_now = null;
        CmdLn.__TEST_exit_error = null;
        CmdLn.__TEST_console = null;
        CmdLn.__TEST_abort_make = null;
        CmdLn.__TEST_abort_wipe = null;
        assertTrue(removePropFiles());
        Exe._propFileName = Exe.DEF_PROP_FILE_NAME;
        IOUtils.__test_CURRENTPATH = null;
        NLS.Reg.instance().reset();
        Shutdown.release();
        assertTrue((this.runMain ? 1 : 0)  == Shutdown.reset());
        Prp.global().clear();
        System.setIn(this.stdinBak);
    }

    ///////////////////////////////////////////////////////////////////////////

    @Test
    public void testShowUsage() throws Exception {
        CmdLn cl = new CmdLn();
        cl.exec(new String[0]);
        CmdLn.ExitError ee = CmdLn.__TEST_exit_error.v;
        assertNotNull(ee);
        assertEquals(ee.result.code, Prg.Result.Code.MISSING_CMDLN_ARG);
        assertEquals(this.tcon.prompts.size(), 0);
    }

    ///////////////////////////////////////////////////////////////////////////

    Verifier.Setup makeDefaultSetup() {
        return new Verifier.Setup() {
            public boolean  usingAbsolutePath() { return false; }
            public int      maxFiles         () { return 20; }
            public int      minSubDirsPerDir () { return 3; }
            public int      maxSubDirsPerDir () { return 5; }
            public long     minFileSize      () { return 0; }
            public long     maxFileSize      () { return 500000; }
            public int      minFilesPerDir   () { return 0; }
            public int      maxFilesPerDir   () { return 10; }
            public int      minFileNameLen   () { return 5; }
            public int      maxFileNameLen   () { return 30; }
            public int      maxPathLen       () { return 160; }
            public long     maxBytes         () { return Long.MAX_VALUE; }
            public String[] basePath         () { return new String[] { "test0" }; }
        };
    }

    ///////////////////////////////////////////////////////////////////////////

    @Test
    public void test0() throws Exception {
        for (boolean clpassw : new boolean[] { false, true }) {
            Verifier ver = new Verifier(null, null,
                    new PrintStream(new NulOutputStream()), //System.out,
                    System.err,
                    new FileNameMaker.Numbered(),
                    null);

            final String PASSW = "test123";
            this.tcon.password = clpassw ? null : PASSW.toCharArray();

            final Verifier.Setup vsetup = makeDefaultSetup();

            File base = ver.makeDirsAndFiles(vsetup);
            assertNotNull(base);

            File vol = ver.newVolumeFile(Exe.EXT_TC);

            this.tcon.prompts.clear();

            CmdLn cl = new CmdLn();

            final String LABEL = "CmdLnTest.test0";

            List<String> args = new ArrayList<>();
            args.add("-v");
            args.add("-r");
            if (clpassw) {
                args.add("--password=" + PASSW);
            }
            args.add("--label=" + LABEL);
            args.add("--trim-path");
            args.add(vol.getAbsolutePath());
            args.add(base.getAbsolutePath());

            cl.exec(args.toArray(new String[0]));

            assertNull(CmdLn.__TEST_exit_error.v);
            assertTrue((clpassw ? 0 : 2) == this.tcon.prompts.size());

            byte[] dec = Verifier.decryptVolume(PASSW.toCharArray(), vol);
            assertTrue(dec.length % BLOCK_SIZE == 0);
            assertTrue(dec.length              == 5905408L);

            for (Verifier.Matcher matcher : new Verifier.Matcher[] {
                ver.readerMatcher (),
                ver.browserMatcher()
            }) {
                matcher.match(base, vol, BLOCK_SIZE,
                      new Password(PASSW.toCharArray(), null),
                     "AES256", "RIPEMD-160", vsetup);
            }

            File decfl = new File(base, "test0.dec.dump");
            MiscUtils.writeFile(decfl, dec);

            final VarBool sawLabel = new VarBool();

            if (UDFTest.available()) {
                assertTrue(UDFTest.exec(decfl, BLOCK_SIZE, true, false, false,
                        new UDFTest.Listener() {
                    public boolean onOutput(String ln) {
                        if (-1 != ln.indexOf(LABEL)) {
                            sawLabel.v = true;
                        }
                        return true;
                    }
                }));
                assertTrue(sawLabel.v);
            }

            assertTrue(vol.delete());

            assertTrue(removeDir(base, true));
        }
    }

    ///////////////////////////////////////////////////////////////////////////

    @Test
    public void testWithWipe() throws Exception {
        Verifier ver = new Verifier(null, null,
                new PrintStream(new NulOutputStream()), //System.out,
                System.err,
                new FileNameMaker.Mixer(new FileNameMaker[] {
                    new FileNameMaker.Numbered(),
                    new FileNameMaker.RandomASCII(),
                    new FileNameMaker.RandomUnicode()
                }),
                null);

        final String PASSW = "ABCDEFG1234567";
        this.tcon.password = PASSW.toCharArray();
        final Verifier.Setup vsetup = makeDefaultSetup();
        File base = ver.makeDirsAndFiles(vsetup);
        assertNotNull(base);
        File vol = ver.newVolumeFile(Exe.EXT_TC);
        CmdLn cl = new CmdLn();
        final String LABEL = "testWithWipe";
        String[] args = new String[7];
        args[0] = "-v";
        args[1] = "-r";
        args[2] = "--label=" + LABEL;
        args[3] = "--trim-path";
        args[4] = "--wipe";
        args[5] = vol.getAbsolutePath();
        args[6] = base.getAbsolutePath();
        cl.exec(args);
        assertNull(CmdLn.__TEST_exit_error.v);
        assertTrue(2 == this.tcon.prompts.size());

        byte[] dec = Verifier.decryptVolume(PASSW.toCharArray(), vol);
        assertTrue(dec.length % BLOCK_SIZE == 0);
        assertEquals(dec.length, 5904896L);

        assertFalse(base.exists());
    }

    ///////////////////////////////////////////////////////////////////////////

    @Test
    public void testWipeOnly() throws Exception {
        Verifier ver = new Verifier(null, null,
                new PrintStream(new NulOutputStream()),
                System.err,
                new FileNameMaker.Mixer(new FileNameMaker[] {
                    new FileNameMaker.Numbered(),
                    new FileNameMaker.RandomASCII(),
                    new FileNameMaker.RandomUnicode()
                }),
                null);

        final Verifier.Setup vsetup = makeDefaultSetup();
        File base = ver.makeDirsAndFiles(vsetup);
        assertNotNull(base);
        CmdLn cl = new CmdLn();
        String[] args = new String[4];
        args[0] = "-v";
        args[1] = "-r";
        args[2] = "--wipe-only";
        args[3] = base.getAbsolutePath();
        cl.exec(args);
        assertNull(CmdLn.__TEST_exit_error.v);
        assertTrue(0 == this.tcon.prompts.size());

        assertFalse(base.exists());
    }

    ///////////////////////////////////////////////////////////////////////////

    @Test
    public void testNoWriteVolumeFile() throws Exception {
        final String PASSW = "test123";
        this.tcon.password = PASSW.toCharArray();

        File tmpDir = createTempDir("cmdlntest.testnowritevolumefile");

        CmdLn cl = new CmdLn();

        String[] args = new String[5];
        args[0] = "-v";
        args[1] = "-r";
        args[2] = "--overwrite";
        args[3] = tmpDir.getAbsolutePath();  // cannot (over)write to that
        args[4] = tmpDir.getAbsolutePath();

        cl.exec(args);

        assertTrue(2 == this.tcon.prompts.size());
        assertNotNull(CmdLn.__TEST_exit_error.v);

        CmdLn.ExitError ee = CmdLn.__TEST_exit_error.v;
        assertTrue(ee.result.code == Prg.Result.Code.CREATE_VOLUME_ERROR);
    }

    ///////////////////////////////////////////////////////////////////////////

    @Test
    public void testUserBreak() throws Exception {
        this.tcon.password = "XYZ".toCharArray();

        File tmpDir = createTempDir("cmdlntest.testuserbreak");
        assertTrue(tmpDir.exists());

        File vol = new File(tmpDir, "test.tc");
        assertTrue(!vol.exists() || vol.delete());

        CmdLn cl = new CmdLn();

        String[] args = new String[4];
        args[0] = "-v";
        args[1] = "-r";
        args[2] = vol   .getAbsolutePath();
        args[3] = tmpDir.getAbsolutePath();

        CmdLn.__TEST_abort_make = 1L;

        cl.exec(args);

        assertTrue(2 == this.tcon.prompts.size());
        assertNotNull(CmdLn.__TEST_exit_error.v);

        CmdLn.ExitError ee = CmdLn.__TEST_exit_error.v;
        assertTrue(ee.result.code == Prg.Result.Code.ABORTED);

        assertFalse(vol.exists());
    }

    @Test
    public void testUserBreakWipe() throws Exception {
        this.tcon.password = "abcdefg".toCharArray();

        Verifier ver = new Verifier(null, null,
                new PrintStream(new NulOutputStream()),
                System.err,
                new FileNameMaker.Numbered(),
                null);
        File base = ver.makeDirsAndFiles(makeDefaultSetup());
        assertNotNull(base);
        File vol = ver.newVolumeFile(Exe.EXT_TC);
        assertTrue(!vol.exists());

        CmdLn cl = new CmdLn();
        String[] args = new String[5];
        args[0] = "-v";
        args[1] = "-r";
        args[2] = "--wipe";
        args[3] = vol.getAbsolutePath();
        args[4] = base.getAbsolutePath();

        CmdLn.__TEST_abort_wipe = 54.321;
        cl.exec(args);
        assertTrue(2 == this.tcon.prompts.size());
        assertNotNull(CmdLn.__TEST_exit_error.v);
        CmdLn.ExitError ee = CmdLn.__TEST_exit_error.v;
        assertTrue(ee.result.code == Prg.Result.Code.ABORTED);

        assertTrue(vol.exists());
        assertTrue(vol.delete());
        assertTrue(base.exists());
        assertTrue(TestUtils.removeDir(base, true));
    }

    ///////////////////////////////////////////////////////////////////////////

    static class C0 extends Combo.Two<String, Prp.Bool>
    {     public C0(String t, Prp.Bool u) { super(t, u); } }
    static class C1 extends Combo.Three<String, String[], PrgProps.Selection>
    {     public C1(String t, String[] u, PrgProps.Selection v) { super(t, u, v); } }
    static class C2 extends Combo.Three<String, String[], Prp.Str>
    {     public C2(String t, String[] u, Prp.Str v) { super(t, u, v); } }

    @Test
    public void testOptions() {
        CmdLn cl;
        CmdLn.ExitError ee;

        for (C0 c : new C0[] {
            new C0("-v", CmdLnProps.OPTS_VERBOSE),
            new C0("-r", new PrgProps.RecursiveSearch()),
            new C0("--store-full-path"   , new PrgProps.StoreFullPath()),
            new C0("--skip-empty-dirs"   , new PrgProps.SkipEmptyDirs()),
            new C0("--allow-merge"       , new PrgProps.AllowMerge()),
            new C0("--overwrite"         , new PrgProps.Overwrite()),
            new C0("--write-protect"     , new PrgProps.WriteProtect()),
            new C0("--keep-broken-volume", new PrgProps.KeepBrokenVolume()),
            new C0("--trim-path"         , new PrgProps.TrimPath()),
            new C0("--wipe"              , CmdLnProps.OPTS_WIPE)
        }) {
            cl = new CmdLn();
            cl.exec(new String[] { c.t });
            ee = CmdLn.__TEST_exit_error.v;
            assertTrue(ee.result.code == Prg.Result.Code.MISSING_CMDLN_ARG);
            assertTrue(c.u.get(cl.props));
            cl = new CmdLn();
            cl.exec(new String[0]);
            ee = CmdLn.__TEST_exit_error.v;
            assertTrue(ee.result.code == Prg.Result.Code.MISSING_CMDLN_ARG);
            assertFalse(cl.props.contains(c.u.name()));
        }

        cl = new CmdLn();
        cl.exec(new String[] { "--free-space=123K" });
        ee = CmdLn.__TEST_exit_error.v;
        assertTrue(ee.result.code == Prg.Result.Code.MISSING_CMDLN_ARG);
        assertTrue(CmdLnProps.OPTS_FREESPACE.get(cl.props) == 123000L);
        assertFalse(new PrgProps.RecursiveSearch().get(cl.props));

        List<C1> lc1 = new ArrayList<>();
        lc1.add(new C1("--block-cipher=" , new String[] { "AES256"    , "AES257" }, new PrgProps.BlockCipher ()));
        lc1.add(new C1("--hash-function=", new String[] { "RIPEMD-160", "HASHME" }, new PrgProps.HashFunction()));

        for (C1 c : new C1[] {
            new C1("--block-cipher=" , new String[] { "AES256"    , "AES257" }, new PrgProps.BlockCipher ()),
            new C1("--hash-function=", new String[] { "RIPEMD-160", "HASHME" }, new PrgProps.HashFunction())
        }) {
            cl = new CmdLn();
            cl.exec(new String[] { c.t + c.u[0] });
            ee = CmdLn.__TEST_exit_error.v;
            assertTrue(ee.result.code == Prg.Result.Code.MISSING_CMDLN_ARG);
            assertTrue(c.v.get(cl.props).equals(c.u[0]));
            cl = new CmdLn();
            cl.exec(new String[] { c.t + c.u[1] });
            ee = CmdLn.__TEST_exit_error.v;
            assertTrue(ee.result.code == Prg.Result.Code.INVALID_CMDLN_ARG);
            assertFalse(cl.props.contains(c.v.name()));
        }

        for (C2 c : new C2[] {
           new C2("--password=", new String[] { "test1234", "" }, CmdLnProps.OPTS_PASSWORD)
        }) {
            cl = new CmdLn();
            cl.exec(new String[] { c.t + c.u[0] });
            ee = CmdLn.__TEST_exit_error.v;
            assertTrue(ee.result.code == Prg.Result.Code.MISSING_CMDLN_ARG);
            assertTrue(c.v.get(cl.props).equals(c.u[0]));
            cl = new CmdLn();
            cl.exec(new String[] { c.t + c.u[1] });
            ee = CmdLn.__TEST_exit_error.v;
            assertTrue(ee.result.code == Prg.Result.Code.INVALID_CMDLN_ARG);
            assertFalse(cl.props.contains(c.v.name()));
        }
    }

    ///////////////////////////////////////////////////////////////////////////

    @Test
    public void testPropertiesFile() {
        String tfn = createTempFileName("testPropertiesFile");
        Exe._propFileName = tfn;

        String userHome = System.getProperty("user.home");
        assertNotNull(userHome);

        File cfg = new File(userHome, MiscUtils.makePropertyFileName(tfn));
        assertTrue(!cfg.exists() || cfg.delete());

        Prp.Bool[] pbl = new Prp.Bool[] {
            CmdLnProps.OPTS_VERBOSE,
            CmdLnProps.OPTS_WIPE,
            new PrgProps.RecursiveSearch(),
            new PrgProps.StoreFullPath(),
            new PrgProps.SkipEmptyDirs(),
            new PrgProps.AllowMerge(),
            new PrgProps.Overwrite(),
            new PrgProps.KeepBrokenVolume()
        };

        Properties props = new Properties();
        for (Prp.Bool pb : pbl) {
            props.put(pb.name(), Boolean.TRUE.toString());
        }
        assertTrue(props.size() == pbl.length);
        assertTrue(Prp.saveToFile(props, cfg, false, "CmdLnTest.testPropertiesFile"));
        props = null;

        CmdLn cl = new CmdLn();
        cl.exec(new String[0]);
        CmdLn.ExitError ee = CmdLn.__TEST_exit_error.v;
        assertTrue(ee.result.code == Prg.Result.Code.MISSING_CMDLN_ARG);

        for (Prp.Bool pb : pbl) {
            System.out.println(pb.name());
            assertTrue(cl.props.containsKey(pb.name()));
            assertTrue(pb.get(cl.props));
        }

        assertTrue(cfg.delete());
    }

    ///////////////////////////////////////////////////////////////////////////

    @Test
    public void testStoreFullPath() throws Exception {
        File outDir = createTempDir("CmdLnTest.testStoreFullPath.out");

        final String fpath = "." + File.separator +
            createTempFileName("CmdLnTest.testStoreFullPath.dat");

        File fl = new File(fpath);

        assertTrue(!fl.exists() || fl.delete());
        fillFile123(fl, 100);

        final String PASSW = "xyz";
        this.tcon.password = PASSW.toCharArray();

        File vol = new File(outDir, "out.tc");

        CmdLn cl = new CmdLn();

        String[] args = new String[3];
        args[0] = "-v";
        args[1] = vol.getAbsolutePath();
        args[2] = fpath;

        assertFalse(vol.exists());

        cl.exec(args);

        assertNull(CmdLn.__TEST_exit_error.v);

        assertTrue(vol.exists());
        assertTrue(fl.delete());
        assertTrue(removeDir(outDir, true));
    }

    ///////////////////////////////////////////////////////////////////////////

    @Test
    public void testPathHandling() throws Exception {
        File outDir = createTempDir("CmdLnTest.testPathHandling.out");
        File tmpDir = createTempDir("CmdLnTest.testPathHandling.tmp");

        final String PASSW = "secret";
        this.tcon.password = PASSW.toCharArray();

        File vol = new File(outDir, "test.tc");

        File dir0 = new File(tmpDir, "dir0");
        File dir1 = new File(dir0  , "dir1");
        File dir2 = new File(dir1  , "dir2");
        File dir3 = new File(dir2  , "dir3");
        assertTrue(dir3.mkdirs());
        File fl0 = new File(tmpDir, "fl0"); fillFile123(fl0, 0L);
        File fl1 = new File(dir0  , "fl1"); fillFile123(fl1, 1L);
        File fl2 = new File(dir1  , "fl2"); fillFile123(fl2, 22L);
        File fl3 = new File(dir2  , "fl3"); fillFile123(fl3, 333L);

        IOUtils.__test_CURRENTPATH = dir1.getCanonicalPath();

        for (String mask : new String[] { null, "*", "???" })
        for (String[] set : new String[][] {
                { "dir2"                      , "/dir2", 1+"" },
                { "./dir2"                    , "/dir2", 1+"" },
                { "../dir1"                   , "/dir1", 2+"" },
                { "../dir1/dir2"              , "/dir1", 2+"" },
                { "../dir1/./ignored/../dir2/", "/dir1", 2+"" },
                { "dir2/dir3"                 , "/dir2", 1+"" },
                { ".."                        , "/dir1", 2+"" },
                { "../"                       , "/dir1", 2+"" },
                { "../.."                     , "/dir0", 3+"" },
                { "../../"                    , "/dir0", 3+"" }
        }) {
            CmdLn cl = new CmdLn();

            String[] args = new String[4];
            args[0] = "-v";
            args[1] = "-r";
            args[2] = vol.getAbsolutePath();
            args[3] = npath(set[0] + (null == mask ? "" : ("/" + mask)));

            assertFalse(vol.exists());
            cl.exec(args);
            assertNull(CmdLn.__TEST_exit_error.v);

            assertTrue(vol.exists());

            final List<String> dirs = new ArrayList<>();
            final VarInt fcount = new VarInt();

            TCBrowser tcb = new TCBrowser(vol, new Browser.Listener() {
                public void onDirectory(String path, long time) throws IOException {
                    dirs.add(path);
                }
                public OutputStream onFile(
                        String name, long time, long length) throws IOException {
                    fcount.v++;
                    return new NulOutputStream();
                }
            }, PrgImpl.BLOCK_SIZE, PASSW);
            tcb.exec();
            tcb.close();

            assertTrue(0 < dirs.size());
            assertEquals(set[1], dirs.get(0));

            assertTrue(vol.delete());
        }
        assertTrue(removeDir(outDir, true));
        assertTrue(removeDir(tmpDir, true));
    }

    ///////////////////////////////////////////////////////////////////////////

    @Test
    public void testUncaughtErrorHandling() throws IOException {
        MiscUtils.__TEST_uncaught_now = 9876543210L;
        this.runMain = true;

        File dfl = new File(System.getProperty("java.io.tmpdir"), "trupax_uncaught_9876543210.txt");
        assertTrue(!dfl.exists() || dfl.delete());

        CmdLn.main(new String[] {});

        assertTrue(dfl.exists());
        assertTrue(dfl.length() > 0L);

        String dump = new String(MiscUtils.readFile(dfl));
        assertTrue(-1 != dump.indexOf("uncaught_test"));
        assertTrue(-1 != dump.indexOf("processArgs"));
        //System.out.println(dump);

        assertTrue(dfl.delete());
    }

    ///////////////////////////////////////////////////////////////////////////

    @Test
    public void testConfirm() throws Exception {
        CmdLn cl = new CmdLn();
        NLS.Reg.instance().load(null);
        final String M = "something in a line";
        final String P = "[O]verwrite  O[v]erwrite All  [S]kip  S[k]ip All  [C]ancel >";
        final String LB = System.getProperty("line.separator");
        for (String[] s : new String[][] {
            { ""          , null, M+LB+P     },
            { "x\no\n"    , "0" , M+LB+P+P   },
            { "x\ny\nC"   , "4" , M+LB+P+P+P },
            { "skip all\n", "3" , M+LB+P     }
        }) {
            cl.in = new LineNumberReader(new StringReader(s[0]));
            Integer exp = null == s[1] ? null : Integer.parseInt(s[1]);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            cl.out = new PrintStream(baos);
            Integer res = cl.confirm(M, NLS.CMDLN_EXISTS_SELECT.s());
            cl.out.flush();
            String outp = new String(baos.toString());
            assertTrue(res == exp);
            assertEquals(outp, s[2]);
        }
    }

    ///////////////////////////////////////////////////////////////////////////

    @Test
    public void testExtractTrueCrypt() throws Exception {
        testExtract(false);
    }

    @Test
    public void testExtractVeraCrypt() throws Exception {
        testExtract(true);
    }
    
    void testExtract(boolean forVeraCrypt) throws Exception {
        final int MAXFLS = 20;
        final Verifier.Setup vsetup = new Verifier.Setup() {
            public boolean  usingAbsolutePath() { return false; }
            public int      maxFiles         () { return MAXFLS; }
            public int      minSubDirsPerDir () { return 3; }
            public int      maxSubDirsPerDir () { return 5; }
            public long     minFileSize      () { return 1L; }
            public long     maxFileSize      () { return 50000L; }
            public int      minFilesPerDir   () { return 0; }
            public int      maxFilesPerDir   () { return 10; }
            public int      minFileNameLen   () { return 10; }
            public int      maxFileNameLen   () { return 20; }
            public int      maxPathLen       () { return 160; }
            public long     maxBytes         () { return Long.MAX_VALUE; }
            public String[] basePath         () { return new String[] { "testExtract" }; }
        };

        Verifier ver = new Verifier(null, null,
                new PrintStream(new NulOutputStream()), //System.out,
                System.err,
                new FileNameMaker.Numbered(),
                null);
        final File vol = ver.newVolumeFile(forVeraCrypt ? Exe.EXT_HC : Exe.EXT_TC);
        final String PASSW = "abc123";
        this.tcon.password = PASSW.toCharArray();
        final File base = ver.makeDirsAndFiles(vsetup);
        assertNotNull(base);
        CmdLn cl = new CmdLn();
        List<String> args = new ArrayList<>();
        args.add("-v");
        args.add("-r");
        args.add("--trim-path");
        if (forVeraCrypt)
            args.add("--for-veracrypt");
        args.add(vol.getAbsolutePath());
        args.add(base.getAbsolutePath());
        cl.exec(args.toArray(new String[args.size()]));
        assertNull(CmdLn.__TEST_exit_error.v);
        assertTrue(2 == this.tcon.prompts.size());
        assertTrue(vol.exists());

        Thread.sleep(1500); // (for time stamp checking)
        this.tcon.prompts.clear();
        final File extractDir = TestUtils.createTempDir("testExtract");
        assertTrue(extractDir.exists());
        cl = new CmdLn();
        args = new ArrayList<>();
        args.add("-v");
        args.add("--extract");
        args.add(vol.getAbsolutePath());
        args.add(extractDir.getAbsolutePath());
        cl.exec(args.toArray(new String[args.size()]));
        assertNull(CmdLn.__TEST_exit_error.v);
        assertTrue(1 == this.tcon.prompts.size());
        final VarInt vfc = new VarInt();
        FilePathWalker fpw = new FilePathWalker() {
            public boolean onObject(File obj) {
                assertTrue(obj.exists());
                String base2 = base.getParent();
                File eobj = new File(extractDir, obj.getAbsolutePath().substring(base2.length()));
                assertTrue(eobj.exists());
                assertTrue(eobj.isDirectory() ^ !obj.isDirectory());
                assertTrue(obj.lastModified() == eobj.lastModified());
                if (eobj.isFile()) {
                    long flen = eobj.length();
                    assertTrue(flen >= vsetup.minFileSize() &&
                               flen <= vsetup.maxFileSize());
                    vfc.v++;
                    try {
                        return TestUtils.areFilesEqual(eobj, obj);
                    }
                    catch (IOException ioe) {
                        fail();
                    }
                }
                return true;
            }
        };
        assertTrue(fpw.walk(base));
        final int fcount = vfc.v;
        assertTrue(MAXFLS == fcount);

        // test all of the possible answers on file collisions, make sure that
        // things do or do not get recreated based on the decisions...

        final VarBool reset = new VarBool();
        final VarInt fcount2 = new VarInt();
        FilePathWalker fpw2 = new FilePathWalker() {
            public boolean onObject(File obj) {
                assertTrue(obj.exists());
                if (obj.isFile()) {
                    fcount2.v++;
                    try {
                        if (reset.v) {
                            new FileOutputStream(obj).close();
                        }
                        assertTrue(0L == new File(obj.getAbsolutePath()).length());
                    }
                    catch (IOException ioe) {
                        fail();
                    }
                }
                return true;
            }
        };

        for (int rsp = 0; rsp < 5; rsp++) {
            final boolean repeat;
            final char outp;
            switch(rsp) {
                case 0: outp = 'o'; repeat = true; break;
                case 1: outp = 'v'; repeat = false; break;
                case 2: outp = 's'; repeat = true; break;
                case 3: outp = 'k'; repeat = false; break;
                case 4: outp = 'c'; repeat = false; break;
                default: fail(); return;
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 0, c = repeat ? MAXFLS : 1; i < c; i++) {
                sb.append(outp);
                sb.append("\r\n");
            }
            reset.v = true;
            fcount2.v = 0;
            fpw2.walk(extractDir);
            assertTrue(MAXFLS == fcount2.v);
            cl = new CmdLn();
            final VarInt lc = new VarInt();
            cl.in = new LineNumberReader(new StringReader(sb.toString())) {
                public String readLine() throws IOException {
                    lc.v++;
                    return super.readLine();
                }
            };
            cl.exec(args.toArray(new String[args.size()]));
            Prg.Result pr = null;
            boolean nowrites = false;
            switch(rsp) {
                case 0: assertTrue(MAXFLS == lc.v); break;
                case 1: assertTrue(1      == lc.v); break;
                case 2: assertTrue(MAXFLS == lc.v); nowrites = true; break;
                case 3: assertTrue(1      == lc.v); nowrites = true; break;
                case 4: assertTrue(1      == lc.v); nowrites = true; pr = Prg.Result.aborted(); break;
                default: fail(); return;
            }
            if (null == pr) {
                assertNull(CmdLn.__TEST_exit_error.v);
            }
            else {
                ExitError ee = CmdLn.__TEST_exit_error.v;
                assertTrue(ee.result.code == Prg.Result.Code.ABORTED);
            }
            if (nowrites) {
                reset.v = false;
                fcount2.v = 0;
                fpw2.walk(extractDir);
                assertTrue(MAXFLS == fcount2.v);
            }
            else {
                assertTrue(fpw.walk(base));
            }
        }

        ///////////!!!!!assertTrue(vol.delete());
        System.out.println(vol.toString());
        
        assertTrue(TestUtils.removeDir(base      , true));
        assertTrue(TestUtils.removeDir(extractDir, true));
    }

    ///////////////////////////////////////////////////////////////////////////

    @Test
    public void testInvalidate() throws Exception {
        for (boolean deleteAfter : new boolean[] { false, true }) {
            int flen = Header.SIZE + Header.BLOCK_SIZE + Header.SIZE;
            File fl = TestUtils.createTempFile("CmdLnTest_testInvalidate", -1);
            byte[] data = new byte[flen];
            Arrays.fill(data, (byte)56);
            Arrays.fill(data, Header.SIZE, Header.SIZE + Header.BLOCK_SIZE, (byte)55);
            RandomAccessFile raf = new RandomAccessFile(fl, "rw");
            raf.write(data);
            raf.close();
            CmdLn cl = new CmdLn();
            List<String> args = new ArrayList<>();
            args.add("--invalidate");
            args.add("-v");
            if (deleteAfter) {
                args.add("--delete-after");
            }
            args.add(fl.getAbsolutePath());
            cl.exec(args.toArray(new String[args.size()]));
            assertNull(CmdLn.__TEST_exit_error.v);
            assertTrue(deleteAfter ^ fl.exists());
            if (!deleteAfter) {
                data = MiscUtils.readFile(fl);
                for (int ofs : new int[] { 0, Header.SIZE + Header.BLOCK_SIZE }) {
                    for (int i = 0; i < Header.SIZE; i++) {
                        assertTrue(0 == data[i + ofs]);
                    }
                }
                for (int i = Header.SIZE; i < Header.SIZE + Header.BLOCK_SIZE; i++) {
                    assertTrue(55 == data[i]);
                }
                assertTrue(fl.delete());
            }
        }
    }
}
