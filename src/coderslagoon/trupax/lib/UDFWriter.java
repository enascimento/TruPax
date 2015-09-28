package coderslagoon.trupax.lib;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Random;

import coderslagoon.baselib.io.BlockDevice;
import coderslagoon.baselib.io.BlockDeviceImpl;
import coderslagoon.baselib.io.FileNode;
import coderslagoon.baselib.io.FileRegistrar;
import coderslagoon.baselib.io.FileRegistrar.Directory;
import coderslagoon.baselib.io.IOUtils;
import coderslagoon.baselib.util.BinUtils;
import coderslagoon.baselib.util.BitBlockWriter;
import coderslagoon.baselib.util.BytePtr;
import coderslagoon.baselib.util.Clock;
import coderslagoon.baselib.util.Log;
import coderslagoon.baselib.util.MiscUtils;
import coderslagoon.baselib.util.Taggable;
import coderslagoon.trupax.lib.io.filesystem.udf.AllocationDescriptor;
import coderslagoon.trupax.lib.io.filesystem.udf.AnchorVolumeDescriptorPointer;
import coderslagoon.trupax.lib.io.filesystem.udf.CharacterSet;
import coderslagoon.trupax.lib.io.filesystem.udf.EntityIdentifier;
import coderslagoon.trupax.lib.io.filesystem.udf.EntityIdentifier.OSInfo;
import coderslagoon.trupax.lib.io.filesystem.udf.EntityIdentifier.RevisionSuffix;
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
import coderslagoon.trupax.lib.io.filesystem.udf.PrimaryVolumeDescriptor;
import coderslagoon.trupax.lib.io.filesystem.udf.RecordedAddress;
import coderslagoon.trupax.lib.io.filesystem.udf.SpaceBitmapDescriptor;
import coderslagoon.trupax.lib.io.filesystem.udf.TerminatingDescriptor;
import coderslagoon.trupax.lib.io.filesystem.udf.Timestamp;
import coderslagoon.trupax.lib.io.filesystem.udf.UDF;
import coderslagoon.trupax.lib.io.filesystem.udf.UDFException;
import coderslagoon.trupax.lib.io.filesystem.udf.UnallocatedSpaceDescriptor;
import coderslagoon.trupax.lib.io.filesystem.udf.VolumeStructureDescriptor;

/**
 * Writer to emit UDF 1.02 compatible volumes.
 */
public class UDFWriter extends Writer implements UDF {
    public final static int ERROR_INTERNAL_1 = ERROR_CUSTOM_BASE + 0;
    public final static int ERROR_INTERNAL_2 = ERROR_CUSTOM_BASE + 1;
    public final static int ERROR_INTERNAL_3 = ERROR_CUSTOM_BASE + 2;
    public final static int ERROR_INTERNAL_4 = ERROR_CUSTOM_BASE + 3;

    Layout      layout;
    String      volumeID;
    Progress    progress;
    BlockDevice bdev;
    Random      rnd = new Random();
    long        bnum;
    long        blocksTotal = -1L;
    long        uniqueFileEntryID;
    boolean     resolving;
    byte[]      block;
    BytePtr     embedBuf;
    String      volumeSetIdentifier;
    Calendar    latestTime;

    // things to be resolved...
    ExtentDescriptor           mainVolumeDescriptorSequence;
    ExtentDescriptor           reserveVolumeDescriptorSequence;
    ExtentDescriptor           integritySequenceExtent;
    AllocationDescriptor.Short unallocatedSpaceBitmap;
    List<ExtentDescriptor>     unallocatedSpace = new ArrayList<>();
    Integer                    partitionStartingLocation;
    Integer                    partitionLength;
    Integer                    partitionFreeSpace;
    int                        numberOfFiles;
    int                        numberOfDirectories;
    Long                       uniqueID;
    AllocationDescriptor.Long  fileSetDescriptor;
    AllocationDescriptor.Long  rootDirectoryICB;
    Integer                    filesAndDirsPBnum;
    Long                       filesAndDirsSize;
    Long                       freeBlocksPBnum;

    public boolean checkResolved() {
        return  // NOTE: keep this check in sync with the variables above!
            0    <  this.numberOfDirectories             &&
            null != this.mainVolumeDescriptorSequence    &&
            null != this.reserveVolumeDescriptorSequence &&
            null != this.integritySequenceExtent         &&
            null != this.unallocatedSpaceBitmap          &&
            0    != this.unallocatedSpace.size()         &&
            null != this.partitionStartingLocation       &&
            null != this.partitionLength                 &&
            null != this.partitionFreeSpace              &&
            null != this.uniqueID                        &&
            null != this.fileSetDescriptor               &&
            null != this.rootDirectoryICB                &&
            null != this.filesAndDirsPBnum               &&
            null != this.filesAndDirsSize                &&
            null != this.freeBlocksPBnum;
    }

    final static short PART_REF_NUM = 0;
    final static short FILE_VERSION_1 = 1;

    final static byte WRITEPROTECT_FLAGS = (byte)(
            EntityIdentifier.OSTASuffix.DOMAINFLAG_HARD_WRITE_PROTECT |
            EntityIdentifier.OSTASuffix.DOMAINFLAG_SOFT_WRITE_PROTECT);

    final static String PARENT_DIR_NAME = "";

    final static Log _log = new Log("udfwriter");

    ///////////////////////////////////////////////////////////////////////////

    long nextUniqueFileEntryID() {
        if (++this.uniqueFileEntryID < MIN_UNIQUE_ID) {
            this.uniqueFileEntryID = MIN_UNIQUE_ID;
        }
        return this.uniqueFileEntryID;
    }

    void makeVolumeID() {
        this.volumeID = this.layout.label();

        this.volumeID = null == this.volumeID ? "UDFVolume" :
              normalizeVolumeID(this.volumeID);
    }

    static String normalizeVolumeID(String vid) {
        final int MAXLEN = (PrimaryVolumeDescriptor.VOL_ID_LEN / 2) - 1;

        final char[] res = vid.substring(0, Math.min(MAXLEN, vid.length())).toCharArray();

        for (int i = 0; i < res.length; i++) {
            char c = res[i];
            if (' ' > c || c > 127) {
                res[i] = '_';
            }
        }

        return new String(res).trim();
    }

    ///////////////////////////////////////////////////////////////////////////

    final static long LIMIT_MAX_BLOCKS             = Integer.MAX_VALUE;
    final static long LIMIT_DIRECTORY_SIZE_DEFAULT = Integer.MAX_VALUE;
          static long LIMIT_DIRECTORY_SIZE         = LIMIT_DIRECTORY_SIZE_DEFAULT;

    protected static void __TEST_limitDirectorySize(final long l) {
        LIMIT_DIRECTORY_SIZE = -1 == l ? LIMIT_DIRECTORY_SIZE_DEFAULT : l;
    }

    protected static Clock _clock = Clock._system;

    ///////////////////////////////////////////////////////////////////////////

    final static int              VERSION_HI        = 1;
    final static int              VERSION_LO        = 0;
    final static EntityIdentifier IMPLEMENTATION_ID = new EntityIdentifier("*UDFWriter");
    static {
        IMPLEMENTATION_ID.flags            = 0;
        IMPLEMENTATION_ID.identifierSuffix = new EntityIdentifier.ImplSuffix(
                EntityIdentifier.OSInfo.OSCLASS_UNIX,   // TODO: UNIX for now, maybe flexible later on
                EntityIdentifier.OSInfo.OSID_UNIX_GENERIC,
                null).data();
    }

    ///////////////////////////////////////////////////////////////////////////

    final byte[] clearBlock() {
        Arrays.fill(this.block, (byte)0);
        return this.block;
    }

    final long bytesToBlocks(long sz) {
        return sz / this.layout.blockSize() + (0 == sz % this.layout.blockSize() ? 0 : 1);
    }

    final int alignToBlock(int sz) {
        int bsz  = this.layout.blockSize();
        int result = sz + bsz - 1;
        return result - (result % bsz);
    }

    ///////////////////////////////////////////////////////////////////////////

    public UDFWriter(FileRegistrar freg, Properties props) {
        super(freg, props);
    }

    ///////////////////////////////////////////////////////////////////////////

    public long resolve(Layout layout) throws IOException {
        try {
            this.layout    = layout;
            this.resolving = true;

            makeVolumeID();

            if (-1L != this.blocksTotal) {
                return this.blocksTotal;
            }

            make(new BlockDeviceImpl.NullWriteDevice(layout.blockSize()), null);

            if (!checkResolved()) {
                throw new Error("checkResolved() failed");
            }

            if (this.blocksTotal > LIMIT_MAX_BLOCKS) {
                throw new Exception(ERROR_TOO_MUCH_DATA,
                            "volume size exceeds the maximum by %d block(s)",
                            this.blocksTotal - LIMIT_MAX_BLOCKS);
            }

            return this.blocksTotal;
        }
        finally {
            this.resolving = false;
        }
    }

    ///////////////////////////////////////////////////////////////////////////

    public void make(BlockDevice bdev, Progress progress) throws IOException {
        if (this.layout.blockSize() != bdev.blockSize()) {
            throw new Error("block size changed: " + bdev.blockSize());
        }

        this.progress = progress;

        this.bdev = bdev;
        this.bnum = 0L;

        this.block    = new byte[bdev.blockSize()];
        this.embedBuf = new BytePtr(this.block.clone());

        this.uniqueFileEntryID = ROOT_FILENTRY_UID;

        if (this.resolving) {
            calculateDirectoriesSize();
            this.filesAndDirsSize = calculatePositions(this.freg.root(), 0);
        }
        else {
            this.latestTime          = MiscUtils.calendarFromMillis(_clock.now());
            this.volumeSetIdentifier = String.format("%08XUDFVolumeSet", this.rnd.nextInt());
        }

        writeInitialSpace();
        writeVolumeStructureDescriptors();
        writeAnchorVolumeDescriptorPointer();
        writeVolumeDescriptorSequence(true);
        writeIntegritySequence();

        if (this.resolving) {
            this.partitionStartingLocation = (int)this.bnum;
        }

        writeFileSetDescriptor();

        if (this.resolving) {
            this.rootDirectoryICB = new AllocationDescriptor.Long();
            this.rootDirectoryICB.type     = AllocationDescriptor.ExtentType.RECORDED_AND_ALLOCATED;
            this.rootDirectoryICB.length   = this.layout.blockSize();
            this.rootDirectoryICB.location = new RecordedAddress((int)pbnum(), PART_REF_NUM);
        }

        if (this.resolving) {
            this.bnum += this.filesAndDirsSize;
            for (int i = 0, c = this.numberOfDirectories +
                                this.numberOfFiles; i < c; i++) {
                nextUniqueFileEntryID();
            }
        }
        else {
            writeDirectory(this.freg.root());
        }
        writeFreeBlocks();
        writeSpaceBitmap();

        if (this.resolving) {
            this.uniqueID = nextUniqueFileEntryID();

            this.partitionLength = (int)(this.bnum -
                    BinUtils.u32ToLng(this.partitionStartingLocation));
        }

        writeVolumeDescriptorSequence(false);
        writeAnchorVolumeDescriptorPointer();

        this.blocksTotal = this.bnum;
    }

    ///////////////////////////////////////////////////////////////////////////

    void writeInitialSpace() throws IOException {
        int c = VOLUME_SPACE_INIT_SIZE / this.bdev.blockSize();

        _log.infof("writing %d blocks of initial space...", c);

        final byte[] empty = clearBlock();

        long end = this.bnum + c;

        while (this.bnum < end) {
            this.bdev.write(this.bnum++, empty, 0);
        }
    }

    ///////////////////////////////////////////////////////////////////////////

    void writeVolumeStructureDescriptors() throws IOException {
        VolumeStructureDescriptor vsd = new VolumeStructureDescriptor();
        byte[] block = clearBlock();

        _log.info("writing volume structure descriptors...");

        for (String sid : new String[] {
                VolumeStructureDescriptor.BEA01,
                VolumeStructureDescriptor.NSR02,
                VolumeStructureDescriptor.TEA01
        }) {
            vsd.standardIdentifier = sid;
            vsd.structureType      = 0;
            vsd.structureVersion   = 1;
            vsd.write(block, 0);
            this.bdev.write(this.bnum++, block, 0);

            block = clearBlock();
            for (int i = 0; i < 3; i++)
            {   // NOTE: not sure if we need these gaps on larger block sizes
                this.bdev.write(this.bnum++, block, 0);
            }
        }

        if (this.resolving) {
            long rest = AnchorVolumeDescriptorPointer.LOCATION - this.bnum;
            this.unallocatedSpace.add(new ExtentDescriptor(
                    (int)rest  * this.layout.blockSize(), (int)this.bnum));
            this.bnum = AnchorVolumeDescriptorPointer.LOCATION;
        }
        else {
            while (this.bnum < AnchorVolumeDescriptorPointer.LOCATION) {
                this.bdev.write(this.bnum++, block, 0);
            }
        }
    }

    ///////////////////////////////////////////////////////////////////////////

    void writeAnchorVolumeDescriptorPointer() throws IOException {
        if (!this.resolving) {
            AnchorVolumeDescriptorPointer avdp = new
            AnchorVolumeDescriptorPointer((int)this.bnum);

            avdp.mainVolumeDescriptorSequence    = this.mainVolumeDescriptorSequence;
            avdp.reserveVolumeDescriptorSequence = this.reserveVolumeDescriptorSequence;

            byte[] block = clearBlock();
            avdp.write(block, 0);
        }
        this.bdev.write(this.bnum++, this.block, 0);
    }

    ///////////////////////////////////////////////////////////////////////////

    void writeVolumeDescriptorSequence(boolean main) throws IOException {
        final int VDS_LEN = 16;

        long bnum0 = this.bnum;

        ExtentDescriptor ed = new ExtentDescriptor(
                this.bdev.blockSize() * VDS_LEN, (int)this.bnum);

        if (main) this.mainVolumeDescriptorSequence    = ed;
        else      this.reserveVolumeDescriptorSequence = ed;

        if (this.resolving) {
            this.bnum += 6;
        }
        else {
            writePrimaryVolumeDescriptor          (1);
            writeLogicalVolumeDescriptor          (2);
            writePartitionDescriptor              (3);
            writeUnallocatedSpaceDescriptor       (4);
            writeImplementationUseVolumeDescriptor(5);
            writeTerminatingDescriptor();
        }

        int rest = VDS_LEN - (int)(this.bnum - bnum0);

        if (this.resolving) {
            this.bnum += rest;
        }
        else {
            byte[] block = clearBlock();
            for (int i = 0; i < rest; i++) {
                this.bdev.write(this.bnum++, block, 0);
            }
        }
    }

    ///////////////////////////////////////////////////////////////////////////

    void writeIntegritySequence() throws IOException {
        final int ISE_LEN = 16;

        if (this.resolving) {
            this.integritySequenceExtent = new ExtentDescriptor(
                    this.layout.blockSize() * ISE_LEN,
                    (int)this.bnum);
            this.bnum += ISE_LEN;
        }
        else {
            long bnum0 = this.bnum;

            LogicalVolumeHeaderDescriptor lvhd = new LogicalVolumeHeaderDescriptor();
            lvhd.uniqueID = this.uniqueID;
            lvhd.reserved = new BytePtr(new byte[LogicalVolumeHeaderDescriptor.RESV_LEN]);

            byte[] block = clearBlock();

            LogicalVolumeIntegrityDescriptor lvid = new LogicalVolumeIntegrityDescriptor((int)this.bnum);

            lvid.recordingDateAndTime      = Timestamp.fromCalendar(this.latestTime);
            lvid.integrityType             = LogicalVolumeIntegrityDescriptor.TYPE_CLOSE;
            lvid.nextIntegrityExtent       = ExtentDescriptor.NONE;
            lvid.logicalVolumeContentsUse  = lvhd.data();
            lvid.numberOfPartitions        = 1;
            lvid.lengthOfImplementationUse = LogicalVolumeIntegrityDescriptor.UDF_IMPL_USE_LEN;
            lvid.freeSpaceTable            = new int[] { this.partitionFreeSpace };
            lvid.sizeTable                 = new int[] { this.partitionLength };
            lvid.entityID                  = IMPLEMENTATION_ID;
            lvid.numberOfFiles             = this.numberOfFiles;
            lvid.numberOfDirectories       = this.numberOfDirectories;
            lvid.minimumUDFReadRevision    =
            lvid.minimumUDFWriteRevision   =
            lvid.maximumUDFWriteRevision   = 0x0102;
            lvid.implementationUse         = new BytePtr(new byte[0]);

            lvid.write(block, 0);
            this.bdev.write(this.bnum++, this.block, 0);

            writeTerminatingDescriptor();

            long rest = Math.max(0, ISE_LEN - (this.bnum - bnum0));
            block = clearBlock();
            for (long i = 0; i < rest; i++) {
                this.bdev.write(this.bnum++, block, 0);
            }
        }
    }

    ///////////////////////////////////////////////////////////////////////////

    void writeFileSetDescriptor() throws IOException {
        if (this.resolving) {
            AllocationDescriptor.Long adl = new AllocationDescriptor.Long();
            adl.type     = AllocationDescriptor.ExtentType.RECORDED_AND_ALLOCATED;
            adl.length   = this.layout.blockSize();
            adl.location = new RecordedAddress((int)pbnum(), PART_REF_NUM);

            this.fileSetDescriptor = adl;
            this.bnum += 1;

            this.filesAndDirsPBnum = (int)pbnum();
        }
        else {
            byte[] block = clearBlock();

            EntityIdentifier.OSTASuffix osfx = new
            EntityIdentifier.OSTASuffix();
            osfx.udfRevision = EntityIdentifier.RevisionSuffix.UDF_REVISION_102;
            osfx.domainFlags = this.layout.writeProtect() ? WRITEPROTECT_FLAGS : 0;

            FileSetDescriptor fsd = new FileSetDescriptor((int)pbnum());
            fsd.recordingDateAndTime                = Timestamp.fromCalendar(this.latestTime);
            fsd.interchangeLevel                    = 3;
            fsd.maximumInterchangeLevel             = 3;
            fsd.characterSetList                    = 1;
            fsd.maximumCharacterSetList             = 1;
            fsd.fileSetNumber                       = 0;
            fsd.fileSetDescriptorNumber             = 0;
            fsd.logicalVolumeIdentifierCharacterSet = CharacterSet.OSTA_COMPRESSED_UNICODE;
            fsd.logicalVolumeIdentifier             = this.volumeID;
            fsd.fileSetCharacterSet                 = CharacterSet.OSTA_COMPRESSED_UNICODE;
            fsd.fileSetIdentifier                   = "UDF Volume Set";
            fsd.copyrightFileIdentifier             = ""; // TODO: we could create these, but
            fsd.abstractFileIdentifier              = ""; //       does it make sense for r/w?
            fsd.rootDirectoryICB                    = this.rootDirectoryICB;
            fsd.domainIdentifier                    = new EntityIdentifier(EntityIdentifier.ID_OSTA);
            fsd.domainIdentifier.flags              = 0;
            fsd.domainIdentifier.identifierSuffix   = osfx.data();
            fsd.nextExtent                          = AllocationDescriptor.Long.ZERO;
            fsd.systemStreamDirectoryICB            = AllocationDescriptor.Long.ZERO;

            fsd.write(block, 0);
            this.bdev.write(this.bnum++, block, 0);

            // 167/42.3.1 says we have to do this, but hasn't been seen in the wild
            // NOTE: apparently not necessary, after running the UDF verifier
            //writeTerminatingDescriptor();
        }
    }

    ///////////////////////////////////////////////////////////////////////////

    void writePrimaryVolumeDescriptor(int seqnum) throws IOException {
        byte[] block = clearBlock();

        PrimaryVolumeDescriptor pvd = new PrimaryVolumeDescriptor((int)this.bnum);

        pvd.volumeDescriptorSequenceNumber            = seqnum;
        pvd.primaryVolumeDescriptorNumber             = 0;
        pvd.volumeIdentifier                          = this.volumeID;
        pvd.volumeSequenceNumber                      = 1;
        pvd.maximumVolumeSequenceNumber               = 1;
        pvd.interchangeLevel                          = 2;
        pvd.maximumInterchangeLevel                   = 2;
        pvd.characterSetList                          = 1;
        pvd.maximumCharacterSetList                   = 1;
        pvd.volumeSetIdentifier                       = this.volumeSetIdentifier;
        pvd.descriptorCharacterSet                    = CharacterSet.OSTA_COMPRESSED_UNICODE;
        pvd.explanatoryCharacterSet                   = CharacterSet.OSTA_COMPRESSED_UNICODE;
        pvd.volumeAbstract                            = ExtentDescriptor.NONE;
        pvd.volumeCopyrightNotice                     = ExtentDescriptor.NONE;
        pvd.applicationIdentifier                     = new EntityIdentifier().noid();
        pvd.recordingDateAndTime                      = Timestamp.fromCalendar(this.latestTime);
        pvd.implementationIdentifier                  = IMPLEMENTATION_ID;
        pvd.implementationUse                         = new BytePtr(new byte[PrimaryVolumeDescriptor.IMPL_USE_LEN]);
        pvd.predecessorSequenceLocation               = 0;
        pvd.flags                                     = PrimaryVolumeDescriptor.FLAG_VSI_COMMON;
        pvd.reserved                                  = new BytePtr(new byte[PrimaryVolumeDescriptor.RESV_LEN]);

        pvd.write(block, 0);
        this.bdev.write(this.bnum++, block, 0);
    }

    void writeLogicalVolumeDescriptor(int seqnum) throws IOException {
        byte[] block = clearBlock();

        EntityIdentifier.OSTASuffix osfx = new
        EntityIdentifier.OSTASuffix();
        osfx.udfRevision = EntityIdentifier.RevisionSuffix.UDF_REVISION_102;
        osfx.domainFlags = this.layout.writeProtect() ? WRITEPROTECT_FLAGS : 0;

        PartitionMap.Type1 pmap = new PartitionMap.Type1();
        pmap.volumeSequenceNumber = 1;
        pmap.partitionNumber      = 0;

        LogicalVolumeDescriptor lvd = new LogicalVolumeDescriptor((int)this.bnum);

        lvd.volumeDescriptorSequenceNumber    = seqnum;
        lvd.descriptorCharacterSet            = CharacterSet.OSTA_COMPRESSED_UNICODE;
        lvd.logicalVolumeIdentifier           = this.volumeID;
        lvd.logicalBlockSize                  = this.bdev.blockSize();
        lvd.domainIdentifier                  = new EntityIdentifier(EntityIdentifier.ID_OSTA);
        lvd.domainIdentifier.flags            = 0;
        lvd.domainIdentifier.identifierSuffix = osfx.data();
        lvd.logicalVolumeContentsUse          = this.fileSetDescriptor.data();
        lvd.mapTableLength                    = pmap.length;
        lvd.numberOfPartitionMaps             = 1;
        lvd.implementationIdentifier          = IMPLEMENTATION_ID;
        lvd.implementationUse                 = new BytePtr(new byte[LogicalVolumeDescriptor.IMPL_USE_LEN]);
        lvd.integritySequenceExtent           = this.integritySequenceExtent;
        lvd.partitionMaps                     = new BytePtr(new byte[PartitionMap.Type1.LENGTH]);

        pmap.write(lvd.partitionMaps.buf,
                   lvd.partitionMaps.ofs);

        lvd.write(block, 0);
        this.bdev.write(this.bnum++, block, 0);
    }

    void writePartitionDescriptor(int seqnum) throws IOException {
        byte[] block = clearBlock();

        PartitionHeaderDescriptor phd = new PartitionHeaderDescriptor();
        phd.unallocatedSpaceBitmap  = this.unallocatedSpaceBitmap;
        phd.unallocatedSpaceTable   = AllocationDescriptor.Short.ZERO;
        phd.freedSpaceTable         = AllocationDescriptor.Short.ZERO;
        phd.freedSpaceBitmap        = AllocationDescriptor.Short.ZERO;
        phd.partitionIntegrityTable = AllocationDescriptor.Short.ZERO;

        PartitionDescriptor pd = new PartitionDescriptor((int)this.bnum);

        pd.volumeDescriptorSequenceNumber     = seqnum;
        pd.partitionFlags                     = PartitionDescriptor.FLAG_VOLUME_SPACE_ALLOCATED;
        pd.partitionNumber                    = 0;
        pd.partitionContents                  = new EntityIdentifier(EntityIdentifier.ID_NSR02);
        pd.partitionContents.flags            = 0;
        pd.partitionContents.identifierSuffix = new BytePtr(new byte[EntityIdentifier.Suffix.LENGTH]);
        pd.partitionContentsUse               = phd;
        pd.accessType                         = PartitionDescriptor.AccessType.OVERWRITABLE;
        pd.partitionStartingLocation          = this.partitionStartingLocation;
        pd.partitionLength                    = this.partitionLength;
        pd.implementationIdentifier           = IMPLEMENTATION_ID;
        pd.implementationUse                  = new BytePtr(new byte[PartitionDescriptor.IMPL_USE_LEN]);
        pd.reserved                           = new BytePtr(new byte[PartitionDescriptor.RESV_USE_LEN]);

        pd.write(block, 0);
        this.bdev.write(this.bnum++, block, 0);
    }

    void writeUnallocatedSpaceDescriptor(int seqNum) throws IOException {
        byte[] block = clearBlock();

        UnallocatedSpaceDescriptor usd = new UnallocatedSpaceDescriptor((int)this.bnum);

        usd.volumeDescriptorSequenceNumber = seqNum;
        usd.allocationDescriptors = this.unallocatedSpace.toArray(new ExtentDescriptor[
                                    this.unallocatedSpace.size()]);

        usd.write(block, 0);
        this.bdev.write(this.bnum++, block, 0);
    }

    void writeImplementationUseVolumeDescriptor(int seqNum) throws IOException {
        byte[] block = clearBlock();

        EntityIdentifier.UDFSuffix usfx = new
        EntityIdentifier.UDFSuffix();
        usfx.udfRevision  = RevisionSuffix.UDF_REVISION_102;
        usfx.osClass      = OSInfo.OSCLASS_UNIX;  // FIXME: good choice? WinNT(6) is not defined in UDF 1.02!
        usfx.osIdentifier = OSInfo.OSID_UNIX_GENERIC;
        usfx.reserved     = new BytePtr(new byte[EntityIdentifier.UDFSuffix.RESV_LEN]);

        ImplementationUseVolumeDescriptor iuvd = new ImplementationUseVolumeDescriptor((int)this.bnum);

        iuvd.volumeDescriptorSequenceNumber            = seqNum;
        iuvd.implementationIdentifier                  = new EntityIdentifier(EntityIdentifier.ID_UDF);
        iuvd.implementationIdentifier.flags            = 0;
        iuvd.implementationIdentifier.identifierSuffix = usfx.data();
        iuvd.lviCharset                                = CharacterSet.OSTA_COMPRESSED_UNICODE;
        iuvd.logicalVolumeIdentifier                   = this.volumeID;
        iuvd.lvInfo1                                   = "UDFWriter " + VERSION_HI + "." + VERSION_LO;
        iuvd.lvInfo2                                   = "";  // TODO: build info? anything?
        iuvd.lvInfo3                                   = "";
        iuvd.implementationID                          = IMPLEMENTATION_ID;
        iuvd.implementationUse                         = new BytePtr(new byte[ImplementationUseVolumeDescriptor.IMPL_USE_LEN]);

        iuvd.write(block, 0);
        this.bdev.write(this.bnum++, block, 0);
    }

    void writeTerminatingDescriptor() throws IOException {
        byte[] block = clearBlock();

        TerminatingDescriptor td = new TerminatingDescriptor((int)this.bnum);
        td.reserved = new BytePtr(new byte[TerminatingDescriptor.RESV_LEN]);

        td.write(block, 0);
        this.bdev.write(this.bnum++, block, 0);
    }

    ///////////////////////////////////////////////////////////////////////////

    void makeFileEntry(byte[] block,
                       String name,
                       boolean isDirectory,
                       BytePtr embed,
                       int links,
                       long length,
                       long timestamp) throws IOException {
        int icbFlags = null == embed ? ICBTag.ALLOCDESC_SHORT :
                                       ICBTag.ALLOCDESC_EMBEDDED;

        // TODO: we could set the system flag here (Vista does it)
        icbFlags |= ICBTag.FLAG_ARCHIVE;

        ICBTag.FileType icbFileType = isDirectory ?
                ICBTag.FileType.DIRECTORY:
                ICBTag.FileType.RANDOM_ACCESS_BYTE_SEQ;

        Calendar cal = MiscUtils.calendarFromMillis(timestamp);  // TODO: cache that one?
        Timestamp tstamp = Timestamp.fromCalendar(cal);

        FileEntry.Standard fe = new FileEntry.Standard((int)pbnum()); // address is local to partition!

        fe.icbTag = new ICBTag();
        fe.icbTag.priorRecordedNumberOfDirectEntries = 0;
        fe.icbTag.strategyType                       = ICBTag.STRATEGY_UDF102;
        fe.icbTag.strategyParameter                  = ICBTag.SPARAMS_ZERO;
        fe.icbTag.maximumNumberOfEntries             = 1;
        fe.icbTag.reserved                           = 0;
        fe.icbTag.fileType                           = icbFileType;
        fe.icbTag.parentICBLocation                  = RecordedAddress.ZERO; // TODO: find out why nobody uses that
        fe.icbTag.flags                              = (short)icbFlags;
        fe.uid                                       = 0;      // 'root' owner
        fe.gid                                       = 0;      // 'root' group
        fe.permissions                               = 0x7fff; // all permissions for everybody for now
        fe.fileLinkCount                             = (short)links;
        fe.recordFormat                              = FileEntry.RecordFormat.NOT_SPECIFIED;
        fe.recordDisplayAttributes                   = FileEntry.RecordDisplayAttribute.NOT_SPECIFIED;
        fe.recordLength                              = 0;
        fe.informationLength                         = length;
        fe.logicalBlocksRecorded                     = null == embed ? bytesToBlocks(length) : 0;
        fe.accessDateAndTime                         = tstamp;  // TODO: use the real one if possible
        fe.modificationDateAndTime                   = tstamp;  // TODO: use the real one if possible
        fe.attributeDateAndTime                      = tstamp;  // TODO: use the real one if possible
        fe.checkpoint                                = 1;
        fe.extendedAttributeICB                      = AllocationDescriptor.Long.ZERO;
        fe.implementationIdentifier                  = IMPLEMENTATION_ID;
        fe.uniqueID                                  = 0 == name.length() ? ROOT_FILENTRY_UID : nextUniqueFileEntryID(); // TODO: better check for root?
        fe.lengthOfExtendedAttributes                = 0; // no extended attributes for now
        fe.extendedAttributes                        = BytePtr.NO_DATA;

        if (null == embed) {
            int chunkSize = AllocationDescriptor.maxLength(this.layout.blockSize());

            int adsLen = (int)((length / chunkSize) +
                         (0 == (length % chunkSize) ? 0 : 1)) * AllocationDescriptor.Short.LENGTH;

            byte[] ads = new byte[adsLen];

            final int blocksPerChunk = chunkSize / this.layout.blockSize();

            AllocationDescriptor.Short ad = new AllocationDescriptor.Short();

            // assuming that the data follows right after the file entry...

            ad.position = (int)(pbnum() + 1L);
            ad.type = AllocationDescriptor.ExtentType.RECORDED_AND_ALLOCATED;

            for (int ofs = 0; length > 0;
                     ofs += AllocationDescriptor.Short.LENGTH) {
                ad.length = chunkSize < length ? chunkSize : (int)length;
                ad.write(ads, ofs);

                ad.position += blocksPerChunk;
                length -= ad.length;
            }

            fe.lengthOfAllocationDescriptors = adsLen;
            fe.allocationDescriptors = new BytePtr(ads);
        }
        else {
            fe.lengthOfAllocationDescriptors = (int)length;
            fe.allocationDescriptors = embed;
        }

        fe.write(block, 0);
    }

    void writeDirectory(final Directory dir) throws IOException {
        writeDirectoryStream(dir);

        Iterator<Directory> itd = dir.dirs();
        while (itd.hasNext()) {
            writeDirectory(itd.next());
        }

        writeFileStreams(dir);
    }

    void writeDirectoryStream(final Directory dir) throws IOException {
        final DirectoryTag dtag = (DirectoryTag)dir.nodes()[0].getTag(DirectoryTag.NAME);
        final int blockSize = this.layout.blockSize();

        byte[] buf = new byte[this.layout.blockSize() << 1];

        int links = 1;
        for (Iterator<Directory> i = dir.dirs(); i.hasNext(); links++, i.next());

        FileNode nd = dir.nodes()[0]; // might encounter the root node here
        long tstamp;
        if (null == nd) {
            tstamp = this.latestTime.getTimeInMillis();
        }
        else {
            onTimestamp(tstamp = nd.timestamp());
        }

        makeFileEntry(buf,
                      null == nd ? "" : dir.nodes()[0].name(),
                      true,
                      null,
                      links,
                      dtag.size,
                      tstamp);

        this.bdev.write(this.bnum++, buf, 0);

        // TODO: we could embed the stream (if it fits)

        DirectoryTag ptag = null == dir.parent() ? dtag : (DirectoryTag)dir.parent().nodes()[0].getTag(DirectoryTag.NAME);

        assert(posToPBNum(dtag.position) == pbnum());

        FileIdentifierDescriptor fid = new FileIdentifierDescriptor((int)pbnum());

        fid.fileVersionNumber         = FILE_VERSION_1;
        fid.fileCharacteristics       = FileIdentifierDescriptor.FCBIT_DIRECTORY |
                                        FileIdentifierDescriptor.FCBIT_PARENT;
        fid.lengthOfFileIdentifier    = -1;
        fid.icb                       = new AllocationDescriptor.Long();
        fid.icb.type                  = AllocationDescriptor.ExtentType.RECORDED_AND_ALLOCATED;
        fid.icb.length                = blockSize;
        fid.icb.implementationUse     = null;
        fid.icb.location              = new RecordedAddress((int)posToPBNum(ptag.position), PART_REF_NUM);
        fid.lengthOfImplementationUse = 0;
        fid.implementationUse         = new BytePtr(new byte[0]);
        fid.paddingBytes              = null;
        fid.setFileIdentifier(null);

        int bufOfs = fid.write(buf, 0);

        Iterator<Directory> itd = dir.dirs();
        while (itd.hasNext()) {
            Directory dir2 = itd.next();
            DirectoryTag dtag2 = (DirectoryTag)dir2.nodes()[0].getTag(DirectoryTag.NAME);

            fid.fileCharacteristics = FileIdentifierDescriptor.FCBIT_DIRECTORY;
            fid.icb.location        = new RecordedAddress((int)posToPBNum(dtag2.position), PART_REF_NUM);

            try {
                fid.setFileIdentifier(dir2.nodes()[0].name());
            }
            catch (UDFException ue) {
                throw new Exception(ERROR_NAME_TOO_LONG, "%s", ue.getMessage());
            }

            bufOfs = fid.write(buf, bufOfs);
            if (bufOfs >= blockSize) {
                this.bdev.write(this.bnum++, buf, 0);
                bufOfs -= blockSize;
                System.arraycopy(buf, blockSize, buf, 0, bufOfs);
                fid.tag.location = (int)pbnum();
            }
        }

        Iterator<FileNode> itf = dir.files();
        fid.fileCharacteristics = 0;

        while (itf.hasNext()) {
            FileNode fnd = itf.next();
            Long fpos = (Long)fnd.getTag(POS_TAG_NAME);

            fid.icb.location = new RecordedAddress((int)posToPBNum(fpos), PART_REF_NUM);

            try {
                fid.setFileIdentifier(fnd.name());
            }
            catch (UDFException ue) {
                throw new Exception(ERROR_NAME_TOO_LONG, "%s", ue.getMessage());
            }

            bufOfs = fid.write(buf, bufOfs);
            if (bufOfs >= blockSize) {
                this.bdev.write(this.bnum++, buf, 0);
                bufOfs -= blockSize;
                System.arraycopy(buf, blockSize, buf, 0, bufOfs);
                fid.tag.location = (int)pbnum();
            }
        }
        if (0 < bufOfs) {
            Arrays.fill(buf, bufOfs, blockSize, (byte)0);
            this.bdev.write(this.bnum++, buf, 0);
        }
    }

    void writeFileStreams(final Directory dir) throws IOException {
        Iterator<FileNode> itf = dir.files();

        while (itf.hasNext()) {
            FileNode fn = itf.next();

            this.progress.onFile(dir, fn);

            long expected = posToPBNum((Long)fn.getTag(POS_TAG_NAME));
            if  (expected != pbnum()) {
                throw new Exception(ERROR_INTERNAL_4,
                        "UDFWriter.FILEPOS_MISALIGN_2",
                        expected, pbnum());
            }

            InputStream ins = null;
            try {
                ins = new BufferedInputStream(fn.fileSystem().openRead(fn));

                BytePtr embed = null;
                int emax = maxEmbedSize(fn);
                if (emax >= fn.size()) {
                    this.embedBuf.len = IOUtils.readAll(
                            ins,
                            this.embedBuf.buf,
                            this.embedBuf.ofs = 0,
                            emax);

                    if (this.embedBuf.len != fn.size() || -1 != ins.read()) {
                        throw new Exception(ERROR_INTERNAL_1,
                                "small size mismatch for file '%s'", fn.path(true));
                    }

                    embed = this.embedBuf;
                    embed.ofs = 0;
                }

                final byte[] block = clearBlock();
                final long fsz = fn.size();

                long tstamp = fn.timestamp();
                onTimestamp(tstamp);

                makeFileEntry(block,
                        fn.name(),
                        false,
                        embed,
                        1,
                        fsz,
                        tstamp);

                //System.out.printf("file entry '%s' written @ %d\n", fn.name(), this.bnum);

                this.bdev.write(this.bnum++, block, 0);

                if (null == embed) {
                    long rsz = 0L;

                    for (boolean done = false; !done;) {
                        int toread = (int)Math.min(fsz - rsz, block.length);

                        int read = IOUtils.readAll(ins, block, 0, toread);

                        if (toread > read) {
                            throw new Exception(ERROR_FILE_SIZE_CHANGED_LO,
                                    "file '%s' smaller than expected",
                                    fn.path(true));
                        }
                        rsz += read;

                        if (rsz > fsz) {
                            throw new Exception(ERROR_INTERNAL_2,
                                    "over-read-inconsistency (%d > %d)", rsz, fsz);
                        }

                        done = rsz == fsz;
                        if (done) {
                            Arrays.fill(block, read, block.length, (byte)0);
                        }

                        this.bdev.write(this.bnum++, block, 0);
                    }

                    if (-1 != ins.read()) {
                        throw new Exception(ERROR_FILE_SIZE_CHANGED_HI,
                            "file '%s' bigger than expected",
                            fn.path(true));
                    }
                }
            }
            finally {
                if (null != ins) {
                    ins.close();
                }
            }
        }
    }

    void writeFreeBlocks() throws IOException {
        final long freeBlocks = this.layout.freeBlocks();
        if (this.resolving) {
            this.freeBlocksPBnum = pbnum();
        }
        else {
            if (0 < freeBlocks) {
                this.progress.onFile(null, null);
            }
            byte[] block = clearBlock();
            for (long i = 0; i < freeBlocks; i++) {
                this.bdev.write(this.bnum + i, block, 0);
            }
        }
        this.bnum += freeBlocks;
    }

    void writeSpaceBitmap() throws IOException {
        byte[] block = clearBlock();

        final long used =           this.freeBlocksPBnum;
        final long free = pbnum() - this.freeBlocksPBnum;

        this.partitionFreeSpace = (int)free;

        SpaceBitmapDescriptor sbd = new SpaceBitmapDescriptor((int)pbnum());

        int pos;

        if (this.resolving) {
            pos = sbd.write(block, 0);

            int sz = computeUnallocatedSpaceBitmapLength(pos, used + free);

            this.unallocatedSpaceBitmap = new AllocationDescriptor.Short();
            this.unallocatedSpaceBitmap.type     = AllocationDescriptor.ExtentType.RECORDED_AND_ALLOCATED;
            this.unallocatedSpaceBitmap.length   = alignToBlock(sz);
            this.unallocatedSpaceBitmap.position = (int)pbnum();

            this.bnum += bytesToBlocks(sz);
            return;
        }

        int bmap = this.unallocatedSpaceBitmap.length / this.layout.blockSize();

        sbd.numberOfBits  = (int)(used + free + bmap);
        sbd.numberOfBytes = (sbd.numberOfBits >>> 3) +
                      (0 == (sbd.numberOfBits & 7) ? 0 : 1);

        pos = sbd.write(block, 0);

        BitBlockWriter bbw = new BitBlockWriter(block, pos, 0,
                             new BitBlockWriter.Callback() {
            public void onBlock(byte[] block) throws IOException {
                UDFWriter.this.bdev.write(
                UDFWriter.this.bnum++, block, 0);
            }
        });

        bbw.write(0, used);
        bbw.write(1, free);
        bbw.write(0, bmap);

        bbw.flush(0);
    }

    int computeUnallocatedSpaceBitmapLength(int dsc, long ubits) {
        final int BSZ = this.layout.blockSize();

        int result = -1;

        long sbits = Math.max(1, ubits >> 11);
        long sbits3 = -1L;
        long delta = sbits;
        for (;;) {
            long bits = ubits + sbits;
            long len = dsc + (int)(bits >> 3) + (0 == (7 & bits) ? 0 : 1);
            long sbits2 = bytesToBlocks(len);

            delta >>= 1;
            if (sbits > sbits2) {
                sbits3 = sbits2;
                result = (int)len;
                sbits -= delta;
            }
            else {
                sbits += delta;
            }
            if (0 == delta) {
                if (-1 == result) {
                    result = (int)len;
                }
                else if (sbits2 <= sbits3) {
                    if (sbits2 == sbits3) {
                        len--;
                    }
                    if ((dsc << 3) + ubits + ((len / BSZ) + ((0 == (len % BSZ)) ? 0 : 1)) <
                        bytesToBlocks(len) * (BSZ << 3)) {
                        result = (int)len;
                    }
                }
                break;
            }
        }
        return result;
    }

    ///////////////////////////////////////////////////////////////////////////

    final static String POS_TAG_NAME = "position.udfwriter";

    static class DirectoryTag {
        public long size     = -1L;
        public long position = -1L;

        final static String NAME = "directory.udfwriter";

        public static DirectoryTag get(Taggable tgbl) {
            Object tag = tgbl.getTag(NAME);
            if (null == tag) {
                DirectoryTag newTag = new DirectoryTag();
                tgbl.setTag(NAME, newTag);
                return newTag;
            }
            return (DirectoryTag)tag;
        }
    }

    ///////////////////////////////////////////////////////////////////////////

    final void calculateDirectorySize(Directory dir, int pathLen) throws Exception {
        long sz = 0;

        pathLen = checkPathLength(dir.nodes()[0], pathLen);

        try {
            sz += FileIdentifierDescriptor.size(PARENT_DIR_NAME, 0);

            Iterator<Directory> dirs = dir.dirs();
            while (dirs.hasNext()) {
                Directory dir2 = dirs.next();
                calculateDirectorySize(dir2, pathLen);
                sz += FileIdentifierDescriptor.size(dir2.nodes()[0].name(), 0);
            }

            Iterator<FileNode> files = dir.files();
            while (files.hasNext()) {
                FileNode fn = files.next();
                checkPathLength(fn, pathLen);
                sz += FileIdentifierDescriptor.size(fn.name(), 0);
            }
        }
        catch (UDFException ue) {
            throw new Exception(ERROR_NAME_TOO_LONG, "%s", ue.getMessage());
        }

        if (sz > LIMIT_DIRECTORY_SIZE) {
            throw new Exception(ERROR_DIRECTORY_TOO_LARGE,
                "size for directory '%s' beyond the limit",
                null == dir.nodes()[0] ? "" : dir.nodes()[0].name());
        }

        DirectoryTag.get(dir.nodes()[0]).size = sz;
    }

    void calculateDirectoriesSize() throws Exception {
        calculateDirectorySize(this.freg.root(), 0);
    }

    long calculatePositions(Directory dir, long pos) throws Exception {
        DirectoryTag dtag = DirectoryTag.get(dir.nodes()[0]);

        dtag.position = pos;
        this.numberOfDirectories++;

        // TODO: can a directory also be embedded safely?
        pos += 1 + bytesToBlocks(dtag.size);

        Iterator<Directory> dirs = dir.dirs();
        while (dirs.hasNext()) {
            pos = calculatePositions(dirs.next(), pos);
        }

        Iterator<FileNode> files = dir.files();
        while (files.hasNext()) {
            FileNode fn = files.next();

            fn.setTag(POS_TAG_NAME, pos);
            this.numberOfFiles++;

            pos++;
            if (maxEmbedSize(fn) < fn.size()) {
                long d = fn.size() - maxFileSize(fn);
                if (d > 0) {
                    throw new Exception(ERROR_FILE_TOO_LARGE,
                        "file '%s' is too large by %d bytes",
                        fn.path(true), d);
                }
                pos += bytesToBlocks(fn.size());
            }
        }
        return pos;
    }

    ///////////////////////////////////////////////////////////////////////////

    int getExtendedAttributesSize(FileNode fn) {
        return BytePtr.NO_DATA.len;
    }

    BytePtr getExtendedAttributes(FileNode fn) {
        return BytePtr.NO_DATA;
    }

    int maxEmbedSize(FileNode fn) {
        return FileEntry.Standard.maxEmbeddedAllocDescSize(
                this.layout.blockSize(),
                getExtendedAttributesSize(fn));
    }

    long maxFileSize(FileNode fn) {
        return FileEntry.Standard.maxFileSize(
                this.layout.blockSize(),
                getExtendedAttributesSize(fn));
    }

    ///////////////////////////////////////////////////////////////////////////

    final long posToPBNum(long pos) {
        return BinUtils.u32ToLng(this.filesAndDirsPBnum) + pos;
    }

    final long pbnum() {
        return this.bnum - BinUtils.u32ToLng(this.partitionStartingLocation);
    }

    ///////////////////////////////////////////////////////////////////////////

    void onTimestamp(long tm) {
        if (tm > this.latestTime.getTimeInMillis()) {
            this.latestTime.setTimeInMillis(tm);
        }
    }

    int checkPathLength(FileNode fn, int pathLen) throws Exception {
        if (null == fn || fn.hasAttributes(FileNode.ATTR_ROOT)) {
            return 0; // TODO: the root has no length? (assumption!)
        }
        // TODO: not sure if the maximum path length is in the dstring space
        // (Unicode characters count twice) or simply the number of characters
        final int result = 1 + pathLen + fn.name().length();
        if (result > MAX_PATH_LEN &&
            !__TEST_noPathLengthCheck) {
            throw new Exception(ERROR_PATH_TOO_LONG,
                "the length path for file '%s' exceeds the maximum of %d characters",
                fn.path(true), MAX_PATH_LEN);
        }
        return result;
    }

    public static boolean __TEST_noPathLengthCheck;
}
