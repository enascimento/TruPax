package coderslagoon.trupax.lib.io.filesystem.udf;

public class TerminalEntry extends Descriptor {
    public ICBTag icbTag;

    public TerminalEntry(Tag tag, byte[] buf, int ofs) throws UDFException {
        super(tag);

        this.icbTag = ICBTag.parse(buf, ofs);

        if (this.icbTag.fileType != ICBTag.FileType.TERMINAL_ENTRY) {
            throw new UDFException(
                    "terminal entry has a wrong ICB tag file type (%s)",
                    this.icbTag.fileType);
        }
    }

    public String toString() {
        return String.format("TE:tag=[%s],it=[%s]",
                this.tag,
                this.icbTag);
    }
}
