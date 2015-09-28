package coderslagoon.trupax.lib.io.filesystem.udf;

import java.util.Arrays;

import coderslagoon.baselib.util.BinUtils;
import coderslagoon.baselib.util.BytePtr;

public class DString {
    public static String read(byte[] buf, int ofs, int len) throws UDFException {
        int end = ofs + len - 1;
        int sz = buf[end] & 0xff;

        if (0 == sz) {
            // if zero then all must be zero or else
            for (; ofs < end; ofs++) {
                if (buf[ofs] != 0) {
                    throw new UDFException("zero string problem");
                }
            }
            return "";
        }
        if (sz > len - 1) {
            throw new UDFException("invalid dstring length " + sz);
        }
        for (int i = ofs + sz; i < end; i++) {
            if (0 != buf[i]) {
                throw new UDFException("nonzero found in dstring void");
            }
        }
        return readCompressedUnicode(new BytePtr(buf, ofs, sz));
    }

    final static int COMPRESSION_ID_8BIT    = 8;
    final static int COMPRESSION_ID_UNICODE = 16;

    public static void write(String str, byte[] buf, int ofs, int len) throws UDFException {
        if (str.length() > 127) {
            throw new UDFException("string is too long");
        }
        if (len < str.length() * 2 + 2) {
            try {
                throw new Exception();
            }
            catch (Exception e) {
                e.printStackTrace();
            }
            throw new UDFException("buffer too small (%d) for string '%s'", len, str);
        }
        if (0 == str.length()) {
            Arrays.fill(buf, ofs, ofs + len, (byte)0);
            return;
        }
        int last = ofs + len - 1;
        buf[ofs++] = COMPRESSION_ID_UNICODE;
        for (char c : str.toCharArray()) {
            BinUtils.writeInt16BE((short)c, buf, ofs);
            ofs += 2;
        }
        if (ofs < last) {
            java.util.Arrays.fill(buf, ofs, last, (byte)0);
        }
        buf[last] = (byte)((str.length() << 1) + 1);
    }

    // NOTE: this method should just be used for debugging, it does not honor
    //       any character sets; consider it to be a prototype for now in that
    //       regard, or as a tool to take zero padded character bytes and
    //       convert them into a string (hoping that it is ASCII or UTF-8)
    public static String readChars(BytePtr data) throws UDFException {
        boolean done = false;
        int len = 0;
        for (int i = 0, c = data.len; i < c; i++) {
            if (done) {
                if (0 != data.at(i)) {
                    // inconsistent padding = we might have picked up garbage
                    throw new UDFException("inconsistent padding");
                }
            }
            else if (0 == data.at(i)) {
                done = true;
            }
            else {
                len++;
            }
        }
        return new String(data.buf, data.ofs, len);
    }

    /**
     * Reads compressed Unicode characters, either via method 8 or 16.
     * @param data The raw data. First byte is the compression ID.
     * @return The uncompressed string.
     * @throws UDFException If the data is corrupted or the compression unknown.
     */
    public static String readCompressedUnicode(BytePtr data) throws UDFException {
        if (!data.isValid()) {
            throw new UDFException("invalid compressed unicode element (%s)", data);
        }
        if (0 == data.len) {
            return "";
        }
        int cid = data.at(0) & 0x0ff;
        char[] result;
        byte[] buf = data.buf;
        int i = data.ofs + 1;
        switch(cid) {
            case 8: {
                result = new char[data.len - 1];
                for (int c = data.end(), j = 0; i < c; i++, j++) {
                    result[j] = (char)(0xff & buf[i]);
                }
                break;
            }
            case COMPRESSION_ID_UNICODE: {
                int len = data.len - 1;
                if (0 != (len & 1)) {
                    throw new UDFException("truncated 16bit Unicode string. length is %d", len);
                }
                result = new char[len >> 1];
                for (int c = data.end(), j = 0; i < c; i += 2, j++) {
                    result[j] = (char)BinUtils.readInt16BE(buf, i);
                }
                break;
            }
            case 254:
            case 255: {
                if (UDF.Compliance.is(UDF.Compliance.VISTA)) {
                    return "///deleted///";
                }
            }
            default: {
                throw new UDFException("invalid Unicode compression ID %d", cid);
            }
        }
        return new String(result);
    }

    public static boolean needsUnicode(CharSequence cs) {
        for (int i = 0, c = cs.length(); i < c; i++) {
            if (0 != (0x0ff00 & cs.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    public static int compressedUnicodeSize(CharSequence cs) {
        return 1 + cs.length() * (needsUnicode(cs) ? 2 : 1);
    }

    public static BytePtr writeCompressedUnicode(CharSequence cs) {
        int len = cs.length();

        boolean unicode = needsUnicode(cs);

        byte[] result;

        if (unicode) {
            result = new byte[1 + (len << 1)];

            result[0] = COMPRESSION_ID_UNICODE;

            for (int i = 0, ofs = 1; i < len; i++, ofs += 2) {
                BinUtils.writeInt16BE((short)cs.charAt(i), result, ofs);
            }
        }
        else {
            result = new byte[1 + len];

            result[0] = COMPRESSION_ID_8BIT;

            for (int i = 0; i < len; i++) {
                result[i + 1] = (byte)cs.charAt(i);
            }
        }

        return new BytePtr(result);
    }
}
