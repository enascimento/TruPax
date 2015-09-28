package coderslagoon.trupax.lib;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Properties;

import coderslagoon.baselib.io.BlockDevice;
import coderslagoon.baselib.io.FileNode;
import coderslagoon.baselib.io.FileRegistrar;
import coderslagoon.baselib.io.FileRegistrar.Directory;

/**
 * Definition of a writer which takes files and creates a volume out of it,
 * based on block storage.
 */
public abstract class Writer {
    public final static int ERROR_UNKNOWN = -1;
    public final static int ERROR_NOERROR = 0;
    public final static int ERROR_TOO_MUCH_DATA = 1;
    public final static int ERROR_FILE_SIZE_CHANGED_LO = 2;
    public final static int ERROR_FILE_SIZE_CHANGED_HI = 3;
    public final static int ERROR_DIRECTORY_TOO_LARGE = 4;
    public final static int ERROR_FILE_TOO_LARGE = 5;
    public final static int ERROR_NAME_TOO_LONG = 6;
    public final static int ERROR_PATH_TOO_LONG = 7;

    /** custom error codes start at this base...*/
    public final static int ERROR_CUSTOM_BASE = 1000;

    public static class Exception extends IOException {
        public final int error;

        private static final long serialVersionUID = 2528273709611685335L;
        public Exception(int error, String fmt, Object... args) {
            super(String.format(fmt, args));
            this.error = error;
        }
    }

    ///////////////////////////////////////////////////////////////////////

    /**
     * Progress callback to report on the writing to a volume. For more granular
     * reports hooking into the output block device is recommended.
     */
    public interface Progress {
        /**
         * @param dir The directory the file is located in logically. Null if
         * all of the files have been written and e.g. free space is about to
         * be emitted.
         * @param node The file being processed. Ignored if directory is null.
         */
        void onFile(Directory dir, FileNode node);

        ///////////////////////////////////////////////////////////////////////

        /**
         * Progress implementation, ignoring all of the calls.
         */
        public static Progress NULL = new Progress() {
            public void onFile(Directory dir, FileNode node) { }
        };
    }

    ///////////////////////////////////////////////////////////////////////

    /**
     * Creates a progress implementation which prints debug-style information.
     * @param out Where to print to.
     * @return The progress instance.
     */
    public static Progress newDebugProgress(final PrintStream out) {
        return new Progress() {
            public void onFile(Directory dir, FileNode node) {
                if (null == dir) {
                    out.println("DEBUG.onFile - all files written");
                    return;
                }
                final char sepa = node.fileSystem().separatorChar();
                final StringBuilder path = new StringBuilder();
                do {
                    FileNode dn = dir.nodes()[0];
                    path.insert(0, sepa);
                    path.insert(0, null == dn ? "" : dn.name());
                    dir = dir.parent();
                }
                while (null != dir);
                out.printf("DEBUG.onFile - path='%s' name='%s' size=%d)\n",
                           path.toString(), node.name(), node.size());
            }
        };
    }

    ///////////////////////////////////////////////////////////////////////////

    protected Properties    props;
    protected FileRegistrar freg;

    /**
     * Default ctor.
     * @param freg The file registrar to use.
     * @param props Properties to get the configuration from.
     */
    public Writer(FileRegistrar freg, Properties props) {
        this.props = props;
        this.freg  = freg;
    }

    /**
     * Definition of a volume layout.
     */
    public interface Layout {
        /**
         * @return The size of a block, multiple of 512.
         */
        int blockSize();
        /**
         * @return Number of free blocks to include. The writer should keep
         * this space unallocated, so it can be used for storing additional
         * files. The actual free space available can vary, depending on the
         * implementation. However it is recommended that this number will be
         * visible to the user when querying for free disk space.
         */
        long freeBlocks();
        /**
         * @return The desired label. What it means, if it actually gets
         * stored at all or normalized to a certain format is up to the
         * implementation and shouldn't cause an error. Can be null if no
         * custom label is desired.
         */
        String label();
        /**
         * @return True to create a write-protected volume. False for a writable
         * version - should be the default, not all file systems support the
         * concept of a sealed state.
         */
        boolean writeProtect();
    }

    /**
     * Calculates the size of the volume based on the given file registrar's
     * content and a certain layout.
     * @param layout The layout to use.
     * @return Number of blocks the volume will have.
     * @throws IOException If any error occurred.
     */
    public abstract long resolve(Layout layout) throws IOException;

    /**
     * Creates the volume.
     * @param bdev Block device to write to.
     * @param progress Progress callback.
     * @throws IOException If any error occurred.
     */
    public abstract void make(BlockDevice bdev, Progress progress) throws IOException;
}
