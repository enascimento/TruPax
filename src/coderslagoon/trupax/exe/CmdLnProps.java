package coderslagoon.trupax.exe;

import coderslagoon.baselib.util.Prp;

public final class CmdLnProps extends ExeProps {
    public final static String PFX = "trupax.cmdln.";

    public final static Prp.Str  OPTS_PASSWORD   = new Password (PFX);
    public final static Prp.Lng  OPTS_FREESPACE  = new FreeSpace(PFX);
    public final static Prp.Bool OPTS_VERBOSE    = new Prp.Bool(PFX + "verbose"   , false);
    public final static Prp.Bool OPTS_WIPE       = new Prp.Bool(PFX + "wipe"      , false);
    public final static Prp.Bool OPTS_WIPEONLY   = new Prp.Bool(PFX + "wipeonly"  , false);
    public final static Prp.Bool OPTS_EXTRACT    = new Prp.Bool(PFX + "extract"   , false);
    public final static Prp.Bool OPTS_INVALIDATE = new Prp.Bool(PFX + "invalidate", false);
}
