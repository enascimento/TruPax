package coderslagoon.trupax.lib.io.filesystem.udf;

import coderslagoon.baselib.util.BinUtils;

public class ExtendedAttributeHeaderDescriptor extends Descriptor {
    public int implementationAttributesLocation;
    public int applicationAttributesLocation;

    public final static int LENGTH = Tag.LENGTH + 8;

    public ExtendedAttributeHeaderDescriptor(Tag tag, byte[] buf, int ofs) {
        super(tag);

        this.implementationAttributesLocation = BinUtils.readInt32LE(buf, ofs); ofs += 4;
        this.applicationAttributesLocation    = BinUtils.readInt32LE(buf, ofs);
    }

    public String toString() {
        return String.format("EAHD:tag=[%s],ial=%d,aal=%d",     // (ial and aal can be negative?)
                this.tag,
                this.implementationAttributesLocation,
                this.applicationAttributesLocation);
    }
}
