package coderslagoon.trupax.lib.io.filesystem.udf;

import coderslagoon.baselib.util.BinUtils;
import coderslagoon.baselib.util.BytePtr;

public class PartitionDescriptor extends Descriptor {
    public static short FLAG_VOLUME_SPACE_ALLOCATED = 0x0001;

    public int                       volumeDescriptorSequenceNumber;
    public short                     partitionFlags;
    public short                     partitionNumber;
    public EntityIdentifier          partitionContents;
    public PartitionHeaderDescriptor partitionContentsUse;
    public AccessType                accessType;
    public int                       partitionStartingLocation;
    public int                       partitionLength;
    public EntityIdentifier          implementationIdentifier;
    public BytePtr                   implementationUse;
    public BytePtr                   reserved;

    public final static int PART_CONT_USE_LEN = 128;
    public final static int IMPL_USE_LEN = 128;
    public final static int RESV_USE_LEN = 156;

    public enum AccessType {
        NOT_SPECIFIED,
        READ_ONLY,
        WRITE_ONCE,
        REWRITABLE,
        OVERWRITABLE
    }

    public PartitionDescriptor(int location) {
        super(new Tag(Tag.Identifier.PARTITION_DESCRIPTOR, location));
    }

    protected PartitionDescriptor(Tag tag, byte[] buf, int ofs) throws UDFException {
        super(tag);

        this.volumeDescriptorSequenceNumber = BinUtils.readInt32LE(buf, ofs);                      ofs += 4;
        this.partitionFlags                 = BinUtils.readInt16LE(buf, ofs);                      ofs += 2;
        this.partitionNumber                = BinUtils.readInt16LE(buf, ofs);                      ofs += 2;
        this.partitionContents              = EntityIdentifier.parse(buf, ofs);                    ofs += EntityIdentifier.LENGTH;
        this.partitionContentsUse           = new PartitionHeaderDescriptor(buf, ofs);             ofs += PART_CONT_USE_LEN;
        this.accessType                     = AccessType.values()[BinUtils.readInt32LE(buf, ofs)]; ofs += 4;
        this.partitionStartingLocation      = BinUtils.readInt32LE(buf, ofs);                      ofs += 4;
        this.partitionLength                = BinUtils.readInt32LE(buf, ofs);                      ofs += 4;
        this.implementationIdentifier       = EntityIdentifier.parse(buf, ofs);                    ofs += EntityIdentifier.LENGTH;
        this.implementationUse              = new BytePtr.Checked(buf, ofs, IMPL_USE_LEN);         ofs += IMPL_USE_LEN;
        this.reserved                       = new BytePtr.Checked(buf, ofs, RESV_USE_LEN);
    }

    public int write(byte[] block, int ofs) throws UDFException {
        int ofs0 = ofs;
        ofs += Tag.LENGTH;

        BinUtils                     .writeInt32LE(this.volumeDescriptorSequenceNumber, block, ofs);         ofs += 4;
        BinUtils                     .writeInt16LE(this.partitionFlags                , block, ofs);         ofs += 2;
        BinUtils                     .writeInt16LE(this.partitionNumber               , block, ofs);         ofs += 2;
        this.partitionContents       .write       (                                     block, ofs);         ofs += EntityIdentifier.LENGTH;
        this.partitionContentsUse    .write       (                                     block, ofs);         ofs += PART_CONT_USE_LEN;
        BinUtils                     .writeInt32LE(this.accessType.ordinal()          , block, ofs);         ofs += 4;
        BinUtils                     .writeInt32LE(this.partitionStartingLocation     , block, ofs);         ofs += 4;
        BinUtils                     .writeInt32LE(this.partitionLength               , block, ofs);         ofs += 4;
        this.implementationIdentifier.write       (                                     block, ofs);         ofs += EntityIdentifier.LENGTH;
        this.implementationUse       .write       (                                     block, ofs);         ofs += IMPL_USE_LEN;
        this.reserved                .write       (                                     block, ofs);         ofs += RESV_USE_LEN;

        this.tag.write(block, ofs0, ofs - ofs0);

        return ofs;
    }

    public String toString() {
        return String.format("PD:tag=[%s],vdsn=%s,pf=0x%04x,pn=%s,pc=[%s],pcu=[%s],at=%s,psl=%s,pl=%s,ii=[%s]",
                this.tag,
                BinUtils.u32ToLng(this.volumeDescriptorSequenceNumber),
                this.partitionFlags,
                BinUtils.u16ToInt(this.partitionNumber),
                this.partitionContents,
                this.partitionContentsUse,
                this.accessType,
                BinUtils.u32ToLng(this.partitionStartingLocation),
                BinUtils.u32ToLng(this.partitionLength),
                this.implementationIdentifier);
    }
}
