package coderslagoon.trupax.lib.io.filesystem.udf;

import coderslagoon.baselib.util.BinUtils;

public class SpaceBitmapDescriptor extends Descriptor {
    public int numberOfBits;
    public int numberOfBytes;

    public SpaceBitmapDescriptor(int location) {
        super(new Tag(Tag.Identifier.SPACE_BITMAP_DESCRIPTOR, location));
    }

    public SpaceBitmapDescriptor(Tag tag, byte[] buf, int ofs) throws UDFException {
        super(tag);

        this.numberOfBits  = BinUtils.readInt32LE(buf, ofs); ofs += 4;
        this.numberOfBytes = BinUtils.readInt32LE(buf, ofs); ofs += 4;

        if ((BinUtils.u32ToLng(this.numberOfBits) + 7L) / 8L >
             BinUtils.u32ToLng(this.numberOfBytes)) {
            throw new UDFException("number of bytes too low");
        }
    }

    public final static int LENGTH = Tag.LENGTH + 4 + 4;

    public int write(byte[] block, int ofs) {
        int ofs0 = ofs;
        ofs += Tag.LENGTH;

        BinUtils.writeInt32LE(this.numberOfBits , block, ofs); ofs += 4;
        BinUtils.writeInt32LE(this.numberOfBytes, block, ofs); ofs += 4;

        this.tag.write(block, ofs0, ofs - ofs0);

        return ofs;
    }

    public String toString() {
        return String.format("SBD:tag=[%s],nbits=%s,nbytes=%s",
                this.tag,
                BinUtils.u32ToLng(this.numberOfBits),
                BinUtils.u32ToLng(this.numberOfBytes));
    }
}
