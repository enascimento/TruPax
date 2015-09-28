package coderslagoon.tclib.crypto;

import coderslagoon.tclib.util.Erasable;
import coderslagoon.tclib.util.Testable;

public interface Algorithm extends Erasable, Testable {
    public String name();
}
