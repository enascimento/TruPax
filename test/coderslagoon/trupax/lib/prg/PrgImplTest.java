package coderslagoon.trupax.lib.prg;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.PrintStream;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import coderslagoon.baselib.io.NulOutputStream;
import coderslagoon.baselib.util.MiscUtils;
import coderslagoon.baselib.util.NLS;
import coderslagoon.baselib.util.Prp;
import coderslagoon.baselib.util.Routine;
import coderslagoon.baselib.util.VarBool;
import coderslagoon.baselib.util.VarDouble;
import coderslagoon.baselib.util.VarInt;
import coderslagoon.baselib.util.VarLong;
import coderslagoon.baselib.util.VarRef;
import coderslagoon.tclib.container.Header;
import coderslagoon.tclib.util.Password;
import coderslagoon.test.util.FileNameMaker;
import coderslagoon.test.util.FilePathWalker;
import coderslagoon.test.util.TestUtils;
import coderslagoon.trupax.lib.UDFTest;
import coderslagoon.trupax.lib.prg.Prg;
import coderslagoon.trupax.lib.prg.PrgImpl;
import coderslagoon.trupax.lib.prg.Prg.Concern;
import coderslagoon.trupax.lib.prg.Prg.Result;
import coderslagoon.trupax.test.util.Verifier;
import coderslagoon.trupax.test.util.Verifier.Creator;

public class PrgImplTest {
    @Before
    public void setUp() throws Exception {
        Prp.global().clear();
        assertTrue(PrgImpl.init().isSuccess());
        NLS.Reg.instance().load("en");
    }

    @After
    public void tearDown() {
        delPrg();
        assertTrue(PrgImpl.cleanup().isSuccess());
        Prp.global().clear();
        NLS.Reg.instance().reset();
    }

    ///////////////////////////////////////////////////////////////////////////

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
    private Prg prgi;

    ///////////////////////////////////////////////////////////////////////////

    static long[] walkDir(File dir) {
        final long[] result = new long[3];
        FilePathWalker fpw = new FilePathWalker() {
            public boolean onObject(File obj) {
                if (obj.isDirectory()) {
                    result[0]++;
                }
                else {
                    result[1]++;
                    result[2] += obj.length();
                }
                return true;
            }
        };
        fpw.walk(dir);

        return result;
    }

    ///////////////////////////////////////////////////////////////////////////

    @Test
    public void testMakeDirsAndFiles() throws Exception {
        Verifier v = new Verifier(
                0xfedcba9876543210L,
                null,
                new PrintStream(new NulOutputStream()),
                System.err,
                new FileNameMaker.RandomUnicode(),
                null);

        File base = v.makeDirsAndFiles(new Verifier.Setup() {
            public boolean  usingAbsolutePath() { return false; }
            public int      maxFiles         () { return 511; }
            public int      minSubDirsPerDir () { return 2; }
            public int      maxSubDirsPerDir () { return 7; }
            public long     minFileSize      () { return 0; }
            public long     maxFileSize      () { return 10000; }
            public int      minFilesPerDir   () { return 0; }
            public int      maxFilesPerDir   () { return 25; }
            public int      minFileNameLen   () { return 3; }
            public int      maxFileNameLen   () { return 31; }
            public int      maxPathLen       () { return 200; }
            public long     maxBytes         () { return Long.MAX_VALUE; }
            public String[] basePath         () { return new String[] { "a", "bb", "ccc" }; }
        });

        assertNotNull(base);

        final VarInt d = new VarInt();
        final VarInt f = new VarInt();

        final VarRef<Routine.Arg1E<Boolean, File>> fcounter = new VarRef<>();

        fcounter.v = new Routine.Arg1E<Boolean, File>() {
            public Boolean call(File dir) throws Exception {
                for (File fl : dir.listFiles()) {
                    if (fl.isDirectory()) {
                        d.v = d.v + 1;
                        fcounter.v.call(fl);
                        continue;
                    }
                    f.v = f.v + 1;
                }
                return true;
            }
        };

        assertTrue(fcounter.v.call(base));
        assertTrue(511 == f.v);
        assertTrue(3   <  d.v);

        assertTrue(TestUtils.removeDir(base, true));
    }

    ///////////////////////////////////////////////////////////////////////////

    @Test
    public void test0() throws Exception {
        Verifier ver = new Verifier(
                null,
                null,
                new PrintStream(new NulOutputStream()), //System.out,
                System.err,
                new FileNameMaker.Numbered(),
                null);

        final int FILE_COUNT = coderslagoon.test.Control.quick() ? 50 : 5000;
        final String PASSW = "test123";

        final Verifier.Setup vsetup = (new Verifier.Setup() {
            public boolean  usingAbsolutePath() { return true; }
            public int      maxFiles         () { return FILE_COUNT; }
            public int      minSubDirsPerDir () { return 3; }
            public int      maxSubDirsPerDir () { return 5; }
            public long     minFileSize      () { return 0; }
            public long     maxFileSize      () { return 5000; }
            public int      minFilesPerDir   () { return 0; }
            public int      maxFilesPerDir   () { return 10; }
            public int      minFileNameLen   () { return 5; }
            public int      maxFileNameLen   () { return 20; }
            public int      maxPathLen       () { return 128; }
            public long     maxBytes         () { return Long.MAX_VALUE; }
            public String[] basePath         () { return new String[] { "base1" }; }
        });

        File base = ver.makeDirsAndFiles(vsetup);

        assertNotNull(base);

        Prg.Setup setup = new Prg.Setup();

        setup.args            = null;
        setup.fromCommandLine = false;
        setup.propertiesFile  = null;
        setup.saveProperties  = false;

        assertTrue(newPrg().ctor(Prp.global(), setup).isSuccess());
        Prg prg = this.prgi;

        assertTrue(prg.addObject(base.getAbsolutePath()).isSuccess());

        assertTrue(prg.setProperty(new Prg.NamedString(
                Prg.Prop.RECURSIVE_SEARCH, Boolean.TRUE.toString())).isSuccess());

        int depth = TestUtils.pathDepth(base, false) - 1;
        final VarInt dcount = new VarInt(depth);
        prg.registerObjects(new Prg.RegisterObjectsCallback() {
            public Result onDirectory(String dir) {
                dcount.v++;
                return Result.ok();
            }
            public void configLocked() {
            }
        });

        Prg.RegSum rsum = prg.registerSummary();
        assertNotNull(rsum);
        assertTrue(rsum.numberOfDirectories == dcount.v);

        long[] w = walkDir(base);
        assertTrue(rsum.numberOfDirectories == w[0] + depth);
        assertTrue(rsum.numberOfFiles       == w[1]);
        assertTrue(rsum.bytesTotal          == w[2]);

        File vol = ver.newVolumeFile();
        assertNotNull(vol);

        prg.setVolumeFile(vol.getAbsolutePath());
        prg.setFreeSpace(0L);

        assertTrue(prg.resolve().isSuccess());

        long volBytes = prg.volumeBytes();
        assertTrue(Header.SIZE * 2 < volBytes);
        assertTrue(602000L         < volBytes);

        final VarLong pcount = new VarLong();
        final VarLong fbytes = new VarLong();
        final VarInt  fcount = new VarInt();
        final VarBool fspace = new VarBool();
        Result res = prg.make(PASSW.toCharArray(), new Prg.MakeCallback() {
            public void onFile(String fileName, long fileSize) {
                fcount.v++;
                fbytes.v += fileSize;
            }
            public Result onVolumeWrite(long pos) {
                pcount.v++;
                return Result.ok();
            }
            public Result onFreeSpace() {
                fspace.v = true;
                return Result.ok();
            }
        });
        assertTrue(res.isSuccess());
        assertTrue(fbytes.v == rsum.bytesTotal);
        assertTrue(volBytes == vol.length());
        assertFalse(fspace.v);

        byte[] dec = Verifier.decryptVolume(PASSW.toCharArray(), vol);
        assertTrue(dec.length % 512 == 0);
        assertTrue(dec.length        > 340000);

        // BinUtils.hexDump(dec, 0, dec.length, System.out, 32, 4);

        for (Verifier.Matcher matcher : new Verifier.Matcher[] {
            ver.readerMatcher (),
            ver.browserMatcher()
        }) {
            matcher.match(base, vol, PrgImpl.BLOCK_SIZE,
                new Password(PASSW.toCharArray(), null),
                prg.getProperty(Prg.Prop.HASH_FUNCTION),
                prg.getProperty(Prg.Prop.BLOCK_CIPHER),
                vsetup);
        }

        File decfl = new File(base, "test0.dec.dump");
        MiscUtils.writeFile(decfl, dec);
        if (UDFTest.available()) {
            assertTrue(UDFTest.exec(decfl, PrgImpl.BLOCK_SIZE, true, false, false, null));
        }
        assertTrue(TestUtils.removeDir(base, true));
    }

    ///////////////////////////////////////////////////////////////////////////

    @Test
    public void testDifferentFileCounts() throws Exception {
        for (final int FILE_COUNT : new int[] { 1, 2, 10, 51, 117, 1001 }) {
            Verifier ver = new Verifier(null, null,
                    new PrintStream(new NulOutputStream()),
                    System.err,
                    new FileNameMaker.Mixer(new FileNameMaker[] {
                            new FileNameMaker.Numbered(),
                            new FileNameMaker.RandomASCII(),
                            new FileNameMaker.RandomUnicode(),
                    }),
                    null);

            final String PASSW = "this must all work!";

            final Verifier.Setup vsetup = (new Verifier.Setup() {
                public boolean  usingAbsolutePath() { return true; }
                public int      maxFiles         () { return FILE_COUNT; }
                public int      minSubDirsPerDir () { return 3; }
                public int      maxSubDirsPerDir () { return 5; }
                public long     minFileSize      () { return 0; }
                public long     maxFileSize      () { return 10000000 / FILE_COUNT; }
                public int      minFilesPerDir   () { return 0; }
                public int      maxFilesPerDir   () { return 10; }
                public int      minFileNameLen   () { return 3; }
                public int      maxFileNameLen   () { return 80; }
                public int      maxPathLen       () { return 240; }
                public long     maxBytes         () { return Long.MAX_VALUE; }
                public String[] basePath         () { return new String[] { "a", "bb"  }; }
            });

            File base = ver.makeDirsAndFiles(vsetup);

            assertNotNull(base);

            Prg.Setup setup = new Prg.Setup();

            assertTrue(newPrg().ctor(Prp.global(), setup).isSuccess());
            Prg prg = this.prgi;

            assertTrue(prg.addObject(base.getAbsolutePath()).isSuccess());

            Prg.NamedString pns = new Prg.NamedString(
                    Prg.Prop.RECURSIVE_SEARCH,
                    Boolean.TRUE.toString());
            assertTrue(prg.setProperty(pns).isSuccess());

            final VarInt dcount = new VarInt();
            final VarBool locked = new VarBool();
            prg.registerObjects(new Prg.RegisterObjectsCallback() {
                public Result onDirectory(String dir) {
                    dcount.v++;
                    return Result.ok();
                }
                public void configLocked() {
                    locked.v = true;
                }
            });
            assertTrue(locked.v);

            Prg.RegSum rsum = prg.registerSummary();
            int depth = TestUtils.pathDepth(base, false) - 1;
            assertNotNull(rsum);
            assertTrue(rsum.numberOfDirectories == dcount.v + depth);

            File vol = ver.newVolumeFile();
            assertNotNull(vol);

            prg.setVolumeFile(vol.getAbsolutePath());
            prg.setFreeSpace(0L);

            assertTrue(prg.resolve().isSuccess());

            long volBytes = prg.volumeBytes();
            assertTrue(Header.SIZE * 2 < volBytes);

            final VarLong pcount = new VarLong();
            final VarInt fcount = new VarInt();
            Result res = prg.make(PASSW.toCharArray(), new Prg.MakeCallback() {
                public void onFile(String fileName, long fileSize) {
                    fcount.v++;
                }
                public Result onVolumeWrite(long pos) {
                    pcount.v++;
                    return Result.ok();
                }
                public Result onFreeSpace() {
                    return Result.ok();
                }
            });
            assertTrue(res.isSuccess());
            assertTrue(volBytes == vol.length());
            assertTrue(pcount.v * PrgImpl.BLOCK_SIZE == vol.length());

            byte[] dec = Verifier.decryptVolume(PASSW.toCharArray(), vol);
            assertTrue(dec.length % PrgImpl.BLOCK_SIZE == 0);

            for (Verifier.Matcher matcher : new Verifier.Matcher[] {
                ver.readerMatcher (),
                ver.browserMatcher()
            }) {
                matcher.match(base, vol, PrgImpl.BLOCK_SIZE,
                    new Password(PASSW.toCharArray(), null),
                    prg.getProperty(Prg.Prop.HASH_FUNCTION),
                    prg.getProperty(Prg.Prop.BLOCK_CIPHER),
                    vsetup);
            }
            File decfl = new File(base, "test1.dec.dump");
            MiscUtils.writeFile(decfl, dec);
            if (UDFTest.available()) {
                assertTrue(UDFTest.exec(decfl, PrgImpl.BLOCK_SIZE, true, false, false, null));
            }
            assertTrue(TestUtils.removeDir(base, true));
            delPrg();
        }
    }

    ///////////////////////////////////////////////////////////////////////////

    @Test
    public void testLargeVolumes() throws Exception {
        final int LOOPS = 2;
        final long MAX_BYTES = (coderslagoon.test.Control.quick() ? 8 : 6000) * 1000 * 1000L;
        final Random seeder = new Random(0xb116f44d);

        for (int i = 0; i < LOOPS; i++) {
            final long tm = System.currentTimeMillis();
            System.out.printf("LOOP #%d\n", i + 1);

            Verifier ver = new Verifier(seeder.nextLong(), null,
                    new PrintStream(new NulOutputStream()),
                    System.err,
                    new FileNameMaker.Mixer(new FileNameMaker[] {
                            new FileNameMaker.Numbered(),
                            new FileNameMaker.RandomASCII(),
                            new FileNameMaker.RandomUnicode(),
                    }),
                    null);

            final String PASSW = "abcdefg";

            final Verifier.Setup vsetup = (new Verifier.Setup() {
                public boolean  usingAbsolutePath() { return true; }
                public int      maxFiles         () { return Integer.MAX_VALUE; }
                public int      minSubDirsPerDir () { return 3; }
                public int      maxSubDirsPerDir () { return 5; }
                public long     minFileSize      () { return 0; }
                public long     maxFileSize      () { return MAX_BYTES / 1000L; }
                public int      minFilesPerDir   () { return 0; }
                public int      maxFilesPerDir   () { return 100; }
                public int      minFileNameLen   () { return 3; }
                public int      maxFileNameLen   () { return 80; }
                public int      maxPathLen       () { return 200; }
                public long     maxBytes         () { return MAX_BYTES; }
                public String[] basePath         () { return new String[] { "biggg"  }; }
            });

            System.out.println("creating structure...");

            File base = ver.makeDirsAndFiles(vsetup);

            assertNotNull(base);

            Prg.Setup setup = new Prg.Setup();

            assertTrue(newPrg().ctor(Prp.global(), setup).isSuccess());
            Prg prg = this.prgi;

            assertTrue(prg.addObject(base.getAbsolutePath()).isSuccess());

            Prg.NamedString pns = new Prg.NamedString(
                    Prg.Prop.RECURSIVE_SEARCH,
                    Boolean.TRUE.toString());
            assertTrue(prg.setProperty(pns).isSuccess());

            System.out.println("registering...");
            prg.registerObjects(new Prg.RegisterObjectsCallback() {
                public Result onDirectory(String dir) {
                    return Result.ok();
                }
                public void configLocked() {
                }
            });

            long[] w = walkDir(base);
            int depth = TestUtils.pathDepth(base, false) - 1;
            assertTrue(prg.registerSummary().numberOfDirectories == w[0] + depth);
            assertTrue(prg.registerSummary().numberOfFiles       == w[1]);
            assertTrue(prg.registerSummary().bytesTotal          == w[2]);
            assertTrue(prg.registerSummary().bytesTotal          == MAX_BYTES);

            System.out.printf("%d dirs, %d files - %,d bytes\n", w[0], w[1], w[2]);

            File vol = ver.newVolumeFile();
            assertNotNull(vol);

            prg.setVolumeFile(vol.getAbsolutePath());
            prg.setFreeSpace(0L);

            System.out.printf("volume file is '%s', resolving...\n", vol.getAbsolutePath());
            assertTrue(prg.resolve().isSuccess());

            long volBytes = prg.volumeBytes();
            System.out.printf("volume size is %,d\n", volBytes);
            assertTrue(Header.SIZE * 2 < volBytes);

            final VarLong pcount = new VarLong();
            final VarInt fcount = new VarInt();
            final long blocks = volBytes / PrgImpl.BLOCK_SIZE;
            System.out.print("make in progress ");
            final long tm2 = System.currentTimeMillis();
            Result res = prg.make(PASSW.toCharArray(), new Prg.MakeCallback() {
                long lastPos = 0;
                long step = blocks / 10;

                public void onFile(String fileName, long fileSize) {
                    fcount.v++;
                }
                public Result onVolumeWrite(long pos) {
                    pcount.v++;
                    if (pos >= this.lastPos) {
                        this.lastPos = pos + this.step;
                        System.out.print("#");
                    }
                    return Result.ok();
                }
                public Result onFreeSpace() {
                    return null;
                }
            });
            System.out.printf(" %,d bytes/sec\n",
                    (volBytes * 1000) / Math.max(1, System.currentTimeMillis() - tm2));

            assertTrue(res.isSuccess());
            assertTrue(volBytes == vol.length());
            assertTrue(volBytes == PrgImpl.BLOCK_SIZE * pcount.v);
            assertTrue(volBytes > MAX_BYTES);

            System.out.println("matching...");
            for (Verifier.Matcher matcher : new Verifier.Matcher[] {
                ver.readerMatcher (),
                ver.browserMatcher()
            }) {
                matcher.match(base, vol, PrgImpl.BLOCK_SIZE,
                    new Password(PASSW.toCharArray(), null),
                    prg.getProperty(Prg.Prop.HASH_FUNCTION),
                    prg.getProperty(Prg.Prop.BLOCK_CIPHER),
                    vsetup);
            }
            File dec = new File(base, "testLargeVolumes.dec.dump");
            assertTrue(!dec.exists() || dec.delete());

            Verifier.decryptVolume(PASSW.toCharArray(), vol, dec);
            assertTrue(dec.length() % PrgImpl.BLOCK_SIZE == 0);
            System.out.println("verifying...");
            if (UDFTest.available()) {
                assertTrue(UDFTest.exec(dec, PrgImpl.BLOCK_SIZE, true, false, false, null));
            }
            assertTrue(TestUtils.removeDir(base, true));
            assertTrue(vol.delete());

            delPrg();

            System.out.println("loop time was " +
                    MiscUtils.printTime(System.currentTimeMillis() - tm));
        }
    }

    ///////////////////////////////////////////////////////////////////////////

    @Test
    public void testManyFiles() throws Exception {
        final int LOOPS = 2;
        final long MAX_BYTES = (coderslagoon.test.Control.quick() ? 2 : 200) * 1000 * 1000L;
        final Random seeder = new Random(0x8091a2b3c4d5e6f7L);

        for (int i = 0; i < LOOPS; i++) {
            final long tm = System.currentTimeMillis();
            System.out.printf("LOOP #%d\n", i + 1);

            Verifier ver = new Verifier(seeder.nextLong(), null,
                    new PrintStream(new NulOutputStream()),
                    System.err,
                    new FileNameMaker.Mixer(new FileNameMaker[] {
                            // NOTE: under Linux there can be collisions due
                            // to case-sensitive filenames (which cannot be
                            // caught by the verifier's generation stage),
                            // additionally one gets a lot of those with the
                            // random unicode than 6 nbytes (=3 characeters),
                            // so for now we try to avoid collisions in the
                            // unicode space, while there is no problem with
                            // just numbers (retries in the verifier gen)...
                            new FileNameMaker.Numbered(),
                            new FileNameMaker.RandomUnicode()
                    }),
                    null);

            final String PASSW = "manymanymony";

            final Verifier.Setup vsetup = (new Verifier.Setup() {
                public boolean  usingAbsolutePath() { return false; }
                public int      maxFiles         () { return Integer.MAX_VALUE; }
                public int      minSubDirsPerDir () { return 1; }
                public int      maxSubDirsPerDir () { return 8; }
                public long     minFileSize      () { return 0; }
                public long     maxFileSize      () { return 600; }
                public int      minFilesPerDir   () { return 0; }
                public int      maxFilesPerDir   () { return 1000; }
                public int      minFileNameLen   () { return 6; }
                public int      maxFileNameLen   () { return 80; }
                public int      maxPathLen       () { return 222; }
                public long     maxBytes         () { return MAX_BYTES; }
                public String[] basePath         () { return new String[] { "many", "files"  }; }
            });

            System.out.println("creating structure...");

            Verifier.Creator.Result vcr = new Verifier.Creator.Result();
            File base = ver.makeDirsAndFiles(vsetup, vcr);

            assertNotNull(base);

            Prg.Setup setup = new Prg.Setup();

            assertTrue(newPrg().ctor(Prp.global(), setup).isSuccess());
            Prg prg = this.prgi;

            assertTrue(prg.addObject(base.getAbsolutePath()).isSuccess());

            Prg.NamedString pns = new Prg.NamedString(
                    Prg.Prop.RECURSIVE_SEARCH,
                    Boolean.TRUE.toString());
            assertTrue(prg.setProperty(pns).isSuccess());
            assertTrue(prg.setProperty(new Prg.NamedString(
                    Prg.Prop.TRIM_PATH,
                    Boolean.TRUE.toString())).isSuccess());

            System.out.print("registering...");
            long tm3 = System.currentTimeMillis();
            Result res = prg.registerObjects(new Prg.RegisterObjectsCallback() {
                public Result onDirectory(String dir) {
                    return Result.ok();
                }
                public void configLocked() {
                }
            });
            assertTrue(res.isSuccess());
            System.out.printf(", time spent: %s\n",
                    MiscUtils.printTime(System.currentTimeMillis() - tm3));

            prg.registerSummary();

            long[] w = walkDir(base);
            final int DEPTH = 1 + 2;   // verifier*/many/files
            assertTrue(vcr.dirs  ==  w[0] - DEPTH);
            assertTrue(vcr.files ==  w[1]);

            assertTrue(prg.registerSummary().numberOfDirectories == vcr.dirs + DEPTH);
            assertTrue(prg.registerSummary().numberOfFiles       == vcr.files);
            assertTrue(prg.registerSummary().bytesTotal          == w[2]);
            assertTrue(prg.registerSummary().bytesTotal          == MAX_BYTES);

            System.out.printf("%d dirs, %d files - %,d bytes\n", w[0], w[1], w[2]);

            File vol = ver.newVolumeFile();
            assertNotNull(vol);

            prg.setVolumeFile(vol.getAbsolutePath());
            prg.setFreeSpace(0L);

            System.out.printf("volume file is '%s', resolving...", vol.getAbsolutePath());
            tm3 = System.currentTimeMillis();
            assertTrue(prg.resolve().isSuccess());
            System.out.printf(", time spent: %s\n",
                    MiscUtils.printTime(System.currentTimeMillis() - tm3));

            long volBytes = prg.volumeBytes();
            System.out.printf("volume size is %,d\n", volBytes);
            assertTrue(Header.SIZE * 2 < volBytes);

            final VarLong pcount = new VarLong();
            final VarInt fcount = new VarInt();
            final long blocks = volBytes / PrgImpl.BLOCK_SIZE;
            System.out.print("make in progress ");
            final long tm2 = System.currentTimeMillis();
            res = prg.make(PASSW.toCharArray(), new Prg.MakeCallback() {
                long lastPos = 0;
                long step = blocks / 10;

                public void onFile(String fileName, long fileSize) {
                    fcount.v++;
                }
                public Result onVolumeWrite(long pos) {
                    pcount.v++;
                    if (pos >= this.lastPos) {
                        this.lastPos = pos + this.step;
                        System.out.print("#");
                    }
                    return Result.ok();
                }
                public Result onFreeSpace() {
                    return Result.ok();
                }
            });
            System.out.printf(" %,d bytes/sec\n",
                    (volBytes * 1000) / Math.max(1, System.currentTimeMillis() - tm2));

            assertTrue(res.isSuccess());
            assertTrue(volBytes == vol.length());
            assertTrue(volBytes == PrgImpl.BLOCK_SIZE * pcount.v);
            assertTrue(volBytes > MAX_BYTES);

            String hashFunction = prg.getProperty(Prg.Prop.HASH_FUNCTION);
            String blockCipher  = prg.getProperty(Prg.Prop.BLOCK_CIPHER);
            prg = null;

            System.out.println("matching...");
            for (Verifier.Matcher matcher : new Verifier.Matcher[] {
                ver.readerMatcher (),
                ver.browserMatcher()
            }) {
                matcher.match(base, vol, PrgImpl.BLOCK_SIZE,
                    new Password(PASSW.toCharArray(), null),
                    hashFunction,
                    blockCipher,
                    vsetup);
            }
            File dec = new File(base, "testManyFiles.dec.dump");
            assertTrue(!dec.exists() || dec.delete());

            Verifier.decryptVolume(PASSW.toCharArray(), vol, dec);
            assertTrue(dec.length() % PrgImpl.BLOCK_SIZE == 0);
            System.out.println("verifying...");
            if (UDFTest.available()) {
                assertTrue(UDFTest.exec(dec, PrgImpl.BLOCK_SIZE, true, false, true, null));
            }
            System.out.print("cleaning up...");
            tm3 = System.currentTimeMillis();
            assertTrue(TestUtils.removeDir(base, true));
            assertTrue(vol.delete());
            System.out.printf(", time spent: %s\n",
                    MiscUtils.printTime(System.currentTimeMillis() - tm3));

            delPrg();

            System.out.println("loop time was " +
                    MiscUtils.printTime(System.currentTimeMillis() - tm));

            assertTrue(ver.cleanUp());
        }
    }

    ///////////////////////////////////////////////////////////////////////////

    @Test
    public void testFreeSpace() throws Exception {
        final Pattern p = Pattern.compile(
                "^.*Unallocated Space Bitmap free space\\:[ ]*([0-9]*)[ ]*$");

        for (final long freeBlocks : new long[] { 0, 1, 100, 4321 }) {
            Verifier ver = new Verifier(0xc0ffe17eL, null,
                    new PrintStream(new NulOutputStream()),
                    System.err,
                    new FileNameMaker.Numbered(),
                    null);

            final String PASSW = "freebee";

            final Verifier.Setup vsetup = (new Verifier.Setup() {
                public boolean  usingAbsolutePath() { return true; }
                public int      maxFiles         () { return Integer.MAX_VALUE; }
                public int      minSubDirsPerDir () { return 1; }
                public int      maxSubDirsPerDir () { return 3; }
                public long     minFileSize      () { return 0; }
                public long     maxFileSize      () { return 5000; }
                public int      minFilesPerDir   () { return 0; }
                public int      maxFilesPerDir   () { return 10; }
                public int      minFileNameLen   () { return 3; }
                public int      maxFileNameLen   () { return 12; }
                public int      maxPathLen       () { return 70; }
                public long     maxBytes         () { return 1000000; }
                public String[] basePath         () { return new String[] { "_occupied"  }; }
            });

            File base = ver.makeDirsAndFiles(vsetup);
            assertNotNull(base);

            Prg.Setup setup = new Prg.Setup();

            assertTrue(newPrg().ctor(Prp.global(), setup).isSuccess());
            Prg prg = this.prgi;

            assertTrue(prg.addObject(base.getAbsolutePath()).isSuccess());

            Prg.NamedString pns = new Prg.NamedString(
                Prg.Prop.RECURSIVE_SEARCH,
                Boolean.TRUE.toString());
            assertTrue(prg.setProperty(pns).isSuccess());

            prg.registerObjects(new Prg.RegisterObjectsCallback() {
                public Result onDirectory(String dir) {
                    return Result.ok();
                }
                public void configLocked() {
                }
            });

            File vol = ver.newVolumeFile();
            assertNotNull(vol);
            prg.setVolumeFile(vol.getAbsolutePath());

            long freeSpace = PrgImpl.BLOCK_SIZE * freeBlocks;

            prg.setFreeSpace(freeSpace);

            assertTrue(prg.resolve().isSuccess());

            long volBytes = prg.volumeBytes();
            assertTrue(Header.SIZE * 2 + freeSpace < volBytes);

            final VarLong fspace = new VarLong();
            assertTrue(prg.make(PASSW.toCharArray(), new Prg.MakeCallback() {
                public void onFile(String fileName, long fileSize) { }
                public Result onVolumeWrite(long pos) {
                    return Result.ok();
                }
                public Result onFreeSpace() {
                    fspace.v++;
                    return Result.ok();
                }
            }).isSuccess());
            assertTrue(volBytes == vol.length());
            assertTrue((0 == freeSpace && 0 == fspace.v) ||
                       (0 < freeSpace && 1L == fspace.v));

            for (Verifier.Matcher matcher : new Verifier.Matcher[] {
                ver.readerMatcher (),
                ver.browserMatcher()
            }) {
                matcher.match(base, vol, PrgImpl.BLOCK_SIZE,
                    new Password(PASSW.toCharArray(), null),
                    prg.getProperty(Prg.Prop.HASH_FUNCTION),
                    prg.getProperty(Prg.Prop.BLOCK_CIPHER),
                    vsetup);
            }
            File dec = new File(base, "testFreeSpace.dec.dump");
            assertTrue(!dec.exists() || dec.delete());

            Verifier.decryptVolume(PASSW.toCharArray(), vol, dec);
            assertTrue(dec.length() % PrgImpl.BLOCK_SIZE == 0);

            if (UDFTest.available()) {
                final VarInt found = new VarInt();
                final VarInt unexpected = new VarInt();
                assertTrue(UDFTest.exec(dec, PrgImpl.BLOCK_SIZE, true, true, true,
                    new UDFTest.Listener() {
                        public boolean onOutput(String ln) {
                            Matcher m = p.matcher(ln);
                            if (m.matches()) {
                                if (m.group(1).equals(Long.toString(freeBlocks))) {
                                    found.v++;
                                }
                            }
                            if (ln.contains("Hard and Soft Write-Protect")) {
                                unexpected.v++;
                            }
                            return true;
                        }
                    }));
                assertTrue(0 < found.v);
                assertTrue(0 == unexpected.v);
            }
            assertTrue(TestUtils.removeDir(base, true));
            assertTrue(vol.delete());
            delPrg();
        }
    }

    ///////////////////////////////////////////////////////////////////////////

    @Test
    public void testWriteProtect() throws Exception {

        Verifier ver = new Verifier(0xc0ffe17eL, null,
                new PrintStream(new NulOutputStream()),
                System.err,
                new FileNameMaker.Numbered(),
                null);

        final String PASSW = "testWriteProtect";

        final Verifier.Setup vsetup = (new Verifier.Setup() {
            public boolean  usingAbsolutePath() { return true; }
            public int      maxFiles         () { return Integer.MAX_VALUE; }
            public int      minSubDirsPerDir () { return 1; }
            public int      maxSubDirsPerDir () { return 3; }
            public long     minFileSize      () { return 0; }
            public long     maxFileSize      () { return 45678; }
            public int      minFilesPerDir   () { return 0; }
            public int      maxFilesPerDir   () { return 10; }
            public int      minFileNameLen   () { return 3; }
            public int      maxFileNameLen   () { return 17; }
            public int      maxPathLen       () { return 71; }
            public long     maxBytes         () { return 1000001; }
            public String[] basePath         () { return new String[] { "_wrtprot"  }; }
        });

        File base = ver.makeDirsAndFiles(vsetup);
        assertNotNull(base);

        Prg.Setup setup = new Prg.Setup();

        assertTrue(newPrg().ctor(Prp.global(), setup).isSuccess());
        Prg prg = this.prgi;

        assertTrue(prg.addObject(base.getAbsolutePath()).isSuccess());

        Prg.NamedString pns = new Prg.NamedString(
            Prg.Prop.WRITEPROTECT,
            Boolean.TRUE.toString());
        assertTrue(prg.setProperty(pns).isSuccess());

        prg.registerObjects(new Prg.RegisterObjectsCallback() {
            public Result onDirectory(String dir) {
                return Result.ok();
            }
            public void configLocked() {
            }
        });

        File vol = ver.newVolumeFile();
        assertNotNull(vol);
        prg.setVolumeFile(vol.getAbsolutePath());

        assertTrue(prg.resolve().isSuccess());

        assertTrue(prg.make(PASSW.toCharArray(), new Prg.MakeCallback() {
            public void onFile(String fileName, long fileSize) { }
            public Result onVolumeWrite(long pos) { return Result.ok(); }
            public Result onFreeSpace() { return Result.ok(); }
        }).isSuccess());

        File dec = new File(base, "testWriteProtect.dec.dump");
        assertTrue(!dec.exists() || dec.delete());

        Verifier.decryptVolume(PASSW.toCharArray(), vol, dec);
        assertTrue(dec.length() % PrgImpl.BLOCK_SIZE == 0);

        if (UDFTest.available()) {
            final VarInt found = new VarInt();
            assertTrue(UDFTest.exec(dec, PrgImpl.BLOCK_SIZE, true, true, true,
                new UDFTest.Listener() {
                    public boolean onOutput(String ln) {
                        if (ln.contains("#03  Hard and Soft Write-Protect")) {
                            found.v++;
                        }
                        return true;
                    }
                }));
            assertEquals(2, found.v);
        }
        assertTrue(TestUtils.removeDir(base, true));
        assertTrue(vol.delete());
        delPrg();
    }

    ///////////////////////////////////////////////////////////////////////////

    @Test
    public void testWiping() throws Exception {
        Verifier ver = new Verifier(
                null,
                null,
                new PrintStream(new NulOutputStream()), //System.out,
                System.err,
                new FileNameMaker.Mixer(new FileNameMaker[] {
                    new FileNameMaker.Numbered(),
                    new FileNameMaker.RandomASCII(),
                    new FileNameMaker.RandomUnicode()
                }),
                null);

        final int FILE_COUNT = coderslagoon.test.Control.quick() ? 30 : 3000;

        final Verifier.Setup vsetup = (new Verifier.Setup() {
            public boolean  usingAbsolutePath() { return true; }
            public int      maxFiles         () { return FILE_COUNT; }
            public int      minSubDirsPerDir () { return 3; }
            public int      maxSubDirsPerDir () { return 5; }
            public long     minFileSize      () { return 0; }
            public long     maxFileSize      () { return 32000; }
            public int      minFilesPerDir   () { return 0; }
            public int      maxFilesPerDir   () { return 10; }
            public int      minFileNameLen   () { return 5; }
            public int      maxFileNameLen   () { return 20; }
            public int      maxPathLen       () { return 128; }
            public long     maxBytes         () { return Long.MAX_VALUE; }
            public String[] basePath         () { return new String[] { "base1" }; }
        });

        Creator.Result cr = new Creator.Result();
        File base = ver.makeDirsAndFiles(vsetup, cr);
        assertNotNull(base);

        Prg.Setup setup = new Prg.Setup();
        setup.args            = null;
        setup.fromCommandLine = false;
        setup.propertiesFile  = null;
        setup.saveProperties  = false;
        assertTrue(newPrg().ctor(Prp.global(), setup).isSuccess());
        Prg prg = this.prgi;

        assertTrue(prg.addObject(base.getAbsolutePath()).isSuccess());
        assertTrue(prg.setProperty(new Prg.NamedString(
                   Prg.Prop.RECURSIVE_SEARCH, Boolean.TRUE.toString())).isSuccess());
        prg.registerObjects(new Prg.RegisterObjectsCallback() {
            public Result onDirectory(String dir) {
                return Result.ok();
            }
            public void configLocked() {
            }
        });

        final VarInt    wipedFiles        = new VarInt();
        final VarLong   wipedBytes        = new VarLong();
        final VarInt    wipeProgressCalls = new VarInt();
        final VarInt    wipeConcerns      = new VarInt();
        final VarDouble wipeLastPercent   = new VarDouble(-1.0);
        final VarRef<String> vanishedFile = new VarRef<>();
        final int VANISH_NUM = 5;
        assertTrue(prg.wipe(new Prg.WipeCallback() {
            int vanishNum;
            public void onFile(String fileName, long fileSize) {
                wipedFiles.v++;
                wipedBytes.v += fileSize;
                if (++this.vanishNum == VANISH_NUM) {
                    assertTrue(new File(vanishedFile.v = fileName).delete());
                }
            }
            public Result onProgress(double percent) {
                assertTrue(percent >= 0.0);
                assertTrue(percent <= 100.0);
                assertTrue(wipeLastPercent.v < percent);
                wipeLastPercent.v = percent;
                wipeProgressCalls.v++;
                return Result.ok();
            }
            public Result onConcern(Concern concern, String message) {
                assertTrue(concern == Concern.WARNING);
                assertTrue(-1 == concern.localized().indexOf("!"));
                wipeConcerns.v++;
                return Result.ok();
            }
        }).isSuccess());

        assertNotNull(vanishedFile.v);
        assertTrue(wipedFiles.v        == FILE_COUNT);
        assertTrue(wipeProgressCalls.v > 0);
        assertTrue(wipeConcerns.v      == 1);
        assertTrue(wipeLastPercent.v   == 100.0);
        assertTrue(wipeLastPercent.v   == 100.0);
        assertTrue(wipedBytes.v        == cr.bytes);
        assertFalse(base.exists());
        delPrg();
    }

    ///////////////////////////////////////////////////////////////////////////

    @Test
    public void testInvalidateNonExistingFile() throws Exception {

        File noSuchFile = new File(System.getProperty("java.io.tmpdir"),
            TestUtils.createTempFileName("testInvalidateNonExistingFile"));
        Assert.assertFalse(noSuchFile.exists());

        Prg prg = newPrg();
        Assert.assertTrue(prg.setVolumeFile(noSuchFile.getAbsolutePath()).isSuccess());
        final VarLong ops = new VarLong();
        Prg.Result res = prg.invalidate(new Prg.ProgressCallback() {
            @Override
            public Result onProgress(double percent) {
                ops.v++;
                return Result.ok();
            }
        });
        Assert.assertFalse(noSuchFile.exists());
        Assert.assertTrue(res.isFailure());
        Assert.assertTrue(0 == ops.v);

        delPrg();
    }
}
