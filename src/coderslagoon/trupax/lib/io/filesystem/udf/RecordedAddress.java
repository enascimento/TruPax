package coderslagoon.trupax.lib.io.filesystem.udf;

import coderslagoon.baselib.util.BinUtils;

public class RecordedAddress { // aka lb_addr
    public final static int LENGTH = 6;

    public int   logicalBlockNumber;
    public short partitionReferenceNumber;

    public static RecordedAddress ZERO = new RecordedAddress(0, (short)0);

    RecordedAddress() {
    }

    public RecordedAddress(int logicalBlockNumber, short partitionReferenceNumber) {
        this.logicalBlockNumber       = logicalBlockNumber;
        this.partitionReferenceNumber = partitionReferenceNumber;
    }

    public static RecordedAddress parse(byte[] data, int ofs) {
        RecordedAddress result = new RecordedAddress();

        result.logicalBlockNumber       = BinUtils.readInt32LE(data, ofs);
        result.partitionReferenceNumber = BinUtils.readInt16LE(data, ofs + 4);

        return result;
    }

    public void write(byte[] buf, int ofs) {
        BinUtils.writeInt32LE(this.logicalBlockNumber      , buf, ofs);
        BinUtils.writeInt16LE(this.partitionReferenceNumber, buf, ofs + 4);
    }

    public String toString() {
        return String.format("RA:lbn=%s,prn=%s",
                BinUtils.u32ToLng(this.logicalBlockNumber),
                BinUtils.u16ToInt(this.partitionReferenceNumber));
    }
}
