package coderslagoon.tclib.crypto;

import coderslagoon.tclib.crypto.RIPEMD160;

public class RIPEMD160Perf {

    final static int  DATA_SZ    = 4096;
    final static int  LOOPS      = 1000; //0;
    final static long RUN_MILLIS = 8000;

    static void measurePerformance(RIPEMD160 re) {
        final long start = System.currentTimeMillis();

        byte[] testData = new byte[DATA_SZ];
        for (int i = 0; i < testData.length; i++) {
            testData[i] = (byte)i;
        }

        long tm;
        long c = 0;

        while ((tm = System.currentTimeMillis() - start) < RUN_MILLIS) {
            for (int i = 0; i < LOOPS; i++) {
                re.update(testData, 0, testData.length);
            }
            c += LOOPS;
        }

        System.out.printf("%,.1f bytes per second",
                ((testData.length * c) * 1000.0) /
                 Math.max(1, tm));
    }

    public static void main(String[] args) {
        final RIPEMD160 re = new RIPEMD160();
        try {
            re.test();
        }
        catch (Throwable err) {
            System.err.println("TEST FAILED: " + err.getMessage());
            return;
        }
        measurePerformance(re);
    }
}
