package coderslagoon.trupax.sdk.demos;

import java.io.File;
import java.util.Properties;

import coderslagoon.trupax.lib.prg.Prg;
import coderslagoon.trupax.lib.prg.PrgImpl;
import coderslagoon.trupax.lib.prg.Prg.Concern;
import coderslagoon.trupax.lib.prg.Prg.Result;

/**
 * Shows how to destroy files (and, if needed, folders= using the TruPax wipe
 * functionality.
 */
public class WipeDemo extends Demo
    implements Prg.WipeCallback, Prg.RegisterObjectsCallback {

    ///////////////////////////////////////////////////////////////////////////

    @Override
    protected void exec() throws Exception {
        // create a 5MB file which we will wipe soon
        File wipethis = createFile(null, "dat", 5 * 1000 * 1000L);

        // first we do the global initialization, without it nothing works
        checkResult("global initialization", PrgImpl.init());

        // create a new program instance
        Prg prg = new PrgImpl();

        // construct the instance, passing no special properties and dealing
        // with a default setup (no pass-through of arguments etc)
        checkResult("instance construction", prg.ctor(new Properties(), new Prg.Setup()));

        // add the file to wipe, this just puts it into an internal  list
        checkResult("adding file", prg.addObject(wipethis.getAbsolutePath()));

        // now register things, meaning resolving and searching given paths
        // and compute total data amounts (for progress reporting) - since we
        // don't deal with directories here the callback will not be invoked
        checkResult("adding file", prg.registerObjects(this));

        // do the wipe action, the callbacks will report on progress (see below)
        checkResult("wiping", prg.wipe(this));

        // dissolve the program instance
        checkResult("instance destruction", prg.dtor());

        // do the global cleanup, leave nothing behind
        checkResult("global cleanup", PrgImpl.cleanup());

        // check if our file is really not there anymore
        if (wipethis.exists()) {
            logerr("ERROR: %s still exists!?\n", wipethis);
        }
        else {
            log("SUCCESS: %s is gone\n", wipethis);
        }

        // release the instance
        checkResult("instance release", prg.dtor());
    }

    ///////////////////////////////////////////////////////////////////////////

    @Override
    public Result onConcern(Concern concern, String message) {
        System.err.printf("concern %s raised (%s)\n", concern, message);
        return Result.aborted();
    }

    @Override
    public void onFile(String fileName, long fileSize) {
        System.out.printf("about to wipe %s (%d bytes)\n", fileName, fileSize);
    }

    @Override
    public Result onProgress(double percent) {
        System.out.printf("wipe progress is %.2f%%\n", percent);
        return Result.ok();
    }

    @Override
    public Result onDirectory(String dir) {
        return Result.ok();
    }

    @Override
    public void configLocked() {
    }

    ///////////////////////////////////////////////////////////////////////////

    public static void main(String[] args) {
        try {
            new WipeDemo().exec();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}
