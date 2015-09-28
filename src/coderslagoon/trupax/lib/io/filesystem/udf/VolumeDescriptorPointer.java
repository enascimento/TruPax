package coderslagoon.trupax.lib.io.filesystem.udf;

import coderslagoon.baselib.util.BinUtils;
import coderslagoon.baselib.util.BytePtr;

public class VolumeDescriptorPointer extends Descriptor {
    public int              volumeDescriptorSequenceNumber;
    public ExtentDescriptor nextVolumeDescriptorSequenceExtent;
    public BytePtr          reserved;

    public final static int RESV_LEN = 484;

    protected VolumeDescriptorPointer(Tag tag, byte[] buf, int ofs) throws UDFException {
        super(tag);

        this.volumeDescriptorSequenceNumber     = BinUtils.readInt32LE(buf, ofs);   ofs += 4;
        this.nextVolumeDescriptorSequenceExtent = ExtentDescriptor.parse(buf, ofs); ofs += ExtentDescriptor.LENGTH;
        this.reserved                           = new BytePtr.Checked(buf, ofs, RESV_LEN);
    }

    public String toString() {
        return String.format("VDP:tag=[%s],vdsn=%s,nvdse=[%s]",
                this.tag,
                this.volumeDescriptorSequenceNumber,
                this.nextVolumeDescriptorSequenceExtent);
    }
}
