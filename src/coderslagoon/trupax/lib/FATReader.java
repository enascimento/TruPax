package coderslagoon.trupax.lib;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;

import coderslagoon.baselib.io.BlockDevice;
import coderslagoon.baselib.util.MiscUtils;
import coderslagoon.baselib.util.Prp;
import coderslagoon.trupax.lib.Reader.Progress.Result;

import de.waldheinz.fs.FileSystem;
import de.waldheinz.fs.FileSystemFactory;
import de.waldheinz.fs.FsDirectoryEntry;
import de.waldheinz.fs.ReadOnlyException;
import de.waldheinz.fs.fat.FatFile;
import de.waldheinz.fs.fat.FatFileSystem;
import de.waldheinz.fs.fat.FatLfnDirectory;
import de.waldheinz.fs.fat.FatLfnDirectoryEntry;

public class FATReader extends Reader {
    static class Props {
        public final static String PFX = Reader.Props.PFX + "fat.";
        public final static Prp.Int  BUFSIZE = new Prp.Int (PFX + "bufsize", 64 * 512);
        public final static Prp.Bool LCSNAME = new Prp.Bool(PFX + "lcsname", true);
    }

    public FATReader(BlockDevice bdev, Properties props) {
        super(bdev, props);
    }

    public void extract(File toDir, Progress progress) throws IOException {
        try {
            BlockDeviceBridge bdb = new BlockDeviceBridge(this.bdev);
            FileSystem fs = FileSystemFactory.create(bdb, true);
            if (fs instanceof FatFileSystem) {
                this.toDir    = toDir;
                this.progress = progress;
                this.ffs = (FatFileSystem)fs;
                extractInternal();
            }
            else {
                throw new MountException(fs.getClass().getSimpleName());
            }
        }
        catch (IOException ioe) {
            throw new MountException(ioe.getMessage());
        }
    }

    void extractInternal() throws IOException {
        this.files = this.dirs = 0;
        FatLfnDirectory root = this.ffs.getRoot();
        walkDir(root, null, null);
        checkAbort(this.progress.onMount(this.files, this.dirs));
        walkDir(root, this.toDir, null);
    }

    void walkDir(final FatLfnDirectory dir, final File toDir, Long tstamp) throws IOException {
        LocalDir ldir = new LocalDir() {
            public void writeEntries() throws IOException {
                Iterator<FsDirectoryEntry> ifde = dir.iterator();
                while (ifde.hasNext()) {
                    final FatLfnDirectoryEntry ffde = (FatLfnDirectoryEntry)ifde.next();
                    String ename = getEntryName(ffde);
                    if (null == ename) {
                        continue;
                    }
                    if (ffde.isDirectory()) {
                        File newDir = toDir;
                        if (null == newDir) {
                            FATReader.this.dirs++;
                        }
                        else {
                            newDir = new File(newDir, ename);
                            if (!checkAbort(FATReader.this.progress.onDirectory(
                                newDir, Progress.SIZE_UNKNOWN, ffde.getLastModified()))) {
                                continue;
                            }
                        }
                        walkDir(ffde.getDirectory(), newDir, ffde.getLastModified());
                    }
                    else if (ffde.isFile()) {
                        if (null == toDir) {
                            FATReader.this.files++;
                        }
                        else {
                            final File newFile = new File(toDir, ename);
                            final long tstamp = ffde.getLastModified();
                            final FatFile ffl = ffde.getFile();
                            final long size = ffl.getLength();
                            if (checkAbort(FATReader.this.progress.onFile(newFile, size, tstamp))) {
                                LocalFile lfl = new LocalFile() {
                                    protected void writeData(OutputStream os, long size) throws IOException {
                                        final int bufSize = Props.BUFSIZE.get(FATReader.this.props);
                                        final ByteBuffer buf = ByteBuffer.allocate(bufSize);
                                        checkAbort(FATReader.this.progress.onData(0L));
                                        for (long ofs = 0; ofs < size;) {
                                            long left = size - ofs;
                                            int toRead = bufSize < left ? bufSize : (int)left;
                                            buf.clear();
                                            buf.limit(toRead);
                                            ffl.read(ofs, buf);
                                            int bofs = buf.arrayOffset();
                                            int bpos = buf.position();
                                            //System.out.printf("BUFFER RET %d @ %d (toread=%d, left=%d)\n", bpos, bofs, toRead, left);
                                            if (0 >= bpos) {
                                                break; // (tsnh)
                                            }
                                            os.write(buf.array(), bofs, bpos);
                                            ofs += bpos;
                                            checkAbort(FATReader.this.progress.onData(ofs));
                                        }
                                    }
                                };
                                lfl.write(newFile, size, tstamp, FATReader.this.progress);
                            }
                        }
                    }
                }
            }
        };
        if (null == toDir) {
            ldir.writeEntries();
            checkAbort(FATReader.this.progress.onMounting(
                       FATReader.this.files + FATReader.this.dirs));
        }
        else {
            ldir.write(toDir, tstamp);
        }
    }

    FatFileSystem ffs;
    Progress      progress;
    File          toDir;
    int           files, dirs;

    boolean checkAbort(Result res) throws Exception {
        if (res == Result.ABORT) {
            throwAbort();
        }
        return res == Result.OK;
    }

    //////////////////////////////////////////////////////////////////////////

    final static AtomicReference<Field>  _fldReadEntry      = new AtomicReference<>();
    final static AtomicReference<Method> _mtdGetShortName   = new AtomicReference<>();
    final static AtomicReference<Method> _mtdAsSimpleString = new AtomicReference<>();

    static String getShortName(FatLfnDirectoryEntry flde) throws IOException {
        try {
            Field fld = _fldReadEntry.get();
            if (null == fld) {
                fld = flde.getClass().getDeclaredField("realEntry");
                fld.setAccessible(true);
                _fldReadEntry.set(fld);
            }
            Object obj = fld.get(flde);
            Method mtd = _mtdGetShortName.get();
            if (null == mtd) {
                mtd = obj.getClass().getDeclaredMethod("getShortName");
                mtd.setAccessible(true);
                _mtdGetShortName.set(mtd);
            }
            obj = mtd.invoke(obj);
            mtd = _mtdAsSimpleString.get();
            if (null == mtd) {
                mtd = obj.getClass().getDeclaredMethod("asSimpleString");
                mtd.setAccessible(true);
                _mtdAsSimpleString.set(mtd);
            }
            return (String)mtd.invoke(obj);
        }
        catch (Throwable err) {
            throw new IOException(err.getMessage());
        }
    }

    String getEntryName(FatLfnDirectoryEntry flde) throws IOException {
        String result = flde.getName();
        if (result.equals(".") || result.equals("..")) {
            return null;
        }
        final int rlen = result.length();
        if ((8 + 3) < rlen) {
            return result;
        }
        String sname = getShortName(flde);
        if (result.equals(sname)) {
            if (rlen <= 8) {
                result = sname.trim();
            }
            else {
                result = sname.substring(0,8).trim() + '.' +
                         sname.substring(8  ).trim();
            }
            if (this.lcsname && !MiscUtils.mixedCase(result)) {
                result = result.toLowerCase();
            }
        }
        return result;
    }

    private boolean lcsname = Props.LCSNAME.get(this.props);

    //////////////////////////////////////////////////////////////////////////

    static class BlockDeviceBridge implements de.waldheinz.fs.BlockDevice {
        BlockDevice bdev;
        byte[] blk;
        public BlockDeviceBridge(BlockDevice bdev) {
            this.bdev = bdev;
            this.blk  = new byte[bdev.blockSize()];
        }
        public long getSize() throws IOException {
            return this.bdev.size() * this.blk.length;
        }
        public void read(long devOffset, ByteBuffer dest) throws IOException {
            //System.out.printf("FAT READ @ %d, %d\n", devOffset, dest.remaining());
            final byte[] blk = this.blk;
            if (0 != devOffset % blk.length) {
                throw new IOException(String.format("unaligned device offset (%d)", devOffset));
            }
            int left = dest.remaining();
            long num = devOffset / blk.length;
            for (; 0 < left; num++) {
                this.bdev.read(num, blk, 0);
                int toPut = Math.min(left, blk.length);
                dest.put(blk, 0, toPut);
                left -= toPut;
            }
        }
        public void write(long devOffset, ByteBuffer src)
            throws ReadOnlyException, IOException, IllegalArgumentException {
            throw new ReadOnlyException();
        }
        public void flush() throws IOException {
        }
        public int getSectorSize() throws IOException {
            return this.bdev.blockSize();
        }
        public void close() throws IOException {
        }
        public boolean isClosed() {
            return false;
        }
        public boolean isReadOnly() {
            return true;
        }
    }
}
