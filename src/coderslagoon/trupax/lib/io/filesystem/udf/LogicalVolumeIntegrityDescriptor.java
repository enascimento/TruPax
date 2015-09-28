package coderslagoon.trupax.lib.io.filesystem.udf;

import coderslagoon.baselib.util.BinUtils;
import coderslagoon.baselib.util.BytePtr;

public class LogicalVolumeIntegrityDescriptor extends Descriptor {
    public Timestamp        recordingDateAndTime;
    public int              integrityType;
    public ExtentDescriptor nextIntegrityExtent;
    public BytePtr          logicalVolumeContentsUse;
    public int              numberOfPartitions;
    public int              lengthOfImplementationUse;
    public int[]            freeSpaceTable;
    public int[]            sizeTable;
    public EntityIdentifier entityID;
    public int              numberOfFiles;
    public int              numberOfDirectories;
    public short            minimumUDFReadRevision;
    public short            minimumUDFWriteRevision;
    public short            maximumUDFWriteRevision;
    public BytePtr          implementationUse;

    public final static int LOG_VOL_CONT_USE_LEN = 32;
    public final static int UDF_IMPL_USE_LEN     = 46;

    public final static int TYPE_OPEN  = 0;
    public final static int TYPE_CLOSE = 1;

    public LogicalVolumeIntegrityDescriptor(int location) {
        super(new Tag(Tag.Identifier.LOGICAL_VOLUME_INTEGRITY_DESCRIPTOR, location));
    }

    public LogicalVolumeIntegrityDescriptor(Tag tag, byte[] buf, int ofs) throws UDFException {
        super(tag);

        int p;

        this.recordingDateAndTime      = Timestamp.parse(buf, ofs);                             ofs += Timestamp.LENGTH;
        this.integrityType             = BinUtils.readInt32LE(buf, ofs);                        ofs += 4;
        this.nextIntegrityExtent       = ExtentDescriptor.parse(buf, ofs);                      ofs += ExtentDescriptor.LENGTH;
        this.logicalVolumeContentsUse  = new BytePtr.Checked(buf, ofs, LOG_VOL_CONT_USE_LEN);   ofs += LOG_VOL_CONT_USE_LEN;
        this.numberOfPartitions    = p = BinUtils.readInt32LE(buf, ofs);                        ofs += 4;
        this.lengthOfImplementationUse = BinUtils.readInt32LE(buf, ofs);                        ofs += 4;

        if (UDF_IMPL_USE_LEN > this.lengthOfImplementationUse) {
            throw new UDFException("length of implementation use (%d) too small", this.lengthOfImplementationUse);
        }

        this.freeSpaceTable            = BinUtils.IntRW.LE.readInt32Array(buf, ofs, p);         ofs += p << 2;
        this.sizeTable                 = BinUtils.IntRW.LE.readInt32Array(buf, ofs, p);         ofs += p << 2;
        this.entityID                  = EntityIdentifier.parse(buf, ofs);                      ofs += EntityIdentifier.LENGTH;
        this.numberOfFiles             = BinUtils.readInt32LE(buf, ofs);                        ofs += 4;
        this.numberOfDirectories       = BinUtils.readInt32LE(buf, ofs);                        ofs += 4;
        this.minimumUDFReadRevision    = BinUtils.readInt16LE(buf, ofs);                        ofs += 2;
        this.minimumUDFWriteRevision   = BinUtils.readInt16LE(buf, ofs);                        ofs += 2;
        this.maximumUDFWriteRevision   = BinUtils.readInt16LE(buf, ofs);                        ofs += 2;
        this.implementationUse         = new BytePtr.Checked(buf, ofs, this.lengthOfImplementationUse - UDF_IMPL_USE_LEN);
    }

    public int write(byte[] block, int ofs) throws UDFException {
        int ofs0 = ofs;
        ofs += Tag.LENGTH;

        this.recordingDateAndTime    .write          (                                block, ofs); ofs += Timestamp.LENGTH;
        BinUtils                     .writeInt32LE   (this.integrityType            , block, ofs); ofs += 4;
        this.nextIntegrityExtent     .write          (                                block, ofs); ofs += ExtentDescriptor.LENGTH;
        this.logicalVolumeContentsUse.write          (                                block, ofs); ofs += LOG_VOL_CONT_USE_LEN;
        BinUtils                     .writeInt32LE   (this.numberOfPartitions       , block, ofs); ofs += 4;
        BinUtils                     .writeInt32LE   (this.lengthOfImplementationUse, block, ofs); ofs += 4;
        BinUtils.IntRW.LE            .writeInt32Array(this.freeSpaceTable           , block, ofs); ofs += this.numberOfPartitions << 2;
        BinUtils.IntRW.LE            .writeInt32Array(this.sizeTable                , block, ofs); ofs += this.numberOfPartitions << 2;
        this.entityID                .write          (                                block, ofs); ofs += EntityIdentifier.LENGTH;
        BinUtils                     .writeInt32LE   (this.numberOfFiles            , block, ofs); ofs += 4;
        BinUtils                     .writeInt32LE   (this.numberOfDirectories      , block, ofs); ofs += 4;
        BinUtils                     .writeInt16LE   (this.minimumUDFReadRevision   , block, ofs); ofs += 2;
        BinUtils                     .writeInt16LE   (this.minimumUDFWriteRevision  , block, ofs); ofs += 2;
        BinUtils                     .writeInt16LE   (this.maximumUDFWriteRevision  , block, ofs); ofs += 2;
        this.implementationUse       .write          (                                block, ofs); ofs += this.lengthOfImplementationUse - UDF_IMPL_USE_LEN;

        this.tag.write(block, ofs0, ofs - ofs0);

        return ofs;
    }

    public String toString() {
        StringBuilder result = new StringBuilder();

        result.append(String.format("LVID:tag=[%s],rdt=[%s],it=%s,nie=[%s],nop=%s,loiu=%s,fstab=[%s],stab=[%s],eid=[%s],nof=%d,nod=%d,murr=0x%04x,miuwr=0x%04x,mxurr=0x%04x,iulen=%d",
                this.tag,
                this.recordingDateAndTime,
                BinUtils.u32ToLng(this.integrityType),
                this.nextIntegrityExtent,
                BinUtils.u32ToLng(this.numberOfPartitions),
                BinUtils.u32ToLng(this.lengthOfImplementationUse),
                u32ArrayToString(this.freeSpaceTable),
                u32ArrayToString(this.sizeTable),
                this.entityID,
                this.numberOfFiles,
                this.numberOfDirectories,
                this.minimumUDFReadRevision,
                this.minimumUDFWriteRevision,
                this.maximumUDFWriteRevision,
                this.implementationUse.len));

        // TODO: should we also print all of the tables?

        return result.toString();
    }

    ///////////////////////////////////////////////////////////////////////////

    // TODO: move to utilities places...

    public static String u32ArrayToString(int[] arr) {
        StringBuilder result = new StringBuilder();
        if (0 < arr.length) {
            result.append(BinUtils.u32ToLng(arr[0]));
            for (int i = 1, c = arr.length; i < c; i++) {
                result.append(',');
                result.append(BinUtils.u32ToLng(i));
            }
        }
        return result.toString();
    }
}
