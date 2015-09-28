package coderslagoon.trupax.sdk.demos;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Random;

import coderslagoon.trupax.lib.prg.Prg.Result;

/** Common things needed in all demos. */
public abstract class Demo {
    // log output to support operation in multiple threads ...

    protected static synchronized void log(String fmt, Object... args) {
        System.out.print("[" + Thread.currentThread().getName() + "] - " +
                         String.format(fmt, args));
    }

    protected static synchronized void logerr(String fmt, Object... args) {
        System.err.print("[" + Thread.currentThread().getId() + "] - " +
                         String.format(fmt, args));
    }

    //////////////////////////////////////////////////////////////////////////

    protected static void checkResult(String action, Result result) {
        if (result.isFailure()) {
            logerr("%s failed: %s '%s' (%s)\n",
                    action, result.code, result.msg, result.details);
            System.exit(1);
        }
    }

    //////////////////////////////////////////////////////////////////////////

    // we need files and directories since we do not want to count on anything
    // existing in the system ...

    private final Random fnameRnd = new Random();

    protected File createPath(File baseDir, String ext) {
        baseDir = null == baseDir ?
            new File(System.getProperty("java.io.tmpdir")) : baseDir;
        synchronized(this.fnameRnd) {
            return new File(baseDir, String.format("%08x%s%s",
                this.fnameRnd.nextLong(),
                null == ext ? "" : ".",
                null == ext ? "" : ext));
        }
    }

    protected File createDirectory(File baseDir) {
        File result = createPath(baseDir, null);
        return result.mkdirs() ? result : null;
    }

    protected File createFile(File intoDir, String ext, long size) throws IOException {
        File result = createPath(intoDir, ext);
        OutputStream os = new BufferedOutputStream(new FileOutputStream(result));
        for (long i = 0; i < size; i++) {
            os.write(255 & (int)i);
        }
        os.close();
        return result;
    }

    //////////////////////////////////////////////////////////////////////////

    protected abstract void exec() throws Exception;
}
