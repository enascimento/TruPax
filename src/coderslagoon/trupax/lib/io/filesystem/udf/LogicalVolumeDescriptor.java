package coderslagoon.trupax.lib.io.filesystem.udf;

import coderslagoon.baselib.util.BinUtils;
import coderslagoon.baselib.util.BytePtr;

public class LogicalVolumeDescriptor extends Descriptor {
    public int              volumeDescriptorSequenceNumber;
    public CharacterSet     descriptorCharacterSet;
    public String           logicalVolumeIdentifier;
    public int              logicalBlockSize;
    public EntityIdentifier domainIdentifier;
    public BytePtr          logicalVolumeContentsUse;
    public int              mapTableLength;
    public int              numberOfPartitionMaps;
    public EntityIdentifier implementationIdentifier;
    public BytePtr          implementationUse;
    public ExtentDescriptor integritySequenceExtent;
    public BytePtr          partitionMaps;

    public final static int CONTENT_USE_LEN = 16;
    public final static int ID_LEN          = 128;
    public final static int IMPL_USE_LEN    = 128;

    public LogicalVolumeDescriptor(int location) {
        super(new Tag(Tag.Identifier.LOGICAL_VOLUME_DESCRIPTOR, location));
    }

    protected LogicalVolumeDescriptor(Tag tag, byte[] buf, int ofs) throws UDFException {
        super(tag);

        this.volumeDescriptorSequenceNumber = BinUtils.readInt32LE(buf, ofs);                 ofs += 4;
        this.descriptorCharacterSet         = CharacterSet.parse(buf, ofs);                   ofs += CharacterSet.LENGTH;
        this.logicalVolumeIdentifier        = DString.read(buf, ofs, ID_LEN);                 ofs += ID_LEN;
        this.logicalBlockSize               = BinUtils.readInt32LE(buf, ofs);                 ofs += 4;
        this.domainIdentifier               = EntityIdentifier.parse(buf, ofs);               ofs += EntityIdentifier.LENGTH;
        this.logicalVolumeContentsUse       = new BytePtr.Checked(buf, ofs, CONTENT_USE_LEN); ofs += CONTENT_USE_LEN;
        this.mapTableLength                 = BinUtils.readInt32LE(buf, ofs);                 ofs += 4;
        this.numberOfPartitionMaps          = BinUtils.readInt32LE(buf, ofs);                 ofs += 4;
        this.implementationIdentifier       = EntityIdentifier.parse(buf, ofs);               ofs += EntityIdentifier.LENGTH;
        this.implementationUse              = new BytePtr.Checked(buf, ofs, IMPL_USE_LEN);    ofs += IMPL_USE_LEN;
        this.integritySequenceExtent        = ExtentDescriptor.parse(buf, ofs);               ofs += ExtentDescriptor.LENGTH;
        this.partitionMaps                  = new BytePtr.Checked(buf, ofs, this.mapTableLength);

        if (!this.partitionMaps.isValid()) {
            throw new UDFException("partition map data range is invalid");
        }
    }

    public int write(byte[] block, int ofs) throws UDFException {
        int ofs0 = ofs;
        ofs += Tag.LENGTH;

        BinUtils                     .writeInt32LE(this.volumeDescriptorSequenceNumber, block, ofs);         ofs += 4;
        this.descriptorCharacterSet  .write       (                                     block, ofs);         ofs += CharacterSet.LENGTH;
        DString                      .write       (this.logicalVolumeIdentifier       , block, ofs, ID_LEN); ofs += ID_LEN;
        BinUtils                     .writeInt32LE(this.logicalBlockSize              , block, ofs);         ofs += 4;
        this.domainIdentifier        .write       (                                     block, ofs);         ofs += EntityIdentifier.LENGTH;
        this.logicalVolumeContentsUse.write       (                                     block, ofs);         ofs += CONTENT_USE_LEN;
        BinUtils                     .writeInt32LE(this.mapTableLength                , block, ofs);         ofs += 4;
        BinUtils                     .writeInt32LE(this.numberOfPartitionMaps         , block, ofs);         ofs += 4;
        this.implementationIdentifier.write       (                                     block, ofs);         ofs += EntityIdentifier.LENGTH;
        this.implementationUse       .write       (                                     block, ofs);         ofs += IMPL_USE_LEN;
        this.integritySequenceExtent .write       (                                     block, ofs);         ofs += ExtentDescriptor.LENGTH;
        this.partitionMaps           .write       (                                     block, ofs);         ofs += this.mapTableLength;

        this.tag.write(block, ofs0, ofs - ofs0);

        return ofs;
    }

    public String toString() {
        return String.format(
                "LVD:tag=[%s],vdsn=%s,dcs=[%s],lvid=%s,lbs=%s,di=[%s],mtl=%s,nopm=%s,iid=[%s],ise=[%s]",
                this.tag,
                BinUtils.u32ToLng(this.volumeDescriptorSequenceNumber),
                this.descriptorCharacterSet,
                this.logicalVolumeIdentifier,
                BinUtils.u32ToLng(this.logicalBlockSize),
                this.domainIdentifier,
                BinUtils.u32ToLng(this.mapTableLength),
                BinUtils.u32ToLng(this.numberOfPartitionMaps),
                this.implementationIdentifier,
                this.integritySequenceExtent);
    }
}
