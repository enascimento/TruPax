package coderslagoon.trupax.sdk.demos;

import java.io.File;
import java.util.Properties;

import coderslagoon.trupax.lib.prg.Prg;
import coderslagoon.trupax.lib.prg.PrgImpl;
import coderslagoon.trupax.lib.prg.Prg.Concern;
import coderslagoon.trupax.lib.prg.Prg.Result;

/**
 * Full TruPaxLib volume creation demonstration, including the possibility to
 * run multiple instances in different threads simultaneously (and safely).
 */
public class MakeExtractInvalidateDemo extends Demo {
    // Please use something much safer in production. Also make sure that
    // all key or password material gets erased as soon as possible. TruPax
    // does it internally for all sensitive material passed into it, but the
    // outside has to be tidy and careful as well!
    final static String PASSWORD = "123456";

    ///////////////////////////////////////////////////////////////////////////

    class Maker extends Thread {

        final File   volumeFile;
        final String directory;
        final String singleFile;

        public Maker(File directory, File singleFile) {
            this.volumeFile = createPath(null, "tc");
            this.directory  = directory .getAbsolutePath();
            this.singleFile = singleFile.getAbsolutePath();
        }

        @Override
        public void run() {
            try {
                runUnsafe();
            }
            catch (Throwable err) {
                err.printStackTrace(System.err);
            }
        }

        void runUnsafe() throws Exception {

            // create one program instance per thread, showing that such
            // parallel usage is perfectly acceptable and safe; remember that
            // one needs one instance per thread since instances themselves
            // cannot be shared amongst threads without access synchronization
            Prg prg = new PrgImpl();

            Properties props = new Properties();

            // make sure all of the directory gets included
            props.put(Prg.Prop.RECURSIVE_SEARCH, true);

            // use the default setup; this is the most recommended way for any
            // embedded usage unless one creates a one-instance application
            // similar to TruPax ...
            Prg.Setup setup = new Prg.Setup();

            // finish instance preparation
            checkResult("instance construction",
                prg.ctor(props, setup));

            // label each volume individually
            props.put(Prg.Prop.LABEL, "volume" + Thread.currentThread().getId());

            // add the directory and the one file we are dealing with
            checkResult("add directory", prg.addObject(this.directory));
            checkResult("add file"     , prg.addObject(this.singleFile));

            // register the whole material
            checkResult("register objects",
                prg.registerObjects(new Prg.RegisterObjectsCallback() {
                    @Override
                    public Result onDirectory(String dir) {
                        log("found directory %s\n", dir);
                        return Result.ok();
                    }
                    @Override
                    public void configLocked() {
                    }
                }));

            // show what the whole registration yielded
            Prg.RegSum regsum = prg.registerSummary();
            log("registered %d files, %d directories, %,d bytes total\n",
                regsum.numberOfFiles,
                regsum.numberOfDirectories,
                regsum.bytesTotal);

            // add some free space (20 MB) to the volume
            checkResult("add free space", prg.setFreeSpace(20 * 1024 * 1024));

            // compute the whole volume layout; this has to be done manually
            // since depending on the amount of registered material it might
            // take a couple of seconds
            checkResult("resolve volume", prg.resolve());

            // show how big the volume is going to get
            final long volumeSize = prg.volumeBytes();
            log("the volume will be %,d bytes large\n", volumeSize);

            // pass in the full path of the volume we want to create
            checkResult("set volume file path",
                prg.setVolumeFile(this.volumeFile.getAbsolutePath()));

            // now make the volume and report progress
            checkResult("make the volume", prg.make(
                PASSWORD.toCharArray(),
                new Prg.MakeCallback() {
                    @Override
                    public void onFile(String fileName, long fileSize) {
                        log("adding file %s (%d bytes) ...\n", fileName, fileSize);
                    }
                    @Override
                    public Result onVolumeWrite(long pos) {
                        // despite having the ability to interrupt things on a
                        // very fine level, progress reporting can be quite
                        // noisy, so here we lower the output by keeping some
                        // record ...
                        if (this.lastpos + (volumeSize / 10) < pos) {
                            log("written %,d of %,d bytes ...\n", pos, volumeSize);
                            this.lastpos= pos;
                        }
                        return Result.ok();
                    }
                    long lastpos = -1;
                    @Override
                    public Result onFreeSpace() {
                        log("writing free space ...\n");
                        return Result.ok();
                    }
                }));
            log("done.\n");

            // clean up the whole instance
            checkResult("", prg.dtor());
        }
    }

    ///////////////////////////////////////////////////////////////////////////

    @Override
    protected void exec() throws Exception {
        log("TruPax version is '%s'\n", Prg.version());

        checkResult("global initialization", PrgImpl.init());

        log("creating files and directories ...\n");
        File workDir  = createDirectory(null);
        File someFile = createFile(workDir, "dat", 90009L);
        File someDir  = createDirectory(workDir);
                        createFile(someDir, null, 404L);
        File dirInDir = createDirectory(someDir);
                        createFile(dirInDir, "big", 12345678L);

        // run three makers simultaneously
        Maker[] makers = new Maker[3];
        for (int i = 0; i < makers.length; i++) {
            Maker maker = new Maker(someDir, someFile);
            maker.setName("maker-" + (1 + i));
            makers[i] = maker;
        }
        for (Maker maker : makers) {
            maker.start();
        }
        for (Thread maker: makers) {
            maker.join();
        }

        // extract the volume created by the first maker
        File extractDir  = createDirectory(workDir);
        extract(makers[0].volumeFile, extractDir);

        // and invalidate that volume
        invalidate(makers[0].volumeFile);

        checkResult("global cleanup", PrgImpl.cleanup());

        log("all done, you may now delete the work directory %s\n", workDir);
    }

    ///////////////////////////////////////////////////////////////////////////

    private static void extract(File volume, File toDir) {

        // common instance genesis
        Prg prg = new PrgImpl();

        checkResult("instance construction",
            prg.ctor(new Properties(), new Prg.Setup()));

        // set the volume file
        checkResult("set the volume file",
            prg.setVolumeFile(volume.getAbsolutePath()));

        // run the extraction, show what's happening via the callback
        checkResult("extraction",
            prg.extract(
                PASSWORD.toCharArray(),
                toDir.getAbsolutePath(),
                new Prg.ExtractCallback() {

                    @Override
                    public void onFile(String fileName, long fileSize) {
                        log("extracting file %s (%,d bytes)\n", fileName, fileSize);
                    }
                    @Override
                    public Result onConcern(Concern concern, String message) {
                        // we really should face any issues here, but still...
                        logerr("%s (%s)", concern, message);
                        return Result.aborted();
                    }
                    @Override
                    public Result onOpening(int objs) {
                        return Result.ok();
                    }
                    @Override
                    public Result onOpen(int files, int dirs) {
                        log("volume contains %d files and %d directories\n", files, dirs);
                        return Result.ok();
                    }
                    @Override
                    public Result onFileWrite(long pos) {
                        return Result.ok();
                    }
                }));

        log("extraction done.\n");
    }

    ///////////////////////////////////////////////////////////////////////////

    private static void invalidate(File volume) {

        Prg prg = new PrgImpl();

        // we want to keep the volume after invalidation, just because, so we
        // have to set a configuration flag to make sure this wisg gets honored
        Properties props = new Properties();
        props.put(Prg.Prop.DELETE_AFTER, false);

        checkResult("instance construction",
            prg.ctor(props, new Prg.Setup()));

        // to demonstrate the abort possibility via callback results we first
        // stop right after the first progress call and in the second round
        // then complete the actual invalidation
        for (boolean abort : new boolean[] { true, false} ) {
            final boolean abrt = abort;

            // the volume file has to be declared before every attempt
            checkResult("set the volume file",
                prg.setVolumeFile(volume.getAbsolutePath()));

            Prg.Result result = prg.invalidate(new Prg.ProgressCallback() {
                    @Override
                    public Result onProgress(double percent) {
                        if (percent - this.lastPercent > 20.0) {
                            log("%.1f%%\n", this.lastPercent = percent);
                        }
                        return abrt ? Result.aborted() : Result.ok();
                    }
                    double lastPercent = -1.0;
                });

            if (abort) {
                log("invalidation attempt returned %s\n", result.code);
            }
            else {
                checkResult("invalidation", result);
                log("volume %s got invalidated\n", volume.getAbsolutePath());
            }
        }
    }

    ///////////////////////////////////////////////////////////////////////////

    public static void main(String[] args) {
        try {
            new MakeExtractInvalidateDemo().exec();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}
