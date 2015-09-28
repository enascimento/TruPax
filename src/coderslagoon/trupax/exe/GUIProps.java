package coderslagoon.trupax.exe;

import coderslagoon.baselib.util.Prp;

public class GUIProps extends ExeProps {
    public final static String GUI_PFX            = "gui.";
    public final static String GUI_PRG_PFX        = GUI_PFX + "prg.";
    public final static String GUI_OPT_PFX        = GUI_PFX + "opt.";
    public final static String GUI_COL_PFX        = GUI_PFX + "col.";
    public final static String GUI_COL_WIDTH_PFX  = GUI_COL_PFX + "width.";
    public final static String GUI_COL_SORT_PFX   = GUI_COL_PFX + "sort.";

    public final static Prp.Int  COL_WIDTH_FILE = new Prp.Int (GUI_COL_WIDTH_PFX + "file", -1);
    public final static Prp.Int  COL_WIDTH_SIZE = new Prp.Int (GUI_COL_WIDTH_PFX + "size", -1);
    public final static Prp.Int  COL_WIDTH_DATE = new Prp.Int (GUI_COL_WIDTH_PFX + "date", -1);
    public final static Prp.Int  COL_SORT_IDX   = new Prp.Int (GUI_COL_SORT_PFX  + "idx" , -1);
    public final static Prp.Bool COL_SORT_ASC   = new Prp.Bool(GUI_COL_SORT_PFX  + "asc", true);

    public final static Prp.Lng  PRG_FREESPACE = new FreeSpace(GUI_PRG_PFX);
    public final static Prp.Bool PRG_WIPE      = new Prp.Bool(GUI_PRG_PFX  + "wipe", false);

    public final static Prp.Bool OPT_SHOWPASSWORD  = new Prp.Bool(GUI_OPT_PFX  + "showpassword" , false);
    public final static Prp.Bool OPT_CACHEPASSWORD = new Prp.Bool(GUI_OPT_PFX  + "cachepassword", false);
    public final static Prp.Bool OPT_COLORPASSWORD = new Prp.Bool(GUI_OPT_PFX  + "colorpassword", true );
}
