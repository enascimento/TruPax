package coderslagoon.trupax.lib;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import coderslagoon.baselib.io.DbgFileSystem;
import coderslagoon.baselib.io.FileNode;
import coderslagoon.baselib.io.FileRegistrar;
import coderslagoon.baselib.io.LocalFileSystem;
import coderslagoon.baselib.io.NulOutputStream;
import coderslagoon.baselib.io.FileRegistrar.BulkCallback;
import coderslagoon.baselib.io.FileRegistrar.InMemory.DefCmp;
import coderslagoon.baselib.util.VarInt;
import coderslagoon.test.util.FileNameMaker;
import coderslagoon.test.util.TestUtils;
import coderslagoon.trupax.lib.Wipe;
import coderslagoon.trupax.test.util.Verifier;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

public class WipeTest {
    File       tmpDir;
    List<File> skips;

    @Before
    public void setUp() throws IOException {
        this.skips = new ArrayList<>();
        assertTrue((this.tmpDir = TestUtils.createTempDir("wipetest")).exists());
    }

    @After
    public void tearDown() {
        this.freg = null;
        if (this.tmpDir.exists()) {
            assertTrue(TestUtils.removeDir(this.tmpDir, true));
        }
        if (null != this.ver) {
            this.ver.cleanUp();
            this.ver = null;
        }
        Wipe.__test_CHECK._instance = null;
        Wipe.__test_IOERROR = 0;
    }

    ///////////////////////////////////////////////////////////////////////////

    FileRegistrar freg;
    Verifier      ver;

    FileNode makeSingleFile(int len) throws IOException {
        File fl = new File(this.tmpDir, "wipe.this");
        TestUtils.fillFile123(fl, len);
        this.freg = new FileRegistrar.InMemory(new DefCmp(false));
        LocalFileSystem lfs = new LocalFileSystem(false);
        FileNode dfn = lfs.nodeFromString(this.tmpDir.getAbsolutePath());
        int c = FileRegistrar.bulk(this.freg, dfn, dfn, null, new BulkCallback() {
            public Merge onMerge(FileNode[] nd0, FileNode nd1) {
                fail();
                return null;
            }
            public boolean matches(FileNode file) {
                return true;
            }
            public boolean onProgress(FileNode current) {
                return true;
            }

        }, true, true);
        assertTrue(1 == c);
        return this.freg.root().dirs().next().files().next();
    }

    FileNode makeFilesAndDirs(final Integer maxFiles,
                              final Integer maxFileSize,
                              final Integer matchSkip,
                              final Integer deleteNumber) throws IOException {
        this.ver = new Verifier(null, null,
                new PrintStream(new NulOutputStream()), //System.out,
                System.err,
                new FileNameMaker.Mixer(new FileNameMaker[] {
                    new FileNameMaker.Numbered(),
                    new FileNameMaker.RandomASCII(),
                    new FileNameMaker.RandomUnicode()
                }),
                null);
        final Verifier.Setup vsetup = new Verifier.Setup() {
            public boolean  usingAbsolutePath() { return false; }
            public int      maxFiles         () { return null == maxFiles ? 20 : maxFiles; }
            public int      minSubDirsPerDir () { return 3; }
            public int      maxSubDirsPerDir () { return 5; }
            public long     minFileSize      () { return 0; }
            public long     maxFileSize      () { return null == maxFileSize ? 500000 : maxFileSize; }
            public int      minFilesPerDir   () { return 0; }
            public int      maxFilesPerDir   () { return 10; }
            public int      minFileNameLen   () { return 5; }
            public int      maxFileNameLen   () { return 30; }
            public int      maxPathLen       () { return 160; }
            public long     maxBytes         () { return Long.MAX_VALUE; }
            public String[] basePath         () { return new String[] { "test0" }; }
        };
        File base = this.ver.makeDirsAndFiles(vsetup);
        assertNotNull(base);
        this.freg = new FileRegistrar.InMemory(new DefCmp(false));
        LocalFileSystem lfs = new LocalFileSystem(false);
        String basePath = base.getAbsolutePath();
        FileNode result = lfs.nodeFromString(basePath);
        final VarInt dc = new VarInt();
        int bres = FileRegistrar.bulk(this.freg, result, result, this.freg.root(),
                new BulkCallback() {
                    int mskip;
                    int delnum;
                    public Merge onMerge(FileNode[] nd0, FileNode nd1) {
                        return Merge.ABORT;
                    }
                    public boolean matches(FileNode fn) {
                        boolean isDir = fn.hasAttributes(FileNode.ATTR_DIRECTORY);
                        if (!isDir && null != deleteNumber && this.delnum++ == deleteNumber) {
                            try {
                                fn.fileSystem().remove(fn);
                                System.out.printf("removed file #%d (%s)\n", deleteNumber, fn.path(true));
                            }
                            catch (IOException ioe) {
                                fail();
                            }
                        }
                        if (null == matchSkip || isDir) {
                            return true;
                        }
                        boolean result = ++this.mskip % matchSkip == 0;
                        if (!result) {
                            File skip = new File(fn.path(true));
                            assertTrue (skip.exists());
                            assertFalse(skip.isDirectory());
                            System.out.println("skipping " + skip.getAbsolutePath());
                            WipeTest.this.skips.add(skip);
                        }
                        return result;
                    }
                    public boolean onProgress(FileNode current) {
                        assertTrue(current.hasAttributes(FileNode.ATTR_DIRECTORY));
                        dc.v++;
                        return true;
                    }
                },
                true, true);
        assertTrue(null == matchSkip ? (maxFiles == bres) : (0 < bres));
        assertTrue(dc.v > 0);
        return result;
    }

    void dumpFileReg() {
        FileRegistrar.dump(this.freg.root(), 0, System.out);
        System.out.println(this.freg.toString());
    }

    ///////////////////////////////////////////////////////////////////////////

    static class Progress implements Wipe.Progress {
        enum AbortAt {
            NOT,
            NODE,
            PROCESSED,
            SKIPPED,
            ERROR,
            WARNING
        }
        final int dump;
        AbortAt abortAt = AbortAt.NOT;
        double oldpct = -1.0;
        public Progress(int dump) {
            this.dump = dump;
        }
        public boolean onNode(FileNode fn) {
            if (0 < this.dump) System.out.printf("onNode(%s %s)\n",
                    fn.hasAttributes(FileNode.ATTR_DIRECTORY) ? "DIR" : "FIL",
                    fn.path(true));
            this.nodes.add(fn);
            return AbortAt.NODE != this.abortAt;
        }
        public boolean onProcessed(double percent) {
            if (1 < this.dump) System.out.printf("onProcessed(%f)\n", percent);
            assertTrue(this.oldpct < percent);
            this.oldpct = percent;
            this.processed.add(percent);
            return AbortAt.PROCESSED != this.abortAt;
        }
        public boolean onSkipped(FileNode fn, Reason reason) {
            System.out.printf("onSkipped(%s, %s)\n", fn.path(true), reason);
            this.skipped.add(fn);
            return AbortAt.SKIPPED != this.abortAt;
        }
        public boolean onError(FileNode fn, Reason reason) {
            System.out.printf("onError(%s, %s)\n", fn.path(true), reason);
            this.errors.add(fn);
            return AbortAt.ERROR != this.abortAt;
        }
        public boolean onWarning(FileNode fn, Reason reason) {
            System.out.printf("onWarning(%s, %s)\n", fn.path(true), reason);
            this.warnings.add(fn);
            return AbortAt.WARNING != this.abortAt;
        }
        public int[] numOfNodes() {
            int[] result = new int[2];
            for (FileNode nd : this.nodes) {
                result[nd.hasAttributes(FileNode.ATTR_DIRECTORY) ? 1 : 0]++;
            }
            return result;
        }
        final List<FileNode> nodes     = new ArrayList<>();
        final List<Double>   processed = new ArrayList<>(1000);
        final List<FileNode> skipped   = new ArrayList<>();
        final List<FileNode> errors    = new ArrayList<>();
        final List<FileNode> warnings  = new ArrayList<>();
    }

    static class TestCycles extends Wipe.Cycles.Zeros {
        public int counts;
        public int sets;
        public int datas;
        public int count() {
            this.counts++;
            return super.count();
        }
        public void set(int num) {
            this.sets++;
            assertTrue(0 == this.count() - 1);
            super.set(num);
        }
        public byte[] data() {
            this.datas++;
            return super.data();
        }
    }

    static class TestCycles2 extends TestCycles {
        public final static int COUNT = 7;
        public int count() {
            this.counts++;
            return COUNT;
        }
        public void set(int num) {
            this.sets++;
            Arrays.fill(this.data, (byte)num);
        }
        public byte[] data() {
            this.datas++;
            return this.data;
        }
        byte[] data = new byte[11];
    }

    ///////////////////////////////////////////////////////////////////////////

    @Test
    public void testSingleFile() throws IOException {
        final int FLEN = 99999;
        FileNode fn = makeSingleFile(FLEN);
        assertTrue(FLEN == fn.size());
        assertTrue(fn.fileSystem() instanceof LocalFileSystem);
        TestCycles testCycles = new TestCycles();
        Wipe w = new Wipe(this.freg, testCycles, false);
        assertTrue(FLEN == w.totalBytes);
        Progress progress = new Progress(2);
        assertTrue(w.perform(progress));
        assertFalse(new File(fn.path(true)).exists());
        assertTrue(this.tmpDir.exists());
        assertTrue(1     == progress.nodes    .size());
        assertTrue(1     == progress.processed.size());
        assertTrue(100.0 == progress.processed.get(progress.processed.size() - 1));
        assertTrue(0     == progress.skipped  .size());
        assertTrue(0     == progress.errors   .size());
        assertTrue(0     == progress.warnings .size());
        assertTrue(0 <  testCycles.counts);
        assertTrue(1 == testCycles.sets);
        assertTrue(0 <  testCycles.datas);
    }

    ///////////////////////////////////////////////////////////////////////////

    @Test
    public void testSingleFileRemoveDirs() throws IOException {
        final int FLEN = 87654;
        FileNode fn = makeSingleFile(FLEN);
        assertTrue(fn.fileSystem() instanceof LocalFileSystem);
        TestCycles testCycles = new TestCycles();
        Wipe w = new Wipe(this.freg, testCycles, true);
        assertTrue(FLEN == w.totalBytes);
        Progress progress = new Progress(0);
        assertTrue(w.perform(progress));
        assertFalse(new File(fn.path(true)).exists());
        assertFalse(this.tmpDir.exists());
        assertTrue(2     == progress.nodes    .size());
        assertTrue(1     == progress.processed.size());
        assertTrue(100.0 == progress.processed.get(progress.processed.size() - 1));
        assertTrue(0     == progress.skipped  .size());
        assertTrue(0     == progress.errors   .size());
        assertTrue(0     == progress.warnings .size());
        assertTrue(0 <  testCycles.counts);
        assertTrue(1 == testCycles.sets);
        assertTrue(0 <  testCycles.datas);
    }

    ///////////////////////////////////////////////////////////////////////////

    @Test
    public void testWipeCycles() throws IOException {
        final int FLEN = 1001;
        FileNode fn = makeSingleFile(FLEN);
        assertTrue(fn.fileSystem() instanceof LocalFileSystem);
        TestCycles2 tc2 = new TestCycles2();
        Wipe w = new Wipe(this.freg, tc2, true);
        assertTrue(FLEN == w.totalBytes);
        Progress progress = new Progress(0);
        Wipe.__test_CHECK._instance = new Wipe.__test_CHECK() {
            public void onCycleDone(File fl, int cycle) {
                FileInputStream fos = null;
                try {
                    assertTrue(fl.length() == FLEN);
                    fos = new FileInputStream(fl);
                    for(int i = 0; i < FLEN; i++) {
                        assertTrue(cycle == fos.read());
                    }
                    assertTrue(-1 == fos.read());
                    fos.close();
                }
                catch (IOException ioe) {
                    fail();
                }
                finally {
                    if (null != fos) {
                        try {
                            fos.close();
                        }
                        catch (IOException ioe) {
                            fail();
                        }
                    }
                }
            }
        };
        assertTrue(w.perform(progress));
        assertFalse(new File(fn.path(true)).exists());
        assertFalse(this.tmpDir.exists());
        assertTrue(2     == progress.nodes    .size());
        assertTrue(2     <  progress.processed.size());
        assertTrue(0.000001 > 100.0 - progress.processed.get(progress.processed.size() - 1));
        assertTrue(0     == progress.skipped  .size());
        assertTrue(0     == progress.errors   .size());
        assertTrue(0     == progress.warnings .size());
        assertTrue(1                 == tc2.counts);
        assertTrue(TestCycles2.COUNT == tc2.sets);
        assertTrue(0                 <  tc2.datas);
    }

    ///////////////////////////////////////////////////////////////////////////

    @Test
    public void testFileAndDirs() throws IOException {
        final int MAX_FILES = 20;
        FileNode base = makeFilesAndDirs(MAX_FILES, null, null, null);
        FileRegistrar.Walker.Counting wc = new FileRegistrar.Walker.Counting();
        assertTrue(FileRegistrar.walk(this.freg.root(), wc, true, false));
        assertTrue(MAX_FILES == wc.files);
        Wipe w = new Wipe(this.freg, new Wipe.Cycles.Zeros(), true);
        Progress progress = new Progress(0);
        assertTrue(w.perform(progress));
        assertFalse(base.fileSystem().exists(base));
        assertFalse(new File(base.path(true)).exists());
        int[] non = progress.numOfNodes();
        assertTrue(MAX_FILES      == non[0]);
        assertTrue(wc.directories == non[1]);
        assertTrue(progress.errors   .size() == 0);
        assertTrue(progress.warnings .size() == 0);
        assertTrue(progress.skipped  .size() == 0);
        assertTrue(progress.processed.size() >  0);
    }

    ///////////////////////////////////////////////////////////////////////////

    @Test
    public void testFileAndDirsPartial() throws IOException {
        final int MAX_FILES = 20;
        FileNode base = makeFilesAndDirs(MAX_FILES, null, 2, null);
        //dumpFileReg();
        assertTrue(this.skips.size() > 0);
        FileRegistrar.Walker.Counting wc = new FileRegistrar.Walker.Counting();
        assertTrue(FileRegistrar.walk(this.freg.root(), wc, true, false));
        assertTrue(MAX_FILES/2 == wc.files);
        Wipe w = new Wipe(this.freg, new Wipe.Cycles.Zeros(), true);
        Progress progress = new Progress(2);
        assertTrue(w.perform(progress));
        for (File skip : this.skips) {
            assertTrue(skip.exists());
        }
        assertTrue(base.fileSystem().exists(base));
        int[] non = progress.numOfNodes();
        assertTrue(MAX_FILES/2    == non[0]);
        assertTrue(wc.directories == non[1]);
        assertTrue(progress.errors   .size() == 0);
        assertTrue(progress.warnings .size() == 0);
        assertTrue(progress.skipped  .size() == 0);
        assertTrue(progress.processed.size() >  0);
    }

    ///////////////////////////////////////////////////////////////////////////

    @Test
    public void testSkipping() throws IOException {
        DbgFileSystem dfs = new DbgFileSystem(false, '/');
        assertTrue(dfs.addRoot("R"));
        assertNotNull(dfs.createFile(null   , new String[] { "R", "a" },  0, 1L, 0, true));
        assertNotNull(dfs.createFile("a.txt", new String[] { "R",     }, 10, 2L, 0, true));
        assertNotNull(dfs.createFile("b.txt", new String[] { "R", "b" }, 20, 3L, 0, true));
        FileNode fn = dfs.nodeFromString("R/");
        FileRegistrar dfreg = new FileRegistrar.InMemory(new DefCmp(false));
        assertTrue(2 == FileRegistrar.bulk(dfreg, fn, null, null,
            new FileRegistrar.BulkCallback() {
                public boolean onProgress(FileNode current            ) { return true; }
                public Merge   onMerge   (FileNode[] nd0, FileNode nd1) { fail(); return Merge.ABORT; }
                public boolean matches   (FileNode file               ) { return true; }
            },
            true, true));
        Wipe w = new Wipe(dfreg, new Wipe.Cycles.Zeros(), true);
        Progress progress = new Progress(2);
        assertTrue(w.perform(progress));
        int[] non = progress.numOfNodes();
        assertTrue(0 == non[0]);
        assertTrue(0 == non[1]);
        assertTrue(progress.errors   .size() == 0);
        assertTrue(progress.warnings .size() == 0);
        assertTrue(progress.skipped  .size() == 4);
        assertTrue(progress.processed.size() == 0);
    }

    ///////////////////////////////////////////////////////////////////////////

    @Test
    public void testErrorsAndWarnings() throws IOException {
        final int MAX_FILES = 10;
        FileNode base = makeFilesAndDirs(MAX_FILES, null, null, 5);
        Wipe.__test_IOERROR = 2;
        assertTrue(this.skips.size() == 0);
        FileRegistrar.Walker.Counting wc = new FileRegistrar.Walker.Counting();
        assertTrue(FileRegistrar.walk(this.freg.root(), wc, true, false));
        assertTrue(MAX_FILES == wc.files);
        Wipe w = new Wipe(this.freg, new Wipe.Cycles.Zeros(), true);
        Progress progress = new Progress(2);
        assertTrue(w.perform(progress));
        assertTrue(base.fileSystem().exists(base));
        int[] non = progress.numOfNodes();
        assertTrue(MAX_FILES      == non[0]);
        assertTrue(wc.directories == non[1]);
        assertTrue(progress.errors   .size() == 2);
        assertTrue(progress.warnings .size() == 1);
        assertTrue(progress.skipped  .size() == 0);
        assertTrue(progress.processed.size() >  0);
        assertTrue(progress.processed.get(-1 +
                   progress.processed.size()) == 100.0);
    }

    ///////////////////////////////////////////////////////////////////////////

    @Test
    public void testAbort() throws IOException {
        final int MAX_FILES = 8;
        for (Progress.AbortAt aa : Progress.AbortAt.values()) {
            switch(aa) {
                case NOT    : continue;
                case SKIPPED: continue;   // TODO: test this too
                case ERROR  : Wipe.__test_IOERROR = 2; break;
                default     : continue;
            }
            FileNode base = makeFilesAndDirs(MAX_FILES, null, null, 5);
            FileRegistrar.Walker.Counting wc = new FileRegistrar.Walker.Counting();
            assertTrue(FileRegistrar.walk(this.freg.root(), wc, true, false));
            assertTrue(MAX_FILES == wc.files);
            Wipe w = new Wipe(this.freg, new Wipe.Cycles.Zeros(), true);
            Progress progress = new Progress(1);
            progress.abortAt = aa;
            assertFalse(w.perform(progress));
            assertTrue(base.fileSystem().exists(base));
            File basef = new File(base.path(true));
            assertTrue(basef.exists());
            assertTrue(TestUtils.removeDir(basef, true));
            Wipe.__test_IOERROR = 0;
        }
    }
}
