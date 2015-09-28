package coderslagoon.tclib.container;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.InputStream;

import org.junit.Before;
import org.junit.Test;

import coderslagoon.baselib.io.IOUtils;
import coderslagoon.baselib.util.BinUtils;
import coderslagoon.tclib.container.Header;
import coderslagoon.tclib.container.Volume;
import coderslagoon.tclib.crypto.AES256;
import coderslagoon.tclib.crypto.BlockCipher;
import coderslagoon.tclib.crypto.RIPEMD160;
import coderslagoon.tclib.crypto.Registry;
import coderslagoon.tclib.crypto.SHA512;
import coderslagoon.tclib.util.Key;

public class VolumeTest {
    
    final static String SOME_TEXT = "GALLIA est omnis divisa in partes tres";
    
    @Before
    public void setUp() throws Exception {
        Registry.setup(false);
    }
    
    @Test
    public void testMinVol() throws Exception {

        InputStream ins = getClass().getResourceAsStream("resources/minvol.tc");
        byte[] minvol = IOUtils.readStreamBytes(ins);

        final int SCT_SIZE = 512;
        final int IMG_SIZE = 281600;
        final int VOL_SIZE = IMG_SIZE - Header.SIZE * 2;

        assertTrue(IMG_SIZE == minvol.length);

        Header hdr = new Header(new Key.ByteArray("123".getBytes()),
                minvol, 0, false);

        assertEquals(hdr.blockCipher   , AES256.class);
        assertEquals(hdr.hashFunction  , RIPEMD160.class);
        assertEquals(hdr.sizeofVolume  , VOL_SIZE);
        assertEquals(hdr.dataAreaSize  , VOL_SIZE);
        assertEquals(hdr.dataAreaOffset, Header.SIZE);

        Volume vol0 = new Volume(BlockCipher.Mode.DECRYPT, hdr);
        Volume vol = (Volume)vol0.clone();
        vol0.erase();

        assertTrue(SCT_SIZE == vol.blockSize());
        assertTrue(0 == IMG_SIZE % vol.blockSize());

        long no = Header.BLOCK_COUNT;
        long end = no + (VOL_SIZE / vol.blockSize());

        System.out.printf("decrypting %d blocks...\n", end - no);

        for (; no < end; no++) {
            int ofs = (int)no * vol.blockSize();

            vol.processBlock(no, minvol, ofs);
        }

        assertTrue(BinUtils.arraysEquals(
                minvol,
                (int)(end - 2) * SCT_SIZE,
                SOME_TEXT.getBytes(),
                0,
                SOME_TEXT.length()));

        final byte[] INIT_DATA = BinUtils.hexStrToBytes("eb3c904d53444f53");

        assertTrue(BinUtils.arraysEquals(
                minvol,
                Header.SIZE,
                INIT_DATA,
                0,
                INIT_DATA.length));

        long csz = Volume.sizeToContainerSize(1000);
        assertTrue(1000 < csz);
    }

    @Test
    public void testMinVolVeraCrypt() throws Exception {

        InputStream ins = getClass().getResourceAsStream("resources/minvol.hc");
        byte[] minvol = IOUtils.readStreamBytes(ins);

        final int SCT_SIZE = 512;
        final int IMG_SIZE = 299008 ;
        final int VOL_SIZE = IMG_SIZE - Header.SIZE * 2;

        assertTrue(IMG_SIZE == minvol.length);

        Header hdr = new Header(new Key.ByteArray("12345".getBytes()),
                minvol, 0, true);

        assertEquals(hdr.blockCipher   , AES256.class);
        assertEquals(hdr.hashFunction  , SHA512.class);
        assertEquals(hdr.sizeofVolume  , VOL_SIZE);
        assertEquals(hdr.dataAreaSize  , VOL_SIZE);
        assertEquals(hdr.dataAreaOffset, Header.SIZE);
        assertEquals(hdr.flags         , 0);

        Volume vol0 = new Volume(BlockCipher.Mode.DECRYPT, hdr);
        Volume vol = (Volume)vol0.clone();
        vol0.erase();

        assertTrue(SCT_SIZE == vol.blockSize());
        assertTrue(0 == IMG_SIZE % vol.blockSize());

        long no = Header.BLOCK_COUNT;
        long end = no + (VOL_SIZE / vol.blockSize());

        System.out.printf("decrypting %d blocks...\n", end - no);

        for (; no < end; no++) {
            int ofs = (int)no * vol.blockSize();
            vol.processBlock(no, minvol, ofs);
        }
        
        assertTrue(BinUtils.arraysEquals(
                minvol,
                (int)(end - 17) * SCT_SIZE,
                SOME_TEXT.getBytes(),
                0,
                SOME_TEXT.length()));

        final byte[] INIT_DATA = BinUtils.hexStrToBytes("eb3c904d53444f53");

        assertTrue(BinUtils.arraysEquals(
                minvol,
                Header.SIZE,
                INIT_DATA,
                0,
                INIT_DATA.length));

        long csz = Volume.sizeToContainerSize(1000);
        assertTrue(1000 < csz);
    }
}
