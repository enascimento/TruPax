package coderslagoon.trupax.lib.prg;

import coderslagoon.baselib.util.BaseLibException;

public class PrgException extends BaseLibException {
    public PrgException(String fmt, Object... args) {
        super(fmt, args);
    }
    public PrgException(Throwable cause, String fmt, Object... args) {
        super(cause, fmt, args);
    }
    private static final long serialVersionUID = -4050668908929358077L;
}
