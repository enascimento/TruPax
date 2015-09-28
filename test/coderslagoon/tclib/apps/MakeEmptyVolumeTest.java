package coderslagoon.tclib.apps;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.security.SecureRandom;
import org.junit.Test;

import coderslagoon.tclib.container.Header;
import coderslagoon.trupax.sdk.apps.MakeEmptyVolume;

public class MakeEmptyVolumeTest {

    @Test
    public void testMain() throws Exception{
        File vol = new File(
            System.getProperty("java.io.tmpdir"),
            String.format("empty_%d_%08x.tc",
                System.currentTimeMillis(), new SecureRandom().nextInt()));
        final long SIZE = 10240000;
        String[] args = new String[] {
            vol.getAbsolutePath(),
            String.valueOf(SIZE),
            "abc123"
        };
        MakeEmptyVolume.ExitCode ec = MakeEmptyVolume._main(args);
        assertEquals(MakeEmptyVolume.ExitCode.SUCCESS, ec);
        assertTrue(vol.exists());
        assertEquals(Header.BLOCK_COUNT * Header.BLOCK_SIZE + SIZE, vol.length());
        assertTrue(vol.delete());
    }
}
