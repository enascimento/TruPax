package coderslagoon.trupax.lib.io.filesystem.udf;

import java.util.HashMap;

import coderslagoon.baselib.util.BinUtils;

public abstract class Descriptor {
    public static class Tag {
        public final static int LENGTH = 16;

        public enum Identifier {
            PRIMARY_VOLUME_DESCRIPTOR           (1),
            ANCHOR_VOLUME_DESCRIPTOR_POINTER    (2),
            VOLUME_DESCRIPTOR_POINTER           (3),
            IMPLEMENTATION_USE_VOLUME_DESCRIPTOR(4),
            PARTITION_DESCRIPTOR                (5),
            LOGICAL_VOLUME_DESCRIPTOR           (6),
            UNALLOCATED_SPACE_DESCRIPTOR        (7),
            TERMINATING_DESCRIPTOR              (8),
            LOGICAL_VOLUME_INTEGRITY_DESCRIPTOR (9),
            FILE_SET_DESCRIPTOR                 (256),
            FILE_IDENTIFIER_DESCRIPTOR          (257),
            ALLOCATION_EXTENT_DESCRIPTOR        (258),
            INDIRECT_ENTRY                      (259),
            TERMINAL_ENTRY                      (260),
            FILE_ENTRY                          (261),
            EXTENDED_ATTRIBUTE_HEADER_DESCRIPTOR(262),
            UNALLOCATED_SPACE_ENTRY             (263),
            SPACE_BITMAP_DESCRIPTOR             (264),
            PARTITION_INTEGRITY_ENTRY           (265),
            EXTENDED_FILE_ENTRY                 (266);

            Identifier(int code) {
                this.code = (short)code;
            }
            public final short code;

            static HashMap<Short, Identifier> _codeMap = new HashMap<>();
            static {
                for (Identifier id : values()) {
                    _codeMap.put(id.code, id);
                }
            }
            public static Identifier fromCode(short code) {
                return _codeMap.get(code);
            }
        }

        public final static short VER_2 = 2;
        public final static short VER_3 = 3;

        public final static byte RESERVED = 0;

        public final static short SERIAL_NONE = 0;
        public final static short SERIAL_ONE  = 1;

        public final static short CRC_NONE = 0;
        public final static short CRC_LENGTH_NONE = 0;

        ///////////////////////////////////////////////////////////////////////////

        public Identifier identifier;
        public short      version;
        public byte       checksum;
        public byte       reserved;
        public short      serialNumber;
        public short      descriptorCRC;
        public short      descriptorCRCLength;
        public int        location;

        ///////////////////////////////////////////////////////////////////////////

        public Tag(Identifier identifier, int location) {
            this.identifier   = identifier;
            this.version      = VER_2;
            this.serialNumber = SERIAL_ONE;
            this.location     = location;
        }

        ///////////////////////////////////////////////////////////////////////////

        public Tag(byte[] buf, int ofs) throws UDFException {
            short code = BinUtils.readInt16LE(buf, ofs);
            this.identifier = Identifier.fromCode(code);
            if (null == this.identifier) {
                throw new UDFException("unknown tag code " + code);
            }

            this.version  = BinUtils.readInt16LE(buf, ofs + 2);
            this.checksum = buf[ofs + 4];

            int chksum = 0;
            for (int i = 0; i <  4; i++) chksum += buf[ofs + i] & 0xff;
            for (int i = 5; i < 16; i++) chksum += buf[ofs + i] & 0xff;
            if ((byte)chksum != this.checksum) {
                throw new UDFException("tag checksum mismatch");
            }

            this.reserved            = buf[ofs + 5];
            this.serialNumber        = BinUtils.readInt16LE(buf, ofs + 6);
            this.descriptorCRC       = BinUtils.readInt16LE(buf, ofs + 8);
            this.descriptorCRCLength = BinUtils.readInt16LE(buf, ofs + 10);

            if (CRC_LENGTH_NONE != this.descriptorCRCLength) {
                CRC_CCITT crc = new CRC_CCITT();
                int len = BinUtils.u16ToInt(this.descriptorCRCLength);
                crc.update(buf, ofs + LENGTH, len);
                if (crc.value() != this.descriptorCRC) {
                    throw new UDFException("CRC mismatch (len=%d)", len);
                }
            }

            this.location = BinUtils.readInt32LE(buf, ofs + 12);
        }

        public void write(byte[] buf, int ofs, int len) {
            int crcLen = -1 == len ? -1 : (len - LENGTH);

            BinUtils.writeInt16LE(this.identifier.code, buf, ofs);
            BinUtils.writeInt16LE(this.version        , buf, ofs + 2);

            buf[ofs + 5] = this.reserved;

            if (-1 != crcLen) {
                CRC_CCITT crc = new CRC_CCITT();
                crc.update(buf, ofs + LENGTH, crcLen);
                this.descriptorCRC       = crc.value();
                this.descriptorCRCLength = (short)crcLen;
            }
            else {
                this.descriptorCRC       = 0;
                this.descriptorCRCLength = CRC_LENGTH_NONE;
            }

            BinUtils.writeInt16LE(this.serialNumber       , buf, ofs + 6);
            BinUtils.writeInt16LE(this.descriptorCRC      , buf, ofs + 8);
            BinUtils.writeInt16LE(this.descriptorCRCLength, buf, ofs + 10);
            BinUtils.writeInt32LE(this.location           , buf, ofs + 12);

            int chksum = 0;
            for (int i = 0; i <  4; i++) chksum += buf[ofs + i] & 0xff;
            for (int i = 5; i < 16; i++) chksum += buf[ofs + i] & 0xff;

            buf[ofs + 4] = (byte)chksum;
        }

        public String toString() {
            return String.format("DT:id=%s,ver=%s,ser=%s,loc=%s",
                    this.identifier,
                    BinUtils.u16ToInt(this.version),
                    BinUtils.u16ToInt(this.serialNumber),
                    BinUtils.u32ToLng(this.location));
        }
    }

    ///////////////////////////////////////////////////////////////////////////

    public Tag tag;

    public Descriptor(Tag tag) {
        this.tag = tag;
    }

    ///////////////////////////////////////////////////////////////////////////

    public static Descriptor parse(byte[] block, int ofs) throws UDFException {
        Tag tag = new Tag(block, ofs);
        ofs += Tag.LENGTH;

        switch (tag.identifier) {
            case PRIMARY_VOLUME_DESCRIPTOR            : return new PrimaryVolumeDescriptor          (tag, block, ofs);
            case ANCHOR_VOLUME_DESCRIPTOR_POINTER     : return new AnchorVolumeDescriptorPointer    (tag, block, ofs);
            case VOLUME_DESCRIPTOR_POINTER            : return new VolumeDescriptorPointer          (tag, block, ofs);
            case IMPLEMENTATION_USE_VOLUME_DESCRIPTOR : return new ImplementationUseVolumeDescriptor(tag, block, ofs);
            case PARTITION_DESCRIPTOR                 : return new PartitionDescriptor              (tag, block, ofs);
            case LOGICAL_VOLUME_DESCRIPTOR            : return new LogicalVolumeDescriptor          (tag, block, ofs);
            case UNALLOCATED_SPACE_DESCRIPTOR         : return new UnallocatedSpaceDescriptor       (tag, block, ofs);
            case TERMINATING_DESCRIPTOR               : return new TerminatingDescriptor            (tag, block, ofs);
            case LOGICAL_VOLUME_INTEGRITY_DESCRIPTOR  : return new LogicalVolumeIntegrityDescriptor (tag, block, ofs);
            case FILE_SET_DESCRIPTOR                  : return new FileSetDescriptor                (tag, block, ofs);
            case FILE_IDENTIFIER_DESCRIPTOR           : return new FileIdentifierDescriptor         (tag, block, ofs);
            case ALLOCATION_EXTENT_DESCRIPTOR         : return new AllocationExtentDescriptor       (tag, block, ofs);
            case INDIRECT_ENTRY                       : return new IndirectEntry                    (tag, block, ofs);
            case TERMINAL_ENTRY                       : return new TerminalEntry                    (tag, block, ofs);
            case FILE_ENTRY                           : return new FileEntry.Standard               (tag, block, ofs);
            case EXTENDED_ATTRIBUTE_HEADER_DESCRIPTOR : return new ExtendedAttributeHeaderDescriptor(tag, block, ofs);
            case UNALLOCATED_SPACE_ENTRY              : return new UnallocatedSpaceEntry            (tag, block, ofs);
            case SPACE_BITMAP_DESCRIPTOR              : return new SpaceBitmapDescriptor            (tag, block, ofs);
            case PARTITION_INTEGRITY_ENTRY            : return new PartitionIntegrityEntry          (tag, block, ofs);
            case EXTENDED_FILE_ENTRY                  : return new FileEntry.Extended               (tag, block, ofs);
        }

        throw new UDFException("no parser found for tag with ID %d", tag.identifier);
    }
}
