package coderslagoon.trupax.exe;

import coderslagoon.baselib.util.CmdLnParser;
import coderslagoon.baselib.util.MiscUtils;
import coderslagoon.trupax.lib.prg.Prg;

public abstract class Exe {
    final static String DEF_PROP_FILE_NAME = "trupax";
    public static String _propFileName = DEF_PROP_FILE_NAME;

    public final static int COPYRIGHT_START_YEAR = 2010;

    public final static String PRODUCT_NAME = "TruPax";
    public final static String PRODUCT_SITE = "https://www.coderslagoon.com/home.php";
    
    protected final static String[][] LANGS = new String[][] {
        { "de"                                         , "Deutsch" },
        { coderslagoon.baselib.util.NLS.DEFAULT_LANG_ID, "English" }
    };
    
    public final static String EXT_TC = ".tc";
    public final static String EXT_HC = ".hc";

    ///////////////////////////////////////////////////////////////////////////

    static class ExitError extends Exception {
        private static final long serialVersionUID = 5054107294731808046L;
        public ExitError(Prg.Result res) {
            super();
            this.result = res;
        }
        public final Prg.Result result;
    }

    ///////////////////////////////////////////////////////////////////////////

    protected CmdLnParser clp;

    protected abstract void addCmdLnOptions();

    protected String[] processArgs(String[] args) throws ExitError {
        if (null != MiscUtils.__TEST_uncaught_now) {
            throw new Error("uncaught_test");
        }
        this.clp = new CmdLnParser();
        addCmdLnOptions();
        try {
            return this.clp.parse(args, true, false);
        }
        catch (CmdLnParser.Error clpe) {
            throw new ExitError(new Prg.Result(Prg.Result.Code.INVALID_CMDLN_ARG,
                                clpe.getMessage(), null));
        }
    }
}
