package coderslagoon.trupax.lib.io.filesystem.udf;

import coderslagoon.baselib.util.BinUtils;
import coderslagoon.baselib.util.BytePtr;

public class EntityIdentifier {   // aka regid
    public final static int FLAG_DIRTY     = 1;
    public final static int FLAG_PROTECTED = 2;

    public final static int LENGTH = 32;

    public byte    flags;
    public BytePtr identifier;
    public BytePtr identifierSuffix;

    public boolean isDirty()       { return FLAG_DIRTY     == (this.flags & FLAG_DIRTY); }
    public boolean isProtected()   { return FLAG_PROTECTED == (this.flags & FLAG_PROTECTED); }
    public boolean isECMA167()     { return 0x2b == this.identifier.at(0); }
    public boolean doNotRegister() { return 0x2d == this.identifier.at(0); }

    final static int ID_LEN = 23;

    public EntityIdentifier() {
    }
    public EntityIdentifier(String id) {
        byte[] tmp = new byte[ID_LEN];
        byte[] idb = id.getBytes();
        System.arraycopy(idb, 0, tmp, 0, idb.length);

        this.identifier = new BytePtr(tmp);
    }

    EntityIdentifier(byte[] buf, int ofs) {
        this.flags            = buf[ofs];                                 ofs++;
        this.identifier       = new BytePtr.Checked(buf, ofs, ID_LEN);    ofs += ID_LEN;
        this.identifierSuffix = new BytePtr.Checked(buf, ofs, Suffix.LENGTH);
    }

    public static EntityIdentifier parse(byte[] buf, int ofs) {
        return new EntityIdentifier(buf, ofs);
    }

    public EntityIdentifier noid() {
        this.identifier       = new BytePtr(new byte[ID_LEN]);
        this.identifierSuffix = new BytePtr(new byte[Suffix.LENGTH]);
        return this;
    }

    public void write(byte[] buf, int ofs) {
        buf[ofs++] = this.flags;
        System.arraycopy(this.identifier.buf,
                         this.identifier.ofs,
                         buf,
                         ofs,
                         ID_LEN);
        ofs += ID_LEN;
        System.arraycopy(this.identifierSuffix.buf,
                this.identifierSuffix.ofs,
                buf,
                ofs,
                Suffix.LENGTH);
    }

    public static String ID_OSTA  = "*OSTA UDF Compliant";
    public static String ID_UDF   = "*UDF LV Info";
    public static String ID_NSR02 = "+NSR02";
    public static String ID_NSR03 = "+NSR03";

    public String identifierAsString() throws UDFException {
        return DString.readChars(this.identifier);
    }

    public Suffix suffix() throws UDFException {
        String idstr = identifierAsString();

             if (idstr.equals(""      )) return null;
        else if (idstr.equals(ID_UDF  )) return new UDFSuffix (this.identifierSuffix);
        else if (idstr.equals(ID_OSTA )) return new OSTASuffix(this.identifierSuffix);
        else if (idstr.equals(ID_NSR02)) return null;
        else if (idstr.equals(ID_NSR03)) return null;
        else                             return new ImplSuffix(this.identifierSuffix);
                                         // FIXME: we shouldn't really default everything else to that, should we?
    }

    public String toString() {
        try {
            Suffix sfx = suffix();

            return String.format("EID:f=%02x,id[0]=0x%02x,id=%s,sfx=[%s]",
                    this.flags & 0x0ff,
                    BinUtils.u8ToInt(this.identifier.at(0)),
                    identifierAsString(),
                    null == sfx ? "0x" + BinUtils.bytesToHexString(this.identifierSuffix) : sfx);
        }
        catch (UDFException ue) {
            return ue.getMessage();
        }
    }

    ///////////////////////////////////////////////////////////////////////////

    public abstract static class Suffix {
        public final static int LENGTH = 8;
        public BytePtr data() {
            byte[] buf = new byte[LENGTH];
            serialize(buf, 0);
            return new BytePtr(buf);
        }
        protected abstract int serialize(byte[] buf, int ofs);
        public abstract String toString();
    }

    public abstract static class RevisionSuffix extends Suffix {
        public final static short UDF_REVISION_102 = 0x0102;
        public short udfRevision;
        protected int parse(BytePtr data) {
            this.udfRevision = BinUtils.readInt16LE(data.buf, data.ofs);
            return data.ofs + 2;
        }
        protected int serialize(byte[] buf, int ofs) {
            BinUtils.writeInt16LE(this.udfRevision, buf, ofs);
            return ofs + 2;
        }
    }

    public static class OSTASuffix extends RevisionSuffix {
        public final static int DOMAINFLAG_HARD_WRITE_PROTECT = 1;
        public final static int DOMAINFLAG_SOFT_WRITE_PROTECT = 2;

        public byte    domainFlags;
        public BytePtr reserved;

        public final static int RESV_LEN = 5;

        public OSTASuffix() {
        }
        protected OSTASuffix(BytePtr data) {
            int ofs = parse(data);

            this.domainFlags = data.buf[ofs++];
            this.reserved    = new BytePtr.Checked(data.buf, ofs, RESV_LEN);
        }
        protected int serialize(byte[] buf, int ofs) {
            ofs = super.serialize(buf, ofs);
            buf[ofs++] = this.domainFlags;
            if (null != this.reserved) {
                System.arraycopy(this.reserved.buf,
                                 this.reserved.ofs,
                                 buf,
                                 ofs,
                                 RESV_LEN);
            }
            return ofs + 1 + RESV_LEN;
        }
        public String toString() {
            return String.format("OSTASFX:rev=0x%04x,df=0x%02x",
                    BinUtils.u16ToInt(this.udfRevision),
                    BinUtils.u8ToInt(this.domainFlags));
        }
    }

    public interface OSInfo {
        public final static byte OSCLASS_UNDEFINED = 0;
        public final static byte OSCLASS_DOS       = 1;
        public final static byte OSCLASS_OS2       = 2;
        public final static byte OSCLASS_MAC       = 3;
        public final static byte OSCLASS_UNIX      = 4;

        public final static byte OSID_DOS             = 0;
        public final static byte OSID_OS2             = 0;
        public final static byte OSID_UNIX_MACOS      = 0;
        public final static byte OSID_UNIX_GENERIC    = 0;
        public final static byte OSID_UNIX_IBMAIX     = 1;
        public final static byte OSID_UNIX_SUNSOLARIS = 2;
        public final static byte OSID_UNIX_HPUX       = 3;
        public final static byte OSID_UNIX_IRIX       = 4;
    }

    public static class UDFSuffix extends RevisionSuffix implements OSInfo {
        public final static int RESV_LEN = 4;

        public byte    osClass;
        public byte    osIdentifier;
        public BytePtr reserved;

        public UDFSuffix() {
        }
        protected UDFSuffix(BytePtr data) {
            int ofs = parse(data);

            this.osClass      = data.buf[ofs++];
            this.osIdentifier = data.buf[ofs++];
            this.reserved     = new BytePtr.Checked(data.buf, ofs, RESV_LEN);
        }
        protected int serialize(byte[] buf, int ofs) {
            ofs = super.serialize(buf, ofs);
            buf[ofs++] = this.osClass;
            buf[ofs++] = this.osIdentifier;
            if (null != this.reserved) {
                System.arraycopy(this.reserved.buf,
                                 this.reserved.ofs,
                                 buf,
                                 ofs,
                                 RESV_LEN);
            }
            return ofs + 2 + RESV_LEN;
        }
        public String toString() {
            return String.format("UDFSFX:rev=0x%04x,osc=%d,osi=%d",
                    BinUtils.u16ToInt(this.udfRevision),
                    BinUtils.u8ToInt(this.osClass),
                    BinUtils.u8ToInt(this.osIdentifier));
        }
    }

    public static class ImplSuffix extends Suffix implements OSInfo {
        public final static int IMPL_USE_AREA_LEN = 6;

        public byte    osClass;
        public byte    osIdentifier;
        public BytePtr implUseArea;

        public ImplSuffix(int osClass, int osIdentifier, BytePtr implUseArea) {
            this.osClass      = (byte)osClass;
            this.osIdentifier = (byte)osIdentifier;
            this.implUseArea  = implUseArea;
        }

        public ImplSuffix(BytePtr data) {
            int ofs = data.ofs;
            this.osClass      = data.buf[ofs++];
            this.osIdentifier = data.buf[ofs++];
            this.implUseArea  = new BytePtr.Checked(data.buf, ofs, IMPL_USE_AREA_LEN);
        }
        protected int serialize(byte[] buf, int ofs) {
            buf[ofs++] = this.osClass;
            buf[ofs++] = this.osIdentifier;
            if (null != this.implUseArea) {
                System.arraycopy(this.implUseArea.buf,
                                 this.implUseArea.ofs,
                                 buf,
                                 ofs,
                                 IMPL_USE_AREA_LEN);
            }
            return ofs + 2 + IMPL_USE_AREA_LEN;
        }
        public String toString() {
            return String.format("IMPLSFX:osc=%d,osi=%d",
                    BinUtils.u8ToInt(this.osClass),
                    BinUtils.u8ToInt(this.osIdentifier));
        }
    }
}
