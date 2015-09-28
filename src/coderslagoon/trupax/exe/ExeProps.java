package coderslagoon.trupax.exe;

import java.util.Properties;

import coderslagoon.baselib.util.MiscUtils;
import coderslagoon.baselib.util.Prp;

public class ExeProps {
    public final static String EXE_PFX = "trupax.exe.";

    public static class FreeSpace extends Prp.Lng {
        public FreeSpace(String pfx) {
            super(pfx + "freespace", 0L);
        }
        public boolean validate(String raw) {
            return 0 <= MiscUtils.strToUSz(raw);
        }
        public Long get(Properties p) {
            return MiscUtils.strToUSz(p.getProperty(this.name, this.dflt.toString()));
        }
    };

    public static class Password extends Prp.Str {
        public Password(String pfx) {
            super(pfx + "password", null);
        }
        public boolean validate(String raw) {
            return 0 < raw.length();
        }
    };

    public final static Prp.Str Lang = new Prp.Str(EXE_PFX  + "lang", null);
}
