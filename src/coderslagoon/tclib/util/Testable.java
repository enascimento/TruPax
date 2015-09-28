package coderslagoon.tclib.util;

/**
 * To run a self-test on a throw-away instance.
 */
public interface Testable {
    /**
     * Run the test. The instance can be invalid afterwards and must be
     * discarded by the caller.
     * @throws Throwable If any error happened during the test. In such a case
     * the test must be treated as a failure and the class of the instance in
     * general being prevented from further usage since it might produce
     * invalid data or worse!
     */
    void test() throws Throwable;
}
