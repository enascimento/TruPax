package coderslagoon.trupax.lib.io.filesystem.udf;

import coderslagoon.baselib.util.BinUtils;
import coderslagoon.baselib.util.BytePtr;

public class PrimaryVolumeDescriptor extends Descriptor {
    public final static int IMPL_USE_LEN = 64;
    public final static int RESV_LEN     = 22;

    public final static int FLAG_VSI_COMMON = 1;

    ///////////////////////////////////////////////////////////////////////////

    public int              volumeDescriptorSequenceNumber;
    public int              primaryVolumeDescriptorNumber;
    public String           volumeIdentifier;
    public short            volumeSequenceNumber;
    public short            maximumVolumeSequenceNumber;
    public short            interchangeLevel;
    public short            maximumInterchangeLevel;
    public int              characterSetList;
    public int              maximumCharacterSetList;
    public String           volumeSetIdentifier;
    public CharacterSet     descriptorCharacterSet;
    public CharacterSet     explanatoryCharacterSet;
    public ExtentDescriptor volumeAbstract;
    public ExtentDescriptor volumeCopyrightNotice;
    public EntityIdentifier applicationIdentifier;
    public Timestamp        recordingDateAndTime;
    public EntityIdentifier implementationIdentifier;
    public BytePtr          implementationUse;
    public int              predecessorSequenceLocation;
    public short            flags;
    public BytePtr          reserved;

    ///////////////////////////////////////////////////////////////////////////

    public boolean isVolumeSetIdentificationCommon() {
        return 1 == (this.flags & 1);
    }

    ///////////////////////////////////////////////////////////////////////////

    public final static int VOL_ID_LEN = 32;

    final static int VOL_SET_ID_LEN = 128;

    ///////////////////////////////////////////////////////////////////////////

    public PrimaryVolumeDescriptor(int location) {
        super(new Tag(Tag.Identifier.PRIMARY_VOLUME_DESCRIPTOR, location));
    }

    ///////////////////////////////////////////////////////////////////////////

    public PrimaryVolumeDescriptor(Tag tag, byte[] buf, int ofs) throws UDFException {
        super(tag);

        this.volumeDescriptorSequenceNumber = BinUtils.readInt32LE(buf, ofs);              ofs += 4;
        this.primaryVolumeDescriptorNumber  = BinUtils.readInt32LE(buf, ofs);              ofs += 4;
        this.volumeIdentifier               = DString.read(buf, ofs, VOL_ID_LEN);          ofs += VOL_ID_LEN;
        this.volumeSequenceNumber           = BinUtils.readInt16LE(buf, ofs);              ofs += 2;
        this.maximumVolumeSequenceNumber    = BinUtils.readInt16LE(buf, ofs);              ofs += 2;
        this.interchangeLevel               = BinUtils.readInt16LE(buf, ofs);              ofs += 2;
        this.maximumInterchangeLevel        = BinUtils.readInt16LE(buf, ofs);              ofs += 2;
        this.characterSetList               = BinUtils.readInt32LE(buf, ofs);              ofs += 4;
        this.maximumCharacterSetList        = BinUtils.readInt32LE(buf, ofs);              ofs += 4;
        this.volumeSetIdentifier            = DString.read(buf, ofs, VOL_SET_ID_LEN);      ofs += VOL_SET_ID_LEN;
        this.descriptorCharacterSet         = CharacterSet.parse(buf, ofs);                ofs += CharacterSet.LENGTH;
        this.explanatoryCharacterSet        = CharacterSet.parse(buf, ofs);                ofs += CharacterSet.LENGTH;
        this.volumeAbstract                 = ExtentDescriptor.parse(buf, ofs);            ofs += ExtentDescriptor.LENGTH;
        this.volumeCopyrightNotice          = ExtentDescriptor.parse(buf, ofs);            ofs += ExtentDescriptor.LENGTH;
        this.applicationIdentifier          = EntityIdentifier.parse(buf, ofs);            ofs += EntityIdentifier.LENGTH;
        this.recordingDateAndTime           = Timestamp.parse(buf, ofs);                   ofs += Timestamp.LENGTH;
        this.implementationIdentifier       = EntityIdentifier.parse(buf, ofs);            ofs += EntityIdentifier.LENGTH;
        this.implementationUse              = new BytePtr.Checked(buf, ofs, IMPL_USE_LEN); ofs += IMPL_USE_LEN;
        this.predecessorSequenceLocation    = BinUtils.readInt32LE(buf, ofs);              ofs += 4;
        this.flags                          = BinUtils.readInt16LE(buf, ofs);              ofs += 2;
        this.reserved                       = new BytePtr.Checked(buf, ofs, RESV_LEN);
    }

    public int write(byte[] block, int ofs) throws UDFException {
        int ofs0 = ofs;
        ofs += Tag.LENGTH;

        BinUtils                     .writeInt32LE(this.volumeDescriptorSequenceNumber, block, ofs);                 ofs += 4;
        BinUtils                     .writeInt32LE(this.primaryVolumeDescriptorNumber , block, ofs);                 ofs += 4;
        DString                      .write       (this.volumeIdentifier              , block, ofs, VOL_ID_LEN);     ofs += VOL_ID_LEN;
        BinUtils                     .writeInt16LE(this.volumeSequenceNumber          , block, ofs);                 ofs += 2;
        BinUtils                     .writeInt16LE(this.maximumVolumeSequenceNumber   , block, ofs);                 ofs += 2;
        BinUtils                     .writeInt16LE(this.interchangeLevel              , block, ofs);                 ofs += 2;
        BinUtils                     .writeInt16LE(this.maximumInterchangeLevel       , block, ofs);                 ofs += 2;
        BinUtils                     .writeInt32LE(this.characterSetList              , block, ofs);                 ofs += 4;
        BinUtils                     .writeInt32LE(this.maximumCharacterSetList       , block, ofs);                 ofs += 4;
        DString                      .write       (this.volumeSetIdentifier           , block, ofs, VOL_SET_ID_LEN); ofs += VOL_SET_ID_LEN;
        this.descriptorCharacterSet  .write       (                                     block, ofs);                 ofs += CharacterSet.LENGTH;
        this.explanatoryCharacterSet .write       (                                     block, ofs);                 ofs += CharacterSet.LENGTH;
        this.volumeAbstract          .write       (                                     block, ofs);                 ofs += ExtentDescriptor.LENGTH;
        this.volumeCopyrightNotice   .write       (                                     block, ofs);                 ofs += ExtentDescriptor.LENGTH;
        this.applicationIdentifier   .write       (                                     block, ofs);                 ofs += EntityIdentifier.LENGTH;
        this.recordingDateAndTime    .write       (                                     block, ofs);                 ofs += Timestamp.LENGTH;
        this.implementationIdentifier.write       (                                     block, ofs);                 ofs += EntityIdentifier.LENGTH;
        this.implementationUse       .write       (                                     block, ofs);                 ofs += IMPL_USE_LEN;
        BinUtils                     .writeInt32LE(this.predecessorSequenceLocation   , block, ofs);                 ofs += 4;
        BinUtils                     .writeInt16LE(this.flags                         , block, ofs);                 ofs += 2;
        this.reserved                .write       (                                     block, ofs);                 ofs += RESV_LEN;

        this.tag.write(block, ofs0, ofs - ofs0);

        return ofs;
    }

    public String toString() {
        return String.format(
                "PVD:tag=[%s],vdsn=%s,pvdn=%s,vid='%s',vsn=%s,mvsn=%s,il=%s," +
                "mil=%s,csl=%s,mcsl=%s,vsi=%s,dcs=[%s],ecs=[%s],va=[%s],vcn=[%s],ai=[%s]," +
                "rdt=[%s],iid=%s,psl=%s,f=0x04%x",
                this.tag,
                BinUtils.u32ToLng(this.volumeDescriptorSequenceNumber),
                BinUtils.u32ToLng(this.primaryVolumeDescriptorNumber),
                this.volumeIdentifier,
                BinUtils.u16ToInt(this.volumeSequenceNumber),
                BinUtils.u16ToInt(this.maximumVolumeSequenceNumber),
                BinUtils.u16ToInt(this.interchangeLevel),
                BinUtils.u16ToInt(this.maximumInterchangeLevel),
                BinUtils.u32ToLng(this.characterSetList),
                BinUtils.u32ToLng(this.maximumCharacterSetList),
                this.volumeSetIdentifier,
                this.descriptorCharacterSet,
                this.explanatoryCharacterSet,
                this.volumeAbstract,
                this.volumeCopyrightNotice,
                this.applicationIdentifier,
                this.recordingDateAndTime,
                this.implementationIdentifier,
                BinUtils.u32ToLng(this.predecessorSequenceLocation),
                this.flags);
    }
}
