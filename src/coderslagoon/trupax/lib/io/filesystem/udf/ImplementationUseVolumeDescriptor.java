package coderslagoon.trupax.lib.io.filesystem.udf;

import coderslagoon.baselib.util.BinUtils;
import coderslagoon.baselib.util.BytePtr;

public class ImplementationUseVolumeDescriptor extends Descriptor {
    public int              volumeDescriptorSequenceNumber;
    public EntityIdentifier implementationIdentifier;
    public CharacterSet     lviCharset;
    public String           logicalVolumeIdentifier;
    public String           lvInfo1;
    public String           lvInfo2;
    public String           lvInfo3;
    public EntityIdentifier implementationID;
    public BytePtr          implementationUse;

    final static int LOG_VOL_ID_LEN = 128;
    final static int LV_INFO_LEN    = 36;

    public final static int IMPL_USE_LEN = 128;

    public ImplementationUseVolumeDescriptor(int location) {
        super(new Tag(Tag.Identifier.IMPLEMENTATION_USE_VOLUME_DESCRIPTOR, location));
    }

    protected ImplementationUseVolumeDescriptor(Tag tag, byte[] buf, int ofs) throws UDFException {
        super(tag);

        this.volumeDescriptorSequenceNumber = BinUtils.readInt32LE  (buf, ofs                ); ofs += 4;
        this.implementationIdentifier       = EntityIdentifier.parse(buf, ofs                ); ofs += EntityIdentifier.LENGTH;
        this.lviCharset                     = CharacterSet.parse    (buf, ofs                ); ofs += CharacterSet.LENGTH;
        this.logicalVolumeIdentifier        = DString.read          (buf, ofs, LOG_VOL_ID_LEN); ofs += LOG_VOL_ID_LEN;
        this.lvInfo1                        = DString.read          (buf, ofs, LV_INFO_LEN   ); ofs += LV_INFO_LEN;
        this.lvInfo2                        = DString.read          (buf, ofs, LV_INFO_LEN   ); ofs += LV_INFO_LEN;
        this.lvInfo3                        = DString.read          (buf, ofs, LV_INFO_LEN   ); ofs += LV_INFO_LEN;
        this.implementationID               = EntityIdentifier.parse(buf, ofs                ); ofs += EntityIdentifier.LENGTH;
        this.implementationUse              = new BytePtr.Checked   (buf, ofs, IMPL_USE_LEN);

        // FIXME: should we check that the charset is the right one?
    }

    public int write(byte[] block, int ofs) throws UDFException {
        int ofs0 = ofs;
        ofs += Tag.LENGTH;

        BinUtils                     .writeInt32LE(this.volumeDescriptorSequenceNumber, block, ofs                ); ofs += 4;
        this.implementationIdentifier.write       (                                     block, ofs                ); ofs += EntityIdentifier.LENGTH;
        this.lviCharset              .write       (                                     block, ofs                ); ofs += CharacterSet.LENGTH;
        DString                      .write       (this.logicalVolumeIdentifier       , block, ofs, LOG_VOL_ID_LEN); ofs += LOG_VOL_ID_LEN;
        DString                      .write       (this.lvInfo1                       , block, ofs, LV_INFO_LEN   ); ofs += LV_INFO_LEN;
        DString                      .write       (this.lvInfo2                       , block, ofs, LV_INFO_LEN   ); ofs += LV_INFO_LEN;
        DString                      .write       (this.lvInfo3                       , block, ofs, LV_INFO_LEN   ); ofs += LV_INFO_LEN;
        this.implementationID        .write       (                                     block, ofs                ); ofs += EntityIdentifier.LENGTH;
        this.implementationUse       .write       (                                     block, ofs                ); ofs += IMPL_USE_LEN;

        this.tag.write(block, ofs0, ofs - ofs0);

        return ofs;
    }

    public String toString() {
        return String.format("IUVD:tag=[%s],vdsn=%s,ii=[%s],lcs=[%s],lvi=%s,li1=%s,li2=%s,li3=%s,iid=[%s]",
                this.tag,
                BinUtils.u32ToLng(this.volumeDescriptorSequenceNumber),
                this.implementationIdentifier,
                this.lviCharset,
                this.logicalVolumeIdentifier,
                this.lvInfo1,
                this.lvInfo2,
                this.lvInfo3,
                this.implementationID);
    }
}
