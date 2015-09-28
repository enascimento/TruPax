package coderslagoon.trupax.exe;

public abstract class Console {
    public abstract char[]  readPassword(String fmt, Object... args);
    public abstract Console format      (String fmt, Object... args);

    public final static Console system() {
        final java.io.Console con = System.console();

        return null == con ? null : new Console() {
            public char[] readPassword(String fmt, Object... args) {
                return con.readPassword(fmt, args);
            }
            public Console format(String fmt, Object... args) {
                con.format(fmt, args);
                return this;
            }
        };
    }
}
