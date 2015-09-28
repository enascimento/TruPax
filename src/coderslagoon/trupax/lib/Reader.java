package coderslagoon.trupax.lib;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.util.Properties;

import coderslagoon.baselib.io.BlockDevice;
import coderslagoon.baselib.io.RandomAccessFileStream;
import coderslagoon.baselib.util.Prp;
import coderslagoon.trupax.lib.Reader.Exception.Code;

public abstract class Reader {
    protected static class Props {
        public final static String PFX = "trupax.lib.reader.";
        public final static Prp.Bool SETTSTAMPERRS = new Prp.Bool(PFX + "settstamperrs", true);
    }

    public interface Progress {
        public enum Result {
            OK,
            ABORT,
            SKIP
        }

        Result onMounting (int numOfObjects); // provisional
        Result onMount    (int numOfFiles, int numOfDirs);
        Result onDirectory(File dir, long size, Long tstamp);
        Result onFile     (File fl , long size, Long tstamp);
        Result onData     (long written);

        public final static long SIZE_UNKNOWN = -1L; // for directories only
    }

    public class MountException extends IOException {
        private static final long serialVersionUID = -8712923061902714249L;
        public MountException()               { super();}
        public MountException(String message) { super(message); }
    }

    public interface Progress2 extends Progress {
        Result onDone(long total);

        public static Progress2 NULL = new Progress2() {
            public Result onMounting (int numOfObjects                 ) { return Result.OK; }
            public Result onMount    (int numOfFiles, int numOfDirs    ) { return Result.OK; }
            public Result onDirectory(File dir , long size, Long tstamp) { return Result.OK; }
            public Result onFile     (File file, long size, Long tstamp) { return Result.OK; }
            public Result onData     (long written                     ) { return Result.OK; }
            public Result onDone     (long total                       ) { return Result.OK; }
        };

        public static Progress2 TRACE = new Progress2() {
            public Result onMounting (int numOfObjects                 ) { System.out.printf("onMounting(%d)\n"         , numOfObjects         ); return Result.OK; }
            public Result onMount    (int numOfFiles, int numOfDirs    ) { System.out.printf("onMount(%d, %d)\n"        , numOfFiles, numOfDirs); return Result.OK; }
            public Result onDirectory(File dir , long size, Long tstamp) { System.out.printf("onDirectory(%s, %d, %s)\n", dir, size, tstamp    ); return Result.OK; }
            public Result onFile     (File file, long size, Long tstamp) { System.out.printf("onFile(%s, %d, %s)\n"     , file, size, tstamp   ); return Result.OK; }
            public Result onData     (long written                     ) { System.out.printf("onData(%d)\n"             , written              ); return Result.OK; }
            public Result onDone     (long total                       ) { System.out.printf("onDone(%d)\n"             , total                ); return Result.OK; }
        };
    }

    protected BlockDevice bdev;
    protected Properties  props;

    public Reader(BlockDevice bdev, Properties props) {
        this.bdev  = bdev;
        this.props = props;
    }

    public abstract void extract(File toDir, Progress progress) throws IOException;

    public static class Exception extends IOException {
        private static final long serialVersionUID = -6227857991418114812L;
        public enum Code {
            ABORTED,
            ERR_MKDIR,
            ERR_OPEN,
            ERR_IO,
            ERR_DEV
        }
        public Exception(Code code, File obj, String fmt, Object... args) {
            this.obj     = obj;
            this.code    = code;
            this.details = String.format(fmt, args);
        }
        public final File   obj;
        public final Code   code;
        public final String details;
    }

    final protected static void throwDev(String fmt, Object... args) throws Exception {
        throw new Exception(Code.ERR_DEV, null, fmt, args);
    }

    final protected static void throwAbort() throws Exception {
        throw new Exception(Code.ABORTED, null, "aborted");
    }

    ///////////////////////////////////////////////////////////////////////////

    public static class Multiple extends Reader {
        final Reader[] readers;

        public Multiple(Reader[] readers) {
            super(null, null);
            this.readers = readers;
        }

        public void extract(File toDir, Progress progress) throws IOException {
            MountException me = null;
            for (Reader rdr : this.readers) {
                try {
                    rdr.extract(toDir, progress);
                    return;
                }
                catch (MountException me2) {
                    me = me2;
                    continue;
                }
            }
            throw me;
        }
    }

    ///////////////////////////////////////////////////////////////////////////

    protected abstract class LocalFile {
        protected abstract void writeData(OutputStream os, long size) throws IOException;

        public void write(File fl, long size, long tstamp, Progress progress) throws IOException {
            RandomAccessFile raf = null;
            IOException ioerr = null;
            OutputStream os = null;
            try {
                raf = new RandomAccessFile(fl, "rw");
                raf.setLength(size);
                raf.seek(0L);
                os = RandomAccessFileStream.newOut(raf);
                writeData(os, size);
            }
            catch (IOException ioe) {
                ioerr = ioe instanceof Exception ? ioe :
                    new Exception(null == os ? Code.ERR_OPEN : Code.ERR_IO,
                                  fl, "file write error (%s)", ioe.getMessage());
                throw ioerr;
            }
            finally {
                if (null != raf) {
                    try {
                        raf.close();
                    }
                    catch (IOException ignored) {
                    }
                }
                if (null != ioerr && fl.exists()) {
                    fl.delete();
                }
            }
            if (!Reader.this.setTimestamp(fl, tstamp)) {
                throw new Exception(Code.ERR_IO, fl, "cannot restore timestamp");
            }
            if (progress instanceof Reader.Progress2) {
                switch(((Reader.Progress2)progress).onDone(size)) {
                    case ABORT: throwAbort();
                    default   : break;
                }
            }
        }
    }

    protected abstract class LocalDir {
        public abstract void writeEntries() throws IOException;

        public void write(File dir, Long timeStamp) throws IOException {
            boolean setTimeStamp = null != timeStamp && dir.mkdir();
            if (!setTimeStamp) {
                if (!dir.exists()) {
                    throw new Exception(Code.ERR_MKDIR, dir, "cannot create directory");
                }
            }
            writeEntries();
            if (setTimeStamp) {
                if (!Reader.this.setTimestamp(dir, timeStamp)) {
                    throw new Exception(Code.ERR_MKDIR, dir, "cannot set directory timestamp");
                }
            }
        }
    }

    boolean setTimestamp(File obj, long tstamp) {
        for (Integer sleep : new Integer[] { null, // shared access violation workaround (Windows)
                1, 5, 10, 50, 100, 200, 500, 1000, 2000 }) {
            if (null != sleep) {
                try {
                    Thread.sleep(sleep);
                }
                catch (InterruptedException ire) {
                    return false;
                }
            }
            if (obj.setLastModified(tstamp)) {
                return true;
            }
        }
        return Props.SETTSTAMPERRS.get(this.props);
    }
}
