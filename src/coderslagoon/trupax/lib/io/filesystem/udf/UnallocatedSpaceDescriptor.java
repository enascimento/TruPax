package coderslagoon.trupax.lib.io.filesystem.udf;

import coderslagoon.baselib.util.BinUtils;

public class UnallocatedSpaceDescriptor extends Descriptor {
    public int                volumeDescriptorSequenceNumber;
    public ExtentDescriptor[] allocationDescriptors;

    public UnallocatedSpaceDescriptor(int location) {
        super(new Tag(Tag.Identifier.UNALLOCATED_SPACE_DESCRIPTOR, location));
    }

    public UnallocatedSpaceDescriptor(Tag tag, byte[] buf, int ofs) throws UDFException {
        super(tag);

        this.volumeDescriptorSequenceNumber = BinUtils.readInt32LE(buf, ofs); ofs += 4;

        int noad = BinUtils.readInt32LE(buf, ofs); ofs += 4;

        this.allocationDescriptors = new ExtentDescriptor[noad];
        for (int i = 0; i < noad; i++) {
            this.allocationDescriptors[i] = ExtentDescriptor.parse(buf, ofs);
            ofs += ExtentDescriptor.LENGTH;
        }
    }

    public int write(byte[] block, int ofs) throws UDFException {
        int ofs0 = ofs;
        ofs += Tag.LENGTH;

        BinUtils.writeInt32LE(this.volumeDescriptorSequenceNumber, block, ofs); ofs += 4;
        BinUtils.writeInt32LE(this.allocationDescriptors.length  , block, ofs); ofs += 4;

        for (ExtentDescriptor ad : this.allocationDescriptors) {
            ad.write(block, ofs);
            ofs += ExtentDescriptor.LENGTH;
        }

        this.tag.write(block, ofs0, ofs - ofs0);

        return ofs;
    }

    public String toString() {
        StringBuilder result = new StringBuilder();

        result.append(String.format("UASD:tag=[%s],vdsn=%s,nad=%s,ad=",
                this.tag,
                BinUtils.u32ToLng(this.volumeDescriptorSequenceNumber),
                this.allocationDescriptors.length));

        for (int i = 0; i < this.allocationDescriptors.length; i++) {
            result.append('[');
            result.append(this.allocationDescriptors[i]);
            result.append(']');
        }

        return result.toString();
    }
}
