package coderslagoon.trupax.lib.io.filesystem.udf;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.Calendar;

import org.junit.Test;


import coderslagoon.baselib.util.BinUtils;
import coderslagoon.baselib.util.BytePtr;
import coderslagoon.test.util.TestUtils;
import coderslagoon.trupax.lib.io.filesystem.udf.AllocationDescriptor;
import coderslagoon.trupax.lib.io.filesystem.udf.AnchorVolumeDescriptorPointer;
import coderslagoon.trupax.lib.io.filesystem.udf.CRC_CCITT;
import coderslagoon.trupax.lib.io.filesystem.udf.CharacterSet;
import coderslagoon.trupax.lib.io.filesystem.udf.DString;
import coderslagoon.trupax.lib.io.filesystem.udf.Descriptor;
import coderslagoon.trupax.lib.io.filesystem.udf.EntityIdentifier;
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
import coderslagoon.trupax.lib.io.filesystem.udf.UDFException;
import coderslagoon.trupax.lib.io.filesystem.udf.UnallocatedSpaceDescriptor;
import coderslagoon.trupax.lib.io.filesystem.udf.VolumeStructureDescriptor;
import coderslagoon.trupax.lib.io.filesystem.udf.EntityIdentifier.OSInfo;
import coderslagoon.trupax.lib.io.filesystem.udf.EntityIdentifier.Suffix;

public class BasicsTest {
    @Test
    public void testRecordedAddress() {
        byte[] buf = new byte[16];

        buf[RecordedAddress.LENGTH] = (byte)0xcc;

        RecordedAddress raddr = new RecordedAddress(0xbebef00d, (short)0xfaac);
        assertTrue(raddr.logicalBlockNumber       == 0xbebef00d);
        assertTrue(raddr.partitionReferenceNumber == (short)0xfaac);

        raddr.write(buf, 0); raddr = null;
        assertTrue(buf[RecordedAddress.LENGTH] == (byte)0xcc);

        raddr = RecordedAddress.parse(buf, 0);
        assertTrue(raddr.logicalBlockNumber       == 0xbebef00d);
        assertTrue(raddr.partitionReferenceNumber == (short)0xfaac);

        assertTrue(RecordedAddress.ZERO.partitionReferenceNumber == 0);
        assertTrue(RecordedAddress.ZERO.logicalBlockNumber       == 0);
    }

    ///////////////////////////////////////////////////////////////////////////

    @Test
    public void testExtentDescriptor() throws UDFException {
        byte[] buf = new byte[11];

        final int end = ExtentDescriptor.LENGTH + 1;
        buf[0  ] = (byte)0xcc;
        buf[end] = (byte)0xbb;

        ExtentDescriptor ed = new ExtentDescriptor(ExtentDescriptor.MAX_LENGTH, 0xeeffeeff);
        assertTrue(ed.length   == ExtentDescriptor.MAX_LENGTH);
        assertTrue(ed.location == 0xeeffeeff);

        ed.write(buf, 1); ed = null;
        assertTrue(buf[0  ] == (byte)0xcc);
        assertTrue(buf[end] == (byte)0xbb);

        ed = ExtentDescriptor.parse(buf, 1);
        assertTrue(ed.length   == ExtentDescriptor.MAX_LENGTH);
        assertTrue(ed.location == 0xeeffeeff);

        ed = new ExtentDescriptor(ExtentDescriptor.MAX_LENGTH + 1, 0);
        try {
            ed.write(buf, 0);
            fail();
        }
        catch (UDFException expected) {
        }

        assertTrue(ExtentDescriptor.NONE.length   == 0);
        assertTrue(ExtentDescriptor.NONE.location == 0);
    }

    ///////////////////////////////////////////////////////////////////////////

    @Test
    public void testCharacterSet() throws UDFException {
        byte[] buf = new byte[100];
        Arrays.fill(buf, (byte)0xa1);
        final int end = CharacterSet.LENGTH + 1;
        buf[0]   = (byte)0xfe;
        buf[end] = (byte)0xef;

        CharacterSet.OSTA_COMPRESSED_UNICODE.write(buf, 1);

        assertTrue(buf[0]   == (byte)0xfe);
        assertTrue(buf[end] == (byte)0xef);
        for (int i = 1; i < end; i++) {
            assertFalse((byte)0xa1 == buf[i]);
        }

        CharacterSet cset = CharacterSet.parse(buf, 1);

        assertTrue(cset.type            == CharacterSet.Type.CS0);
        assertTrue(cset.information.len == CharacterSet.INFORMATION_LENGTH);

        assertEquals(DString.readChars(cset.information),
                     "OSTA Compressed Unicode");

        assertEquals(cset, CharacterSet.OSTA_COMPRESSED_UNICODE);
    }

    ///////////////////////////////////////////////////////////////////////////

    @Test
    public void testAllocationDescriptorShort() throws UDFException {
        byte[] buf = new byte[64];

        AllocationDescriptor.Short ads = new AllocationDescriptor.Short();
        ads.type     = AllocationDescriptor.ExtentType.RECORDED_AND_ALLOCATED;
        ads.length   = AllocationDescriptor.MAX_LENGTH;
        ads.position = 0xcafebebe;

        final int end = AllocationDescriptor.Short.LENGTH + 1;
        buf[0]   = (byte)0xee;
        buf[end] = (byte)0xcc;
        assertTrue(ads.write(buf, 1) == end); ads = null;
        assertTrue(buf[0]                                     == (byte)0xee);
        assertTrue(buf[AllocationDescriptor.Short.LENGTH + 1] == (byte)0xcc);

        ads = AllocationDescriptor.Short.parse(buf, 1);
        assertTrue(ads.type   == AllocationDescriptor.ExtentType.RECORDED_AND_ALLOCATED);
        assertTrue(ads.length == AllocationDescriptor.MAX_LENGTH);

        assertTrue(ads.position == 0xcafebebe);
    }

    ///////////////////////////////////////////////////////////////////////////

    @Test
    public void testAllocationDescriptorLong() throws UDFException {
        byte[] buf = new byte[64];

        AllocationDescriptor.Long adl = new AllocationDescriptor.Long();
        adl.type     = AllocationDescriptor.ExtentType.RECORDED_AND_ALLOCATED;
        adl.length   = AllocationDescriptor.MAX_LENGTH;
        adl.location = new RecordedAddress(0xcafebebe, (short)0x1234);

        int end = AllocationDescriptor.Long.LENGTH + 1;
        buf[0]   = (byte)0xcc;
        buf[end] = (byte)0xee;
        assertTrue(adl.write(buf, 1) == end); adl = null;
        assertTrue(buf[0]                                    == (byte)0xcc);
        assertTrue(buf[AllocationDescriptor.Long.LENGTH + 1] == (byte)0xee);

        adl = AllocationDescriptor.Long.parse(buf, 1);
        assertTrue(adl.type   == AllocationDescriptor.ExtentType.RECORDED_AND_ALLOCATED);
        assertTrue(adl.length == AllocationDescriptor.MAX_LENGTH);
        assertTrue(adl.location.logicalBlockNumber       == 0xcafebebe);
        assertTrue(adl.location.partitionReferenceNumber == 0x1234);
    }

    ///////////////////////////////////////////////////////////////////////////

    @Test
    public void testAnchorVolumeDescriptorPointer() throws UDFException {
        final int LOCATION = 1234567890;

        AnchorVolumeDescriptorPointer avdp = new
        AnchorVolumeDescriptorPointer(LOCATION);

        avdp.mainVolumeDescriptorSequence    = new ExtentDescriptor(0x01234567, 0x89abcdef);
        avdp.reserveVolumeDescriptorSequence = new ExtentDescriptor(0x0fedcba9, 0x97654321);
        avdp.reserved                        = null;

        byte[] buf = new byte[600];
        Arrays.fill(buf, (byte)0xee);

        int ofs = avdp.write(buf, 1); avdp = null;
        assertTrue(buf[0] == (byte)0xee);
        assertTrue(ofs < buf.length);
        assertTrue(ofs == 513);
        assertTrue((byte)0xee == buf[0]);
        while (ofs < buf.length) {
            assertTrue(buf[ofs++] == (byte)0xee);
        }

        Descriptor d = Descriptor.parse(buf, 1);
        assertTrue(d instanceof AnchorVolumeDescriptorPointer);
        avdp = (AnchorVolumeDescriptorPointer)d;

        assertTrue(avdp.tag.identifier == Descriptor.Tag.Identifier.ANCHOR_VOLUME_DESCRIPTOR_POINTER);
        assertTrue(avdp.tag.descriptorCRC       != 0);
        assertTrue(avdp.tag.descriptorCRCLength == 496);
        assertTrue(avdp.tag.location            == LOCATION);
        assertTrue(avdp.tag.serialNumber        == Descriptor.Tag.SERIAL_ONE);
        assertTrue(avdp.tag.version             == Descriptor.Tag.VER_2);

        assertTrue(avdp.mainVolumeDescriptorSequence.length      == 0x01234567);
        assertTrue(avdp.mainVolumeDescriptorSequence.location    == 0x89abcdef);
        assertTrue(avdp.reserveVolumeDescriptorSequence.length   == 0x0fedcba9);
        assertTrue(avdp.reserveVolumeDescriptorSequence.location == 0x97654321);

        assertTrue(AnchorVolumeDescriptorPointer.RESV_LEN == avdp.reserved.len);
        for (int i = 0; i < avdp.reserved.len; i++) {
            assertTrue(0 == avdp.reserved.buf[avdp.reserved.ofs]);
        }
    }

    ///////////////////////////////////////////////////////////////////////////

    @Test
    public void testTimestamp() {
        for (int i = 0; i < 2; i++) {
            final Timestamp ts;
            final long REF_MILLIS = 1239291169812L;
            Calendar CAL = null;
            if (0 == i) {
                CAL = Calendar.getInstance();
                CAL.setTimeInMillis(REF_MILLIS);
                ts = Timestamp.fromCalendar(CAL);
            }
            else {
                ts = new Timestamp();
                ts.year                   = 1977;
                ts.month                  = 11;
                ts.day                    = 22;
                ts.hour                   = 16;
                ts.minute                 = 5;
                ts.second                 = 48;
                ts.centiseconds           = 101;
                ts.microseconds           = 102;
                ts.hundredsOfMicroseconds = 103;
                ts.timeZone               = Timestamp.LOCAL_TIME;
                ts.offsetMinutesUTC       = Timestamp.NO_OFFSET;
            }

            final byte[] buf = new byte[100];
            Arrays.fill(buf, (byte)0xcc);

            ts.write(buf, 1);

            assertTrue((byte)0xcc == buf[0]);
            int ofs = 1 + Timestamp.LENGTH;
            assertTrue(ofs < buf.length);
            for (; ofs < buf.length; ofs++) {
                assertTrue((byte)0xcc == buf[ofs]);
            }

            final Timestamp ts2 = Timestamp.parse(buf, 1);

            assertTrue(ts.year                   == ts2.year);
            assertTrue(ts.month                  == ts2.month);
            assertTrue(ts.day                    == ts2.day);
            assertTrue(ts.hour                   == ts2.hour);
            assertTrue(ts.minute                 == ts2.minute);
            assertTrue(ts.second                 == ts2.second);
            assertTrue(ts.centiseconds           == ts2.centiseconds);
            assertTrue(ts.microseconds           == ts2.microseconds);
            assertTrue(ts.hundredsOfMicroseconds == ts2.hundredsOfMicroseconds);
            assertTrue(ts.timeZone               == ts2.timeZone);
            assertTrue(ts.offsetMinutesUTC       == ts2.offsetMinutesUTC);

            if (null != CAL) {
                assertTrue(CAL.getTimeInMillis() == REF_MILLIS);
                Calendar cal2 = ts2.toCalendar();
                long tm = cal2.getTimeInMillis();
                if (tm != REF_MILLIS) {
                    fail(String.format("time delta is %d", tm - REF_MILLIS));
                }
            }
        }
    }

    ///////////////////////////////////////////////////////////////////////////

    @Test
    public void testCRC_CCITT() {
        assertTrue(CRC_CCITT.test());

    }

    ///////////////////////////////////////////////////////////////////////////

    @Test
    public void testDString() throws UDFException {
        final char[] MAX = new char[127];
        Arrays.fill(MAX, 'a');

        final String[] USTRS = { "", "1", "test\uffc5 123", new String(MAX) };

        for (final String USTR : USTRS) {
            byte[] buf = new byte[MAX.length * 2 + 16];
            Arrays.fill(buf, (byte)0xee);

            final int BLEN = buf.length - 2;
            DString.write(USTR, buf, 1, BLEN);

            assertTrue(buf[0             ] == (byte)0xee);
            assertTrue(buf[buf.length - 1] == (byte)0xee);

            if (0 == USTR.length()) {
                for (int i = 1; i <= BLEN; i++) assertTrue(0 == buf[i]);
                continue;
            }

            assertTrue(buf[1] == DString.COMPRESSION_ID_UNICODE);

            int i;
            for (i = 2 + USTR.length() * 2; i < buf.length - 2; i++) {
                assertTrue(0 == buf[i]);
            }

            assertTrue((0xff & buf[i]) == USTR.length() * 2 + 1);

            String str = DString.read(buf, 1, BLEN);
            assertEquals(str, USTR);
        }

        for (String s : new String[] { "", "1", " ", "\t", "test", "\u0020" }) {
            assertFalse(DString.needsUnicode(s));
        }
        for (String s : new String[] { "\u0100", "\ufffe", " \uabcd",
                                       " \uabcd ", "x\uabcdx" }) {
            assertTrue(DString.needsUnicode(s));
        }

        for (final String USTR : USTRS) {
            BytePtr cuc = DString.writeCompressedUnicode(USTR);

            final byte b = cuc.at(0);
            if (DString.needsUnicode(USTR)) assertTrue(b == DString.COMPRESSION_ID_UNICODE);
            else                            assertTrue(b == DString.COMPRESSION_ID_8BIT);

            String str = DString.readCompressedUnicode(cuc);
            assertEquals(str, USTR);
        }
    }

    ///////////////////////////////////////////////////////////////////////////

    EntityIdentifier makeTestEID(final int num) {
        final EntityIdentifier.OSTASuffix os = new EntityIdentifier.OSTASuffix();

        os.udfRevision = EntityIdentifier.RevisionSuffix.UDF_REVISION_102;
        os.domainFlags = EntityIdentifier.OSTASuffix.DOMAINFLAG_HARD_WRITE_PROTECT;
        os.reserved    = new BytePtr(new byte[EntityIdentifier.OSTASuffix.RESV_LEN]);

        BinUtils.writeInt32BE(num, os.reserved.buf,
                                   os.reserved.ofs + 1);

        final EntityIdentifier result = new EntityIdentifier(EntityIdentifier.ID_OSTA);
        result.flags            = EntityIdentifier.FLAG_PROTECTED;
        result.identifierSuffix = os.data();

        return result;
    }

    void checkTestEID(final EntityIdentifier eid, final int num) throws UDFException {
        assertTrue(eid.flags == EntityIdentifier.FLAG_PROTECTED);

        EntityIdentifier.Suffix sfx = eid.suffix();
        assertTrue(sfx instanceof EntityIdentifier.OSTASuffix);

        EntityIdentifier.OSTASuffix os = (EntityIdentifier.OSTASuffix)sfx;

        assertTrue(os.udfRevision == EntityIdentifier.RevisionSuffix.UDF_REVISION_102);
        assertTrue(os.domainFlags == EntityIdentifier.OSTASuffix.DOMAINFLAG_HARD_WRITE_PROTECT);
        assertTrue(5 == os.reserved.len);
        assertTrue(0 == os.reserved.at(0));
        assertTrue(num == BinUtils.readInt32BE(os.reserved.buf,
                                               os.reserved.ofs + 1));
    }

    ///////////////////////////////////////////////////////////////////////////

    @Test
    public void testEntityIdentifier() throws UDFException {
        final int TYPE_COUNT = 3;
        int checks = 0;
        for (int type = 0; type < TYPE_COUNT; type++) {
            final byte[] rsvdata = new byte[1000];
            TestUtils.fillPattern123(rsvdata, 0, rsvdata.length);

            final BytePtr bsfx;
            final String  id;
            switch (type) {
                case 0: {
                    EntityIdentifier.UDFSuffix us = new EntityIdentifier.UDFSuffix();
                    us.osClass      = OSInfo.OSCLASS_UNIX;
                    us.osIdentifier = OSInfo.OSID_UNIX_IRIX;
                    us.reserved     = new BytePtr(rsvdata, 0, EntityIdentifier.UDFSuffix.RESV_LEN);
                    bsfx = us.data();
                    id = EntityIdentifier.ID_UDF;
                    break;
                }
                case 1: {
                    EntityIdentifier.OSTASuffix os = new EntityIdentifier.OSTASuffix();
                    os.udfRevision = EntityIdentifier.RevisionSuffix.UDF_REVISION_102;
                    os.domainFlags = EntityIdentifier.OSTASuffix.DOMAINFLAG_SOFT_WRITE_PROTECT;
                    os.reserved    = new BytePtr(rsvdata, 0, EntityIdentifier.OSTASuffix.RESV_LEN);
                    bsfx = os.data();
                    id = EntityIdentifier.ID_OSTA;
                    break;
                }
                case 2: {
                    EntityIdentifier.ImplSuffix is = new EntityIdentifier.ImplSuffix((byte)0xfe, (byte)0xef,
                            new BytePtr(rsvdata, 0, EntityIdentifier.ImplSuffix.IMPL_USE_AREA_LEN));
                    bsfx = is.data();
                    id = "customized!";
                    break;
                }
                default: {
                    fail();
                    return;
                }
            }

            EntityIdentifier eid = new EntityIdentifier(id);
            eid.flags            = EntityIdentifier.FLAG_PROTECTED;
            eid.identifierSuffix = bsfx;

            byte[] buf = new byte[51];
            Arrays.fill(buf, (byte)0xee);

            eid.write(buf, 1); eid = null;
            assertTrue(buf[0] == (byte)0xee);
            for (int i = 1 + EntityIdentifier.LENGTH; i < buf.length; i++) {
                assertTrue(buf[i] == (byte)0xee);
            }

            eid = new EntityIdentifier(buf, 1);
            assertTrue  (eid.flags == EntityIdentifier.FLAG_PROTECTED);
            assertEquals(eid.identifierAsString(), id);

            final EntityIdentifier.Suffix sfx = eid.suffix();

            switch (type) {
                case 0: {
                    assertTrue(sfx instanceof EntityIdentifier.UDFSuffix);
                    EntityIdentifier.UDFSuffix us = (EntityIdentifier.UDFSuffix)sfx;
                    assertTrue(us.osClass      == OSInfo.OSCLASS_UNIX);
                    assertTrue(us.osIdentifier == OSInfo.OSID_UNIX_IRIX);
                    assertTrue(us.reserved.len == EntityIdentifier.UDFSuffix.RESV_LEN);
                    assertTrue(TestUtils.checkPattern123(us.reserved));
                    checks++;
                    break;
                }
                case 1: {
                    assertTrue(sfx instanceof EntityIdentifier.OSTASuffix);
                    EntityIdentifier.OSTASuffix os = (EntityIdentifier.OSTASuffix)sfx;
                    assertTrue(os.udfRevision  == EntityIdentifier.RevisionSuffix.UDF_REVISION_102);
                    assertTrue(os.domainFlags  == EntityIdentifier.OSTASuffix.DOMAINFLAG_SOFT_WRITE_PROTECT);
                    assertTrue(os.reserved.len == EntityIdentifier.OSTASuffix.RESV_LEN);
                    assertTrue(TestUtils.checkPattern123(os.reserved));
                    checks++;
                    break;
                }
                case 2: {
                    assertTrue(sfx instanceof EntityIdentifier.ImplSuffix);
                    EntityIdentifier.ImplSuffix is = (EntityIdentifier.ImplSuffix)sfx;
                    assertTrue(is.osClass      == (byte)0xfe);
                    assertTrue(is.osIdentifier == (byte)0xef);
                    assertTrue(is.implUseArea.len == EntityIdentifier.ImplSuffix.IMPL_USE_AREA_LEN);
                    assertTrue(TestUtils.checkPattern123(is.implUseArea));
                    checks++;
                    break;
                }
                default: {
                    fail();
                    return;
                }
            }
        }
        assertTrue(TYPE_COUNT == checks);

        for (final int num : new int[] { 0, 1, 65536, -1 }) {
            checkTestEID(makeTestEID(num), num);
        }
    }

    ///////////////////////////////////////////////////////////////////////////

    static Calendar CAL0;
    static Calendar CAL1;
    static Calendar CAL2;

    static void resetCalendars() {
        Calendar c;
        c = Calendar.getInstance(); c.set(1977, 5, 12, 20, 22,  3); c.set(Calendar.MILLISECOND, 123); CAL0 = c;
        c = Calendar.getInstance(); c.set(2014, 2, 11,  5,  4,  7); c.set(Calendar.MILLISECOND, 999); CAL1 = c;
        c = Calendar.getInstance(); c.set(2000,11, 31, 11, 12, 13); c.set(Calendar.MILLISECOND,   1); CAL2 = c;
    }

    static {
        resetCalendars();
    }

    ///////////////////////////////////////////////////////////////////////////

    @Test
    public void testFileEntry() throws UDFException {
        resetCalendars();

        FileEntry.Standard fe = new FileEntry.Standard(1234567890);

        fe.icbTag = new ICBTag();
        fe.icbTag.priorRecordedNumberOfDirectEntries = 0xf0013355;
        fe.icbTag.strategyType                       = ICBTag.STRATEGY_UDF102;
        fe.icbTag.strategyParameter                  = ICBTag.SPARAMS_ZERO;
        fe.icbTag.maximumNumberOfEntries             = 1234;
        fe.icbTag.reserved                           = 77;
        fe.icbTag.fileType                           = ICBTag.FileType.DIRECTORY;
        fe.icbTag.parentICBLocation                  = RecordedAddress.ZERO;
        fe.icbTag.flags                              = 14141;
        fe.uid                                       = 1234567890;
        fe.gid                                       = 1010101010;
        fe.permissions                               = 1122334455;
        fe.fileLinkCount                             = 9991;
        fe.recordFormat                              = FileEntry.RecordFormat.VARIABLE_LENGTH_16_MSB_RECORDS;
        fe.recordDisplayAttributes                   = FileEntry.RecordDisplayAttribute.LF_CR;
        fe.recordLength                              = 10000001;
        fe.informationLength                         = 2000000200000099L;
        fe.logicalBlocksRecorded                     = 30000003;
        fe.accessDateAndTime                         = Timestamp.fromCalendar(CAL0);
        fe.modificationDateAndTime                   = Timestamp.fromCalendar(CAL1);
        fe.attributeDateAndTime                      = Timestamp.fromCalendar(CAL2);
        fe.checkpoint                                = 0xcec01555;
        fe.extendedAttributeICB                      = AllocationDescriptor.Long.ZERO;
        fe.implementationIdentifier                  = new EntityIdentifier("*UDFWriter");
        fe.implementationIdentifier.flags            = EntityIdentifier.FLAG_PROTECTED;
        fe.implementationIdentifier.identifierSuffix = new EntityIdentifier.ImplSuffix(1, 2, null).data();
        fe.uniqueID                                  = 998877665544332211L;
        fe.lengthOfExtendedAttributes                = 0;
        fe.lengthOfAllocationDescriptors             = 321;
        fe.extendedAttributes                        = BytePtr.NO_DATA;
        fe.allocationDescriptors                     = TestUtils.fillPattern123(321);

        byte[] buf = new byte[1000];
        Arrays.fill(buf, (byte)0xcc);

        int ofs = fe.write(buf, 1); fe = null;
        assertTrue(498 == ofs);
        assertTrue((byte)0xcc == buf[0]);
        while (ofs < buf.length) {
            assertTrue((byte)0xcc == buf[ofs++]);
        }

        Descriptor d = Descriptor.parse(buf, 1);
        assertTrue(d instanceof FileEntry.Standard);

        fe = (FileEntry.Standard)d;

        assertTrue(fe.tag.location                                           == 1234567890);
        assertTrue(fe.icbTag.priorRecordedNumberOfDirectEntries              == 0xf0013355);
        assertTrue(fe.icbTag.strategyType                                    == ICBTag.STRATEGY_UDF102);
        assertTrue(fe.icbTag.strategyParameter.at(0)                         == 0);
        assertTrue(fe.icbTag.strategyParameter.at(1)                         == 0);
        assertTrue(fe.icbTag.maximumNumberOfEntries                          == 1234);
        assertTrue(fe.icbTag.reserved                                        == 77);
        assertTrue(fe.icbTag.fileType                                        == ICBTag.FileType.DIRECTORY);
        assertTrue(fe.icbTag.parentICBLocation.logicalBlockNumber            == 0);
        assertTrue(fe.icbTag.parentICBLocation.partitionReferenceNumber      == 0);
        assertTrue(fe.icbTag.flags                                           == 14141);
        assertTrue(fe.uid                                                    == 1234567890);
        assertTrue(fe.gid                                                    == 1010101010);
        assertTrue(fe.permissions                                            == 1122334455);
        assertTrue(fe.fileLinkCount                                          == 9991);
        assertTrue(fe.recordFormat                                           == FileEntry.RecordFormat.VARIABLE_LENGTH_16_MSB_RECORDS);
        assertTrue(fe.recordDisplayAttributes                                == FileEntry.RecordDisplayAttribute.LF_CR);
        assertTrue(fe.recordLength                                           == 10000001);
        assertTrue(fe.informationLength                                      == 2000000200000099L);
        assertTrue(fe.logicalBlocksRecorded                                  == 30000003);
        assertTrue(fe.accessDateAndTime                                      .equals(Timestamp.fromCalendar(CAL0)));
        assertTrue(fe.modificationDateAndTime                                .equals(Timestamp.fromCalendar(CAL1)));
        assertTrue(fe.attributeDateAndTime                                   .equals(Timestamp.fromCalendar(CAL2)));
        assertTrue(fe.checkpoint                                             == 0xcec01555);
        assertTrue(fe.extendedAttributeICB.length                            == 0);
        assertTrue(fe.extendedAttributeICB.location.logicalBlockNumber       == 0);
        assertTrue(fe.extendedAttributeICB.location.partitionReferenceNumber == 0);
        assertTrue(fe.extendedAttributeICB.length                            == 0);
        assertTrue(fe.implementationIdentifier.identifierAsString()          .equals("*UDFWriter"));
        assertTrue(fe.implementationIdentifier.flags                         == EntityIdentifier.FLAG_PROTECTED);
        assertTrue(fe.implementationIdentifier.suffix()                      instanceof EntityIdentifier.ImplSuffix);
        assertTrue(fe.uniqueID                                               == 998877665544332211L);
        assertTrue(fe.lengthOfExtendedAttributes                             == 0);
        assertTrue(fe.lengthOfAllocationDescriptors                          == 321);
        assertTrue(fe.extendedAttributes.len                                 == 0);
        assertTrue(TestUtils.checkPattern123(fe.allocationDescriptors));

        EntityIdentifier.ImplSuffix is = (EntityIdentifier.ImplSuffix)fe.implementationIdentifier.suffix();
        assertTrue(is.osClass      == 1);
        assertTrue(is.osIdentifier == 2);

        long maxsz = FileEntry.Standard.maxFileSize(512, 0);

        assertTrue(45097135104L == maxsz);
    }

    ///////////////////////////////////////////////////////////////////////////

    @Test
    public void testFileIdentifierDescriptor() throws UDFException {
        final char[] MAXFNAME = new char[254];
        Arrays.fill(MAXFNAME, 'a');

        for (final String fname : new String[] { "", "1", "\u55aa.txt", new String(MAXFNAME) }) {
            FileIdentifierDescriptor fid = new FileIdentifierDescriptor(999);

            fid.fileVersionNumber         = 18900;
            fid.fileCharacteristics       = FileIdentifierDescriptor.FCBIT_DIRECTORY;
            fid.lengthOfFileIdentifier    = -42;
            fid.icb                       = new AllocationDescriptor.Long();
            fid.icb.type                  = AllocationDescriptor.ExtentType.NOT_RECORDED_AND_NOT_ALLOCATED;
            fid.icb.length                = 1024;
            fid.icb.implementationUse     = TestUtils.fillPattern123(AllocationDescriptor.Long.IMPL_USE_LEN);
            fid.icb.location              = new RecordedAddress(0x10023004, (short)17);
            fid.lengthOfImplementationUse = 12;
            fid.implementationUse         = TestUtils.fillPattern123(12);
            fid.paddingBytes              = null;
            fid.setFileIdentifier(fname);

            byte[] buf = new byte[333];
            Arrays.fill(buf, (byte)0xaa);

            int ofs = fid.write(buf, 1); fid = null;
            assertTrue((byte)0xaa == buf[0]);
            while (ofs < buf.length) {
                assertTrue((byte)0xaa == buf[ofs++]);
            }

            Descriptor d = Descriptor.parse(buf, 1);
            assertTrue(d instanceof FileIdentifierDescriptor);
            fid = (FileIdentifierDescriptor)d;

            assertTrue(fid.tag.location                          == 999);
            assertTrue(fid.fileVersionNumber                     == 18900);
            assertTrue(fid.fileCharacteristics                   == FileIdentifierDescriptor.FCBIT_DIRECTORY);
            assertTrue(fid.fileIdentifierStr                     .equals(fname));
            assertTrue(fid.icb.type                              == AllocationDescriptor.ExtentType.NOT_RECORDED_AND_NOT_ALLOCATED);
            assertTrue(fid.icb.length                            == 1024);
            assertTrue(fid.icb.implementationUse.len             == AllocationDescriptor.Long.IMPL_USE_LEN);
            assertTrue(fid.icb.location.logicalBlockNumber       == 0x10023004);
            assertTrue(fid.icb.location.partitionReferenceNumber == 17);
            assertTrue(fid.lengthOfImplementationUse             == 12);
            assertTrue(fid.implementationUse.len                 == 12);
            assertTrue(fid.paddingBytes.len                      == FileIdentifierDescriptor.pad(fid.fileIdentifier.len));

            assertEquals(fid.fileIdentifierStr, fname);

            assertTrue(TestUtils.checkPattern123(fid.icb.implementationUse));
            assertTrue(TestUtils.checkPattern123(fid.implementationUse));
        }
    }

    ///////////////////////////////////////////////////////////////////////////

    @Test
    public void testFileSetDescriptor() throws UDFException {
        FileSetDescriptor fsd = new FileSetDescriptor(773311);

        fsd.recordingDateAndTime                = Timestamp.fromCalendar(CAL0);
        fsd.interchangeLevel                    = (short)0xabcd;
        fsd.maximumInterchangeLevel             = (short)0x9876;
        fsd.characterSetList                    = 0x01234567;
        fsd.maximumCharacterSetList             = 10000001;
        fsd.fileSetNumber                       = 20000002;
        fsd.fileSetDescriptorNumber             = 30000003;
        fsd.logicalVolumeIdentifierCharacterSet = CharacterSet.OSTA_COMPRESSED_UNICODE;
        fsd.logicalVolumeIdentifier             = "the_volume";
        fsd.fileSetCharacterSet                 = CharacterSet.OSTA_COMPRESSED_UNICODE;
        fsd.fileSetIdentifier                   = "123";
        fsd.copyrightFileIdentifier             = "kopir8";
        fsd.abstractFileIdentifier              = "afid";
        fsd.rootDirectoryICB                    = new AllocationDescriptor.Long();
        fsd.rootDirectoryICB.location           = new RecordedAddress(1, (short)2);
        fsd.rootDirectoryICB.type               = AllocationDescriptor.ExtentType.RECORDED_AND_ALLOCATED;
        fsd.rootDirectoryICB.length             = 256;
        fsd.rootDirectoryICB.implementationUse  = null;
        fsd.domainIdentifier                    = makeTestEID(0x55662233);
        fsd.nextExtent                          = AllocationDescriptor.Long.ZERO;
        fsd.systemStreamDirectoryICB            = new AllocationDescriptor.Long();
        fsd.systemStreamDirectoryICB.location   = new RecordedAddress(12345, (short)6789);
        fsd.systemStreamDirectoryICB.type       = AllocationDescriptor.ExtentType.NOT_RECORDED_BUT_ALLOCATED;
        fsd.systemStreamDirectoryICB.length     = 512;

        byte[] buf = new byte[1000];
        Arrays.fill(buf, (byte)0xbb);

        int end = fsd.write(buf, 1); fsd = null;
        assertTrue(end < buf.length);
        assertTrue(end == 513);
        while (end < buf.length) {
            assertTrue((byte)0xbb == buf[end++]);
        }
        assertTrue((byte)0xbb == buf[0]);

        final Descriptor d = Descriptor.parse(buf, 1);
        assertTrue(d instanceof FileSetDescriptor);
        fsd = (FileSetDescriptor)d;

        assertTrue(  fsd.recordingDateAndTime.equals(Timestamp.fromCalendar(CAL0)));
        assertTrue(  fsd.interchangeLevel        == (short)0xabcd);
        assertTrue(  fsd.maximumInterchangeLevel == (short)0x9876);
        assertTrue(  fsd.characterSetList        == 0x01234567);
        assertTrue(  fsd.maximumCharacterSetList == 10000001);
        assertTrue(  fsd.fileSetNumber           == 20000002);
        assertTrue(  fsd.fileSetDescriptorNumber == 30000003);
        assertTrue(  fsd.logicalVolumeIdentifierCharacterSet.equals(CharacterSet.OSTA_COMPRESSED_UNICODE));
        assertTrue(  fsd.logicalVolumeIdentifier            .equals("the_volume"));
        assertTrue(  fsd.fileSetCharacterSet                .equals(CharacterSet.OSTA_COMPRESSED_UNICODE));
        assertTrue(  fsd.fileSetIdentifier                  .equals("123"));
        assertTrue(  fsd.copyrightFileIdentifier            .equals("kopir8"));
        assertTrue(  fsd.abstractFileIdentifier             .equals("afid"));
        assertTrue(  fsd.rootDirectoryICB.location.logicalBlockNumber       == 1);
        assertTrue(  fsd.rootDirectoryICB.location.partitionReferenceNumber == 2);
        assertTrue(  fsd.rootDirectoryICB.type   == AllocationDescriptor.ExtentType.RECORDED_AND_ALLOCATED);
        assertTrue(  fsd.rootDirectoryICB.length == 256);
        checkTestEID(fsd.domainIdentifier, 0x55662233);
        assertTrue(  fsd.nextExtent.length                                          == 0);
        assertTrue(  fsd.nextExtent.location.logicalBlockNumber                     == 0);
        assertTrue(  fsd.nextExtent.location.partitionReferenceNumber               == 0);
        assertTrue(  fsd.systemStreamDirectoryICB.location.logicalBlockNumber       == 12345);
        assertTrue(  fsd.systemStreamDirectoryICB.location.partitionReferenceNumber == 6789);
        assertTrue(  fsd.systemStreamDirectoryICB.type                              == AllocationDescriptor.ExtentType.NOT_RECORDED_BUT_ALLOCATED);
        assertTrue(  fsd.systemStreamDirectoryICB.length                            == 512);
        assertTrue(  fsd.reserved.len                                               == FileSetDescriptor.RSV_LEN);
        assertTrue(TestUtils.checkFill(fsd.reserved, (byte)0));
    }

    ///////////////////////////////////////////////////////////////////////////

    @Test
    public void testICBTag() {
        ICBTag itag = new ICBTag();

        itag.priorRecordedNumberOfDirectEntries = 0xbaadbebe;
        itag.strategyType                       = ICBTag.STRATEGY_UDF102;
        itag.strategyParameter                  = new BytePtr(new byte[] { 127, 1 });
        itag.maximumNumberOfEntries             = (short)0xf001;
        itag.reserved                           = (byte)0x99;
        itag.fileType                           = ICBTag.FileType.CHARACTER_SPECIAL_DEVICE;
        itag.parentICBLocation                  = new RecordedAddress(0x12345678, (short)0xb011);
        itag.flags                              = ICBTag.FLAG_MULTIVER | ICBTag.ALLOCDESC_EMBEDDED;

        byte[] buf = new byte[32];
        Arrays.fill(buf, (byte)0xdd);

        itag.write(buf, 1); itag = null;

        assertTrue((byte)0xdd == buf[0]);
        int end = 1 + ICBTag.LENGTH;
        assertTrue(end < buf.length);
        for (int i = end; i < buf.length; i++) {
            assertTrue((byte)0xdd == buf[i]);
        }

        itag = ICBTag.parse(buf, 1);

        assertTrue(itag.priorRecordedNumberOfDirectEntries         == 0xbaadbebe);
        assertTrue(itag.strategyType                               == ICBTag.STRATEGY_UDF102);
        assertTrue(itag.strategyParameter.len                      == 2);
        assertTrue(itag.strategyParameter.at(0)                    == 127);
        assertTrue(itag.strategyParameter.at(1)                    == 1);
        assertTrue(itag.maximumNumberOfEntries                     == (short)0xf001);
        assertTrue(itag.reserved                                   == (byte)0x99);
        assertTrue(itag.fileType                                   == ICBTag.FileType.CHARACTER_SPECIAL_DEVICE);
        assertTrue(itag.parentICBLocation.logicalBlockNumber       == 0x12345678);
        assertTrue(itag.parentICBLocation.partitionReferenceNumber == (short)0xb011);
        assertTrue(itag.flags                                      == (short)(ICBTag.FLAG_MULTIVER | ICBTag.ALLOCDESC_EMBEDDED));

        assertTrue(itag.allocDescriptor() == ICBTag.ALLOCDESC_EMBEDDED);
    }

    ///////////////////////////////////////////////////////////////////////////

    @Test
    public void testImplementationUseVolumeDescriptor() throws UDFException {
        EntityIdentifier.UDFSuffix usfx = new EntityIdentifier.UDFSuffix();
        usfx.osClass      = OSInfo.OSCLASS_UNIX;
        usfx.osIdentifier = OSInfo.OSID_UNIX_SUNSOLARIS;
        usfx.reserved     = TestUtils.fillPattern123(EntityIdentifier.UDFSuffix.RESV_LEN);

        ImplementationUseVolumeDescriptor iuvd = new ImplementationUseVolumeDescriptor(4711);

        iuvd.volumeDescriptorSequenceNumber            = 1234567;
        iuvd.implementationIdentifier                  = new EntityIdentifier(EntityIdentifier.ID_UDF);
        iuvd.implementationIdentifier.flags            = EntityIdentifier.FLAG_PROTECTED;
        iuvd.implementationIdentifier.identifierSuffix = usfx.data();
        iuvd.lviCharset                                = CharacterSet.OSTA_COMPRESSED_UNICODE;
        iuvd.logicalVolumeIdentifier                   = "voluminous";
        iuvd.lvInfo1                                   = "test711";
        iuvd.lvInfo2                                   = "two";
        iuvd.lvInfo3                                   = "andthree";
        iuvd.implementationID                          = new EntityIdentifier("testimpl");
        iuvd.implementationID.flags                    = EntityIdentifier.FLAG_DIRTY;
        iuvd.implementationID.identifierSuffix         = new EntityIdentifier.ImplSuffix(33, 1, TestUtils.fillPattern123(EntityIdentifier.ImplSuffix.IMPL_USE_AREA_LEN)).data();
        iuvd.implementationUse                         = TestUtils.fillPattern123(ImplementationUseVolumeDescriptor.IMPL_USE_LEN);

        byte[] buf = new byte[1000];
        Arrays.fill(buf, (byte)0xcc);

        int ofs = iuvd.write(buf, 1); iuvd = null;
        assertTrue(ofs < buf.length);
        assertTrue(ofs == 512 + 1);
        assertTrue((byte)0xcc == buf[0]);
        for (; ofs < buf.length; ofs++) {
            assertTrue((byte)0xcc == buf[0]);
        }

        Descriptor d = Descriptor.parse(buf, 1);
        assertTrue(d instanceof ImplementationUseVolumeDescriptor);
        iuvd = (ImplementationUseVolumeDescriptor)d;

        assertTrue(iuvd.tag.location                   == 4711);
        assertTrue(iuvd.volumeDescriptorSequenceNumber == 1234567);
        assertTrue(iuvd.implementationIdentifier.identifierAsString().equals(EntityIdentifier.ID_UDF));
        assertTrue(iuvd.implementationIdentifier.flags == EntityIdentifier.FLAG_PROTECTED);
        assertTrue(iuvd.implementationIdentifier.suffix() instanceof EntityIdentifier.UDFSuffix);
        assertTrue(iuvd.lviCharset.equals(CharacterSet.OSTA_COMPRESSED_UNICODE));
        assertTrue(iuvd.logicalVolumeIdentifier.equals("voluminous"));
        assertTrue(iuvd.lvInfo1                .equals("test711"));
        assertTrue(iuvd.lvInfo2                .equals("two"));
        assertTrue(iuvd.lvInfo3                .equals("andthree"));
        assertTrue(iuvd.implementationID.identifierAsString().equals("testimpl"));
        assertTrue(iuvd.implementationID.flags == EntityIdentifier.FLAG_DIRTY);
        assertTrue(iuvd.implementationID.suffix() instanceof EntityIdentifier.ImplSuffix);
        assertTrue(iuvd.implementationUse.len  == ImplementationUseVolumeDescriptor.IMPL_USE_LEN);
        assertTrue(TestUtils.checkPattern123(iuvd.implementationUse));

        EntityIdentifier.ImplSuffix isfx = (EntityIdentifier.ImplSuffix)iuvd.implementationID.suffix();
        assertTrue(isfx.osClass         == 33);
        assertTrue(isfx.osIdentifier    == 1);
        assertTrue(isfx.implUseArea.len == EntityIdentifier.ImplSuffix.IMPL_USE_AREA_LEN);
        assertTrue(TestUtils.checkPattern123(isfx.implUseArea));

        usfx = (EntityIdentifier.UDFSuffix)iuvd.implementationIdentifier.suffix();
        assertTrue(usfx.osClass      == OSInfo.OSCLASS_UNIX);
        assertTrue(usfx.osIdentifier == OSInfo.OSID_UNIX_SUNSOLARIS);
        assertTrue(usfx.reserved.len == EntityIdentifier.UDFSuffix.RESV_LEN);
        assertTrue(TestUtils.checkPattern123(usfx.reserved));
    }

    ///////////////////////////////////////////////////////////////////////////

    @Test
    public void testLogicalVolumeDescriptor() throws UDFException {
        EntityIdentifier.OSTASuffix osfx = new
        EntityIdentifier.OSTASuffix();
        osfx.udfRevision = EntityIdentifier.RevisionSuffix.UDF_REVISION_102;
        osfx.domainFlags = EntityIdentifier.OSTASuffix.DOMAINFLAG_SOFT_WRITE_PROTECT;

        LogicalVolumeDescriptor lvd = new LogicalVolumeDescriptor(2009);

        lvd.volumeDescriptorSequenceNumber            = 6;
        lvd.descriptorCharacterSet                    = CharacterSet.OSTA_COMPRESSED_UNICODE;
        lvd.logicalVolumeIdentifier                   = "the_volume";
        lvd.logicalBlockSize                          = 512;
        lvd.domainIdentifier                          = new EntityIdentifier(EntityIdentifier.ID_OSTA);
        lvd.domainIdentifier.flags                    = EntityIdentifier.FLAG_PROTECTED;
        lvd.domainIdentifier.identifierSuffix         = osfx.data();
        lvd.logicalVolumeContentsUse                  = TestUtils.fillPattern123(LogicalVolumeDescriptor.CONTENT_USE_LEN);
        lvd.mapTableLength                            = PartitionMap.Type1.LENGTH;
        lvd.numberOfPartitionMaps                     = 2;
        lvd.implementationIdentifier                  = new EntityIdentifier("*UDFWriter");
        lvd.implementationIdentifier.flags            = EntityIdentifier.FLAG_PROTECTED;
        lvd.implementationIdentifier.identifierSuffix = new EntityIdentifier.ImplSuffix((byte)0xfa, (byte)0xac, null).data();
        lvd.implementationUse                         = TestUtils.fillPattern123(LogicalVolumeDescriptor.IMPL_USE_LEN);
        lvd.integritySequenceExtent                   = new ExtentDescriptor(ExtentDescriptor.MAX_LENGTH, 0xeeffeeff);
        lvd.partitionMaps                             = TestUtils.fillPattern123(PartitionMap.Type1.LENGTH);

        byte[] buf = new byte[1000];
        Arrays.fill(buf, (byte)0xee);
        int ofs = lvd.write(buf, 1); lvd = null;
        assertTrue((byte)0xee == buf[0]);
        assertTrue(ofs < buf.length);
        assertTrue(ofs == 447);
        while (ofs < buf.length) {
            assertTrue((byte)0xee == buf[ofs++]);
        }

        Descriptor d = Descriptor.parse(buf, 1);
        lvd = (LogicalVolumeDescriptor)d;

        assertTrue(lvd.volumeDescriptorSequenceNumber   == 6);
        assertTrue(lvd.descriptorCharacterSet           .equals(CharacterSet.OSTA_COMPRESSED_UNICODE));
        assertTrue(lvd.logicalVolumeIdentifier          .equals("the_volume"));
        assertTrue(lvd.logicalBlockSize                 == 512);
        assertTrue(lvd.domainIdentifier.flags           == EntityIdentifier.FLAG_PROTECTED);
        assertTrue(lvd.logicalVolumeContentsUse.len     == LogicalVolumeDescriptor.CONTENT_USE_LEN);
        assertTrue(lvd.mapTableLength                   == PartitionMap.Type1.LENGTH);
        assertTrue(lvd.numberOfPartitionMaps            == 2);
        assertTrue(lvd.implementationIdentifier.identifierAsString().equals("*UDFWriter"));
        assertTrue(lvd.implementationIdentifier.flags   == EntityIdentifier.FLAG_PROTECTED);
        assertTrue(lvd.implementationUse.len            == LogicalVolumeDescriptor.IMPL_USE_LEN);
        assertTrue(lvd.integritySequenceExtent.length   == ExtentDescriptor.MAX_LENGTH);
        assertTrue(lvd.integritySequenceExtent.location == 0xeeffeeff);
        assertTrue(lvd.partitionMaps.len                == PartitionMap.Type1.LENGTH);

        assertTrue(lvd.domainIdentifier        .suffix() instanceof EntityIdentifier.OSTASuffix);
        assertTrue(lvd.implementationIdentifier.suffix() instanceof EntityIdentifier.ImplSuffix);

        osfx = (EntityIdentifier.OSTASuffix)lvd.domainIdentifier.suffix();
        assertTrue(osfx.udfRevision == EntityIdentifier.RevisionSuffix.UDF_REVISION_102);
        assertTrue(osfx.domainFlags == EntityIdentifier.OSTASuffix.DOMAINFLAG_SOFT_WRITE_PROTECT);

        EntityIdentifier.ImplSuffix isfx = (EntityIdentifier.ImplSuffix)lvd.implementationIdentifier.suffix();
        assertTrue(isfx.osClass      == (byte)0xfa);
        assertTrue(isfx.osIdentifier == (byte)0xac);

        assertTrue(TestUtils.checkPattern123(lvd.logicalVolumeContentsUse));
        assertTrue(TestUtils.checkPattern123(lvd.implementationUse));
        assertTrue(TestUtils.checkPattern123(lvd.partitionMaps));
    }

    ///////////////////////////////////////////////////////////////////////////

    @Test
    public void testLogicalVolumeHeaderDescriptor() {
        LogicalVolumeHeaderDescriptor lvhd = new
        LogicalVolumeHeaderDescriptor();

        lvhd.uniqueID = 0xf00dbabecafebaadL;
        lvhd.reserved = TestUtils.fillPattern123(LogicalVolumeHeaderDescriptor.RESV_LEN);

        byte[] buf = new byte[100];
        Arrays.fill(buf, (byte)0xaf);
        lvhd.write(buf, 1); lvhd = null;
        assertTrue((byte)0xaf == buf[0]);
        assertTrue(  LogicalVolumeHeaderDescriptor.LENGTH < buf.length);
        for (int i = LogicalVolumeHeaderDescriptor.LENGTH + 1; i < buf.length;) {
            assertTrue((byte)0xaf == buf[i++]);
        }

        lvhd = new LogicalVolumeHeaderDescriptor(buf, 1);
        assertTrue(lvhd.uniqueID     == 0xf00dbabecafebaadL);
        assertTrue(lvhd.reserved.len == LogicalVolumeHeaderDescriptor.RESV_LEN);
        assertTrue(TestUtils.checkPattern123(lvhd.reserved));
    }

    ///////////////////////////////////////////////////////////////////////////

    @Test
    public void testLogicalVolumeIntegrityDescriptor() throws UDFException {
        LogicalVolumeIntegrityDescriptor lvid = new
        LogicalVolumeIntegrityDescriptor(2001);

        lvid.recordingDateAndTime      = Timestamp.fromCalendar(CAL2);
        lvid.integrityType             = LogicalVolumeIntegrityDescriptor.TYPE_CLOSE;
        lvid.nextIntegrityExtent       = ExtentDescriptor.NONE;
        lvid.logicalVolumeContentsUse  = TestUtils.fillPattern123(LogicalVolumeIntegrityDescriptor.LOG_VOL_CONT_USE_LEN);
        lvid.numberOfPartitions        = 3;
        lvid.lengthOfImplementationUse = LogicalVolumeIntegrityDescriptor.UDF_IMPL_USE_LEN + 11;
        lvid.freeSpaceTable            = new int[] { 1, 2, 3 };
        lvid.sizeTable                 = new int[] { 3000, 5005, 88008 };
        lvid.entityID                  = new EntityIdentifier("*UDFWriter");
        lvid.entityID.flags            = EntityIdentifier.FLAG_PROTECTED;
        lvid.entityID.identifierSuffix = new EntityIdentifier.ImplSuffix(33, 88, null).data();
        lvid.numberOfFiles             = 0xabbabebe;
        lvid.numberOfDirectories       = 0xa001b002;
        lvid.minimumUDFReadRevision    = (short)0xbaad;
        lvid.minimumUDFWriteRevision   = (short)0xf00d;
        lvid.maximumUDFWriteRevision   = (short)0xbabe;
        lvid.implementationUse         = TestUtils.fillPattern123(11);

        byte[] buf = new byte[200];
        Arrays.fill(buf, (byte)0xcd);
        int ofs = lvid.write(buf, 1); lvid = null;
        assertTrue((byte)0xcd == buf[0]);
        assertTrue(ofs < buf.length);
        assertTrue(ofs == 162);
        while (ofs < buf.length) {
            assertTrue((byte)0xcd == buf[ofs++]);
        }

        Descriptor d = Descriptor.parse(buf, 1);
        assertTrue(d instanceof LogicalVolumeIntegrityDescriptor);
        lvid = (LogicalVolumeIntegrityDescriptor)d;

        assertTrue(lvid.tag.location                 == 2001);
        assertTrue(lvid.integrityType                == LogicalVolumeIntegrityDescriptor.TYPE_CLOSE);
        assertTrue(lvid.nextIntegrityExtent.location == 0);
        assertTrue(lvid.nextIntegrityExtent.length   == 0);
        assertTrue(lvid.logicalVolumeContentsUse.len == LogicalVolumeIntegrityDescriptor.LOG_VOL_CONT_USE_LEN);
        assertTrue(lvid.numberOfPartitions           == 3);
        assertTrue(lvid.lengthOfImplementationUse    == LogicalVolumeIntegrityDescriptor.UDF_IMPL_USE_LEN + 11);
        assertTrue(lvid.entityID.flags               == EntityIdentifier.FLAG_PROTECTED);
        assertTrue(lvid.numberOfFiles                == 0xabbabebe);
        assertTrue(lvid.numberOfDirectories          == 0xa001b002);
        assertTrue(lvid.minimumUDFReadRevision       == (short)0xbaad);
        assertTrue(lvid.minimumUDFWriteRevision      == (short)0xf00d);
        assertTrue(lvid.maximumUDFWriteRevision      == (short)0xbabe);
        assertTrue(lvid.implementationUse.len        == 11);

        assertTrue(lvid.recordingDateAndTime         .equals(Timestamp.fromCalendar(CAL2)));
        assertTrue(lvid.entityID.identifierAsString().equals("*UDFWriter"));

        assertTrue(TestUtils.checkPattern123(lvid.logicalVolumeContentsUse));
        assertTrue(TestUtils.checkPattern123(lvid.implementationUse));

        assertTrue(BinUtils.arraysEquals(lvid.freeSpaceTable, new int[] { 1, 2, 3 }));
        assertTrue(BinUtils.arraysEquals(lvid.sizeTable     , new int[] { 3000, 5005, 88008 }));

        assertTrue(lvid.entityID.suffix() instanceof EntityIdentifier.ImplSuffix);
        EntityIdentifier.ImplSuffix isfx = (EntityIdentifier.ImplSuffix)lvid.entityID.suffix();
        assertTrue(isfx.osClass         == 33);
        assertTrue(isfx.osIdentifier    == 88);
        assertTrue(isfx.implUseArea.len == EntityIdentifier.ImplSuffix.IMPL_USE_AREA_LEN);
        assertTrue(BinUtils.checkFillValue(isfx.implUseArea.buf,
                                           isfx.implUseArea.ofs,
                                           isfx.implUseArea.len, (byte)0));
    }

    ///////////////////////////////////////////////////////////////////////////

    @Test
    public void testPartitionDescriptor() throws UDFException {
        PartitionHeaderDescriptor phd = new PartitionHeaderDescriptor();
        phd.unallocatedSpaceBitmap           = new AllocationDescriptor.Short();
        phd.unallocatedSpaceBitmap.type      = AllocationDescriptor.ExtentType.RECORDED_AND_ALLOCATED;
        phd.unallocatedSpaceBitmap.length    = AllocationDescriptor.MAX_LENGTH;
        phd.unallocatedSpaceBitmap.position  = 555555555;
        phd.unallocatedSpaceTable            = new AllocationDescriptor.Short();
        phd.unallocatedSpaceTable.type       = AllocationDescriptor.ExtentType.RECORDED_AND_ALLOCATED;
        phd.unallocatedSpaceTable.length     = 14;
        phd.unallocatedSpaceTable.position   = 15;
        phd.freedSpaceTable                  = new AllocationDescriptor.Short();
        phd.freedSpaceTable.type             = AllocationDescriptor.ExtentType.RECORDED_AND_ALLOCATED;
        phd.freedSpaceTable.length           = 0xbaad;
        phd.freedSpaceTable.position         = 0xf0010001;
        phd.freedSpaceBitmap                 = new AllocationDescriptor.Short();
        phd.freedSpaceBitmap.type            = AllocationDescriptor.ExtentType.RECORDED_AND_ALLOCATED;
        phd.freedSpaceBitmap.length          = 12345;
        phd.freedSpaceBitmap.position        = 67890;
        phd.partitionIntegrityTable          = new AllocationDescriptor.Short();
        phd.partitionIntegrityTable.type     = AllocationDescriptor.ExtentType.RECORDED_AND_ALLOCATED;
        phd.partitionIntegrityTable.length   = 1000001;
        phd.partitionIntegrityTable.position = 1000002;

        PartitionDescriptor pd = new PartitionDescriptor(1010101010);

        pd.volumeDescriptorSequenceNumber            = 0xabbac001;
        pd.partitionFlags                            = PartitionDescriptor.FLAG_VOLUME_SPACE_ALLOCATED;
        pd.partitionNumber                           = 325;
        pd.partitionContents                         = new EntityIdentifier(EntityIdentifier.ID_NSR02);
        pd.partitionContents.flags                   = EntityIdentifier.FLAG_PROTECTED;
        pd.partitionContents.identifierSuffix        = new BytePtr(new byte[EntityIdentifier.Suffix.LENGTH]);
        pd.partitionContentsUse                      = phd;
        pd.accessType                                = PartitionDescriptor.AccessType.OVERWRITABLE;
        pd.partitionStartingLocation                 = 100;
        pd.partitionLength                           = 7531;
        pd.implementationIdentifier                  = new EntityIdentifier("*UDFWriter");
        pd.implementationIdentifier.flags            = EntityIdentifier.FLAG_PROTECTED;
        pd.implementationIdentifier.identifierSuffix = new EntityIdentifier.ImplSuffix(1, 2, null).data();
        pd.implementationUse                         = TestUtils.fillPattern123(PartitionDescriptor.IMPL_USE_LEN);
        pd.reserved                                  = TestUtils.fillPattern123(PartitionDescriptor.RESV_USE_LEN);

        byte[] buf = new byte[1000];
        Arrays.fill(buf, (byte)0x44);

        int ofs = pd.write(buf, 1); pd = null;
        assertTrue(0x44 == buf[0]);
        assertTrue(ofs < buf.length);
        assertTrue(ofs == 513);
        while (ofs < buf.length) {
            assertTrue(0x44 == buf[ofs++]);
        }

        Descriptor d = Descriptor.parse(buf, 1);
        assertTrue(d instanceof PartitionDescriptor);
        pd = (PartitionDescriptor)d;

        assertTrue(pd.tag.location                           == 1010101010);
        assertTrue(pd.volumeDescriptorSequenceNumber         == 0xabbac001);
        assertTrue(pd.partitionFlags                         == PartitionDescriptor.FLAG_VOLUME_SPACE_ALLOCATED);
        assertTrue(pd.partitionNumber                        == 325);
        assertTrue(pd.partitionContents.flags                == EntityIdentifier.FLAG_PROTECTED);
        assertTrue(pd.partitionContents.identifierSuffix.len == EntityIdentifier.Suffix.LENGTH);
        assertTrue(pd.accessType                             == PartitionDescriptor.AccessType.OVERWRITABLE);
        assertTrue(pd.partitionStartingLocation              == 100);
        assertTrue(pd.partitionLength                        == 7531);
        assertTrue(pd.implementationIdentifier.flags         == EntityIdentifier.FLAG_PROTECTED);
        assertTrue(pd.implementationUse.len                  == PartitionDescriptor.IMPL_USE_LEN);
        assertTrue(pd.reserved.len                           == PartitionDescriptor.RESV_USE_LEN);

        assertTrue(pd.partitionContents.identifierAsString().equals(EntityIdentifier.ID_NSR02));
        assertTrue(BinUtils.checkFillValue(pd.partitionContents.identifierSuffix.buf,
                                           pd.partitionContents.identifierSuffix.ofs,
                                           pd.partitionContents.identifierSuffix.len,
                                           (byte)0));

        assertTrue(pd.implementationIdentifier.identifierAsString().equals("*UDFWriter"));
        assertTrue(pd.implementationIdentifier.suffix() instanceof EntityIdentifier.ImplSuffix);
        EntityIdentifier.ImplSuffix isfx = (EntityIdentifier.ImplSuffix)pd.implementationIdentifier.suffix();
        assertTrue(isfx.osClass      == 1);
        assertTrue(isfx.osIdentifier == 2);

        assertTrue(TestUtils.checkPattern123(pd.implementationUse));
        assertTrue(TestUtils.checkPattern123(pd.reserved));

        assertTrue(phd.unallocatedSpaceBitmap.type      == AllocationDescriptor.ExtentType.RECORDED_AND_ALLOCATED);
        assertTrue(phd.unallocatedSpaceBitmap.length    == AllocationDescriptor.MAX_LENGTH);
        assertTrue(phd.unallocatedSpaceBitmap.position  == 555555555);
        assertTrue(phd.unallocatedSpaceTable.type       == AllocationDescriptor.ExtentType.RECORDED_AND_ALLOCATED);
        assertTrue(phd.unallocatedSpaceTable.length     == 14);
        assertTrue(phd.unallocatedSpaceTable.position   == 15);
        assertTrue(phd.freedSpaceTable.type             == AllocationDescriptor.ExtentType.RECORDED_AND_ALLOCATED);
        assertTrue(phd.freedSpaceTable.length           == 0xbaad);
        assertTrue(phd.freedSpaceTable.position         == 0xf0010001);
        assertTrue(phd.freedSpaceBitmap.type            == AllocationDescriptor.ExtentType.RECORDED_AND_ALLOCATED);
        assertTrue(phd.freedSpaceBitmap.length          == 12345);
        assertTrue(phd.freedSpaceBitmap.position        == 67890);
        assertTrue(phd.partitionIntegrityTable.type     == AllocationDescriptor.ExtentType.RECORDED_AND_ALLOCATED);
        assertTrue(phd.partitionIntegrityTable.length   == 1000001);
        assertTrue(phd.partitionIntegrityTable.position == 1000002);
    }

    ///////////////////////////////////////////////////////////////////////////

    @Test
    public void testPartitionMap() throws UDFException {
        PartitionMap.Type1 pm1 = new PartitionMap.Type1();
        pm1.volumeSequenceNumber = (short)0xabcd;
        pm1.partitionNumber      = (short)55555;
        assertTrue(pm1.length == PartitionMap.Type1.LENGTH);

        byte[] buf = new byte[10];
        Arrays.fill(buf, (byte)0xcc);

        int ofs = pm1.write(buf, 1); pm1 = null;
        assertTrue((byte)0xcc == buf[0]);
        assertTrue(ofs == 1 + PartitionMap.Type1.LENGTH);
        while (ofs < buf.length) {
            assertTrue((byte)0xcc == buf[ofs++]);
        }

        PartitionMap[] pm = PartitionMap.parse(1,
                new BytePtr(buf, 1, PartitionMap.Type1.LENGTH));

        assertTrue(1 == pm.length);
        assertTrue(pm[0] instanceof PartitionMap.Type1);

        pm1 = (PartitionMap.Type1)pm[0];

        assertTrue(pm1.length == PartitionMap.Type1.LENGTH);
        assertTrue(pm1.volumeSequenceNumber == (short)0xabcd);
        assertTrue(pm1.partitionNumber      == (short)55555);
        assertTrue(pm1.mapping.len == 2 + 2);
        assertTrue(BinUtils.readInt16LE(pm1.mapping.buf, pm1.mapping.ofs    ) == (short)0xabcd);
        assertTrue(BinUtils.readInt16LE(pm1.mapping.buf, pm1.mapping.ofs + 2) == (short)55555);
    }

    ///////////////////////////////////////////////////////////////////////////

    @Test
    public void testPrimaryVolumeDescriptor() throws UDFException {
        PrimaryVolumeDescriptor pvd = new PrimaryVolumeDescriptor(444444444);

        pvd.volumeDescriptorSequenceNumber            = 5;
        pvd.primaryVolumeDescriptorNumber             = 123;
        pvd.volumeIdentifier                          = "some_volume";
        pvd.volumeSequenceNumber                      = 1234;
        pvd.maximumVolumeSequenceNumber               = 5678;
        pvd.interchangeLevel                          = 7777;
        pvd.maximumInterchangeLevel                   = 8888;
        pvd.characterSetList                          = 1;
        pvd.maximumCharacterSetList                   = 1;
        pvd.volumeSetIdentifier                       = "unique4711";
        pvd.descriptorCharacterSet                    = CharacterSet.OSTA_COMPRESSED_UNICODE;
        pvd.explanatoryCharacterSet                   = CharacterSet.OSTA_COMPRESSED_UNICODE;
        pvd.volumeAbstract                            = ExtentDescriptor.NONE;
        pvd.volumeCopyrightNotice                     = new ExtentDescriptor(88, 89);
        pvd.applicationIdentifier                     = new EntityIdentifier().noid();
        pvd.recordingDateAndTime                      = Timestamp.fromCalendar(CAL1);
        pvd.implementationIdentifier                  = new EntityIdentifier("*UDFWriter");
        pvd.implementationIdentifier.flags            = EntityIdentifier.FLAG_PROTECTED;
        pvd.implementationIdentifier.identifierSuffix = new EntityIdentifier.ImplSuffix(55, 66, null).data();
        pvd.implementationUse                         = TestUtils.fillPattern123(PrimaryVolumeDescriptor.IMPL_USE_LEN);
        pvd.predecessorSequenceLocation               = 398621;
        pvd.flags                                     = PrimaryVolumeDescriptor.FLAG_VSI_COMMON;
        pvd.reserved                                  = TestUtils.fillPattern123(PrimaryVolumeDescriptor.RESV_LEN);

        byte[] buf = new byte[555];
        Arrays.fill(buf, (byte)0xdd);

        int ofs = pvd.write(buf, 1); pvd = null;
        assertTrue(ofs < buf.length);
        assertTrue(ofs == 513);
        assertTrue((byte)0xdd == buf[0]);
        while (ofs < buf.length) {
            assertTrue((byte)0xdd == buf[ofs++]);
        }

        Descriptor d = Descriptor.parse(buf, 1);
        pvd = (PrimaryVolumeDescriptor)d;

        assertTrue(pvd.tag.location                                  == 444444444);
        assertTrue(pvd.volumeDescriptorSequenceNumber                == 5);
        assertTrue(pvd.primaryVolumeDescriptorNumber                 == 123);
        assertTrue(pvd.volumeIdentifier                              .equals("some_volume"));
        assertTrue(pvd.volumeSequenceNumber                          == 1234);
        assertTrue(pvd.maximumVolumeSequenceNumber                   == 5678);
        assertTrue(pvd.interchangeLevel                              == 7777);
        assertTrue(pvd.maximumInterchangeLevel                       == 8888);
        assertTrue(pvd.characterSetList                              == 1);
        assertTrue(pvd.maximumCharacterSetList                       == 1);
        assertTrue(pvd.volumeSetIdentifier                           .equals("unique4711"));
        assertTrue(pvd.descriptorCharacterSet                        .equals(CharacterSet.OSTA_COMPRESSED_UNICODE));
        assertTrue(pvd.explanatoryCharacterSet                       .equals(CharacterSet.OSTA_COMPRESSED_UNICODE));
        assertTrue(pvd.volumeAbstract.length                         == 0);
        assertTrue(pvd.volumeAbstract.location                       == 0);
        assertTrue(pvd.volumeCopyrightNotice.location                == 89);
        assertTrue(pvd.volumeCopyrightNotice.length                  == 88);
        assertTrue(pvd.applicationIdentifier.flags                   == 0);
        assertTrue(pvd.applicationIdentifier.identifier.len          == EntityIdentifier.ID_LEN);
        assertTrue(pvd.applicationIdentifier.identifierSuffix .len   == EntityIdentifier.Suffix.LENGTH);
        assertTrue(pvd.recordingDateAndTime                          .equals(Timestamp.fromCalendar(CAL1)));
        assertTrue(pvd.implementationIdentifier.identifierAsString() .equals("*UDFWriter"));
        assertTrue(pvd.implementationIdentifier.flags                == EntityIdentifier.FLAG_PROTECTED);
        assertTrue(pvd.implementationIdentifier.identifierSuffix.len == Suffix.LENGTH);
        assertTrue(pvd.implementationUse.len                         == PrimaryVolumeDescriptor.IMPL_USE_LEN);
        assertTrue(pvd.predecessorSequenceLocation                   == 398621);
        assertTrue(pvd.flags                                         == PrimaryVolumeDescriptor.FLAG_VSI_COMMON);
        assertTrue(pvd.reserved.len                                  == PrimaryVolumeDescriptor.RESV_LEN);

        assertTrue(pvd.implementationIdentifier.suffix() instanceof EntityIdentifier.ImplSuffix);
        EntityIdentifier.ImplSuffix isfx = (EntityIdentifier.ImplSuffix)pvd.implementationIdentifier.suffix();
        assertTrue(isfx.osClass      == 55);
        assertTrue(isfx.osIdentifier == 66);

        assertTrue(TestUtils.checkPattern123(pvd.implementationUse));
        assertTrue(TestUtils.checkPattern123(pvd.reserved));
    }

    ///////////////////////////////////////////////////////////////////////////

    @Test
    public void testSpaceBitmapDescriptor() throws UDFException {
        SpaceBitmapDescriptor sbd = new SpaceBitmapDescriptor(1357902468);

        sbd.numberOfBits  = 0xabcdc001;
        sbd.numberOfBytes = 0x44220088;

        byte[] buf = new byte[50];
        Arrays.fill(buf, (byte)0xee);

        int ofs = sbd.write(buf, 1); sbd = null;
        assertTrue(ofs < buf.length);
        assertTrue(ofs == 25);
        assertTrue((byte)0xee == buf[0]);
        while (ofs < buf.length) {
            assertTrue((byte)0xee == buf[ofs++]);
        }

        Descriptor d = Descriptor.parse(buf, 1);
        assertTrue(d instanceof SpaceBitmapDescriptor);
        sbd = (SpaceBitmapDescriptor)d;

        assertTrue(sbd.tag.location            == 1357902468);
        assertTrue(sbd.tag.descriptorCRCLength == SpaceBitmapDescriptor.LENGTH - Descriptor.Tag.LENGTH);
        assertTrue(sbd.numberOfBits            == 0xabcdc001);
        assertTrue(sbd.numberOfBytes           == 0x44220088);
    }

    ///////////////////////////////////////////////////////////////////////////

    @Test
    public void testTerminatingDescriptor() throws UDFException {
        TerminatingDescriptor td = new TerminatingDescriptor(0x1a1ca10e);
        td.reserved = TestUtils.fillPattern123(TerminatingDescriptor.RESV_LEN);

        byte[] buf = new byte[600];
        Arrays.fill(buf, (byte)0xdd);

        int ofs = td.write(buf, 1); td = null;
        assertTrue(ofs < buf.length);
        assertTrue(ofs == 513);
        assertTrue((byte)0xdd == buf[0]);
        while (ofs < buf.length) {
            assertTrue((byte)0xdd == buf[ofs++]);
        }

        Descriptor d = Descriptor.parse(buf, 1);
        assertTrue(d instanceof TerminatingDescriptor);
        td = (TerminatingDescriptor)d;
        assertTrue(td.tag.location            == 0x1a1ca10e);
        assertTrue(td.tag.descriptorCRC       == 21423);
        assertTrue(td.tag.descriptorCRCLength == TerminatingDescriptor.RESV_LEN);
        assertTrue(td.reserved.len            == TerminatingDescriptor.RESV_LEN);
        assertTrue(TestUtils.checkPattern123(td.reserved));
    }

    ///////////////////////////////////////////////////////////////////////////

    @Test
    public void testUnallocatedSpaceDescriptor() throws UDFException {
        for (int i = 0; i < 3; i++) {
            UnallocatedSpaceDescriptor usd = new UnallocatedSpaceDescriptor(404 + i);
            usd.volumeDescriptorSequenceNumber = 303030303 + i;

            usd.allocationDescriptors = new ExtentDescriptor[i];
            for (int j = 0; j < i; j++) {
                usd.allocationDescriptors[j] = new ExtentDescriptor(
                        50000     + j + i,
                        120871234 + j + i);
            }

            byte[] buf = new byte[200];
            Arrays.fill(buf, (byte)0xee);

            int ofs = usd.write(buf, 1);
            assertTrue(ofs < buf.length);
            assertTrue((byte)0xee == buf[0]);
            while (ofs < buf.length) {
                assertTrue((byte)0xee == buf[ofs++]);
            }

            Descriptor d = Descriptor.parse(buf, 1);
            assertTrue(d instanceof UnallocatedSpaceDescriptor);
            usd = (UnallocatedSpaceDescriptor)d;
            assertTrue(usd.volumeDescriptorSequenceNumber == 303030303 + i);
            assertTrue(usd.allocationDescriptors.length == i);
            for (int j = 0; j < i; j++) {
                assertTrue(usd.allocationDescriptors[j].length   == 50000     + j + i);
                assertTrue(usd.allocationDescriptors[j].location == 120871234 + j + i);
            }
        }
    }

    ///////////////////////////////////////////////////////////////////////////

    @Test
    public void testVolumeStructureDescriptor() {
        for (final String id : new String[] { "BEA01", "TEA01", "NSR02", "NSR03" }) {
            VolumeStructureDescriptor vsd = new VolumeStructureDescriptor();

            vsd.standardIdentifier = id;
            vsd.structureType      = (byte)id.hashCode();
            vsd.structureVersion   = (byte)0x20;
            vsd.data               = TestUtils.fillPattern123(100 + (id.hashCode() & 31));

            byte[] buf = new byte[200];
            Arrays.fill(buf, (byte)0xaa);

            int ofs = vsd.write(buf, 1); vsd = null;
            final int len = ofs - 1;
            assertTrue((byte)0xaa == buf[0]);
            assert(ofs < buf.length);
            while (ofs < buf.length) {
                assertTrue((byte)0xaa == buf[ofs++]);
            }

            vsd = new VolumeStructureDescriptor(buf, 1, len);
            assertTrue(vsd.standardIdentifier .equals(id));
            assertTrue(vsd.structureType      == (byte)id.hashCode());
            assertTrue(vsd.structureVersion   == (byte)0x20);
            assertTrue(vsd.data.len           == 100 + (id.hashCode() & 31));
            assertTrue(TestUtils.checkPattern123(vsd.data));
        }
    }
}
