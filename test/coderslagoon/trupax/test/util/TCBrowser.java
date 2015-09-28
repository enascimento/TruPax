package coderslagoon.trupax.test.util;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;



import coderslagoon.baselib.io.BlockDeviceImpl;
import coderslagoon.tclib.util.Key;
import coderslagoon.tclib.util.Password;
import coderslagoon.tclib.util.TCLibException;
import coderslagoon.trupax.lib.io.filesystem.udf.Browser;
import coderslagoon.trupax.tc.TCReader;

public class TCBrowser extends Browser {
    public  final TCReader tcr;
    private final RandomAccessFile raf;

    public TCBrowser(File volume, Listener listener, int blockSz, String passw) throws IOException, TCLibException {
        this(volume, listener, blockSz, new Password(passw.toCharArray(), null));
    }

    public TCBrowser(File volume, Listener listener, int blockSz, Key key) throws IOException, TCLibException {
        this.raf = new RandomAccessFile(volume, "r");

        BlockDeviceImpl.FileBlockDevice fbd = new
        BlockDeviceImpl.FileBlockDevice(this.raf, blockSz, -1L, true, false);

        this.tcr = new TCReader(fbd, key, false, false);

        super.init(this.tcr, listener);
    }

    public void close() throws IOException {
        this.tcr.close(false);
        this.raf.close();
    }
}
