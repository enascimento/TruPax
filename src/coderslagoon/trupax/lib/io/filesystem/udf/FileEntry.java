package coderslagoon.trupax.lib.io.filesystem.udf;

import coderslagoon.baselib.util.BinUtils;
import coderslagoon.baselib.util.BytePtr;

public abstract class FileEntry extends Descriptor {
    public int PERM_ALL_EXEC   = 0x0001;
    public int PERM_ALL_WRITE  = 0x0002;
    public int PERM_ALL_READ   = 0x0004;
    public int PERM_ALL_CHATTR = 0x0008;
    public int PERM_ALL_DELETE = 0x0010;
    public int PERM_GRP_EXEC   = 0x0020;
    public int PERM_GRP_WRITE  = 0x0040;
    public int PERM_GRP_READ   = 0x0080;
    public int PERM_GRP_CHATTR = 0x0100;
    public int PERM_GRP_DELETE = 0x0200;
    public int PERM_USR_EXEC   = 0x0400;
    public int PERM_USR_WRITE  = 0x0800;
    public int PERM_USR_READ   = 0x1000;
    public int PERM_USR_CHATTR = 0x2000;
    public int PERM_USR_DELETE = 0x4000;

    public enum RecordFormat {
        NOT_SPECIFIED,
        PADDED_FIXED_LENGTH_RECORDS,
        FIXED_LENGTH_RECORDS,
        VARIABLE_LENGTH_8_RECORDS,
        VARIABLE_LENGTH_16_RECORDS,
        VARIABLE_LENGTH_16_MSB_RECORDS,
        VARIABLE_LENGTH_32_RECORDS,
        STREAM_PRINT_RECORDS,
        STREAM_LF_RECORDS,
        STREAM_CR_RECORDS,
        STREAM_CRLF_RECORDS,
        STREAM_LFCR_RECORDS;

        public static RecordFormat fromOrdinal(int ord) throws UDFException {
            try {
                return values()[ord];
            }
            catch (ArrayIndexOutOfBoundsException aioobe) {
                throw new UDFException("invalid record format %d", ord);
            }
        }
    }

    public enum RecordDisplayAttribute {
        NOT_SPECIFIED,
        LF_CR,
        FIRST_BYTE_POSITION,
        IMPLIED;

        public static RecordDisplayAttribute fromOrdinal(int ord) throws UDFException {
            try {
                return values()[ord];
            }
            catch (ArrayIndexOutOfBoundsException aioobe) {
                throw new UDFException("invalid record display attribute %d", ord);
            }
        }
    }

    ///////////////////////////////////////////////////////////////////////////

    public ICBTag                    icbTag;
    public int                       uid;
    public int                       gid;
    public int                       permissions;
    public short                     fileLinkCount;
    public RecordFormat              recordFormat;
    public RecordDisplayAttribute    recordDisplayAttributes;
    public int                       recordLength;
    public long                      informationLength;
    public long                      logicalBlocksRecorded;
    public Timestamp                 accessDateAndTime;
    public Timestamp                 modificationDateAndTime;
    public Timestamp                 attributeDateAndTime;
    public int                       checkpoint;
    public AllocationDescriptor.Long extendedAttributeICB;
    public EntityIdentifier          implementationIdentifier;
    public long                      uniqueID;
    public int                       lengthOfExtendedAttributes;
    public int                       lengthOfAllocationDescriptors;
    public BytePtr                   extendedAttributes;
    public BytePtr                   allocationDescriptors;

    ///////////////////////////////////////////////////////////////////////////

    public FileEntry(int location) {
        super(new Tag(Tag.Identifier.FILE_ENTRY, location));
    }

    protected FileEntry(Tag tag, byte[] buf, int ofs) throws UDFException {
        super(tag);

        this.icbTag                  = ICBTag.parse(buf, ofs); ofs += ICBTag.LENGTH;
        this.uid                     = BinUtils.readInt32LE(buf, ofs);               ofs += 4;
        this.gid                     = BinUtils.readInt32LE(buf, ofs);               ofs += 4;
        this.permissions             = BinUtils.readInt32LE(buf, ofs);               ofs += 4;
        this.fileLinkCount           = BinUtils.readInt16LE(buf, ofs);               ofs += 2;
        this.recordFormat            = RecordFormat          .fromOrdinal(buf[ofs]); ofs++;
        this.recordDisplayAttributes = RecordDisplayAttribute.fromOrdinal(buf[ofs]); ofs++;
        this.recordLength            = BinUtils.readInt32LE(buf, ofs);               ofs += 4;
        this.informationLength       = BinUtils.readInt64LE(buf, ofs);               ofs += 8;

        ctor(buf, ofs);
    }

    protected abstract void ctor(byte[] buf, int ofs) throws UDFException;
    protected abstract String type();

    public abstract int length();

    @SuppressWarnings("unused")
	public int write(byte[] buf, int ofs) throws UDFException {
                                                                      ofs += Tag.LENGTH;
                              this.icbTag.write(           buf, ofs); ofs += ICBTag.LENGTH;
        BinUtils.writeInt32LE(this.uid                   , buf, ofs); ofs += 4;
        BinUtils.writeInt32LE(this.gid                   , buf, ofs); ofs += 4;
        BinUtils.writeInt32LE(this.permissions           , buf, ofs); ofs += 4;
        BinUtils.writeInt16LE(this.fileLinkCount         , buf, ofs); ofs += 2;
             buf[ofs] = (byte)this.recordFormat.ordinal();            ofs++;
             buf[ofs] = (byte)this.recordDisplayAttributes.ordinal(); ofs++;
        BinUtils.writeInt32LE(this.recordLength          , buf, ofs); ofs += 4;
        BinUtils.writeInt64LE(this.informationLength     , buf, ofs); ofs += 8;

        return ofs;
    }

    protected String tostr() {
        return String.format(
                "%s:tag=[%s],it=[%s],uid=%s,gid=%s,per=0x%04x,flc=%s,rf=%s,rda=%s,rl=%s,il=%s,",
                type(),
                this.tag,
                this.icbTag,
                BinUtils.u32ToLng(this.uid),
                BinUtils.u32ToLng(this.gid),
                this.permissions,
                BinUtils.u16ToInt(this.fileLinkCount),
                this.recordFormat,
                this.recordDisplayAttributes,
                BinUtils.u32ToLng(this.recordLength),
                this.informationLength);
    }

    public static class Standard extends FileEntry {
        public Standard(int location) {
            super(location);
        }

        public Standard(Tag tag, byte[] buf, int ofs) throws UDFException {
            super(tag, buf, ofs);
        }

        protected void ctor(byte[] buf, int ofs) throws UDFException {
            int lea, lad;

            this.logicalBlocksRecorded               = BinUtils.readInt64LE(buf, ofs);            ofs += 8;
            this.accessDateAndTime                   = Timestamp.parse(buf, ofs);                 ofs += Timestamp.LENGTH;
            this.modificationDateAndTime             = Timestamp.parse(buf, ofs);                 ofs += Timestamp.LENGTH;
            this.attributeDateAndTime                = Timestamp.parse(buf, ofs);                 ofs += Timestamp.LENGTH;
            this.checkpoint                          = BinUtils.readInt32LE(buf, ofs);            ofs += 4;
            this.extendedAttributeICB                = AllocationDescriptor.Long.parse(buf, ofs); ofs += AllocationDescriptor.Long.LENGTH;
            this.implementationIdentifier            = EntityIdentifier.parse(buf, ofs);          ofs += EntityIdentifier.LENGTH;
            this.uniqueID                            = BinUtils.readInt64LE(buf, ofs);            ofs += 8;
            this.lengthOfExtendedAttributes    = lea = BinUtils.readInt32LE(buf, ofs);            ofs += 4;
            this.lengthOfAllocationDescriptors = lad = BinUtils.readInt32LE(buf, ofs);            ofs += 4;
            this.extendedAttributes                  = new BytePtr.Checked(buf, ofs, lea);        ofs += lea;
            this.allocationDescriptors               = new BytePtr.Checked(buf, ofs, lad);
        }

        protected String type() {
            return "FE";
        }

        public int write(byte[] buf, int ofs) throws UDFException {
            int ofs0 = ofs;
            ofs = super.write(buf, ofs);

            BinUtils.writeInt64LE(this.logicalBlocksRecorded        , buf, ofs); ofs += 8;
                                  this.accessDateAndTime       .write(buf, ofs); ofs += Timestamp.LENGTH;
                                  this.modificationDateAndTime .write(buf, ofs); ofs += Timestamp.LENGTH;
                                  this.attributeDateAndTime    .write(buf, ofs); ofs += Timestamp.LENGTH;
            BinUtils.writeInt32LE(this.checkpoint                   , buf, ofs); ofs += 4;
                                  this.extendedAttributeICB    .write(buf, ofs); ofs += AllocationDescriptor.Long.LENGTH;
                                  this.implementationIdentifier.write(buf, ofs); ofs += EntityIdentifier.LENGTH;
            BinUtils.writeInt64LE(this.uniqueID                     , buf, ofs); ofs += 8;
            BinUtils.writeInt32LE(this.lengthOfExtendedAttributes   , buf, ofs); ofs += 4;
            BinUtils.writeInt32LE(this.lengthOfAllocationDescriptors, buf, ofs); ofs += 4;
                                  this.extendedAttributes      .write(buf, ofs); ofs += this.lengthOfExtendedAttributes;

            // enhancement to allow embedded file data to be filled in at a
            // later point in time...
            if (null != this.allocationDescriptors) {
                this.allocationDescriptors.write(buf, ofs);
            }
            ofs += this.lengthOfAllocationDescriptors;

            this.tag.write(buf, ofs0, ofs - ofs0);

            return ofs;
        }

        public String toString() {
            return super.tostr() +
                 String.format(
                    "lbr=%d,adt=[%s],mdt=[%s],tdt=[%s],cp=%s,eai=[%s],ii=[%s]," +
                    "ui=%s,lea=%s,lad=%s",
                    this.logicalBlocksRecorded,
                    this.accessDateAndTime,
                    this.modificationDateAndTime,
                    this.attributeDateAndTime,
                    BinUtils.u32ToLng(this.checkpoint),
                    this.extendedAttributeICB,
                    this.implementationIdentifier,
                    this.uniqueID,
                    BinUtils.u32ToLng(this.lengthOfExtendedAttributes),
                    BinUtils.u32ToLng(this.lengthOfAllocationDescriptors));
        }

        public int length() {
            return 176 + this.lengthOfExtendedAttributes +
                         this.lengthOfAllocationDescriptors;
        }

        /**
         * @param blockSize The block size in bytes.
         * @param lengthOfExtendedAttributes The length of the extended
         * attributes <b>must</b> also contain the size of the extended
         * attribute header descriptor!
         * @return The size of space free for allocation descriptors.
         */
        private static int freeForAlloc(
                int blockSize,
                int lengthOfExtendedAttributes) {
            return blockSize - (176 + lengthOfExtendedAttributes);
        }

        public static int maxEmbeddedAllocDescSize(
                int blockSize, int lengthOfExtendedAttributes) {
            return freeForAlloc(blockSize, lengthOfExtendedAttributes);
        }

        public static long maxFileSize(
                int blockSize, int lengthOfExtendedAttributes) {
            int free = freeForAlloc(blockSize, lengthOfExtendedAttributes);

            long c = free / AllocationDescriptor.Short.LENGTH;

            return AllocationDescriptor.maxLength(blockSize) * c;
        }
    }

    public static class Extended extends FileEntry {
        public long                      objectSize;
        public Timestamp                 creationDateAndTime;
        public BytePtr                   reserved;
        public AllocationDescriptor.Long streamDirectoryICB;

        public final static int RESV_LEN = 4;

        public Extended(Tag tag, byte[] buf, int ofs) throws UDFException {
            super(tag, buf, ofs);
        }

        protected void ctor(byte[] buf, int ofs) throws UDFException {
            int lea, lad;

            this.objectSize                          = BinUtils.readInt64LE(buf, ofs);            ofs += 8;
            this.logicalBlocksRecorded               = BinUtils.readInt64LE(buf, ofs);            ofs += 8;
            this.accessDateAndTime                   = Timestamp.parse(buf, ofs);                 ofs += Timestamp.LENGTH;
            this.modificationDateAndTime             = Timestamp.parse(buf, ofs);                 ofs += Timestamp.LENGTH;
            this.creationDateAndTime                 = Timestamp.parse(buf, ofs);                 ofs += Timestamp.LENGTH;
            this.attributeDateAndTime                = Timestamp.parse(buf, ofs);                 ofs += Timestamp.LENGTH;
            this.checkpoint                          = BinUtils.readInt32LE(buf, ofs);            ofs += 4;
            this.reserved                            = new BytePtr.Checked(buf, ofs, RESV_LEN);   ofs += RESV_LEN;
            this.extendedAttributeICB                = AllocationDescriptor.Long.parse(buf, ofs); ofs += AllocationDescriptor.Long.LENGTH;
            this.streamDirectoryICB                  = AllocationDescriptor.Long.parse(buf, ofs); ofs += AllocationDescriptor.Long.LENGTH;
            this.implementationIdentifier            = EntityIdentifier.parse(buf, ofs);          ofs += EntityIdentifier.LENGTH;
            this.uniqueID                            = BinUtils.readInt64LE(buf, ofs);            ofs += 8;
            this.lengthOfExtendedAttributes    = lea = BinUtils.readInt32LE(buf, ofs);            ofs += 4;
            this.lengthOfAllocationDescriptors = lad = BinUtils.readInt32LE(buf, ofs);            ofs += 4;
            this.extendedAttributes                  = new BytePtr.Checked(buf, ofs, lea);        ofs += lea;
            this.allocationDescriptors               = new BytePtr.Checked(buf, ofs, lad);
        }

        protected String type() {
            return "FEE";
        }

        public int write(byte[] block, int ofs) throws UDFException {
            // TODO: implement
            throw new Error();
        }

        public String toString() {
            return super.tostr() +
                 String.format(
                    "os=%d,lbr=%d,adt=[%s],mdt=[%s],cdt=[%s],tdt=[%s],cp=%s," +
                    "eai=[%s],sdi=[%s],ii=[%s],ui=%s,lea=%s,lad=%s",
                    this.objectSize,
                    this.logicalBlocksRecorded,
                    this.accessDateAndTime,
                    this.modificationDateAndTime,
                    this.creationDateAndTime,
                    this.attributeDateAndTime,
                    BinUtils.u32ToLng(this.checkpoint),
                    this.extendedAttributeICB,
                    this.streamDirectoryICB,
                    this.implementationIdentifier,
                    this.uniqueID,
                    BinUtils.u32ToLng(this.lengthOfExtendedAttributes),
                    BinUtils.u32ToLng(this.lengthOfAllocationDescriptors));
        }

        public int length() {
            // TODO: implement
            throw new Error();
        }
    }
}
