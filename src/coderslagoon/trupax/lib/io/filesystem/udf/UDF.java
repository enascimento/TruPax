package coderslagoon.trupax.lib.io.filesystem.udf;

public interface UDF {
    public final static int    VOLUME_SPACE_INIT_SIZE = 32768;
    public final static long   ROOT_FILENTRY_UID = 0L;
    public final static int    MIN_UNIQUE_ID = 16;
    public final static int    MAX_FILENAME_DSTRLEN = 255;
    public final static int    MAX_PATH_LEN = 1023;
    public final static String ENCODING_UTF8 = "UTF-8";

    public enum Compliance {
        STRICT(10),
        VISTA(9);
        Compliance(int level) {
            this.level = level;
        }
        int level;
        public static Compliance _default = Compliance.STRICT;
        public static boolean is(Compliance cmpl) {
            return _default.level <= cmpl.level;
        }
        public static void setTo(Compliance cmpl) {
            _default = cmpl;
        }
    }
}
