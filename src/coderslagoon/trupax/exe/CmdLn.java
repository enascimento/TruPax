package coderslagoon.trupax.exe;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Properties;

import coderslagoon.baselib.io.IOUtils;
import coderslagoon.baselib.io.NulOutputStream;
import coderslagoon.baselib.util.BaseLibException;
import coderslagoon.baselib.util.Clock;
import coderslagoon.baselib.util.CmdLnParser;
import coderslagoon.baselib.util.MiscUtils;
import coderslagoon.baselib.util.Prp;
import coderslagoon.baselib.util.Shutdown;
import coderslagoon.baselib.util.VarLong;
import coderslagoon.baselib.util.VarRef;
import coderslagoon.trupax.lib.prg.Prg;
import coderslagoon.trupax.lib.prg.PrgImpl;
import coderslagoon.trupax.lib.prg.Prg.Concern;
import coderslagoon.trupax.lib.prg.Prg.Result;

public class CmdLn extends Exe {
    Prg        prg;
    Properties props = Prp.global(); // same for app+lib space
    long       freeSpace;

    PrintStream err = System.err;
    PrintStream out;
    LineNumberReader in = new
    LineNumberReader(new InputStreamReader(System.in));

    ///////////////////////////////////////////////////////////////////////////

    public static Console __TEST_console;
    public static String __TEST_password;

    char[] password(boolean confirm) throws ExitError {
        if (null != __TEST_password) {
            return __TEST_password.toCharArray();
        }

        String clpassw = CmdLnProps.OPTS_PASSWORD.get(this.props);
        if (null != clpassw) {
            return clpassw.toCharArray();
        }

        Console con = null == __TEST_console ? Console.system() :
                              __TEST_console;
        if (null == con) {
            throw new ExitError(new Prg.Result(Prg.Result.Code.PRG_ERROR,
                                NLS.CMDLN_NO_CONSOLE.s(), null));
        }
        for (;;) {
            char[] passw = con.readPassword(NLS.CMDLN_PASSWORD_PROMPT.s());
            if (null == passw || Shutdown.active()) {
                throw new ExitError(Prg.Result.aborted());
            }
            if (0 == passw.length) {
                con.format(NLS.CMDLN_PASSWORD_EMPTY.s());
                continue;
            }
            if (!confirm) {
                return passw;
            }
            char[] passw2 = con.readPassword(NLS.CMDLN_PASSWORD_REPEAT.s());
            if (null == passw2 || Shutdown.active()) {
                throw new ExitError(Prg.Result.aborted());
            }
            final boolean matches = Arrays.equals(passw, passw2);
            Arrays.fill(passw2, ' ');
            if (matches) {
                return passw;
            }
            Arrays.fill(passw, ' ');
            con.format(NLS.CMDLN_PASSWORD_MISMATCH.s());
        }
    }

    void showUsage() {
        try {
            System.out.println(new String(IOUtils.readStreamBytes(
                getClass().getResourceAsStream("resources/" + NLS.CMDLN_USAGE.s()))));

            System.out.printf(
                NLS.CMDLN_ABOUT_2.s(),
                Prg.version(),
                MiscUtils.copyrightYear(COPYRIGHT_START_YEAR, Calendar.getInstance()));
        }
        catch (IOException ignored) {
        }
    }

    final static int CONFIRM_ABORTED = -1;

    Integer confirm(String txt, String selstr) {
        this.out.println(txt);
        final String[] sel = selstr.split(",");
        for (int i = 0; i < sel.length; i++) {
            sel[i] = sel[i].trim();
        }
        final StringBuilder keys = new StringBuilder(sel.length);
        final StringBuilder prompt = new StringBuilder();
        for (int i = 0; i < sel.length; i++) {
            String s = sel[i];
            int j = 0;
            String chr = null;
            for (; j < s.length(); j++) {
                char c = s.charAt(j);
                if (127 < c || !Character.isLetter(c)) {
                    continue;
                }
                chr = String.valueOf(c).toLowerCase();
                if (-1 == keys.indexOf(chr)) {
                    break;
                }
            }
            if (j == s.length()) {
                return null;
            }
            keys.append(chr);
            prompt.append(String.format("%s[%s]%s%s ",
                    s.substring(0, j),
                    s.charAt(j),
                    s.substring(j + 1),
                    i < sel.length - 1 ? " " : ""));
        }
        prompt.append('>');
        for (int i = 0; i < sel.length; i++) {
            sel[i] = sel[i].toLowerCase();
        }
        for(;;) {
            this.out.print(prompt);
            String cmd;
            try {
                if (null == (cmd = this.in.readLine())) {
                    throw new IOException();
                }
            }
            catch (IOException unexpected) {
                return Shutdown.active() ? CONFIRM_ABORTED : null;
            }
            cmd = cmd.toLowerCase();
            if (1 == cmd.length()) {
                int i = keys.indexOf(String.valueOf(cmd.charAt(0)));
                if (-1 != i) {
                    return i;
                }
            }
            else {
                for (int i = 0; i < sel.length; i++) {
                    if (sel[i].equals(cmd)) {
                        return i;
                    }
                }
            }
        }
    }

    ///////////////////////////////////////////////////////////////////////////

    public static VarRef<ExitError> __TEST_exit_error;

    public void exec(String[] args) {
        try {
            execInternal(args);
        }
        catch (ExitError ee) {
            this.err.printf(NLS.CMDLN_ERROREXIT_4.s(),
                            ee.result.code.value,
                            ee.result.code,
                            ee.result.msg,
                            null ==                   ee.result.details ?
                            NLS.CMDLN_NODETAILS.s() : ee.result.details);

            switch(ee.result.code) {
                case MISSING_CMDLN_ARG:
                case INVALID_CMDLN_ARG: {
                    showUsage();
                }
                default: {
                    break;
                }
            }

            if (null == __TEST_exit_error) {
                exit(ee.result.code.value);
            }
            else {
                __TEST_exit_error.v = ee;
            }
        }
    }

    static Prg.Setup.InitOp initOpFromProps(Properties props) throws ExitError {
        Prg.Setup.InitOp iop = Prg.Setup.InitOp.DEFAULT;
        int c = 0;
        if (CmdLnProps.OPTS_WIPEONLY  .get(props)) { iop = Prg.Setup.InitOp.WIPE      ; c++; }
        if (CmdLnProps.OPTS_EXTRACT   .get(props)) { iop = Prg.Setup.InitOp.EXTRACT   ; c++; }
        if (CmdLnProps.OPTS_INVALIDATE.get(props)) { iop = Prg.Setup.InitOp.INVALIDATE; c++; }
        if (1 < c) {
            throw new ExitError(new Prg.Result(
                Prg.Result.Code.INVALID_CMDLN_ARG, NLS.CMDLN_VAGUE_OP.s(), null));
        }
        return iop;
    }

    static Long   __TEST_abort_make;
    static Double __TEST_abort_wipe;

    void execInternal(String[] args) throws ExitError {
        Prg.Setup  setup;
        Prg.Result res;
        Prg        prg = null;
        try {
            Prp.global().clear();

            NLS.Reg.instance().load(null);
            File propFile = MiscUtils.determinePropFile(getClass(), Exe._propFileName, false);

            setup = new Prg.Setup();

            setup.args            = processArgs(args);
            setup.fromCommandLine = true;
            setup.initOp          = initOpFromProps(this.clp.options());
            setup.saveProperties  = false;
            setup.propertiesFile  = propFile.exists() ? propFile.getAbsolutePath() : null;
            setup.propFileExists  = null != setup.propertiesFile;

            final VarRef<BaseLibException> ble = new VarRef<>();
            setup.cb = new Prg.Setup.Callback() {
                public void onProps() {
                    String langid = ExeProps.Lang.get();
                    if (null != langid) {
                        try {
                            NLS.Reg.instance().load(langid);
                        }
                        catch (BaseLibException ble2) {
                            ble.v = ble2;
                        }
                    }
                }
                @Override
                public void onLoadErrorReset(Result err) {
                }
            };

            res = PrgImpl.init();
            if (res.isSuccess()) {
                prg = new PrgImpl();
                res = prg.ctor(this.props, setup);
                this.props.putAll(this.clp.options());
                if (res.isSuccess()) {
                    if (null != ble.v) {
                        throw ble.v;
                    }
                }
            }
        }
        catch (BaseLibException ble) {
            throw new ExitError(new Prg.Result(
                    Prg.Result.Code.INTERNAL_ERROR,
                    ble.getLocalizedMessage(), null));
        }

        if (res.isFailure()) {
            throw new ExitError(res);
        }
        this.prg = prg;
        // better than no extra seed, no?
        this.prg.addRandomSeed(Runtime.getRuntime().freeMemory());
        this.prg.addRandomSeed(System.currentTimeMillis());
        this.prg.addRandomSeed(System.nanoTime());

        this.out = CmdLnProps.OPTS_VERBOSE.get(this.props) ?
                System.out :
                new PrintStream(new NulOutputStream());

        VarLong tm = new VarLong();
        switch(setup.initOp) {
            case EXTRACT   : extract   (tm); break;
            case INVALIDATE: invalidate(tm); break;
            case WIPE      : wipe      (tm); break;
            case DEFAULT:
            default        : create(tm); break;
        }
        CmdLn.this.out.printf(NLS.CMDLN_DONE_1.s(),
                MiscUtils.printTime(Clock._system.now() - tm.v));

        if (this.prg.dtor().isSuccess()) {
            PrgImpl.cleanup();
        }
    }

    ///////////////////////////////////////////////////////////////////////////

    void create(VarLong tm) throws ExitError {
        stepRegister();

        long freeSpace = CmdLnProps.OPTS_FREESPACE.get(this.props);
        Prg.Result res = this.prg.setFreeSpace(freeSpace);
        if (res.isFailure()) {
            throw new ExitError(res);
        }
        this.out.printf(NLS.CMDLN_FREESPACE_SET_1.s(), freeSpace);

        this.out.print(NLS.CMDLN_RESOLVING.s());
        res = this.prg.resolve();
        if (res.isFailure()) {
            throw new ExitError(res);
        }
        final long volSz = this.prg.volumeBytes();
        this.out.printf(NLS.CMDLN_VOL_SZ_1.s(), volSz);

        char[] passw = password(true);

        tm.v = Clock._system.now();

        res = this.prg.make(passw, new Prg.MakeCallback() {
            long lastPos;
            public void onFile(String fileName, long fileSize) {
                double pct = (this.lastPos * 100.0) / volSz;
                CmdLn.this.out.printf(
                        NLS.CMDLN_PROGRESS_3.s(),
                        pct, fileName, fileSize);
            }
            public Result onVolumeWrite(long pos) {
                this.lastPos = pos;
                return (Shutdown.active() || (__TEST_abort_make != null &&
                                              __TEST_abort_make <= pos)) ?
                        Prg.Result.aborted() :
                        Prg.Result.ok();
            }
            @Override
            public Result onFreeSpace() {
                CmdLn.this.out.println(NLS.CMDLN_PROGRESS_FREESPACE.s());
                return Shutdown.active() ?
                        Prg.Result.aborted() :
                        Prg.Result.ok();
            }
        });
        if (res.isFailure()) {
            throw new ExitError(res);
        }

        if (CmdLnProps.OPTS_WIPE.get(this.props)) {
            CmdLn.this.out.printf(NLS.CMDLN_VOLUME_CREATED_1.s(),
                                  MiscUtils.printTime(Clock._system.now() - tm.v));
            res = stepWipe();

            if (res.isFailure()) {
                throw new ExitError(res);
            }
        }
    }

    private void stepRegister() throws ExitError {

        Prg.Result res = this.prg.registerObjects(new Prg.RegisterObjectsCallback() {
            public Result onDirectory(String dir) {
                CmdLn.this.out.printf(NLS.CMDLN_SEARCHING_1.s(), dir);

                return Shutdown.active() ? Prg.Result.aborted() :
                                           Prg.Result.ok();
            }
            public void configLocked() {
            }
        });

        if (res.isFailure()) {
            throw new ExitError(res);
        }
        Prg.RegSum regSum = this.prg.registerSummary();
        this.out.printf(NLS.CMDLN_REGSUM_3.s(),
                regSum.numberOfDirectories,
                regSum.numberOfFiles,
                regSum.bytesTotal);
    }

    private Prg.Result stepWipe() {
        return this.prg.wipe(new Prg.WipeCallback() {
            double lastPct;
            public void onFile(String fileName, long fileSize) {
                CmdLn.this.out.printf(
                        NLS.CMDLN_PROGRESS_WIPE_3.s(),
                        this.lastPct, fileName, fileSize);
            }
            public Result onProgress(double percent) {
                this.lastPct = percent;
                return abortCheck();
            }
            public Result onConcern(Concern concern, String message) {
                @SuppressWarnings("resource")
                PrintStream ps = concern == Concern.ERROR ? CmdLn.this.err : CmdLn.this.out;
                ps.printf(NLS.CMDLN_CONCERN_2.s(),
                          concern.localized().toUpperCase(), message);
                return abortCheck();
            }
            Result abortCheck() {
                return (Shutdown.active() ||
                        (__TEST_abort_wipe != null &&
                         __TEST_abort_wipe <= this.lastPct)) ?
                                 Prg.Result.aborted() :
                                 Prg.Result.ok();
            }
        });
    }

    ///////////////////////////////////////////////////////////////////////////

    void extract(VarLong tm) throws ExitError {
        char[] passw = password(false);
        tm.v = Clock._system.now();
        Result res = this.prg.extract(passw, null, new Prg.ExtractCallback() {
            boolean overwriteAll;
            boolean skipAll;
            public Result onConcern(Concern concern, String message) {
                if (Shutdown.active()) {
                    return Prg.Result.aborted();
                }
                if (concern != Concern.EXISTS) {
                    throw new Error(concern.toString());
                }
                if (this.overwriteAll) {
                    return Result.ok();
                }
                if (this.skipAll) {
                    return new Result(Result.Code.IGNORE, null, null);
                }
                Integer cnf = confirm(message, NLS.CMDLN_EXISTS_SELECT.s());
                if (null == cnf) {
                    return Result.internalError(null);
                }
                switch(cnf) {
                    case 1: this.overwriteAll = true;
                    case 0: return Result.ok();
                    case 3: this.skipAll = true;
                    case 2: return new Result(Result.Code.IGNORE, null, null);
                }
                return Result.aborted();
            }
            public void onFile(String fileName, long fileSize) {
                CmdLn.this.out.printf(
                        NLS.CMDLN_PROGRESS_EXTRACT_4.s(),
                        ++this.fileNum,
                        this.filesTotal,
                        fileName,
                        fileSize);
            }
            public Result onOpen(int files, int dirs) {
                if (Shutdown.active()) {
                    return Prg.Result.aborted();
                }
                this.filesTotal = files;
                this.fileNum = 0;
                return Result.ok();
            }
            public Result onOpening(int objs) {
                return simpleCheck();
            }
            public Result onFileWrite(long pos) {
                return simpleCheck();
            }
            private Result simpleCheck() {
                if (Shutdown.active()) {
                    return Prg.Result.aborted();
                }
                return Prg.Result.ok();
            }
            int filesTotal;
            int fileNum;
        });

        if (res.isFailure()) {
            throw new ExitError(res);
        }
    }

    ///////////////////////////////////////////////////////////////////////////

    void invalidate(VarLong tm) throws ExitError {
        CmdLn.this.out.printf(NLS.CMDLN_INVALIDATING.s());
        tm.v = Clock._system.now();

        Result res = this.prg.invalidate(new Prg.ProgressCallback() {
            public Result onProgress(double percent) {
                return Result.ok();
            }
        });

        if (res.isFailure()) {
            throw new ExitError(res);
        }
    }

    ///////////////////////////////////////////////////////////////////////////

    void wipe(VarLong tm) throws ExitError {
        tm.v = Clock._system.now();

        stepRegister();

        Result res = stepWipe();

        if (res.isFailure()) {
            throw new ExitError(res);
        }
    }

    ///////////////////////////////////////////////////////////////////////////

    @Override
    protected void addCmdLnOptions() {
        this.clp.addProp(CmdLnParser.OPT_PFX   + "v"         , CmdLnProps.OPTS_VERBOSE   );
        this.clp.addProp(CmdLnParser.OPT_PFX_L + "free-space", CmdLnProps.OPTS_FREESPACE );
        this.clp.addProp(CmdLnParser.OPT_PFX_L + "wipe"      , CmdLnProps.OPTS_WIPE      );
        this.clp.addProp(CmdLnParser.OPT_PFX_L + "wipe-only" , CmdLnProps.OPTS_WIPEONLY  );
        this.clp.addProp(CmdLnParser.OPT_PFX_L + "extract"   , CmdLnProps.OPTS_EXTRACT   );
        this.clp.addProp(CmdLnParser.OPT_PFX_L + "invalidate", CmdLnProps.OPTS_INVALIDATE);
        this.clp.addProp(CmdLnParser.OPT_PFX_L + "password"  , CmdLnProps.OPTS_PASSWORD  );
    }

    ///////////////////////////////////////////////////////////////////////////

    public static void exit(int code) {
        Shutdown.release();
        System.exit(code);
    }

    ///////////////////////////////////////////////////////////////////////////

    final static String PROP_FILE_NAME = "trupaxcmd";

    public static void main(String[] args) {
        _propFileName = PROP_FILE_NAME;
        Shutdown.install("shutdown");
        try {
            CmdLn cl = new CmdLn();
            cl.exec(args);
        }
        catch (Throwable uncaught) {
            MiscUtils.dumpUncaughtError(uncaught, DEF_PROP_FILE_NAME);
        }
        finally {
            Shutdown.release();
        }
    }
}
