package coderslagoon.trupax.lib.io.filesystem.udf;

import java.util.Arrays;

import coderslagoon.baselib.util.BinUtils;
import coderslagoon.baselib.util.BytePtr;

public class FileIdentifierDescriptor extends Descriptor implements UDF {
    public final static int FCBIT_EXISTENCE = 1;
    public final static int FCBIT_DIRECTORY = 2;
    public final static int FCBIT_DELETED   = 4;
    public final static int FCBIT_PARENT    = 8;
    public final static int FCBIT_METADATA  = 16;

    public short                     fileVersionNumber;
    public byte                      fileCharacteristics;
    public byte                      lengthOfFileIdentifier;
    public AllocationDescriptor.Long icb;
    public short                     lengthOfImplementationUse;
    public BytePtr                   implementationUse;
    public BytePtr                   fileIdentifier;
    public String                    fileIdentifierStr;
    public BytePtr                   paddingBytes;

    public FileIdentifierDescriptor(int location) {
        super(new Tag(Tag.Identifier.FILE_IDENTIFIER_DESCRIPTOR, location));
    }

    protected FileIdentifierDescriptor(Tag tag, byte[] buf, int ofs) throws UDFException {
        super(tag);

        this.fileVersionNumber         = BinUtils.readInt16LE(buf, ofs);            ofs += 2;
        this.fileCharacteristics       = buf[ofs];                                  ofs++;
        this.lengthOfFileIdentifier    = buf[ofs];                                  ofs++;

        int lfi = BinUtils.u8ToInt(this.lengthOfFileIdentifier);

        this.icb                       = AllocationDescriptor.Long.parse(buf, ofs); ofs += AllocationDescriptor.Long.LENGTH;
        this.lengthOfImplementationUse = BinUtils.readInt16LE(buf, ofs);            ofs += 2;

        int liu = BinUtils.u16ToInt(this.lengthOfImplementationUse);

        this.implementationUse         = new BytePtr.Checked(buf, ofs, liu);        ofs += liu;
        this.fileIdentifier            = new BytePtr.Checked(buf, ofs, lfi);        ofs += lfi;
        this.fileIdentifierStr         = DString.readCompressedUnicode(this.fileIdentifier);

        int pad = pad(lfi + liu);

        this.paddingBytes              = new BytePtr.Checked(buf, ofs, pad);
    }

    public final static int pad(int sz) {
        sz += 38;
        return ((sz + 3 >> 2) << 2) - sz;
    }

    public int write(byte[] buf, int ofs) throws UDFException {
        int ofs0 = ofs;
        ofs += Tag.LENGTH;

        int padLen = pad(this.fileIdentifier.len +
                         this.implementationUse.len);

        if (0 != (~255 & this.fileIdentifier.len)) {
            throw new UDFException("filename cannot be stored (size is %d bytes)",
                                   this.fileIdentifier.len);
        }

        BinUtils.writeInt16LE(this.fileVersionNumber        , buf, ofs); ofs += 2;
                   buf[ofs] = this.fileCharacteristics                 ; ofs++;
                   buf[ofs] = this.lengthOfFileIdentifier              ; ofs++;
                              this.icb              .write(   buf, ofs); ofs += AllocationDescriptor.Long.LENGTH;
        BinUtils.writeInt16LE(this.lengthOfImplementationUse, buf, ofs); ofs += 2;
                              this.implementationUse.write(   buf, ofs); ofs += this.implementationUse.len;
                              this.fileIdentifier   .write(   buf, ofs); ofs += this.fileIdentifier.len;

        Arrays.fill(buf, ofs, ofs + padLen, (byte)0); ofs += padLen;

        this.tag.write(buf, ofs0, ofs - ofs0);

        return ofs;
    }

    public String toString() {
        return String.format(
             "FID:tag=[%s],fvn=0x%04x,fc=%s,lfi=%s,icb=[%s],liu=%s,fid=%s",
             this.tag,
             this.fileVersionNumber,
             fileCharacteristicsToString(),
             this.lengthOfFileIdentifier,
             this.icb,
             this.lengthOfImplementationUse,
             this.fileIdentifierStr);
    }

    public int length() {
        return 38 + this.implementationUse.len
                  + this.fileIdentifier.len
                  + this.paddingBytes.len;
    }

    String fileCharacteristicsToString() {
        char[] result = new char[5];

        int fc = this.fileCharacteristics;

        result[0] = BinUtils.flags(fc, FCBIT_EXISTENCE) ? 'e' : '.';
        result[1] = BinUtils.flags(fc, FCBIT_DIRECTORY) ? 'd' : '.';
        result[2] = BinUtils.flags(fc, FCBIT_DELETED  ) ? 'x' : '.';
        result[3] = BinUtils.flags(fc, FCBIT_PARENT   ) ? 'p' : '.';
        result[4] = BinUtils.flags(fc, FCBIT_METADATA ) ? 'm' : '.';

        return new String(result);
    }

    public void setFileIdentifier(String fid) throws UDFException {
        BytePtr tmp = null == fid ?
                      new BytePtr(new byte[0]) :
                      DString.writeCompressedUnicode(fid);

        checkLength(tmp.len, fid);

        this.fileIdentifier         = tmp;
        this.lengthOfFileIdentifier = (byte)this.fileIdentifier.len;
    }

    public static int size(String fileIdentifier, int implUseLen) throws UDFException {
        int fidLen = DString.compressedUnicodeSize(fileIdentifier);

        checkLength(fidLen, fileIdentifier);

        return Tag.LENGTH +
               2 +
               1 +
               1 +
               AllocationDescriptor.Long.LENGTH +
               2 +
               implUseLen +
               fidLen +
               pad(implUseLen + fidLen);
    }

    static void checkLength(int len, String name) throws UDFException {
        if (len > MAX_FILENAME_DSTRLEN) {
            throw new UDFException(
                    "file name %s is too long (%d characters)",
                    name,
                    name.length());
        }
    }
}
