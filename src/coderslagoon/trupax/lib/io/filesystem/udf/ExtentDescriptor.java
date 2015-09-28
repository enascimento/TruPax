package coderslagoon.trupax.lib.io.filesystem.udf;

import coderslagoon.baselib.util.BinUtils;

public class ExtentDescriptor {   // aka extent_ad
    public final static int LENGTH = 8;

    public final static ExtentDescriptor NONE = new ExtentDescriptor(0, 0);

    public final static int MAX_LENGTH = 0x3fffffff;

    public final int length;
    public final int location;

    public ExtentDescriptor(int length, int location) {
        this.length   = length;
        this.location = location;
    }

    public static void checkLength(int length) throws UDFException {
        if (0 != (length & 0xc0000000)) {
            throw new UDFException(
                   "invalid extend descriptor length 0x%08x", length);
        }
    }

    public static ExtentDescriptor parse(byte[] buf, int ofs) throws UDFException {
        ExtentDescriptor result = new ExtentDescriptor(
            BinUtils.readInt32LE(buf, ofs),
            BinUtils.readInt32LE(buf, ofs + 4));

        checkLength(result.length);

        return result;
    }

    public boolean none() {
        return 0 == this.location;
    }

    public void write(byte[] buf, int ofs) throws UDFException {
        checkLength(this.length);
        BinUtils.writeInt32LE(this.length  , buf, ofs);
        BinUtils.writeInt32LE(this.location, buf, ofs + 4);
    }

    public String toString() {
        return String.format("ED:len=%d,loc=%d",
                BinUtils.u32ToLng(this.length),
                BinUtils.u32ToLng(this.location));
    }
}
