package coderslagoon.trupax.lib.io.filesystem.udf;

import java.util.Arrays;


import coderslagoon.baselib.util.BinUtils;
import coderslagoon.baselib.util.BytePtr;

public abstract class AllocationDescriptor {
    public enum ExtentType {
        RECORDED_AND_ALLOCATED,
        NOT_RECORDED_BUT_ALLOCATED,
        NOT_RECORDED_AND_NOT_ALLOCATED,
        NEXT_EXTENT_OF_ALLOCATION_DESCRIPTOPS;
    };

    public final static int MAX_LENGTH = 0x3fffffff;

    public final static int maxLength(final int blockSize) {
        return MAX_LENGTH - (MAX_LENGTH % blockSize);
    }

    public int        length;
    public ExtentType type;

    public int ctor(byte[] buf, int ofs) throws UDFException {
        int extLen = BinUtils.readInt32LE(buf, ofs);

        this.length = extLen & MAX_LENGTH;
        this.type   = ExtentType.values()[extLen >>> 30];

        if (0 == this.length &&
            ExtentType.RECORDED_AND_ALLOCATED != this.type) {
            throw new UDFException("illegal extent length 0x%08x in SAD", extLen);
        }
        return ofs + 4;
    }

    public int write(byte[] buf, int ofs) throws UDFException {
        if (0 > this.length || this.length > MAX_LENGTH) {
            throw new UDFException("illegal AD length %d", this.length);
        }

        int extLen = this.length | (this.type.ordinal() << 30);

        BinUtils.writeInt32LE(extLen, buf, ofs); ofs += 4;

        return ofs;
    }

    public static class Short extends AllocationDescriptor { // aka short_ad
        public final static int LENGTH = 8;

        public int position;

        public static Short parse(byte[] buf, int ofs) throws UDFException {
            Short result = new Short();

            ofs = result.ctor(buf, ofs);

            result.position = BinUtils.readInt32LE(buf, ofs);

            return result;
        }

        public int write(byte[] buf, int ofs) throws UDFException {
            ofs = super.write(buf, ofs);

            BinUtils.writeInt32LE(this.position, buf, ofs); ofs += 4;

            return ofs;
        }

        public final static AllocationDescriptor.Short ZERO;
        static {
            ZERO = new AllocationDescriptor.Short();
            ZERO.type = ExtentType.RECORDED_AND_ALLOCATED;
        }

        public String toString() {
            return String.format("SAD:len=%s,typ=%s,p=%s",
                    BinUtils.u32ToLng(this.length),
                    this.type,
                    BinUtils.u32ToLng(this.position));
        }
    }

    ///////////////////////////////////////////////////////////////////////////

    public static class Long extends AllocationDescriptor { // aka long_ad
        public final static int LENGTH = 16;

        public RecordedAddress location;
        public BytePtr         implementationUse;

        public final static int IMPL_USE_LEN = 6;

        public final static AllocationDescriptor.Long ZERO;
        static {
            ZERO = new AllocationDescriptor.Long();
            ZERO.location          = RecordedAddress.ZERO;
            ZERO.type              = ExtentType.RECORDED_AND_ALLOCATED;
            ZERO.implementationUse = null;
        }

        public static Long parse(byte[] buf, int ofs) throws UDFException {
            Long result = new Long();

            ofs = result.ctor(buf, ofs);

            result.location          = RecordedAddress.parse(buf, ofs); ofs += RecordedAddress.LENGTH;
            result.implementationUse = new BytePtr.Checked(buf, ofs, IMPL_USE_LEN);

            return result;
        }

        public int write(byte[] buf, int ofs) throws UDFException {
            ofs = super.write(buf, ofs);

            this.location.write(buf, ofs); ofs += RecordedAddress.LENGTH;
            if (null == this.implementationUse) {
                Arrays.fill(buf, ofs, ofs + IMPL_USE_LEN, (byte)0);
                ofs += IMPL_USE_LEN;
            }
            else {
                this.implementationUse.write(buf, ofs);
            }

            return ofs;
        }

        public BytePtr data() throws UDFException {
            byte[] result = new byte[LENGTH];
            write(result, 0);
            return new BytePtr(result);
        }

        public String toString() {
            return String.format("LAD:len=%s,typ=%s,ra=[%s]",
                    BinUtils.u32ToLng(this.length),
                    this.type,
                    this.location);
        }
    }
}
