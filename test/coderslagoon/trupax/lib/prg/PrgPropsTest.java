package coderslagoon.trupax.lib.prg;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;


import coderslagoon.tclib.crypto.Registry;
import coderslagoon.trupax.lib.prg.PrgProps;
import coderslagoon.trupax.lib.prg.Prg.PropertyInfo;

public class PrgPropsTest {
    @Before
    public void setup() throws Exception {
        Registry.setup(false);
    }

    @Test
    public void test0() throws Exception {
        PrgProps p = new PrgProps();

        PropertyInfo pinf = p.getInfo("trupax.prg.recursivesearch");
        assertNotNull(pinf);
        assertTrue(pinf.type == PropertyInfo.Type.FLAG);

        assertNull(p.getInfo("doesnotexist"));

        pinf = p.getInfo("trupax.prg.blockcipher");
        assertNotNull(pinf);
        assertTrue(pinf.type == PropertyInfo.Type.SELECT);
        assertTrue(0 < pinf.selection.length);
        assertTrue(pinf.selection[0].equals("AES256"));
    }
}
