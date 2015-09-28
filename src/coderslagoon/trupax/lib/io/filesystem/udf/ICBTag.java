package coderslagoon.trupax.lib.io.filesystem.udf;

import coderslagoon.baselib.util.BinUtils;
import coderslagoon.baselib.util.BytePtr;

public class ICBTag {
    public final static int LENGTH = 20;

    public final static short STRATEGY_NO          = 0;
    public final static short STRATEGY_UDF102      = 4;
    public final static short STRATEGY_UDF102_WORM = 4096;

    public final static BytePtr SPARAMS_ZERO = new BytePtr(new byte[2]);

    public enum FileType {
        NOT_SPECIFIED           (0),
        UNALLOCATED_SPACE_ENTRY (1),
        PARTITION_INTEGRY_ENTRY (2),
        INDIRECT_ENTRY          (3),
        DIRECTORY               (4),
        RANDOM_ACCESS_BYTE_SEQ  (5),
        BLOCK_SPECIAL_DEVICE    (6),
        CHARACTER_SPECIAL_DEVICE(7),
        EXTENDED_ATTRIBUTES     (8),
        FIFO                    (9),
        C_ISSOCK                (10),
        TERMINAL_ENTRY          (11),
        SYMBOLIC_LINK           (12),
        STREAM_DIRECTORY        (13);

        FileType(int code) {
            this.code = (byte)code;
        }
        public final byte code;

        public static FileType fromCode(byte code) {
            return _map.get(code);
        }
        static java.util.HashMap<Byte, FileType> _map = new java.util.HashMap<>();
        static {
            for (FileType ft : values()) {
                _map.put(ft.code, ft);
            }
        }
    }

    public final static int FLAG_DIRSORT     = 8;
    public final static int FLAG_NONRELOC    = 16;
    public final static int FLAG_ARCHIVE     = 32;
    public final static int FLAG_SETUID      = 64;
    public final static int FLAG_SETGID      = 128;
    public final static int FLAG_STICKY      = 256;
    public final static int FLAG_CONTIGUOUS  = 512;
    public final static int FLAG_SYSTEM      = 1024;
    public final static int FLAG_TRANSFORMED = 2048;
    public final static int FLAG_MULTIVER    = 4096;
    public final static int FLAG_STREAM      = 16384;

    public int             priorRecordedNumberOfDirectEntries;
    public short           strategyType;
    public BytePtr         strategyParameter;
    public short           maximumNumberOfEntries;
    public byte            reserved;
    public FileType        fileType;
    public RecordedAddress parentICBLocation;
    public short           flags;

    public final static int STRAT_PARAM_LEN = 2;

    public static ICBTag parse(byte[] buf, int ofs) {
        ICBTag result = new ICBTag();

        result.priorRecordedNumberOfDirectEntries = BinUtils.readInt32LE(buf, ofs);                 ofs += 4;
        result.strategyType                       = BinUtils.readInt16LE(buf, ofs);                 ofs += 2;
        result.strategyParameter                  = new BytePtr.Checked(buf, ofs, STRAT_PARAM_LEN); ofs += STRAT_PARAM_LEN;
        result.maximumNumberOfEntries             = BinUtils.readInt16LE(buf, ofs);                 ofs += 2;
        result.reserved                           = buf[ofs];                                       ofs++;
        result.fileType                           = FileType.fromCode(buf[ofs]);                    ofs++;
        result.parentICBLocation                  = RecordedAddress.parse(buf, ofs);                ofs += RecordedAddress.LENGTH;
        result.flags                              = BinUtils.readInt16LE(buf, ofs);

        return result;
    }

    public final static int ALLOCDESC_SHORT    = 0;
    public final static int ALLOCDESC_LONG     = 1;
    public final static int ALLOCDESC_EXTENDED = 2;
    public final static int ALLOCDESC_EMBEDDED = 3;

    public int allocDescriptor() {
        return 7 & this.flags;
    }

    public void write(byte[] buf, int ofs) {
        BinUtils.writeInt32LE(this.priorRecordedNumberOfDirectEntries,  buf, ofs); ofs += 4;
        BinUtils.writeInt16LE(this.strategyType                       , buf, ofs); ofs += 2;
                              this.strategyParameter.write             (buf, ofs); ofs += STRAT_PARAM_LEN;
        BinUtils.writeInt16LE(this.maximumNumberOfEntries             , buf, ofs); ofs += 2;
                  buf [ofs] = this.reserved;                                       ofs++;
                  buf [ofs] = this.fileType.code;                                  ofs++;
                              this.parentICBLocation.write             (buf, ofs); ofs += RecordedAddress.LENGTH;
        BinUtils.writeInt16LE(this.flags                              , buf, ofs);
    }

    public String toString() {
        return String.format("ICBTAG:prnde=%s,st=%s,sp=[%d,%d],mne=%s,r=%d,ft=%s,pil=[%s],f=0x%04x",
                BinUtils.u32ToLng(this.priorRecordedNumberOfDirectEntries),
                BinUtils.u16ToInt(this.strategyType),
                BinUtils.u8ToInt(this.strategyParameter.at(0)),
                BinUtils.u8ToInt(this.strategyParameter.at(1)),
                BinUtils.u16ToInt(this.maximumNumberOfEntries),
                BinUtils.u8ToInt(this.reserved),
                this.fileType,
                this.parentICBLocation,
                this.flags);
    }
}
