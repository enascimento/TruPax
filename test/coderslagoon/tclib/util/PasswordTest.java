package coderslagoon.tclib.util;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import coderslagoon.baselib.util.BinUtils;
import coderslagoon.tclib.util.Key;
import coderslagoon.tclib.util.Password;

public class PasswordTest {
    @Test
    public void test0() throws Exception {
        Password p = new Password("123".toCharArray(), null);

        byte[] enc = p.data();

        assertNotNull(enc);
        assertTrue(BinUtils.arraysEquals("123".getBytes(), enc));

        final String NOASCII = "h\u00e4user";

        p.erase();
        p = new Password(NOASCII.toCharArray(), null);

        enc = p.data();

        assertTrue(enc.length == NOASCII.length());
        assertTrue(BinUtils.arraysEquals(NOASCII.getBytes("ISO-8859-1"), enc));

        p.erase();
        p = new Password(NOASCII.toCharArray(), "UTF-8");

        enc = p.data();

        assertTrue(enc.length != NOASCII.length());
        assertTrue(BinUtils.arraysEquals(NOASCII.getBytes("UTF-8"), enc));

        p.erase();

        try {
            p.data();
            fail();
        }
        catch (Key.ErasedException kee) {
        }
    }
}
