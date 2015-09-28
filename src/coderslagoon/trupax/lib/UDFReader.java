package coderslagoon.trupax.lib;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Properties;

import coderslagoon.baselib.io.BlockDevice;
import coderslagoon.baselib.util.BinUtils;
import coderslagoon.baselib.util.Log;
import coderslagoon.baselib.util.VarLong;
import coderslagoon.trupax.lib.Reader.Exception.Code;
import coderslagoon.trupax.lib.io.filesystem.udf.AllocationDescriptor;
import coderslagoon.trupax.lib.io.filesystem.udf.AnchorVolumeDescriptorPointer;
import coderslagoon.trupax.lib.io.filesystem.udf.Descriptor;
import coderslagoon.trupax.lib.io.filesystem.udf.EntityIdentifier;
import coderslagoon.trupax.lib.io.filesystem.udf.ExtentDescriptor;
import coderslagoon.trupax.lib.io.filesystem.udf.FileEntry;
import coderslagoon.trupax.lib.io.filesystem.udf.FileIdentifierDescriptor;
import coderslagoon.trupax.lib.io.filesystem.udf.FileSetDescriptor;
import coderslagoon.trupax.lib.io.filesystem.udf.ICBTag;
import coderslagoon.trupax.lib.io.filesystem.udf.LogicalVolumeDescriptor;
import coderslagoon.trupax.lib.io.filesystem.udf.LogicalVolumeIntegrityDescriptor;
import coderslagoon.trupax.lib.io.filesystem.udf.PartitionDescriptor;
import coderslagoon.trupax.lib.io.filesystem.udf.RecordedAddress;
import coderslagoon.trupax.lib.io.filesystem.udf.TerminatingDescriptor;

public class UDFReader extends Reader {
    final static Log _log = new Log("udf.reader");

    ///////////////////////////////////////////////////////////////////////////

    public UDFReader(BlockDevice bdev, Properties props) {
        super(bdev, props);
        this.block = new byte[this.blockSize = this.bdev.blockSize()];
    }

    public void extract(File toDir, Progress progress) throws IOException {
        this.progress = progress;
        readAnchorVolumeDescriptorPointer();
        readVolumeDescriptorSequence();
        checkIntegrity();
        checkPartition();
        locateFileSetDescriptor();
        readRootFileEntry();
        switch(this.progress.onDirectory(toDir,
            this.rootFileEntry.informationLength, null)) {
            case OK   : readDirectory(this.rootFileEntry, toDir, true); break;
            case ABORT: throwAbort(); break;
            default   : { }
        }
    }

    ///////////////////////////////////////////////////////////////////////////

    final int    blockSize;
    final byte[] block;

    Progress progress;

    AnchorVolumeDescriptorPointer anchorVolumeDescriptorPointer;
    LogicalVolumeDescriptor       logicalVolumeDescriptor;
    PartitionDescriptor           partitionDescriptor;
    FileSetDescriptor             fileSetDescriptor;
    FileEntry                     rootFileEntry;

    ///////////////////////////////////////////////////////////////////////////

    final private void readBlock(long num) throws IOException {
        readBlock(num, this.block);
    }
    final private byte[] readBlockNew(long num) throws IOException {
        byte[] result = new byte[this.blockSize];
        readBlock(num, result);
        return result;
    }
    final private void readBlock(long num, byte[] buf) throws IOException {
        try {
            this.bdev.read(num, buf, 0);
        }
        catch (IOException ioe) {
            throw new Exception(Code.ERR_IO, null, "block device read error (%s)",
                                ioe.getMessage());
        }
    }
    final private byte[] readLogicalBlockNew(int num) throws IOException {
        byte[] result = new byte[this.blockSize];
        readLogicalBlock(num, result);
        return result;
    }
    final private void readLogicalBlock(int num, byte[] buf) throws IOException {
        readBlock(blockAddressToLogicalBlock(num), buf);
    }
    final private long blockAddressToLogicalBlock(int num) {
        return BinUtils.u32ToLng(this.partitionDescriptor.partitionStartingLocation) +
               BinUtils.u32ToLng(num);
    }

    ///////////////////////////////////////////////////////////////////////////

    private void readAnchorVolumeDescriptorPointer() throws IOException {
        try {
            readBlock(AnchorVolumeDescriptorPointer.LOCATION);

            this.anchorVolumeDescriptorPointer =
                (AnchorVolumeDescriptorPointer)Descriptor.parse(this.block, 0);
        }
        catch (IOException ioe) {
            throw new MountException(ioe.getMessage());
        }

        _log.debugf("AVDP is %s", this.anchorVolumeDescriptorPointer);
    }

    private void readVolumeDescriptorSequence() throws IOException {
        long loc = this.anchorVolumeDescriptorPointer.mainVolumeDescriptorSequence.location;
        long max = this.anchorVolumeDescriptorPointer.mainVolumeDescriptorSequence.length / this.blockSize + loc;
        for (;loc < max; loc++) {
            byte[] sct = readBlockNew(loc);
            Descriptor d = Descriptor.parse(sct, 0);
            _log.debugf("found descriptor %s at sector %d", d.tag.identifier, loc);
            if (d instanceof LogicalVolumeDescriptor) {
                this.logicalVolumeDescriptor = (LogicalVolumeDescriptor)d;
            }
            if (d instanceof PartitionDescriptor) {
                this.partitionDescriptor = (PartitionDescriptor)d;
            }
            if (d.tag.identifier == Descriptor.Tag.Identifier.TERMINATING_DESCRIPTOR) {
                break;
            }
        }
        if (null == this.logicalVolumeDescriptor) {
            throwDev("missing logical volume descriptor");
        }
        if (null == this.partitionDescriptor) {
            throwDev("missing partition descriptor");
        }
    }

    private void checkIntegrity() throws IOException {
        ExtentDescriptor ise = this.logicalVolumeDescriptor.integritySequenceExtent;
        if (ise.none()) {
            throwDev("no integrity sequence extent present");
        }
        int c = ise.length / this.blockSize;
        for (int i = 0; i < c; i++) {
            readBlock(ise.location + i);

            Descriptor d = Descriptor.parse(this.block, 0);

            if (d instanceof TerminatingDescriptor) {
                break;
            }
            else if (d instanceof LogicalVolumeIntegrityDescriptor) {
                LogicalVolumeIntegrityDescriptor lvid = (LogicalVolumeIntegrityDescriptor)d;
                if (Progress.Result.OK != this.progress.onMount(
                        lvid.numberOfFiles,
                        lvid.numberOfDirectories)) {
                    throwAbort();
                }
                return;
            }
        }
        throwDev("no LVID found");
    }

    private void checkPartition() throws IOException {
        String iid = this.partitionDescriptor.partitionContents.identifierAsString();
        if (null == iid ||
            (!iid.equals(EntityIdentifier.ID_NSR02) &&
             !iid.equals(EntityIdentifier.ID_NSR03))) {
            throwDev("unsupported partition implementation identifier \"%s\"", iid);
        }

        EntityIdentifier.ImplSuffix iisfx = new EntityIdentifier.ImplSuffix(
                this.partitionDescriptor.implementationIdentifier.identifierSuffix);

        _log.debugf("got partition implementation identifier suffix (%s)", iisfx);

        EntityIdentifier.OSTASuffix osfx = (EntityIdentifier.OSTASuffix)
            this.logicalVolumeDescriptor.domainIdentifier.suffix();

        if (EntityIdentifier.RevisionSuffix.UDF_REVISION_102 != osfx.udfRevision) {
            throwDev("unsupported UDF version 0x%04x",
                     BinUtils.u16ToInt(osfx.udfRevision));
        }
        _log.debugf("got LVD domain identifier OSTA suffix (%s)", osfx);
    }

    private void locateFileSetDescriptor() throws IOException {
        AllocationDescriptor.Long adl = AllocationDescriptor.Long.parse(
                this.logicalVolumeDescriptor.logicalVolumeContentsUse.buf,
                this.logicalVolumeDescriptor.logicalVolumeContentsUse.ofs);

        _log.debugf("LVD file set extent is at %s", adl);

        if (0 != adl.location.partitionReferenceNumber) {
            throwDev("partition reference number is %d",
                     adl.location.partitionReferenceNumber);
        }

        byte[] blk = readLogicalBlockNew(adl.location.logicalBlockNumber);
        this.fileSetDescriptor = (FileSetDescriptor)Descriptor.parse(blk, 0);

        _log.debug(this.fileSetDescriptor.toString());

        if (0 != this.fileSetDescriptor.nextExtent.length) {
            throwDev("more than one FSD extent found");
        }
    }

    FileEntry readFileEntry(AllocationDescriptor.Long adl) throws IOException {
        if (0 == adl.length) {
            return null;
        }
        RecordedAddress raddr = adl.location;
        if (0 != raddr.partitionReferenceNumber) {
            throwDev("file entry in different partition (%d)",
                     raddr.partitionReferenceNumber);
        }
        byte[] blk = readLogicalBlockNew(raddr.logicalBlockNumber);
        return (FileEntry)Descriptor.parse(blk, 0);
    }

    private void readRootFileEntry() throws IOException {
        this.rootFileEntry = readFileEntry(this.fileSetDescriptor.rootDirectoryICB);
        if (null == this.rootFileEntry) {
            throwDev("root file entry does not exist");
        }
        _log.debug(this.rootFileEntry.toString());
    }

    public void readDirectory(final FileEntry fe, final File dir, final boolean recursive) throws IOException {
        LocalDir ldir = new LocalDir() {
            public void writeEntries() throws IOException {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                readFileEntryData(fe, baos, fe == UDFReader.this.rootFileEntry ? null : dir);
                baos.close();

                byte[] fds = baos.toByteArray();

                int ofs2 = 0;
                while (ofs2 < fds.length) {
                    FileIdentifierDescriptor fid = (FileIdentifierDescriptor)Descriptor.parse(fds, ofs2);
                    ofs2 += fid.length();

                    if (0 == fid.fileIdentifierStr.length()) {
                        continue;
                    }

                    final FileEntry fe2 = readFileEntry(fid.icb);
                    if (null != fe2) {
                        switch (fe2.icbTag.fileType) {
                            case DIRECTORY: {
                                if (recursive) {
                                    File dir2 = new File(dir, fid.fileIdentifierStr);
                                    long tstamp = fe2.modificationDateAndTime.toCalendar().getTimeInMillis();
                                    long size   = fe2.informationLength;
                                    switch(UDFReader.this.progress.onDirectory(dir2, size, tstamp)) {
                                        case OK: {
                                            readDirectory(fe2, dir2, true);
                                            break;
                                        }
                                        case ABORT: {
                                            throwAbort();
                                        }
                                        // SKIP
                                        default: {
                                            break;
                                        }
                                    }
                                }
                                break;
                            }
                            case RANDOM_ACCESS_BYTE_SEQ: {
                                final File fl = new File(dir, fid.fileIdentifierStr);
                                long tstamp = fe2.modificationDateAndTime.toCalendar().getTimeInMillis();
                                long size   = fe2.informationLength;
                                switch (UDFReader.this.progress.onFile(fl, size, tstamp)) {
                                    case OK: {
                                        LocalFile lf = new LocalFile() {
                                            protected void writeData(OutputStream os, long size) throws IOException {
                                                readFileEntryData(fe2, os, fl);
                                            }
                                        };
                                        lf.write(fl, size, tstamp, UDFReader.this.progress);
                                        break;
                                    }
                                    case ABORT: {
                                        throwAbort();
                                    }
                                    // SKIP
                                    default: {
                                        break;
                                    }
                                }
                                break;
                            }
                            default: {
                                throwDev("unsupported file type %s", fe2.icbTag.fileType);
                            }
                        }
                    }
                }
                if (ofs2 != fds.length) {
                    throwDev("FID sizes are off by %d", ofs2 - fds.length);
                }
            }
        };
        ldir.write(dir, fe.modificationDateAndTime.toCalendar().getTimeInMillis());
    }

    public void readFileEntryData(FileEntry fe, OutputStream os, File fl) throws IOException {
        VarLong total = new VarLong();
        switch (fe.icbTag.allocDescriptor()) {
            case ICBTag.ALLOCDESC_EXTENDED: {
                throwDev("extended allocation descriptors not supported");
            }
            case ICBTag.ALLOCDESC_SHORT: {
                int c = fe.lengthOfAllocationDescriptors / AllocationDescriptor.Short.LENGTH;
                for (int i = 0, ofs = fe.allocationDescriptors.ofs; i < c; i++, ofs += AllocationDescriptor.Short.LENGTH) {
                    AllocationDescriptor.Short ads = AllocationDescriptor.Short.parse(
                            fe.allocationDescriptors.buf, ofs);
                    xferBytes(ads.position, ads.length, os, total);
                }
                break;
            }
            case ICBTag.ALLOCDESC_LONG: {
                int c = fe.lengthOfAllocationDescriptors / AllocationDescriptor.Long.LENGTH;
                for (int i = 0, ofs = fe.allocationDescriptors.ofs; i < c; i++, ofs += AllocationDescriptor.Long.LENGTH) {
                    AllocationDescriptor.Long adl = AllocationDescriptor.Long.parse(
                            fe.allocationDescriptors.buf, ofs);
                    if (0 != adl.location.partitionReferenceNumber) {
                        throwDev("cross-partition data storage not supported");
                    }
                    xferBytes(adl.location.logicalBlockNumber, adl.length, os, total);
                }
                break;
            }
            case ICBTag.ALLOCDESC_EMBEDDED: {
                int c = fe.lengthOfAllocationDescriptors;
                if (c != fe.allocationDescriptors.len) {
                    throwDev("allocation descriptors size conflict");
                }
                dataProgress(total.v);
                try {
                    os.write(fe.allocationDescriptors.buf,
                             fe.allocationDescriptors.ofs,
                             fe.allocationDescriptors.len);
                }
                catch (IOException ioe) {
                    throw new Exception(Code.ERR_IO, fl,
                            "write of embedded data failed (%s)",
                            ioe.getMessage());
                }
                total.v += fe.allocationDescriptors.len;
                break;
            }
            default: {
                throwDev("unknown allocation descriptor %s", fe.icbTag.allocDescriptor());
            }
        }
        dataProgress(total.v);
    }

    ///////////////////////////////////////////////////////////////////////////

    private void xferBytes(int block, int len, OutputStream os, VarLong total) throws IOException {
        byte[] buf = new byte[this.blockSize];
        int i = 0;
        for (int c = len / this.blockSize; i < c; i++) {
            dataProgress(total.v);
            readLogicalBlock(block + i, buf);
            os.write(buf);
            total.v += this.blockSize;
        }
        int rest = len % this.blockSize;
        if (0 < rest) {
            dataProgress(total.v);
            readLogicalBlock(block + i, buf);
            os.write(buf, 0, rest);
            total.v += rest;
        }
    }

    private final void dataProgress(long pos) throws IOException {
        switch(this.progress.onData(pos)) {
            case ABORT: throwAbort(); break;
            default   : break;
        }
    }
}
