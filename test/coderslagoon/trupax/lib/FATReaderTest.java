package coderslagoon.trupax.lib;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.Properties;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import coderslagoon.baselib.io.BlockDevice;
import coderslagoon.baselib.io.BlockDeviceImpl;
import coderslagoon.baselib.io.IOUtils;
import coderslagoon.baselib.util.MiscUtils;
import coderslagoon.tclib.crypto.Registry;
import coderslagoon.tclib.util.Key;
import coderslagoon.test.util.TestUtils;
import coderslagoon.trupax.lib.FATReader;
import coderslagoon.trupax.lib.Reader;
import coderslagoon.trupax.lib.Reader.Progress;
import coderslagoon.trupax.tc.TCReader;
import coderslagoon.trupax.test.util.FReceipt;
import coderslagoon.trupax.test.util.Verifier;

public class FATReaderTest {
    
    @Before
    public void setUp() throws Exception {
        FReceipt.resetTweaks();
        Registry.setup(false);
    }

    @After
    public void tearDown() {
        FReceipt.resetTweaks();
    }

    @Test
    public void testTC() throws Exception {
        test0("../../tclib/container/resources/minvol.tc", "123", false);
    }

    @Test
    public void testHC() throws Exception {
        test0("../../tclib/container/resources/minvol.hc", "12345", true);
    }

    void test0(String res, String passw, boolean veraCrypt) throws Exception {
        byte[] img = IOUtils.readStreamBytes(getClass().getResourceAsStream(res));
        BlockDevice bdev = new BlockDeviceImpl.MemoryBlockDevice(512, img, true, false);

        TCReader tcr = new TCReader(bdev,
                new Key.ByteArray(passw.getBytes()), false, veraCrypt);

        FATReader fs = new FATReader(tcr, new Properties());
        assertNotNull(fs.bdev);

        File toDir = TestUtils.createTempDir("FATReaderTest.test0");

        fs.extract(toDir,
            //Reader.Progress2.TRACE
            new Progress() {
                public Result onMounting(int numOfObjects) {
                    return Result.OK;
                }
                public Result onMount(int numOfFiles, int numOfDirs) {
                    return Result.OK;
                }
                public Result onDirectory(File dir, long size, Long tstamp) {
                    return Result.OK;
                }
                public Result onFile(File fl, long size, Long tstamp) {
                    return Result.OK;
                }
                public Result onData(long written) {
                    return Result.OK;
                }
            });

        tcr.close(false);

        File testTxt = new File(toDir, "test.txt");
        assertTrue(testTxt.exists());
        assertTrue(testTxt.length() == 777L);
        assertEquals(TestUtils.md5OfFile(testTxt), "567e283d9d5eeee0060a23b2460c2745");

        assertTrue(TestUtils.removeDir(toDir, true));
    }

    ///////////////////////////////////////////////////////////////////////////

    @Test
    public void testFullUnicode() throws Exception {
        assumeTrue(MiscUtils.underWindows());

        byte[] img = IOUtils.readStreamBytes(new GZIPInputStream(
                getClass().getResourceAsStream("resources/fatreader_test.gz")));
        BlockDevice bdev = new BlockDeviceImpl.MemoryBlockDevice(512, img, true, false);

        FATReader fs = new FATReader(bdev, new Properties());
        assertNotNull(fs.bdev);

        final File toDir = TestUtils.createTempDir("FATReaderTest.test1.extract");
        fs.extract(toDir, Reader.Progress2.NULL);

        FReceipt fr = new FReceipt();
        FReceipt._store = false;
        FReceipt._caseMatch = false;
        fr.exec(new String[] { toDir.getAbsolutePath(), RCP_PATH });
        assertEquals(0  , fr.numOfNewFiles);
        assertEquals(0  , fr.numOfNewDirs);
        assertEquals(0  , fr.numOfMissingDirs);
        assertEquals(0  , fr.numOfMissingFiles);
        assertEquals(0  , fr.numOfNullFiles);
        assertEquals(758, fr.numOfFiles);
        assertEquals(28 , fr.numOfDirs);
        assertEquals(5  , FReceipt._caseMatchMisses);

        bdev.close(false);
        assertTrue(TestUtils.removeDir(toDir , true));
    }

    ///////////////////////////////////////////////////////////////////////////

    @Test
    public void testDE() throws Exception {
        byte[] img = IOUtils.readStreamBytes(new GZIPInputStream(
                getClass().getResourceAsStream("resources/fatreader_test_DE.gz")));
        BlockDevice bdev = new BlockDeviceImpl.MemoryBlockDevice(512, img, true, false);

        FATReader fs = new FATReader(bdev, new Properties());
        assertNotNull(fs.bdev);

        final File toDir = TestUtils.createTempDir("FATReaderTest.testDE.extract");
        fs.extract(toDir, Reader.Progress2.NULL);

        FReceipt fr = new FReceipt();
        FReceipt._store = false;
        FReceipt._caseMatch = false;
        fr.exec(new String[] { toDir.getAbsolutePath(), RCP_PATH_DE });
        assertEquals(0  , fr.numOfNewFiles);
        assertEquals(0  , fr.numOfNewDirs);
        assertEquals(0  , fr.numOfMissingDirs);
        assertEquals(0  , fr.numOfMissingFiles);
        assertEquals(0  , fr.numOfNullFiles);
        assertEquals(747, fr.numOfFiles);
        assertEquals(37 , fr.numOfDirs);
        assertEquals(4  , FReceipt._caseMatchMisses);

        bdev.close(false);
        assertTrue(TestUtils.removeDir(toDir , true));
    }

    ///////////////////////////////////////////////////////////////////////////

    // manual preparations to (re)create the test material

    final static String TC_DIR  = "..\\..\\Desktop";
    final static String TC_NAME = "1.tc";

    public static void main(String[] ignored) throws Exception {
        // mount 10MB .TC (password:123), then run this ...
        //final String ROOT = "V:\\";
        //fillDisk(ROOT, "nau"); // either
        //fillDisk(ROOT, "nad"); // or
        //createReceipt(ROOT, RCP_PATH);
        //createReceipt(ROOT, RCP_PATH_DE);
        // unmount .TC, then run one of these
        //extractDisk(IMG_PATH);
        //extractDisk(IMG_PATH_DE);
    }

    static void extractDisk(String imgPath) throws Exception {
        File dir = new File(TC_DIR);
        byte[] blk = new byte[512];
        TCReader tcr = new TCReader(
            new BlockDeviceImpl.MemoryBlockDevice(
                blk.length,   // size of .tc was 5242880 bytes
                IOUtils.readStreamBytes(new FileInputStream(new File(dir, "1.tc"))),
                true,
                false),
            new Key.ByteArray("123".getBytes()),
            false,
            false);
        OutputStream os = new GZIPOutputStream(new FileOutputStream(imgPath));
        for (long num = 0; num < tcr.size(); num++) {
            tcr.read(num, blk, 0);
            os.write(blk);
        }
        os.close();
        tcr.close(false);
    }

    final static String RES_PATH    = "./test/coderslagoon/trupax/lib/resources/";
    final static String RCP_PATH    = RES_PATH + "fatreader_test.freceipt";
    final static String RCP_PATH_DE = RES_PATH + "fatreader_test_DE.freceipt";
    final static String IMG_PATH    = RES_PATH + "fatreader_test.gz";
    final static String IMG_PATH_DE = RES_PATH + "fatreader_test_DE.gz";

    static void fillDisk(String root, String spec) throws Exception {
        final String[] args = VERIFIER_ARGS.clone();
        args[0] = root;
        args[3] = spec;
        Verifier.main(args);
    }

    static void createReceipt(String root, String rcpPath) throws Exception {
        FReceipt fr = new FReceipt();
        new File(rcpPath).delete();
        FReceipt._resetReceipt = true;
        fr.exec(new String[] { root, rcpPath });
    }

    final static String[] VERIFIER_ARGS = new String[] {
        null,
        "1000000",
        "3800000",
        null
    };
}
