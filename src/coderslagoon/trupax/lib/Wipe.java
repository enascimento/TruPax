package coderslagoon.trupax.lib;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.util.Random;

import coderslagoon.baselib.io.FileNode;
import coderslagoon.baselib.io.FileRegistrar;
import coderslagoon.baselib.io.LocalFileSystem;
import coderslagoon.baselib.util.Prp;
import coderslagoon.baselib.util.VarLong;

public class Wipe {
    final static String PROPS_PFX = "wipe.";

    public interface Cycles {
        int    count();
        void   set  (int num);
        byte[] data ();

        public static class Zeros implements Cycles {
            Prp.Int pbsz = new Prp.Int(PROPS_PFX + "bufsize", 512 * 1024);
            public int count() {
                return 1;
            }
            public void set(int num) {
                if (null == this.data) {
                    this.data = new byte[this.pbsz.get()];
                }
            }
            public byte[] data() {
                return this.data;
            }
            byte[] data;
        }
    }

    ///////////////////////////////////////////////////////////////////////////

    public interface Progress {
         public enum Reason {
             NOT_LOCALFILESYSTEM,
             VANISHED,
             CANNOT_OPEN,
             IO_ERROR,
             RENAME1_FAILED,
             RENAME2_FAILED,
             DELETE_FAILED
         }
         boolean onNode     (FileNode fn);
         boolean onProcessed(double percent);
         boolean onSkipped  (FileNode fn, Reason reason);
         boolean onError    (FileNode fn, Reason reason);
         boolean onWarning  (FileNode fn, Reason reason);
    }

    ///////////////////////////////////////////////////////////////////////////

    final FileRegistrar freg;
    final Cycles        cycles;
    final boolean       removeDirs;
    final long          totalBytes;
    final SecureRandom  srnd = new SecureRandom();

    ///////////////////////////////////////////////////////////////////////////

    public Wipe(FileRegistrar freg,
                Cycles        cycles,
                boolean       removeDirs) {
        this.freg       = freg;
        this.cycles     = cycles;
        this.removeDirs = removeDirs;

        final VarLong totalBytes = new VarLong();
        FileRegistrar.walk(freg.root(), new FileRegistrar.Walker() {
            final LocalFileSystem lfs = new LocalFileSystem(true);

            public boolean onNodes(FileNode[] fns) {
                for (FileNode fn : fns) {
                    if (fn.fileSystem().scheme().equals(this.lfs.scheme()) &&
                       !fn.hasAttributes(FileNode.ATTR_DIRECTORY)) {
                        totalBytes.v += fn.size();
                    }
                }
                return true;
            }
        },
        true, false);
        this.totalBytes = totalBytes.v;
    }

    ///////////////////////////////////////////////////////////////////////////

    public boolean perform(Progress progress) {
        this.progress = progress;

        return FileRegistrar.walk(this.freg.root(), new FileRegistrar.Walker() {
            final LocalFileSystem lfs = new LocalFileSystem(true);

            public boolean onNodes(FileNode[] fns) {
                for (FileNode fn : fns) {
                    if (!fn.fileSystem().scheme().equals(this.lfs.scheme())) {
                        if (!Wipe.this.progress.onSkipped(fn, Progress.Reason.NOT_LOCALFILESYSTEM)) {
                            return false;
                        }
                        continue;
                    }
                    if (!wipe(fn)) {
                        return false;
                    }
                }
                return true;
            }
        },
        true, false);
    }

    long     bytesSoFar;
    Progress progress;

    ///////////////////////////////////////////////////////////////////////////

    boolean wipe(FileNode fn) {
        if (fn.hasAttributes(FileNode.ATTR_DIRECTORY)) {
            if (!this.removeDirs) {
                return true;
            }
        }
        if (!this.progress.onNode(fn)) {
            return false;
        }
        Boolean result = null;
        if (!fn.hasAttributes(FileNode.ATTR_DIRECTORY)) {
            switch(erase(fn)) {
                case ABORTED: result = false; break;
                case SKIPPED: result = true; break;
                case YES: default: break;
            }
            this.bytesSoFar += fn.size();
        }
        if (null == result) {
            return renameAndDelete(fn);
        }
        return result;
    }

    ///////////////////////////////////////////////////////////////////////////

    public static abstract class __test_CHECK {
        public abstract void onCycleDone(File fl, int cycle);

        public static __test_CHECK _instance;
    }

    static int __test_IOERROR;

    enum Erased {
        YES,
        ABORTED,
        SKIPPED
    }

    Erased erase(FileNode fn) {
        File fl = new File(fn.path(true));
        if (!fl.exists()) {
        	return this.progress.onWarning(fn, Progress.Reason.VANISHED) ?
                   Erased.SKIPPED : Erased.ABORTED;
        }
    	long len = fl.length();
        final int c = this.cycles.count();
        for (int cycle = 0; cycle < c; cycle++) {
            this.cycles.set(cycle);
            OutputStream os = null;
            try {
	            os = Files.newOutputStream(fl.toPath(), 
	                	new OpenOption[] { StandardOpenOption.WRITE,
	                					   StandardOpenOption.DSYNC });
	            final double ofs = this.bytesSoFar +
	                              (cycle * ((double)len / (double)c));
	            for (long written = 0; written < len;) {
	                byte[] d = this.cycles.data();
	                int toWrite = (int)Math.min(d.length, len - written);
	                if (0 < __test_IOERROR) {
	                    __test_IOERROR--;
	                    throw new IOException("__test_IOERROR");
	                }
	                os.write(d, 0, toWrite);
	                os.flush();
	                written += toWrite;
	                double lvl = ofs + ((double)written / (double)c);
	                double pct = (lvl * 100.0) / this.totalBytes;
	                if (!this.progress.onProcessed(pct)) {
	                    return Erased.ABORTED;
	                }
	            }
	            if (null != __test_CHECK._instance) {
	                __test_CHECK._instance.onCycleDone(fl, cycle);
	            }
            }
            catch (IOException ioe) {
                return this.progress.onError(fn,
                    null != os ? Progress.Reason.IO_ERROR :
                                 Progress.Reason.CANNOT_OPEN) ? Erased.SKIPPED :
                                                                Erased.ABORTED;
            }
            finally {
                if (null != os) {
                    try { os.close(); } catch (IOException ignored) { }
                    os = null;
                }
            }
        }
        return Erased.YES;
    }

    ///////////////////////////////////////////////////////////////////////////

    final static int MAX_PATH_PAD_LEN = 198;

    boolean renameAndDelete(FileNode fn) {
        File fl = new File(fn.path(true));
        if (fl.isDirectory()) {
            String[] objs = fl.list();
            if (null == objs || objs.length > 0) {
                // TODO: this is sort of a hack, lacking better ideas for now...
                return true;
            }
        }
        File orig = fl;
        boolean result = true;
        File path = fl.getParentFile();
        int nlen = fl.getName().length();
        File fl2 = new File(path, renameChars(nlen));
        if (fl.renameTo(fl2)) {
            fl = fl2;
            int plen = MAX_PATH_PAD_LEN - path.getAbsolutePath().length();
            plen = Math.max(plen, nlen);
            fl2 = new File(path, renameChars(plen));
            if (fl.renameTo(fl2)) {
                fl = fl2;
            }
            else {
                result &= this.progress.onWarning(fn, Progress.Reason.RENAME2_FAILED);
            }
        }
        else {
            result &= this.progress.onWarning(fn, Progress.Reason.RENAME1_FAILED);
        }
        if (!fl.delete()) {
            fl.renameTo(orig);
            result &= this.progress.onError(fn, Progress.Reason.DELETE_FAILED);
        }

        return result;
    }

    ///////////////////////////////////////////////////////////////////////////

    final static char[] RENAME_CHARSET = ("abcdefghijklmnopqrstuvwxyz" +
                                          "ABCDEFGHIJKLMNOPQRSTUVWXYZ"  +
                                          "0123456789").toCharArray();
    final Random rnd = new Random();

    String renameChars(int len) {
        final char[] result = new char[len];
        final int RCSLEN = RENAME_CHARSET.length;
        for (int i = 0; i < len; i++) {
            result[i] = RENAME_CHARSET[(this.rnd.nextInt() & 0x0fff) % RCSLEN];
        }
        return new String(result);
    }
}
