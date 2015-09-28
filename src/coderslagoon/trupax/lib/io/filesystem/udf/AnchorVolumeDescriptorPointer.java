package coderslagoon.trupax.lib.io.filesystem.udf;

import java.util.Arrays;

import coderslagoon.baselib.util.BytePtr;

// TODO: make sure this gets written to two locations, as specified!

public class AnchorVolumeDescriptorPointer extends Descriptor {
    public final static long LOCATION = 256L;

    final static int RESV_LEN = 480;

    public ExtentDescriptor mainVolumeDescriptorSequence;
    public ExtentDescriptor reserveVolumeDescriptorSequence;
    public BytePtr          reserved;

    public AnchorVolumeDescriptorPointer(int location) {
        super(new Tag(Tag.Identifier.ANCHOR_VOLUME_DESCRIPTOR_POINTER, location));
    }

    AnchorVolumeDescriptorPointer(Tag tag, byte[] buf, int ofs) throws UDFException {
        super(tag);

        this.mainVolumeDescriptorSequence    = ExtentDescriptor.parse(buf, ofs); ofs += ExtentDescriptor.LENGTH;
        this.reserveVolumeDescriptorSequence = ExtentDescriptor.parse(buf, ofs); ofs += ExtentDescriptor.LENGTH;

        this.reserved = new BytePtr.Checked(buf, ofs, 480);
    }

    public int write(byte[] block, int ofs) throws UDFException {
        int ofs0 = ofs;
        ofs += Tag.LENGTH;

        this.mainVolumeDescriptorSequence   .write(block, ofs); ofs += ExtentDescriptor.LENGTH;
        this.reserveVolumeDescriptorSequence.write(block, ofs); ofs += ExtentDescriptor.LENGTH;
        Arrays.fill(block, ofs, ofs + RESV_LEN, (byte)0);       ofs += RESV_LEN;

        this.tag.write(block, ofs0, ofs - ofs0);

        return ofs;
    }

    public String toString() {
        return String.format("AVDP:tag=[%s],mvds=[%s],rvds=[%s]",
                this.tag,
                this.mainVolumeDescriptorSequence,
                this.reserveVolumeDescriptorSequence);
    }
}
