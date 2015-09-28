package coderslagoon.trupax.lib;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.PrintStream;
import java.security.SecureRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UDFTest {

    final static String ENV_UDFTESTPATH = "udftestpath";

    final static int DEF_BLK_SZ = 512;

    public interface Listener {
        boolean onOutput(String ln);
    }

    public static boolean available() {
        return null != System.getenv(ENV_UDFTESTPATH);
    }

    public static boolean exec(File img, int blockSize, boolean delay,
            boolean dump, boolean keepLogs, Listener lstn) throws IOException {
        final String utpath = System.getenv(ENV_UDFTESTPATH);
        if (null == utpath) {
            return false;
        }

        final ProcessBuilder pb = new ProcessBuilder(
                utpath,
                "-ecclength",
                "16",
                "-blocksize",
                String.valueOf(-1 == blockSize ? DEF_BLK_SZ : blockSize),
                img.getAbsolutePath()).redirectErrorStream(true);

        if (delay) {
            try { Thread.sleep(1001L); }
            catch (InterruptedException ignored) { }
        }

        long tm = System.currentTimeMillis();

        final Process p = pb.start();

        LineNumberReader lnr = new LineNumberReader(
                               new InputStreamReader(p.getInputStream()));

        Integer errs = null;
        Integer wrns = null;

        final Pattern perr = Pattern.compile("^    Error count:[\\x20]+([0-9]+).+$");
        final Pattern pwrn = Pattern.compile("^  Warning count:[\\x20]+([0-9]+).+$");

        File log = new File(
                System.getProperty("java.io.tmpdir"),
                String.format("udftest_%d_%08x.log",
                        System.currentTimeMillis(),
                        new SecureRandom().nextInt()));

        PrintStream logs = new PrintStream(log);
        for(;;) {
            String ln = lnr.readLine();
            if (null == ln) {
                break;
            }
            logs.println(ln);
            if (dump) {
                System.out.println("UDFTEST >>> " + ln);
            }
            if (null == errs) {
                Matcher m = perr.matcher(ln);
                if(m.matches()) {
                    errs = Integer.parseInt(m.group(1));
                }
            }
            else if (null == wrns) {
                Matcher m = pwrn.matcher(ln);
                if (m.matches()) {
                    wrns = Integer.parseInt(m.group(1));
                }
            }
            if (null != lstn && !lstn.onOutput(ln)) {
                break;
            }
        }
        logs.close();
        lnr.close();

        int exitCode = -1;
        try {
            exitCode = p.waitFor();
        }
        catch (InterruptedException ignored) {
        }

        System.out.printf("====udftest took %.3f seconds, exitcode is %d\n",
        		(System.currentTimeMillis() - tm) / 1000.0, exitCode);

        if (0 != exitCode) {
            return false;
        }
        else if (null == errs || null == wrns) {
            System.err.println("missing errors/warnings!");
            return false;
        }
        else if (0 == errs && 0 == wrns) {
            if (!keepLogs) {
                if (!log.delete()) {
                    System.err.println("cannot delete log file!");
                }
            }
            return true;
        }
        return false;
    }
}
