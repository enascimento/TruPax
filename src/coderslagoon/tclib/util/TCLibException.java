package coderslagoon.tclib.util;

public class TCLibException extends Exception {
    public TCLibException()                { super(); }
    public TCLibException(String message)  { super(message); }
    public TCLibException(Throwable cause) { super(cause); }

    private static final long serialVersionUID = 113929922920725296L;
}
