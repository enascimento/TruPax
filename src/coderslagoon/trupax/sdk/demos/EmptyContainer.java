package coderslagoon.trupax.sdk.demos;

import java.io.File;
import java.util.Properties;

import coderslagoon.trupax.lib.prg.Prg;
import coderslagoon.trupax.lib.prg.PrgImpl;
import coderslagoon.trupax.lib.prg.Prg.Result;

/**
 * This shows how to create an completely empty volume (formatted in UDF).
 * Notice that under legacy Windows XP UDF containers will be always be
 * read-only, hence any kind of free space generation in general is useless.
 */
public class EmptyContainer extends Demo {

    @Override
    protected void exec() throws Exception {

        // global initialization we need
        checkResult("init", PrgImpl.init());

        // create the program instance, needs no initial properties
        Prg prg = new PrgImpl();
        Properties props = new Properties();

        // initialize the instance, default setup is sufficient
        Prg.Setup setup = new Prg.Setup();
        checkResult("ctor", prg.ctor(props, setup));

        // optional: set a label for the volume
        props.put(Prg.Prop.LABEL, "empty");

        // set the size of the volume and resolve its layout
        checkResult("freespace", prg.setFreeSpace(10 * 1024 * 1024));
        checkResult("resolve", prg.resolve());

        // remember the promised size for later (just for display below)
        long expectedSize = prg.volumeBytes();

        // create a valid file name (here in the temporary folder) and set it
        File volume = new File(
                System.getProperty("java.io.tmpdir"),
                "empty" + System.currentTimeMillis() + ".tc");
        checkResult("setvolumefile",
                prg.setVolumeFile(volume.getAbsolutePath()));

        // now create the volume (with the given password)
        final String PASSWORD = "test123";
        checkResult("make", prg.make(
            PASSWORD.toCharArray(),
            new Prg.MakeCallback() {
                @Override
                public void onFile(String fileName, long fileSize) {
                    // (this will never be called)
                }
                @Override
                public Result onVolumeWrite(long pos) {
                    return Result.ok();
                }
                @Override
                public Result onFreeSpace() {
                    return Result.ok();
                }
            }));

        log("done, expected size does%s match.\n",
            volume.length() == expectedSize ? "" : " NOT");

        // clean up
        checkResult("", prg.dtor());
        //volume.delete();
    }

    ///////////////////////////////////////////////////////////////////////////

    public static void main(String[] args) {
        try {
            new EmptyContainer().exec();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}
