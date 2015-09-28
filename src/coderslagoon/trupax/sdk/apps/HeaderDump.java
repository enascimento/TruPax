package coderslagoon.trupax.sdk.apps;

import java.io.File;
import java.io.PrintStream;
import java.io.RandomAccessFile;
import java.util.Arrays;

import coderslagoon.baselib.util.BinUtils;
import coderslagoon.tclib.container.Header;
import coderslagoon.tclib.crypto.Registry;
import coderslagoon.tclib.util.Key;

/** To dump header information of a volume file. */
public class HeaderDump {
    
    static void dumpHeader(byte[] data, String passw) throws Exception {
        Header h = new Header(
                new Key.ByteArray(passw.getBytes()),
                data, 0, true);
        PrintStream out = System.out;
        out.printf("block cipher         : %s\n", h.blockCipher);
        out.printf("data area offset     : %d\n", h.dataAreaOffset);
        out.printf("data area size       : %d\n", h.dataAreaSize);
        out.printf("flags                : %d\n", h.flags);
        out.printf("hash function        : %s\n", h.hashFunction);
        out.printf("hidden volume header : %s\n", h.hiddenVolumeHeader);
        out.printf("key material         : %s\n", BinUtils.bytesToHexString(h.keyMaterial));
        out.printf("minimum version      : %s\n", h.minimumVersion);
        out.printf("reserved             : %s\n", BinUtils.bytesToHexString(h.reserved));
        out.printf("reserved2            : %s\n", BinUtils.bytesToHexString(h.reserved2));
        out.printf("reserved3            : %s\n", h.reserved3);
        out.printf("salt                 : %s\n", BinUtils.bytesToHexString(h.salt));
        out.printf("size of hidden volume: %d\n", h.sizeofHiddenVolume);
        out.printf("size of of volume    : %d\n", h.sizeofVolume);
        out.printf("version              : %s\n", h.version);
    }
    
    static void exec(File vol, String passw) throws Exception {
        final int HEADER_SIZE = Header.BLOCK_SIZE * Header.BLOCK_COUNT;
        byte[] data = new byte[HEADER_SIZE];
        RandomAccessFile raf = new RandomAccessFile(vol, "r");
        raf.readFully(data);
        System.out.println("file: " + vol.getAbsolutePath());
        System.out.println("____PRIMARY____");
        dumpHeader(data, passw);
        Arrays.fill(data, (byte)0);
        raf.seek(raf.length() - HEADER_SIZE);
        raf.readFully(data);
        System.out.println("____BACKUP____");
        dumpHeader(data, passw);
        raf.close();
    }
    
    public static void main(String[] args) throws Exception {

        if (2 != args.length) {
            System.err.println("usage: HeaderDump [volume] [password]");
            return;
        }
        try {
            Registry.setup(false);
            exec(new File(args[0]), args[1]);
        }
        catch (Throwable err) {
            err.printStackTrace();
        }
    }
}

