package coderslagoon.trupax.lib.io.filesystem.udf;

import coderslagoon.baselib.util.BytePtr;

public class PartitionHeaderDescriptor {
    public AllocationDescriptor.Short unallocatedSpaceTable;
    public AllocationDescriptor.Short unallocatedSpaceBitmap;
    public AllocationDescriptor.Short partitionIntegrityTable;
    public AllocationDescriptor.Short freedSpaceTable;
    public AllocationDescriptor.Short freedSpaceBitmap;
    public BytePtr                    reserved;

    public final static int LENGTH   = 128;
    public final static int RESV_LEN = 88;

    public PartitionHeaderDescriptor() {
    }

    public PartitionHeaderDescriptor(byte[] buf, int ofs) throws UDFException {
        this.unallocatedSpaceTable   = AllocationDescriptor.Short.parse(buf, ofs); ofs += AllocationDescriptor.Short.LENGTH;
        this.unallocatedSpaceBitmap  = AllocationDescriptor.Short.parse(buf, ofs); ofs += AllocationDescriptor.Short.LENGTH;
        this.partitionIntegrityTable = AllocationDescriptor.Short.parse(buf, ofs); ofs += AllocationDescriptor.Short.LENGTH;
        this.freedSpaceTable         = AllocationDescriptor.Short.parse(buf, ofs); ofs += AllocationDescriptor.Short.LENGTH;
        this.freedSpaceBitmap        = AllocationDescriptor.Short.parse(buf, ofs); ofs += AllocationDescriptor.Short.LENGTH;
        this.reserved                = new BytePtr(buf, ofs, RESV_LEN);
    }

    public String toString() {
        return String.format("PHD:ust=[%s],usb=[%s],pit=[%s],fst=[%s],fsb=[%s]",
                this.unallocatedSpaceTable,
                this.unallocatedSpaceBitmap,
                this.partitionIntegrityTable,
                this.freedSpaceTable,
                this.freedSpaceBitmap);
    }

    public void write(byte[] buf, int ofs) throws UDFException {
        ofs = this.unallocatedSpaceTable  .write(buf, ofs);
        ofs = this.unallocatedSpaceBitmap .write(buf, ofs);
        ofs = this.partitionIntegrityTable.write(buf, ofs);
        ofs = this.freedSpaceTable        .write(buf, ofs);
        ofs = this.freedSpaceBitmap       .write(buf, ofs);
        if (null != this.reserved) {
            this.reserved.write(buf, ofs);
        }
    }
}
