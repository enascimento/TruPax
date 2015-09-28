package coderslagoon.trupax.lib.io.filesystem.udf;

import java.util.Arrays;

import coderslagoon.baselib.util.BinUtils;
import coderslagoon.baselib.util.BytePtr;

public class FileSetDescriptor extends Descriptor {
    public Timestamp                 recordingDateAndTime;
    public short                     interchangeLevel;
    public short                     maximumInterchangeLevel;
    public int                       characterSetList;
    public int                       maximumCharacterSetList;
    public int                       fileSetNumber;
    public int                       fileSetDescriptorNumber;
    public CharacterSet              logicalVolumeIdentifierCharacterSet;
    public String                    logicalVolumeIdentifier;
    public CharacterSet              fileSetCharacterSet;
    public String                    fileSetIdentifier;
    public String                    copyrightFileIdentifier;
    public String                    abstractFileIdentifier;
    public AllocationDescriptor.Long rootDirectoryICB;
    public EntityIdentifier          domainIdentifier;
    public AllocationDescriptor.Long nextExtent;
    public AllocationDescriptor.Long systemStreamDirectoryICB;
    public BytePtr                   reserved;

    public final static int LOG_VOL_ID_LEN  = 128;
    public final static int FILE_SET_ID_LEN = 32;
    public final static int CR_FILE_ID_LEN  = 32;
    public final static int ABS_FILE_ID_LEN = 32;
    public final static int RSV_LEN         = 48 - AllocationDescriptor.Long.LENGTH;

    public FileSetDescriptor(int location) {
        super(new Tag(Tag.Identifier.FILE_SET_DESCRIPTOR, location));
    }

    protected FileSetDescriptor(Tag tag, byte[] buf, int ofs) throws UDFException {
        super(tag);

        this.recordingDateAndTime                = Timestamp.parse(buf, ofs);                 ofs += Timestamp.LENGTH;
        this.interchangeLevel                    = BinUtils.readInt16LE(buf, ofs);            ofs += 2;
        this.maximumInterchangeLevel             = BinUtils.readInt16LE(buf, ofs);            ofs += 2;
        this.characterSetList                    = BinUtils.readInt32LE(buf, ofs);            ofs += 4;
        this.maximumCharacterSetList             = BinUtils.readInt32LE(buf, ofs);            ofs += 4;
        this.fileSetNumber                       = BinUtils.readInt32LE(buf, ofs);            ofs += 4;
        this.fileSetDescriptorNumber             = BinUtils.readInt32LE(buf, ofs);            ofs += 4;
        this.logicalVolumeIdentifierCharacterSet = CharacterSet.parse(buf, ofs);              ofs += CharacterSet.LENGTH;
        this.logicalVolumeIdentifier             = DString.read(buf, ofs, LOG_VOL_ID_LEN);    ofs += LOG_VOL_ID_LEN;
        this.fileSetCharacterSet                 = CharacterSet.parse(buf, ofs);              ofs += CharacterSet.LENGTH;
        this.fileSetIdentifier                   = DString.read(buf, ofs, FILE_SET_ID_LEN);   ofs += FILE_SET_ID_LEN;
        this.copyrightFileIdentifier             = DString.read(buf, ofs, CR_FILE_ID_LEN);    ofs += CR_FILE_ID_LEN;
        this.abstractFileIdentifier              = DString.read(buf, ofs, ABS_FILE_ID_LEN);   ofs += ABS_FILE_ID_LEN;
        this.rootDirectoryICB                    = AllocationDescriptor.Long.parse(buf, ofs); ofs += AllocationDescriptor.Long.LENGTH;
        this.domainIdentifier                    = EntityIdentifier.parse(buf, ofs);          ofs += EntityIdentifier.LENGTH;
        this.nextExtent                          = AllocationDescriptor.Long.parse(buf, ofs); ofs += AllocationDescriptor.Long.LENGTH;
        this.systemStreamDirectoryICB            = AllocationDescriptor.Long.parse(buf, ofs); ofs += AllocationDescriptor.Long.LENGTH;
        this.reserved                            = new BytePtr(buf, ofs, RSV_LEN);
    }

    public int write(byte[] buf, int ofs) throws UDFException {
        int ofs0 = ofs;
        ofs += Tag.LENGTH;

        this.recordingDateAndTime               .write       (                              buf, ofs                 ); ofs += Timestamp.LENGTH;
        BinUtils                                .writeInt16LE(this.interchangeLevel       , buf, ofs                 ); ofs += 2;
        BinUtils                                .writeInt16LE(this.maximumInterchangeLevel, buf, ofs                 ); ofs += 2;
        BinUtils                                .writeInt32LE(this.characterSetList       , buf, ofs                 ); ofs += 4;
        BinUtils                                .writeInt32LE(this.maximumCharacterSetList, buf, ofs                 ); ofs += 4;
        BinUtils                                .writeInt32LE(this.fileSetNumber          , buf, ofs                 ); ofs += 4;
        BinUtils                                .writeInt32LE(this.fileSetDescriptorNumber, buf, ofs                 ); ofs += 4;
        this.logicalVolumeIdentifierCharacterSet.write       (                              buf, ofs                 ); ofs += CharacterSet.LENGTH;
        DString                                 .write       (this.logicalVolumeIdentifier, buf, ofs, LOG_VOL_ID_LEN ); ofs += LOG_VOL_ID_LEN;
        this.fileSetCharacterSet                .write       (                              buf, ofs                 ); ofs += CharacterSet.LENGTH;
        DString                                 .write       (this.fileSetIdentifier      , buf, ofs, FILE_SET_ID_LEN); ofs += FILE_SET_ID_LEN;
        DString                                 .write       (this.copyrightFileIdentifier, buf, ofs, CR_FILE_ID_LEN ); ofs += CR_FILE_ID_LEN;
        DString                                 .write       (this.abstractFileIdentifier , buf, ofs, ABS_FILE_ID_LEN); ofs += ABS_FILE_ID_LEN;
        this.rootDirectoryICB                   .write       (                              buf, ofs                 ); ofs += AllocationDescriptor.Long.LENGTH;
        this.domainIdentifier                   .write       (                              buf, ofs                 ); ofs += EntityIdentifier.LENGTH;
        this.nextExtent                         .write       (                              buf, ofs                 ); ofs += AllocationDescriptor.Long.LENGTH;
        this.systemStreamDirectoryICB           .write       (                              buf, ofs                 ); ofs += AllocationDescriptor.Long.LENGTH;

        if (null == this.reserved) Arrays.fill        (buf, ofs, ofs + RSV_LEN, (byte)0);
        else                       this.reserved.write(buf, ofs);
        ofs += RSV_LEN;

        this.tag.write(buf, ofs0, ofs - ofs0);

        return ofs;
    }

    public String toString() {
        return String.format(
             "FSD:tag=[%s],rdt=[%s],il=%s,mil=%s,csl=%s,mcsl=%s,fsn=%s,fsdn=%s," +
             "lvics=[%s],lvi=%s,fscs=[%s],fsi=%s,cfi=%s,afi=%s,rdicb=[%s]," +
             "di=[%s],ne=[%s],ssdicb=[%s]",
            this.tag,
            this.recordingDateAndTime,
            this.interchangeLevel,
            this.maximumInterchangeLevel,
            this.characterSetList,
            this.maximumCharacterSetList,
            this.fileSetNumber,
            this.fileSetDescriptorNumber,
            this.logicalVolumeIdentifierCharacterSet,
            this.logicalVolumeIdentifier,
            this.fileSetCharacterSet,
            this.fileSetIdentifier,
            this.copyrightFileIdentifier,
            this.abstractFileIdentifier,
            this.rootDirectoryICB,
            this.domainIdentifier,
            this.nextExtent,
            this.systemStreamDirectoryICB);
    }
}
