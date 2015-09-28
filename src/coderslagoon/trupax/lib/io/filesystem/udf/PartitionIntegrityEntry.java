package coderslagoon.trupax.lib.io.filesystem.udf;

import java.util.HashMap;

import coderslagoon.baselib.util.BytePtr;

public class PartitionIntegrityEntry extends Descriptor {
    enum IntegrityType {
        OPEN  (0),
        CLOSE (1),
        STABLE(2);

        IntegrityType(int code) {
            this.code = (byte)code;
        }
        public final byte code;

        static HashMap<Byte, IntegrityType> _codeMap = new HashMap<>();
        static {
            for (IntegrityType it : values()) {
                _codeMap.put(it.code, it);
            }
        }
        public static IntegrityType fromCode(byte code) {
            return _codeMap.get(code);
        }
    }

    public ICBTag           icbTag;
    public Timestamp        recordingDateAndTime;
    public IntegrityType    integrityType;
    public BytePtr          reserved;
    public EntityIdentifier implementationIdentifier;
    public BytePtr          implementationUse;

    public final static int RESV_LEN     = 175;
    public final static int IMPL_USE_LEN = 256;

    public PartitionIntegrityEntry(Tag tag, byte[] buf, int ofs) {
        super(tag);

        this.icbTag                   = ICBTag.parse(buf, ofs);                  ofs += ICBTag.LENGTH;
        this.recordingDateAndTime     = Timestamp.parse(buf, ofs);               ofs += Timestamp.LENGTH;
        this.integrityType            = IntegrityType.fromCode(buf[ofs]);        ofs++;
        this.reserved                 = new BytePtr.Checked(buf, ofs, RESV_LEN); ofs += RESV_LEN;
        this.implementationIdentifier = EntityIdentifier.parse(buf, ofs);        ofs += EntityIdentifier.LENGTH;
        this.implementationUse        = new BytePtr.Checked(buf, ofs, IMPL_USE_LEN);
    }

    public String toString() {
        return String.format("PIE:tag=[%s],it=[%s],rdt=[%s],int=%s,ii=[%s]",
                this.tag,
                this.icbTag,
                this.recordingDateAndTime,
                this.integrityType,
                this.implementationIdentifier);
    }
}
