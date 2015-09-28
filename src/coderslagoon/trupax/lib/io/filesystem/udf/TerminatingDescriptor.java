package coderslagoon.trupax.lib.io.filesystem.udf;

import coderslagoon.baselib.util.BytePtr;

public class TerminatingDescriptor  extends Descriptor {
    public BytePtr reserved;

    public final static int RESV_LEN = 496;

    public TerminatingDescriptor(int location) {
        super(new Tag(Tag.Identifier.TERMINATING_DESCRIPTOR, location));
    }

    protected TerminatingDescriptor(Tag tag, byte[] buf, int ofs) {
        super(tag);

        this.reserved = new BytePtr.Checked(buf, ofs, RESV_LEN);
    }

    public int write(byte[] block, int ofs) {
        int ofs0 = ofs;
        ofs += Tag.LENGTH;

        this.reserved.write(block, ofs); ofs += RESV_LEN;

        this.tag.write(block, ofs0, ofs - ofs0);

        return ofs;
    }

    public String toString() {
        return String.format("TD:tag=[%s]", this.tag);
    }
}
