package coderslagoon.trupax.exe;

import java.io.File;
import java.io.PrintStream;
import java.util.Arrays;

import coderslagoon.test.util.TestUtils;
import coderslagoon.trupax.exe.CmdLn;
import coderslagoon.trupax.lib.UDFTest;
import coderslagoon.trupax.lib.prg.PrgImpl;
import coderslagoon.trupax.test.util.Verifier;

public class Examiner extends CmdLn {
    static void run(String[] args) throws Exception {
        __TEST_password = args[0];

        args = Arrays.copyOfRange(args, 1, args.length);

        String volume = null;
        for (String arg : args) {
            if (!arg.trim().startsWith("-")) {
                volume = arg;
                break;
            }
        }

        CmdLn.main(args);

        File tmpDir = TestUtils.createTempDir("examiner");
        File dump = new File(tmpDir, "dump");

        Verifier.decryptVolume(__TEST_password.toCharArray(),
                               new File(volume), dump);

        final PrintStream ps = new PrintStream(new File(tmpDir, "udftest.txt"));
        if (UDFTest.available()) {
            if (UDFTest.exec(dump,
                             PrgImpl.BLOCK_SIZE,
                             true,
                             false,
                             false,
                             new UDFTest.Listener() {
                public boolean onOutput(String ln) {
                    ps.println(ln);
                    return true;
                }
            })) {
                System.out.println("OK");
            }
            else {
                System.err.println("UDFTEST FAILED!");
            }
        }
        ps.close();
    }

    public static void main(String[] args) {
        try {
            run(args);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}
