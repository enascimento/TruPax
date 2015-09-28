package coderslagoon.trupax.lib.io.filesystem.udf;

import coderslagoon.baselib.util.BinUtils;

public class AllocationExtentDescriptor extends Descriptor {
    public int previousAllocationExtentLocation;
    public int lengthOfAllocationDescriptors;

    protected AllocationExtentDescriptor(Tag tag, byte[] buf, int ofs) {
        super(tag);

        this.previousAllocationExtentLocation = BinUtils.readInt32LE(buf, ofs); ofs += 4;
        this.lengthOfAllocationDescriptors    = BinUtils.readInt32LE(buf, ofs);
    }

    public String toString() {
        return String.format("AED:tag=[%s],pael=%s,lad=%s",
                this.tag,
                BinUtils.u32ToLng(this.previousAllocationExtentLocation),
                BinUtils.u32ToLng(this.lengthOfAllocationDescriptors));
    }
}
