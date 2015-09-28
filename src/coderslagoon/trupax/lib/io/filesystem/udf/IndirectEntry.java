package coderslagoon.trupax.lib.io.filesystem.udf;

public class IndirectEntry extends Descriptor {
    public ICBTag                    icbTag;
    public AllocationDescriptor.Long indirectICB;

    public IndirectEntry(Tag tag, byte[] buf, int ofs) throws UDFException {
        super(tag);

        this.icbTag      = ICBTag.parse(buf, ofs); ofs += ICBTag.LENGTH;
        this.indirectICB = AllocationDescriptor.Long.parse(buf, ofs);
    }

    public String toString() {
        return String.format("IE:tag=[%s],it=[%s],iicb=[%s]",
                this.tag,
                this.icbTag,
                this.indirectICB);
    }
}
