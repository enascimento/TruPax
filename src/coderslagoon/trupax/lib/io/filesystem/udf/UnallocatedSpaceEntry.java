package coderslagoon.trupax.lib.io.filesystem.udf;

import coderslagoon.baselib.util.BinUtils;
import coderslagoon.baselib.util.BytePtr;

public class UnallocatedSpaceEntry extends Descriptor {
    public ICBTag  icbTag;
    public int     lengthOfAllocationDescriptors;
    public BytePtr allocationDescriptors;

    public UnallocatedSpaceEntry(Tag tag, byte[] buf, int ofs) {
        super(tag);
        this.icbTag                        = ICBTag.parse(buf, ofs);         ofs += ICBTag.LENGTH;
        this.lengthOfAllocationDescriptors = BinUtils.readInt32LE(buf, ofs); ofs += 4;
        this.allocationDescriptors         = new BytePtr.Checked(buf, ofs, this.lengthOfAllocationDescriptors);
    }

    public String toString() {
        return String.format("USE:tag=[%s],it=[%s],lad=%s",
                this.tag,
                this.icbTag,
                BinUtils.u32ToLng(this.lengthOfAllocationDescriptors));
    }
}
