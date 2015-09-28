package coderslagoon.trupax.lib.io.filesystem.udf;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.RandomAccessFile;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Stack;


import coderslagoon.baselib.io.BlockDevice;
import coderslagoon.baselib.io.BlockDeviceImpl;
import coderslagoon.baselib.util.BinUtils;
import coderslagoon.baselib.util.BytePtr;
import coderslagoon.baselib.util.Log;
import coderslagoon.baselib.util.MiscUtils;
import coderslagoon.trupax.lib.io.filesystem.udf.AllocationDescriptor;
import coderslagoon.trupax.lib.io.filesystem.udf.AnchorVolumeDescriptorPointer;
import coderslagoon.trupax.lib.io.filesystem.udf.CRC_CCITT;
import coderslagoon.trupax.lib.io.filesystem.udf.Descriptor;
import coderslagoon.trupax.lib.io.filesystem.udf.EntityIdentifier;
import coderslagoon.trupax.lib.io.filesystem.udf.ExtendedAttribute;
import coderslagoon.trupax.lib.io.filesystem.udf.ExtendedAttributeHeaderDescriptor;
import coderslagoon.trupax.lib.io.filesystem.udf.ExtentDescriptor;
import coderslagoon.trupax.lib.io.filesystem.udf.FileEntry;
import coderslagoon.trupax.lib.io.filesystem.udf.FileIdentifierDescriptor;
import coderslagoon.trupax.lib.io.filesystem.udf.FileSetDescriptor;
import coderslagoon.trupax.lib.io.filesystem.udf.ICBTag;
import coderslagoon.trupax.lib.io.filesystem.udf.ImplementationUseVolumeDescriptor;
import coderslagoon.trupax.lib.io.filesystem.udf.LogicalVolumeDescriptor;
import coderslagoon.trupax.lib.io.filesystem.udf.LogicalVolumeHeaderDescriptor;
import coderslagoon.trupax.lib.io.filesystem.udf.LogicalVolumeIntegrityDescriptor;
import coderslagoon.trupax.lib.io.filesystem.udf.PartitionDescriptor;
import coderslagoon.trupax.lib.io.filesystem.udf.PartitionHeaderDescriptor;
import coderslagoon.trupax.lib.io.filesystem.udf.PartitionMap;
import coderslagoon.trupax.lib.io.filesystem.udf.RecordedAddress;
import coderslagoon.trupax.lib.io.filesystem.udf.SpaceBitmapDescriptor;
import coderslagoon.trupax.lib.io.filesystem.udf.TerminatingDescriptor;
import coderslagoon.trupax.lib.io.filesystem.udf.UDF;
import coderslagoon.trupax.lib.io.filesystem.udf.UDFException;
import coderslagoon.trupax.lib.io.filesystem.udf.VolumeStructureDescriptor;

public class Browser implements UDF {
    final static Log _log = new Log("udf.browser");

    ///////////////////////////////////////////////////////////////////////////

    BlockDevice bdev;

    static int SECTOR_SIZE = 512;

    static boolean _printExtAttrs = true;

    ///////////////////////////////////////////////////////////////////////////

    int                                  blockSize = -1;
    int                                  expectedNumOfFiles = -1;
    int                                  expectedNumOfDirs = -1;
    int                                  numOfFiles, numOfDirs;
    long                                 anchorOffset; // to skip unknown header data found in Vista images
    ArrayList<VolumeStructureDescriptor> volumeStructureDescriptors;
    AnchorVolumeDescriptorPointer        anchorVolumeDescriptorPointer;
    ArrayList<Descriptor>                volumeDescriptorSequence;
    PartitionMap[]                       partitionMaps;
    PartitionDescriptor                  partitionDescriptor;
    FileEntry                            rootFileEntry;
    Stack<String>                        directories = new Stack<>();

    public LogicalVolumeDescriptor logicalVolumeDescriptor;
    public FileSetDescriptor       fileSetDescriptor;

    ///////////////////////////////////////////////////////////////////////////

    public interface Listener {
        public final static char PATH_SEPA = '/';

        public void         onDirectory(String path, long time)              throws IOException;
        public OutputStream onFile     (String name, long time, long length) throws IOException;
    }

    public Listener listener;

    String makePath(String name) {
        final StringBuilder result = new StringBuilder();
        for (final String dir : this.directories) {
            result.append(Listener.PATH_SEPA);
            result.append(dir);
        }
        result.append(Listener.PATH_SEPA);
        result.append(name);
        return result.toString();
    }

    ///////////////////////////////////////////////////////////////////////////

    long readRelativeSector(long sct, byte[] buf, int ofs) throws IOException {
        long result = sct + this.anchorOffset;
        this.bdev.read(result, buf, ofs);
        return result;
    }

    long blockAddressToLogicalSector(int block) {
        return BinUtils.u32ToLng(this.partitionDescriptor.partitionStartingLocation) +
               BinUtils.u32ToLng(block) * (this.blockSize / SECTOR_SIZE);
    }

    byte[] readBlock(int block, byte[] blk, int ofs) throws IOException {
        long i = blockAddressToLogicalSector(block);
        long end = i + this.blockSize / SECTOR_SIZE;

        while (i < end) {
            readRelativeSector(i++, blk, ofs);
            ofs += SECTOR_SIZE;
        }

        return blk;
    }

    byte[] readBlock(int block) throws IOException {
        return readBlock(block, new byte[this.blockSize], 0);
    }

    public static boolean _bulkReadProgress;

    void readBytes(int block, int len, OutputStream os) throws IOException {
        byte[] buf = new byte[this.blockSize];

        int i = 0;
        for (int c = len / this.blockSize; i < c; i++) {
            if (_bulkReadProgress && (0 == i % 100000)) {
                _log.debugf("read %d blocks", i);
                Log.flush();
            }
            readBlock(block + i, buf, 0);
            os.write(buf);
        }

        int rest = len % this.blockSize;
        if (0 < rest) {
            readBlock(block + i, buf, 0);
            os.write(buf, 0, rest);
        }
    }

    byte[] readBytes(int block, int len) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        readBytes(block, len, baos);

        baos.flush();
        baos.close();

        return baos.toByteArray();
    }

    ///////////////////////////////////////////////////////////////////////////

    public void readInitialSpace() throws IOException {
        _log.debug("reading initial space...");

        byte[] sector = new byte[SECTOR_SIZE];

        for (int i = 0; i < VOLUME_SPACE_INIT_SIZE / SECTOR_SIZE; i++) {
            this.bdev.read(i, sector, 0);
            if (!BinUtils.checkFillValue(sector, 0, sector.length, (byte)0)) {
                _log.warnf("initial sector %d not empty", i);
                break;
            }
        }
    }

    public void readVolumeStructureDescriptors() throws IOException {
        this.volumeStructureDescriptors = new ArrayList<>();

        byte[] sector = new byte[SECTOR_SIZE];

        int sct = VOLUME_SPACE_INIT_SIZE / SECTOR_SIZE;

        _log.debug("collecting volume structure descriptors...");
        final int MAX_VSD_COUNT = 64;
        for (int c = 0;;sct++) {
            this.bdev.read(sct, sector, 0);

            if (BinUtils.checkFillValue(sector, 0, sector.length, (byte)0)) {
                if (0 == c) {
                    this.anchorOffset++;
                }
                _log.tracef("skipping empty sector at %d...", sct);
                // UNKNOWN: apparently blocks are not sectors in this part of
                //          the volume; they appear to be 2048 bytes at least;
                //          1.02 references says that the sectors should be
                //          consecutive, maybe some old DVD reminiscent?
                continue;
            }
            VolumeStructureDescriptor vsd = new VolumeStructureDescriptor(sector, 0, sector.length);
            if (!vsd.check()) {
                throw new IOException(String.format("check failed (%s)", vsd.toString()));
            }
            if (++c > MAX_VSD_COUNT) {
                throw new IOException("too many volume structure descriptors");
            }

            _log.debug(vsd.toString());
            this.volumeStructureDescriptors.add(vsd);

            if (vsd.standardIdentifier.equals(VolumeStructureDescriptor.TEA01)) {
                break;
            }
        }
        _log.infof("read %s VSDs", this.volumeStructureDescriptors.size());
        if (0 != this.anchorOffset) {
            long aofs = SECTOR_SIZE * this.anchorOffset;
            _log.warnf("anchor offset is %d sectors (%d, 0x%x)",
                       this.anchorOffset, aofs, aofs);
        }
    }

    public void verifyVolumeStructureDescriptors() throws IOException {
        for (VolumeStructureDescriptor vsd : this.volumeStructureDescriptors) {
            if (vsd.standardIdentifier.equals(VolumeStructureDescriptor.NSR02) ||
                vsd.standardIdentifier.equals(VolumeStructureDescriptor.NSR03)) {
                return;
            }
        }
        throw new IOException("no NSR0x VSD found");
    }

    public void readAnchorVolumeDescriptorPointer() throws IOException {
        byte[] sector = new byte[SECTOR_SIZE];

        readRelativeSector(AnchorVolumeDescriptorPointer.LOCATION, sector, 0);

        this.anchorVolumeDescriptorPointer =
            (AnchorVolumeDescriptorPointer)Descriptor.parse(sector, 0);

        _log.infof("read %s", this.anchorVolumeDescriptorPointer);

        // TODO: try to read the AVDP from the alternative locations also
    }

    public void readVolumeDescriptorSequence() throws IOException {
        this.volumeDescriptorSequence = new ArrayList<>();

        long sct = this.anchorVolumeDescriptorPointer.mainVolumeDescriptorSequence.location;
        long max = this.anchorVolumeDescriptorPointer.mainVolumeDescriptorSequence.length / SECTOR_SIZE + sct;

        for (;sct < max; sct++) {
            // each sector one descriptor (is this assumption correct?)
            // NOTE: because of BytePtr usage we can NOT REUSE the sector buffer!
            byte[] sector = new byte[SECTOR_SIZE];

            long sct_r = readRelativeSector(sct, sector, 0);

            Descriptor dsc = Descriptor.parse(sector, 0);

            _log.debugf("found descriptor %s at sector %d", dsc.tag.identifier, sct_r);
            _log.trace(dsc.toString());

            this.volumeDescriptorSequence.add(dsc);

            if (dsc.tag.identifier == Descriptor.Tag.Identifier.TERMINATING_DESCRIPTOR) {
                return;
            }
        }

        throw new IOException("no terminating descriptor found in VDS");
    }

    Descriptor findDescriptorInVDS(Descriptor.Tag.Identifier dtid, boolean expectOne) throws IOException {
        Descriptor result = null;
        for (Descriptor d : this.volumeDescriptorSequence) {
            if (d.tag.identifier == dtid) {
                if (null == result) {
                    result = d;
                }
                else if (expectOne) {
                    throw new IOException("found more than one descriptor of type " + dtid);
                }
            }
        }
        return result;
    }

    public void findEssentialDescriptorsInVSD() throws IOException {
        Descriptor d = findDescriptorInVDS(Descriptor.Tag.Identifier.LOGICAL_VOLUME_DESCRIPTOR, true);
        if (null == d) {
            throw new IOException("no logical volume descriptor available");
        }
        this.logicalVolumeDescriptor = (LogicalVolumeDescriptor)d;
        this.blockSize = this.logicalVolumeDescriptor.logicalBlockSize;

        d = findDescriptorInVDS(Descriptor.Tag.Identifier.PARTITION_DESCRIPTOR, true);
        if (null == d) {
            throw new IOException("no partition descriptor available");
        }
        this.partitionDescriptor = (PartitionDescriptor)d;
    }

    public void checkIntegrity() throws IOException {
        ExtentDescriptor ise = this.logicalVolumeDescriptor.integritySequenceExtent;
        if (ise.none()) {
            _log.info("no integrity sequence extent defined");
            return;
        }

        int c = ise.length / this.blockSize;
        _log.infof("walking %d potential integrity descriptor(s)...", c);

        for (int i = 0; i < c; i++) {
            byte[] blk = new byte[this.blockSize];

            readRelativeSector(ise.location + i, blk, 0);

            Descriptor dsc = Descriptor.parse(blk, 0);
            _log.info(dsc.toString());

            if (dsc instanceof TerminatingDescriptor) {
                _log.infof("found terminating descriptor, skipping the %d ones left", c - i - 1);
                break;
            }
            else if (dsc instanceof LogicalVolumeIntegrityDescriptor) {
                LogicalVolumeIntegrityDescriptor lvid = (LogicalVolumeIntegrityDescriptor)dsc;

                LogicalVolumeHeaderDescriptor lvhd = new LogicalVolumeHeaderDescriptor(
                        lvid.logicalVolumeContentsUse.buf,
                        lvid.logicalVolumeContentsUse.ofs);

                this.expectedNumOfFiles = lvid.numberOfFiles;
                this.expectedNumOfDirs  = lvid.numberOfDirectories;

                _log.info(lvhd.toString());
                continue;
            }
            else {
                throw new IOException("unexpected descriptor encountered");
            }
        }
    }

    public void readPartitionMaps() throws IOException {
        _log.debugf("parsing %d partition map(s)...",
                    this.logicalVolumeDescriptor.numberOfPartitionMaps);

        this.partitionMaps = PartitionMap.parse(
                this.logicalVolumeDescriptor.numberOfPartitionMaps,
                this.logicalVolumeDescriptor.partitionMaps);

        for (PartitionMap pm : this.partitionMaps) {
            _log.info(pm.toString());
        }
    }

    public void checkPartition() throws IOException {
        // can we handle it?
        String iid = this.partitionDescriptor.partitionContents.identifierAsString();
        if (null == iid ||
            (!iid.equals(EntityIdentifier.ID_NSR02) &&
             !iid.equals(EntityIdentifier.ID_NSR03))) {
            throw new IOException(String.format(
                    "unsupported implementation identifier \"%s\"", iid));
        }

        EntityIdentifier.ImplSuffix iisfx = new EntityIdentifier.ImplSuffix(
                this.partitionDescriptor.implementationIdentifier.identifierSuffix);

        _log.infof("got PD implementation identifier suffix (%s)", iisfx);

        // is it the right UDF version?
        EntityIdentifier.OSTASuffix osfx = (EntityIdentifier.OSTASuffix)
            this.logicalVolumeDescriptor.domainIdentifier.suffix();

        if (EntityIdentifier.RevisionSuffix.UDF_REVISION_102 != osfx.udfRevision) {
            throw new IOException(String.format("unsupported UDF version 0x%04x",
                    BinUtils.u16ToInt(osfx.udfRevision)));
        }
        _log.infof("got LVD domain identifier OSTA suffix (%s)", osfx);

        // let's also check the IUVD...
        Descriptor d = findDescriptorInVDS(Descriptor.Tag.Identifier.IMPLEMENTATION_USE_VOLUME_DESCRIPTOR, true);
        if (null == d) {
            _log.info("no IUVD found");
        }
        else {
            ImplementationUseVolumeDescriptor iuvd = (ImplementationUseVolumeDescriptor)d;
            // FIXME: we might encounter suffixes unknown to us, so we should
            //        not crash here, or should we?
            EntityIdentifier.Suffix sfx = iuvd.implementationIdentifier.suffix();
            if (null != sfx) {
                _log.infof("recognized IUVD implementation identifier suffix (%s)", sfx);
            }
        }
    }

    static boolean FSD_TERM_CHECK = false;  // NOTE: termination of FSD not needed in UDF, apparently

    public void locateFileSetDescriptor() throws IOException {
        AllocationDescriptor.Long adl = AllocationDescriptor.Long.parse(
                this.logicalVolumeDescriptor.logicalVolumeContentsUse.buf,
                this.logicalVolumeDescriptor.logicalVolumeContentsUse.ofs);

        _log.infof("LVD file set extent is at %s", adl);

        // NOTE: for now we just can deal with one volume and one partition
        if (0 != adl.location.partitionReferenceNumber) {
            throw new IOException("partition reference number not zero");
        }

        // the most important link: from initial descriptor land towards the
        // files, so show it where it is physically...
        _log.infof("loading FSD at sector %s...",
                   blockAddressToLogicalSector(adl.location.logicalBlockNumber));

        byte[] blk = readBlock(adl.location.logicalBlockNumber);

        this.fileSetDescriptor = (FileSetDescriptor)Descriptor.parse(blk, 0);

        _log.info(this.fileSetDescriptor.toString());

        if (0 != this.fileSetDescriptor.nextExtent.length) {
            throw new IOException("more FSD extents out there, not supported");
        }

        if (FSD_TERM_CHECK) {
            blk = readBlock(adl.location.logicalBlockNumber + 1);
            try {
                Descriptor dsc = Descriptor.parse(blk, 0);
                if (dsc instanceof TerminatingDescriptor) {
                    _log.info("FSD sequence terminator found");
                }
                else {
                    _log.warnf("no FSD sequence terminator found, but a %s",
                               dsc.tag.identifier);
                }
            }
            catch (UDFException ue) {
                _log.warn("no FSD sequence terminator found");
            }
        }
    }

    public FileEntry readFileEntry(AllocationDescriptor.Long adl) throws IOException {
        if (0 == adl.length) {
            return null;
        }
        RecordedAddress raddr = adl.location;
        if (0 != raddr.partitionReferenceNumber) {
            throw new IOException("file entry in unexpected partition");
        }

        _log.infof("reading file entry from sector %d...",
                   blockAddressToLogicalSector(raddr.logicalBlockNumber));

        byte[] blk = readBlock(raddr.logicalBlockNumber);

        return (FileEntry)Descriptor.parse(blk, 0);
    }

    void printExtendedAttributes(FileEntry fe) throws UDFException {
        if (!_printExtAttrs || 0 == fe.extendedAttributes.len) {
            return;
        }

        ExtendedAttributeHeaderDescriptor eahd =
       (ExtendedAttributeHeaderDescriptor)Descriptor.parse(fe.extendedAttributes.buf,
                                                           fe.extendedAttributes.ofs);
        _log.debug(eahd.toString());

        BytePtr attrSpace = fe.extendedAttributes.grab();
        attrSpace.ofs += ExtendedAttributeHeaderDescriptor.LENGTH;
        attrSpace.len -= ExtendedAttributeHeaderDescriptor.LENGTH;

        ExtendedAttribute[] eas = ExtendedAttribute.read(attrSpace);
        _log.debugf("%d extended attribute(s)...", eas.length);
        for (ExtendedAttribute ea : eas) {
            _log.info(ea.toString());
        }
    }

    public void readRootFileEntry() throws IOException {
        this.rootFileEntry = readFileEntry(this.fileSetDescriptor.rootDirectoryICB);
        if (null == this.rootFileEntry) {
            throw new IOException("root file entry does not exist");
        }
        _log.info(this.rootFileEntry.toString());
        printExtendedAttributes(this.rootFileEntry);
    }

    public void readFileEntryData(FileEntry fe, OutputStream os) throws IOException {
        switch (fe.icbTag.allocDescriptor()) {
            case ICBTag.ALLOCDESC_EXTENDED: {
                throw new IOException("extended allocation descriptors are not supported");
            }
            case ICBTag.ALLOCDESC_SHORT: {
                int c = fe.lengthOfAllocationDescriptors / AllocationDescriptor.Short.LENGTH;
                _log.infof("traversing file entry's %d short allocation descriptor(s)...", c);
                for (int i = 0, ofs = fe.allocationDescriptors.ofs; i < c; i++, ofs += AllocationDescriptor.Short.LENGTH) {
                    AllocationDescriptor.Short ads = AllocationDescriptor.Short.parse(
                            fe.allocationDescriptors.buf, ofs);

                    _log.infof("ADS[%d] is %s", i, ads);

                    readBytes(ads.position, ads.length, os);
                }
                break;
            }
            case ICBTag.ALLOCDESC_LONG: {
                int c = fe.lengthOfAllocationDescriptors / AllocationDescriptor.Long.LENGTH;
                _log.infof("traversing file entry's %d long allocation descriptor(s)...", c);
                for (int i = 0, ofs = fe.allocationDescriptors.ofs; i < c; i++, ofs += AllocationDescriptor.Long.LENGTH) {
                    AllocationDescriptor.Long adl = AllocationDescriptor.Long.parse(
                            fe.allocationDescriptors.buf, ofs);

                    _log.infof("ADL[%d] is %s", i, adl);

                    if (0 != adl.location.partitionReferenceNumber) {
                        throw new IOException("cross-partition data storage is not supported right now");
                    }

                    readBytes(adl.location.logicalBlockNumber, adl.length, os);
                }
                break;
            }
            case ICBTag.ALLOCDESC_EMBEDDED: {
                int c = fe.lengthOfAllocationDescriptors;
                _log.infof("reading file entry's embedded %d byte(s)...", c);

                if (c != fe.allocationDescriptors.len) {
                    throw new IOException("allocation descriptors size conflict");
                }

                os.write(fe.allocationDescriptors.buf,
                         fe.allocationDescriptors.ofs,
                         fe.allocationDescriptors.len);
                break;
            }
            default: {
                throw new IOException();
            }
        }
    }

    public static int MAX_DIR_DEPTH = 256;

    public void listFiles(FileEntry fe, boolean recursive, boolean readContent) throws IOException {
        this.directories.clear();
        listFiles(fe, recursive, readContent, 0);
        if (!this.directories.empty()) {
            throw new UDFException(
                    "directory stack is out of sync (%d remaining)",
                    this.directories.size());
        }
    }

    static class MD5OutputStream extends OutputStream {
        MessageDigest md;
        long sz;
        public MD5OutputStream() throws IOException {
            super();
            try {
                this.md = MessageDigest.getInstance("MD5");
            }
            catch (NoSuchAlgorithmException nsae) {
                throw new IOException(nsae);
            }
        }
        public void write(int b) throws IOException {
            this.md.update((byte)b);
            this.sz++;
        }
        public byte[] digest() {
            return this.md.digest();
        }
        public long size() {
            return this.sz;
        }
        public void close() {
            _log.infof("%d byte(s), MD5=%s",
                       size(),
                       size() == 0 ? "*" : BinUtils.bytesToHexString(digest()));
        }
    }

    void listFiles(FileEntry fe, boolean recursive, boolean readContent, int level) throws IOException {
        this.numOfDirs++;

        if (fe.icbTag.fileType != ICBTag.FileType.DIRECTORY) {
            throw new IOException("file entry is not a directory");
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        readFileEntryData(fe, baos);
        baos.close();

        byte[] fds = baos.toByteArray();

        //BinUtils.hexDump(fds, System.out, 16, 4);

        _log.debugf("FID list size is %d bytes", fds.length);

        int ofs2 = 0, j = 0;
        while (ofs2 < fds.length) {
            FileIdentifierDescriptor fid = (FileIdentifierDescriptor)Descriptor.parse(fds, ofs2);
            ofs2 += fid.length();

            _log.infof("file #%d FID is %s", j++, fid);

            if (0 == fid.fileIdentifierStr.length()) {
                _log.info("skipping parent entry...");
                continue;
            }

            FileEntry fe2 = readFileEntry(fid.icb);
            if (null == fe2) {
                _log.infof("file entry has no data");
            }
            else {
                _log.info(fe2.toString());
                printExtendedAttributes(fe2);

                switch (fe2.icbTag.fileType) {
                    case DIRECTORY: {
                        final String dname = fid.fileIdentifierStr;
                        _log.infof("file entry \"%s\" is a directory", dname);
                        if (recursive) {
                            if (++level > MAX_DIR_DEPTH) {
                                throw new IOException("directory nesting level too deep");
                            }
                            _log.info("entering directory...");
                            this.listener.onDirectory(
                                    makePath(dname),
                                    fe2.modificationDateAndTime.toCalendar().getTimeInMillis());
                            this.directories.push(dname);
                            listFiles(fe2, true, readContent, level);
                            level--;
                            this.directories.pop();
                        }
                        break;
                    }
                    case RANDOM_ACCESS_BYTE_SEQ: {
                        final String fname = fid.fileIdentifierStr;
                        _log.infof("file entry \"%s\" seems to be a file", fname);
                        this.numOfFiles++;
                        if (readContent) {
                            readFileEntryData(fe2, this.listener.onFile(
                                              makePath(fname),
                                              fe2.modificationDateAndTime.toCalendar().getTimeInMillis(),
                                              fe2.informationLength));
                        }
                        break;
                    }
                    default: {
                        _log.infof("unsupported file type %s", fe2.icbTag.fileType);
                        break;
                    }
                }
            }
        }
        if (ofs2 != fds.length) {
            throw new IOException(String.format(
                    "FID sizes are off by %d", ofs2 - fds.length));
        }
    }

    void dumpUnallocatedSpaceBitmap() throws IOException {
        PartitionHeaderDescriptor phd = this.partitionDescriptor.partitionContentsUse;

        if (phd.unallocatedSpaceBitmap.length > 0) {
            byte[] blk = readBlock(phd.unallocatedSpaceBitmap.position);

            Descriptor d = Descriptor.parse(blk, 0);

            if (d instanceof SpaceBitmapDescriptor) {
                SpaceBitmapDescriptor sbd = (SpaceBitmapDescriptor)d;
                _log.info(sbd.toString());

                // TODO: walk through the bitmap block per block, instead of
                //       reading it all into memory - and also skip the
                //       descriptor data...

                byte[] uasb = readBytes(phd.unallocatedSpaceBitmap.position,
                                        phd.unallocatedSpaceBitmap.length);

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                PrintStream ps = new PrintStream(baos);
                BinUtils.hexDump(uasb, ps, 64, 4);
                ps.close();

                _log.infof("unallocated space bitmap dump @ %d (%d bytes):\n%s",
                           phd.unallocatedSpaceBitmap.position,
                           phd.unallocatedSpaceBitmap.length,
                           new String(baos.toByteArray()));
            }
            else {
                throw new IOException("not an unallocated space bitmap descriptor");
            }
        }
        else {
            throw new IOException("no unallocated space bitmap");
        }
    }

    void showSummary() {
        _log.infof("found %d/%d files and %d/%d directories",
                   this.numOfFiles,
                   this.expectedNumOfFiles,
                   this.numOfDirs,
                   this.expectedNumOfDirs);
    }

    ///////////////////////////////////////////////////////////////////////////

    public Browser(File image, Listener listener) throws IOException {
        this(new BlockDeviceImpl.FileBlockDevice(
                new RandomAccessFile(image, "r"),
                SECTOR_SIZE,
                -1L,
                true,
                false),
             listener);
    }

    public Browser(BlockDevice bdev, Listener listener) {
        init(bdev, listener);
    }

    protected Browser() {
    }

    protected void init(BlockDevice bdev, Listener listener) {
        this.listener = listener;
        this.bdev     = bdev;
    }

    public void exec() throws IOException {
        readInitialSpace();
        readVolumeStructureDescriptors();
        verifyVolumeStructureDescriptors();
        readAnchorVolumeDescriptorPointer();
        readVolumeDescriptorSequence();
        findEssentialDescriptorsInVSD();
        checkIntegrity();
        readPartitionMaps();
        checkPartition();
        locateFileSetDescriptor();
        readRootFileEntry();
        listFiles(this.rootFileEntry, true, true);
        dumpUnallocatedSpaceBitmap();
        showSummary();
    }

    ///////////////////////////////////////////////////////////////////////////

    public static void selfTest() throws Exception {
        if (!CRC_CCITT.test()) {
            throw new Exception("CRC-CCITT implementation is broken!");
        }
    }

    static class DefaultListener implements Listener {
        public void onDirectory(String name, long time) {
            _log.infof("=== directory '%s' (modified %s)...", name,
                       MiscUtils.calendarFromMillis(time));
        }
        public OutputStream onFile(String name, long time, long length) throws IOException {
            _log.infof("=== file '%s' (%d bytes, modified %s)...", name, length,
                       MiscUtils.calendarFromMillis(time));
            return new MD5OutputStream();
        }
    }

    public static void main(String[] args) throws Throwable {
        selfTest();

        UDF.Compliance.setTo(UDF.Compliance.VISTA);

        Log.addPrinter(System.out);

        if (1 > args.length || args.length > 2) {
            System.err.printf("usage: %s [imagefile] {sectorsize}", Browser.class.getName());
            return;
        }

        SECTOR_SIZE = 1 == args.length ? 512 : Integer.parseInt(args[1]);

        try {
            new Browser(new File(args[0]), new DefaultListener()).exec();
        }
        catch (Throwable err) {
            System.err.println("----\nERROR OCCURRED: " + err.getMessage());
            err.printStackTrace(System.err);

        }

        Log.reset();

        System.exit(0);
    }
}
