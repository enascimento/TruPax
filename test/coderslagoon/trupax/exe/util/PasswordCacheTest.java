package coderslagoon.trupax.exe.util;

import java.util.Random;

import org.junit.Assert;
import org.junit.Test;

import coderslagoon.trupax.exe.util.PasswordCache;

public class PasswordCacheTest {

    @Test
    public void testPasswordCache() {
        PasswordCache pc = new PasswordCache();
        Assert.assertNull(pc.get());
        pc.clear();
        Assert.assertNull(pc.get());
        pc.set("");
        Assert.assertEquals(pc.get(), "");
        Assert.assertEquals(pc.get(), "");
        pc.clear();
        Assert.assertNull(pc.get());
        Random rnd = new Random(0xccddeeff00112233L);
        for (int len : new int[] { 0, 1, 3, 4, 5, 7, 8, 9, 14, 15, 16, 23, 24, 25, 1023, 49137 }) {
            char[] passwb = new char[len];
            for (int i = 0; i < passwb.length; i++) {
                passwb[i] = (char)((rnd.nextInt() & 4095) + ' ');
            }
            String passw = new String(passwb);
            Assert.assertEquals(len, passw.length());
            Assert.assertNull(pc.get());
            pc.set(passw);
            String passw2 = pc.get();
            Assert.assertEquals(passw2, passw);
            Assert.assertTrue(passw2 != passw);
            pc.clear();
        }
    }
}
