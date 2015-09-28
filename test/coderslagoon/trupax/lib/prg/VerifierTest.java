package coderslagoon.trupax.lib.prg;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.PrintStream;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;


import coderslagoon.baselib.io.NulOutputStream;
import coderslagoon.baselib.util.MiscUtils;
import coderslagoon.baselib.util.Prp;
import coderslagoon.baselib.util.Routine;
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
import coderslagoon.trupax.lib.prg.Prg.Result;
import coderslagoon.trupax.test.util.Verifier;
import coderslagoon.trupax.test.util.Verifier.Creator;

public class VerifierTest {
    @Before
    public void setUp() {
        assertTrue(PrgImpl.init().isSuccess());
    }

    @After
    public void tearDown() {
        delPrg();
        assertTrue(PrgImpl.cleanup().isSuccess());
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

        final int MAX_FILES = 511;
        Creator.Result cr = new Creator.Result();
        File base = v.makeDirsAndFiles(new Verifier.Setup() {
            public boolean  usingAbsolutePath() { return false; }
            public int      maxFiles         () { return MAX_FILES; }
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
        }, cr);

        assertNotNull(base);
        assertTrue(MAX_FILES == cr.files);

        final VarInt d = new VarInt();
        final VarInt f = new VarInt();

        final VarRef<Routine.Arg1E<Boolean, File>> fcounter = new VarRef<>();

        fcounter.v = new Routine.Arg1E<Boolean, File>() {
            public Boolean call(File dir) throws Exception {
                for (File fl : dir.listFiles()) {
                    if (fl.isDirectory()) {
                        d.v = d.v + 1;
                        fcounter.v.call(fl);
                    }
                    else {
                        f.v = f.v + 1;
                    }
                }
                return true;
            }
        };

        assertTrue(fcounter.v.call(base));
        assertTrue(MAX_FILES == f.v);
        assertTrue(3 < d.v);

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

        assertTrue(this.prgi.addObject(base.getAbsolutePath()).isSuccess());

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
        assertTrue(600000L         < volBytes);

        final VarLong pcount = new VarLong();
        final VarLong fbytes = new VarLong();
        final VarInt  fcount = new VarInt();
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
                return Result.ok();
            }
        });
        assertTrue(res.isSuccess());
        assertTrue(fbytes.v == rsum.bytesTotal);
        assertTrue(volBytes == vol.length());

        byte[] dec = Verifier.decryptVolume(PASSW.toCharArray(), vol);
        assertTrue(dec.length % 512 == 0);
        assertTrue(dec.length > 340000);

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
            prg.registerObjects(new Prg.RegisterObjectsCallback() {
                public Result onDirectory(String dir) {
                    dcount.v++;
                    return Result.ok();
                }
                public void configLocked() {
                }
            });

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
                    return Result.ok();
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
                            new FileNameMaker.Numbered(),
                            new FileNameMaker.RandomASCII(),
                            new FileNameMaker.RandomUnicode(),
                    }),
                    null);

            final String PASSW = "manymanymony";

            final Verifier.Setup vsetup = (new Verifier.Setup() {
                public boolean  usingAbsolutePath() { return true; }
                public int      maxFiles         () { return Integer.MAX_VALUE; }
                public int      minSubDirsPerDir () { return 1; }
                public int      maxSubDirsPerDir () { return 8; }
                public long     minFileSize      () { return 0; }
                public long     maxFileSize      () { return 600; }
                public int      minFilesPerDir   () { return 0; }
                public int      maxFilesPerDir   () { return 1000; }
                public int      minFileNameLen   () { return 3; }
                public int      maxFileNameLen   () { return 80; }
                public int      maxPathLen       () { return 222; }
                public long     maxBytes         () { return MAX_BYTES; }
                public String[] basePath         () { return new String[] { "many", "files"  }; }
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

            System.out.print("registering...");
            long tm3 = System.currentTimeMillis();
            prg.registerObjects(new Prg.RegisterObjectsCallback() {
                public Result onDirectory(String dir) {
                    return Result.ok();
                }
                public void configLocked() {
                }
            });
            System.out.printf(", time spent: %s\n",
                    MiscUtils.printTime(System.currentTimeMillis() - tm3));

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
            if (UDFTest.available()) {
                System.out.println("verifying...");
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
            assertEquals(volBytes, vol.length());
            assertEquals(0 == freeBlocks ? 0L : 1L, fspace.v);

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
                assertTrue(UDFTest.exec(dec, PrgImpl.BLOCK_SIZE, true, true, true,
                    new UDFTest.Listener() {
                        public boolean onOutput(String ln) {
                            Matcher m = p.matcher(ln);
                            if (m.matches()) {
                                if (m.group(1).equals(Long.toString(freeBlocks))) {
                                    found.v++;
                                }
                            }
                            return true;
                        }
                    }));
                assertTrue(0 < found.v);
            }
            assertTrue(TestUtils.removeDir(base, true));
            assertTrue(vol.delete());
            delPrg();
        }
    }
}
