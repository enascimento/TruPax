package coderslagoon.trupax.lib.io.filesystem.udf;

import java.io.IOException;

public class UDFException extends IOException {
    public UDFException()                      { super(); }
    public UDFException(String s, Throwable t) { super(s, t); }
    public UDFException(String s)              { super(s); }
    public UDFException(Throwable t)           { super(t); }
    public UDFException(String fmt, Object... args) {
        super(String.format(fmt, args));
    }

    private static final long serialVersionUID = -9210861877102421673L;
}
