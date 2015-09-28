package coderslagoon.trupax.exe;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.net.URLEncoder;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.DropTarget;
import org.eclipse.swt.dnd.DropTargetAdapter;
import org.eclipse.swt.dnd.DropTargetEvent;
import org.eclipse.swt.dnd.FileTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.events.FocusListener;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseListener;
import org.eclipse.swt.events.ShellEvent;
import org.eclipse.swt.events.ShellListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.FontMetrics;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.program.Program;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;

import coderslagoon.baselib.swt.dialogs.About;
import coderslagoon.baselib.swt.dialogs.DirectoryDialog2;
import coderslagoon.baselib.swt.dialogs.FileDialog2;
import coderslagoon.baselib.swt.dialogs.LangDialog;
import coderslagoon.baselib.swt.dialogs.MessageBox2;
import coderslagoon.baselib.swt.util.SWTUtil;
import coderslagoon.baselib.swt.util.ShellProps;
import coderslagoon.baselib.swt.util.ToolTips;
import coderslagoon.baselib.util.BaseLibException;
import coderslagoon.baselib.util.Clock;
import coderslagoon.baselib.util.Log;
import coderslagoon.baselib.util.MiscUtils;
import coderslagoon.baselib.util.Prp;
import coderslagoon.baselib.util.VarInt;
import coderslagoon.baselib.util.VarLong;
import coderslagoon.baselib.util.VarRef;
import coderslagoon.trupax.exe.NLS;
import coderslagoon.trupax.exe.dialogs.Password;
import coderslagoon.trupax.exe.dialogs.Progress;
import coderslagoon.trupax.exe.util.PasswordCache;
import coderslagoon.trupax.lib.prg.Prg;
import coderslagoon.trupax.lib.prg.PrgImpl;
import coderslagoon.trupax.lib.prg.PrgProps;
import coderslagoon.trupax.lib.prg.Prg.Concern;
import coderslagoon.trupax.lib.prg.Prg.Result;

public class GUI extends Exe implements NLS.Reg.Listener {
    static Log _log = new Log("GUI");

    Display     display;
    Shell       shell;
    ShellProps  shellProps;
    Composite   left;
    Composite   right;
    Table       objects;
    TableColumn tcFile, tcSize, tcDate;
    Label       info;
    Button      btnAddFiles, btnAddFolder, btnClear, btnMake;
    Button      chkStoreFullPath, chkIncludeSubfolders, chkWipe;
    Button      chkWriteProtect, chkForVeraCrypt;
    Combo       cmbMerge;
    MenuItem    mniFile;
    MenuItem    mniFileAddFiles;
    MenuItem    mniFileAddFolder;
    MenuItem    mniFileWipe;
    MenuItem    mniFileExtract;
    MenuItem    mniFileInvalidate;
    MenuItem    mniFileQuit;
    MenuItem    mniHelp;
    MenuItem    mniWebsite;
    MenuItem    mniHelpDocumentation;
    MenuItem    mniHelpWebsite;
    MenuItem    mniHelpLang;
    MenuItem    mniHelpAbout;
    Composite   spc1, spc2, spc3;
    Label       lblFreeSpace, lblLabel;
    Text        txtFreeSpace, txtLabel;
    DropTarget  drop;
    Menu        mnMain;
    Prg         prg;
    Properties  props = Prp.global();
    DateFormat  dateFmt;
    Prg.Result  setupResult, setupError;
    ToolTips    toolTips = new ToolTips();

    AtomicBoolean debug = new
    AtomicBoolean();

    final PasswordCache pwcache = new PasswordCache();
    final Map<String, File> manualFiles = new HashMap<>();

    final static int EXITCODE_UNRECERR = 1;
    final static int INFO_LINES = 3;

    final static Integer SLOMO = null;

    ///////////////////////////////////////////////////////////////////////////

    public final static double BTN_ADJ_FCT = .5;

    ///////////////////////////////////////////////////////////////////////////

    private void initLang() throws BaseLibException {
        String lang = ExeProps.Lang.get();
        if (null == lang) {
            LangDialog ldlg = new LangDialog(this.shell, this.props, LANGS, PRODUCT_NAME, true);
            ldlg.open();
            ldlg.waitForClose();
            ExeProps.Lang.set(Prp.global(), lang = ldlg.id());
        }
        NLS.Reg.instance().load(lang);
    }

    ///////////////////////////////////////////////////////////////////////////

    public GUI(String[] args) throws ExitError {

        Display.setAppName(Exe.PRODUCT_NAME);
        Display.setAppVersion(Prg.version());

        try {
            NLS.Reg.instance().load(null);
            initialize(args);
            this.display = new Display();
            this.shell = new Shell(this.display, SWT.BORDER | SWT.SHELL_TRIM | SWT.TITLE);
            this.shell.setText(PRODUCT_NAME);
            initLang();
        }
        catch (BaseLibException ble) {
            throw new ExitError(new Prg.Result(
                    Prg.Result.Code.INTERNAL_ERROR, ble.getMessage(), null));
        }

        this.display.addFilter(SWT.MouseMove, new Listener() {
            @Override
            public void handleEvent(Event event) {
                Point p = GUI.this.display.getCursorLocation();
                GUI.this.prg.addRandomSeed(p.x);
                GUI.this.prg.addRandomSeed(p.y);
                GUI.this.prg.addRandomSeed(System.currentTimeMillis());
                GUI.this.prg.addRandomSeed(System.nanoTime());
            }
        });

        this.shellProps = new ShellProps(
                this.shell,
                GUIProps.GUI_PFX,
                Prp.global(),
                new Point(-1, -1),
                false);

        this.shell.addShellListener(new ShellListener() {
            public void shellClosed(ShellEvent e) {
                cleanup();
            }
            public void shellActivated  (ShellEvent e) { }
            public void shellDeactivated(ShellEvent e) { }
            public void shellDeiconified(ShellEvent e) { }
            public void shellIconified  (ShellEvent e) { }
        });

        setProgramIcon();
        createShell();
        makeMenu();
        prepareDND();
        this.shell.layout();
        addTableColumns();
        addListeners();
        NLS.Reg.instance().addListener(this);
    }

    void prepareDND() {
        this.drop = new DropTarget(this.shell, DND.DROP_MOVE | DND.DROP_DEFAULT);
        this.drop.setTransfer(new Transfer[] { FileTransfer.getInstance() });
        this.drop.addDropListener(this.onDrop);
    }

    void addTooltip(Control ctrl, NLS.Str nlstr) {
        this.toolTips.add(ctrl, nlstr);
    }

    void createShell() {

        GridLayout gridLayout = new GridLayout(2, false);
        gridLayout.marginWidth = 0;
        gridLayout.marginHeight = 0;
        gridLayout.horizontalSpacing = 0;
        gridLayout.verticalSpacing = 0;
        this.shell.setLayout(gridLayout);

        this.left = new Composite(this.shell, SWT.NONE);
        GridData data = new GridData(SWT.FILL, SWT.FILL, true, true);
        this.left.setLayoutData(data);
        this.left.setBackground(this.display.getSystemColor(SWT.COLOR_WHITE));

        this.right = new Composite(this.shell, SWT.NONE);
        data = new GridData(SWT.BEGINNING, SWT.FILL, false, true);
        this.right.setLayoutData(data);
        this.right.setBackground(this.display.getSystemColor(SWT.COLOR_WHITE));

        gridLayout = new GridLayout();
        gridLayout.marginWidth  = 0;
        gridLayout.marginHeight = 0;
        gridLayout.marginLeft = 5;
        gridLayout.marginRight = 5;
        gridLayout.marginTop = 5;
        gridLayout.marginBottom = 5;
        gridLayout.horizontalSpacing = 0;
        this.left .setLayout(gridLayout);
        gridLayout = new GridLayout();
        gridLayout.marginWidth  = 0;
        gridLayout.marginHeight = 0;
        gridLayout.marginRight = 5;
        gridLayout.marginTop = 5;
        gridLayout.marginBottom = 5;
        gridLayout.horizontalSpacing = 0;
        this.right.setLayout(gridLayout);

        this.objects = new Table(this.left, SWT.BORDER   |
                                            SWT.H_SCROLL |
                                            SWT.V_SCROLL |
                                            SWT.VIRTUAL  |
                                            SWT.FULL_SELECTION);
        this.objects.setLinesVisible(true);
        this.objects.setHeaderVisible(true);
        this.objects.pack();
        this.objects.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        this.objects.addListener(SWT.SetData, this.onObjectsSetData);
        this.objects.addMouseListener(this.onObjectOpen);
        addTooltip(this.objects, NLS.GUI_TOOLTIP_OBJECTS);

        this.info = new Label(this.left, SWT.NONE);
        setInfo(NLS.GUI_INFO_WELCOME_1.fmt(PRODUCT_NAME), false);
        data = new GridData(SWT.FILL, SWT.CENTER, true, false);
        GC gc = new GC(this.info);
        FontMetrics fm = gc.getFontMetrics();
        int h = fm.getAscent() + fm.getDescent() + fm.getLeading();
        data.heightHint = INFO_LINES * h + h / 2;
        gc.dispose();
        this.info.setLayoutData(data);

        this.btnAddFiles = new Button(this.right, SWT.PUSH);
        this.btnAddFiles.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        this.btnAddFiles.addListener(SWT.Selection, this.onAddFiles);
        addTooltip(this.btnAddFiles, NLS.GUI_TOOLTIP_BTN_ADDFILES);

        this.btnAddFolder = new Button(this.right, SWT.PUSH);
        this.btnAddFolder.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        this.btnAddFolder.addListener(SWT.Selection, this.onAddFolder);
        addTooltip(this.btnAddFolder, NLS.GUI_TOOLTIP_BTN_ADDFOLDER);

        this.chkIncludeSubfolders = new Button(this.right, SWT.CHECK);
        this.chkIncludeSubfolders.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        this.chkIncludeSubfolders.setSelection(getPropertyFlag(Prg.Prop.RECURSIVE_SEARCH));
        addTooltip(this.chkIncludeSubfolders, NLS.GUI_TOOLTIP_CHK_INCLUDESUBFOLDERS);

        this.chkStoreFullPath = new Button(this.right, SWT.CHECK);
        this.chkStoreFullPath.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        this.chkStoreFullPath.setSelection(getPropertyFlag(Prg.Prop.STORE_FULL_PATH));
        addTooltip(this.chkStoreFullPath, NLS.GUI_TOOLTIP_CHK_STOREFULLPATH);

        this.cmbMerge = new Combo(this.right, SWT.SIMPLE | SWT.READ_ONLY | SWT.DROP_DOWN);
        this.cmbMerge.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        for (int i = 0; i < 3; i++) {
            this.cmbMerge.add("");
        }
        addTooltip(this.cmbMerge, NLS.GUI_TOOLTIP_CMB_MERGE);

        this.btnClear = new Button(this.right, SWT.PUSH);
        this.btnClear.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        this.btnClear.addListener(SWT.Selection, this.onClear);
        addTooltip(this.btnClear, NLS.GUI_TOOLTIP_BTN_CLEAR);

        this.spc1 = new Composite (this.right, SWT.NONE);
        this.spc1.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        this.lblFreeSpace = new Label(this.right, SWT.NONE);
        this.txtFreeSpace = new Text (this.right, SWT.BORDER | SWT.RIGHT);
        this.txtFreeSpace.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        this.txtFreeSpace.addFocusListener(this.onFreeSpaceListener);
        this.txtFreeSpace.setText(this.props.getProperty(GUIProps.PRG_FREESPACE.name(), Long.toString(0L)));
        this.txtFreeSpace.addListener(SWT.KeyUp, this.onFreeSpaceListener);
        addTooltip(this.lblFreeSpace, NLS.GUI_TOOLTIP_LBL_FREESPACE);

        this.lblLabel = new Label(this.right, SWT.NONE);
        this.txtLabel = new Text (this.right, SWT.BORDER);
        this.txtLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        this.txtLabel.setTextLimit(Prg.MAX_LABEL_LEN);
        this.txtLabel.setText(getPropertyString(Prg.Prop.LABEL));
        addTooltip(this.lblLabel, NLS.GUI_TOOLTIP_LBL_LABEL);

        this.spc2 = new Composite(this.right, SWT.NONE);
        this.spc2.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        this.chkWriteProtect = new Button(this.right, SWT.CHECK);
        this.chkWriteProtect.setLayoutData(new GridData (SWT.FILL, SWT.CENTER, true, false));
        this.chkWriteProtect.setSelection(getPropertyFlag(Prg.Prop.WRITEPROTECT));
        this.chkWriteProtect.setEnabled(false);
        addTooltip(this.chkWriteProtect, NLS.GUI_TOOLTIP_CHK_WRITEPROTECT);

        this.chkWipe = new Button(this.right, SWT.CHECK);
        this.chkWipe.setLayoutData(new GridData (SWT.FILL, SWT.CENTER, true, false));
        this.chkWipe.setSelection(GUIProps.PRG_WIPE.get(this.props));
        this.chkWipe.setEnabled(false);
        addTooltip(this.chkWipe, NLS.GUI_TOOLTIP_CHK_WIPE);

        this.chkForVeraCrypt = new Button(this.right, SWT.CHECK);
        this.chkForVeraCrypt.setLayoutData(new GridData (SWT.FILL, SWT.CENTER, true, false));
        this.chkForVeraCrypt.setSelection(getPropertyFlag(Prg.Prop.FOR_VERACRYPT));
        this.chkForVeraCrypt.setEnabled(false);
        addTooltip(this.chkForVeraCrypt, NLS.GUI_TOOLTIP_CHK_FORVERACRYPT);

        this.btnMake = new Button(this.right, SWT.PUSH);
        this.btnMake.setLayoutData(new GridData (SWT.FILL, SWT.CENTER, true, false));
        this.btnMake.addListener(SWT.Selection, this.onMake);
        this.btnMake.setEnabled(false);
        addTooltip(this.btnMake, NLS.GUI_TOOLTIP_BTN_MAKE);

        for (Control c : this.right.getChildren()) {
            c.setBackground(this.display.getSystemColor(SWT.COLOR_WHITE));
        }

        onLoadedShell();
    }

    void addTableColumns() {
        // TODO: subclass all of this...
        int w = GUIProps.COL_WIDTH_FILE.get(this.props);
        boolean def = -1 == w;
        int caw = this.objects.getClientArea().width;

        this.tcFile = new TableColumn(this.objects, SWT.NONE);
        w = def ? caw >> 1 : w;
        this.tcFile.setWidth(w);
        this.tcFile.setData(Prg.RegObj.Sort.NAME);

        int w2 = def ? caw >> 2 : GUIProps.COL_WIDTH_SIZE.get(this.props);
        this.tcSize = new TableColumn(this.objects, SWT.NONE | SWT.RIGHT);
        this.tcSize.setWidth(w2);

        w2 = def ? caw >> 2 : GUIProps.COL_WIDTH_DATE.get(this.props);
        this.tcDate = new TableColumn(this.objects, SWT.NONE);
        this.tcDate.setWidth(w2);
        this.tcDate.setData(Prg.RegObj.Sort.TIMESTAMP);

        this.tcSize.setData(Prg.RegObj.Sort.LENGTH);
        int sortIdx = GUIProps.COL_SORT_IDX.get(this.props);
        if (0 <= sortIdx && sortIdx < this.objects.getColumnCount()) {
            this.objects.setSortColumn(this.objects.getColumn(sortIdx));
            boolean sortAsc = GUIProps.COL_SORT_ASC.get(this.props);
            this.objects.setSortDirection(sortAsc ? SWT.UP : SWT.DOWN);
        }

        for (final TableColumn tc : this.objects.getColumns()) {
            tc.addListener(SWT.Selection, new Listener() {
                public void handleEvent(Event evt) {
                    sortObjects(tc, true);
                }
            });
        }

        onLoadedTableColumns();
    }

    void makeMenu() {
        Menu mn;

        this.mnMain = SWTUtil.getMainMenu(this.display, this.shell);

        this.mniFile = new MenuItem(this.mnMain, SWT.CASCADE);
        this.mniFile.setMenu(mn = new Menu(this.shell, SWT.DROP_DOWN));
        this.mniFileAddFiles = new MenuItem(mn, SWT.NONE);
        this.mniFileAddFiles.setAccelerator (SWT.MOD1 | 'F');
        this.mniFileAddFiles.addListener(SWT.Selection, this.onAddFiles);
        this.mniFileAddFolder = new MenuItem(mn, SWT.NONE);
        this.mniFileAddFolder.setAccelerator (SWT.MOD1 | 'D');
        this.mniFileAddFolder.addListener(SWT.Selection, this.onAddFolder);
        new MenuItem(mn, SWT.SEPARATOR);
        this.mniFileWipe = new MenuItem(mn, SWT.NONE);
        this.mniFileWipe.setAccelerator(SWT.MOD1 | 'W');
        this.mniFileWipe.addListener(SWT.Selection, this.onWipe);
        this.mniFileWipe.setEnabled(false);
        new MenuItem(mn, SWT.SEPARATOR);
        this.mniFileExtract = new MenuItem(mn, SWT.NONE);
        this.mniFileExtract.setAccelerator(SWT.MOD1 | 'E');
        this.mniFileExtract.addListener(SWT.Selection, this.onExtract);
        this.mniFileInvalidate = new MenuItem(mn, SWT.NONE);
        this.mniFileInvalidate.setAccelerator(SWT.MOD1 | 'I');
        this.mniFileInvalidate.addListener(SWT.Selection, this.onInvalidate);
        new MenuItem(mn, SWT.SEPARATOR);
        this.mniFileQuit = new MenuItem(mn, SWT.NONE);
        this.mniFileQuit.setAccelerator(SWT.MOD1 | (MiscUtils.underOSX() ? 'B' : 'Q'));
        this.mniFileQuit.addListener(SWT.Selection, this.onQuit);

        this.mniHelp = new MenuItem(this.mnMain, SWT.CASCADE);
        this.mniHelp.setMenu(mn = new Menu(this.shell, SWT.DROP_DOWN));
        this.mniHelpDocumentation = new MenuItem(mn, SWT.NONE);
        this.mniHelpDocumentation.setAccelerator(SWT.F1);
        this.mniHelpDocumentation.addListener(SWT.Selection, this.onDocumentation);
        this.mniHelpWebsite = new MenuItem(mn, SWT.NONE);
        this.mniHelpWebsite.addListener(SWT.Selection, this.onWebsite);
        new MenuItem(mn, SWT.SEPARATOR);
        this.mniHelpLang = new MenuItem(mn, SWT.NONE);
        this.mniHelpLang.addListener(SWT.Selection, this.onLanguage);
        new MenuItem(mn, SWT.SEPARATOR);
        this.mniHelpAbout = new MenuItem(mn, SWT.NONE);
        this.mniHelpAbout.addListener(SWT.Selection, this.onAbout);

        onLoadedMenu();
    }

    /** @see coderslagoon.baselib.util.NLS.Reg.Listener#onLoaded() */
    public void onLoaded() {
        onLoadedShell();
        onLoadedTableColumns();
        onLoadedMenu();
        this.shell.layout();
    }

    public void onLoadedMenu() {
        this.mniFile             .setText(NLS.GUI_MN_FILE.s());
        this.mniFileAddFiles     .setText(NLS.GUI_MN_FILE_ADDFILES.s());
        this.mniFileAddFolder    .setText(NLS.GUI_MN_FILE_ADDFOLDER.s());
        this.mniFileWipe         .setText(NLS.GUI_MN_FILE_WIPE.s());
        this.mniFileExtract      .setText(NLS.GUI_MN_FILE_EXTRACT.s());
        this.mniFileInvalidate   .setText(NLS.GUI_MN_FILE_INVALIDATE.s());
        this.mniFileQuit         .setText(NLS.GUI_MN_FILE_QUIT.s());
        this.mniHelp             .setText(NLS.GUI_MN_HELP.s());
        this.mniHelpDocumentation.setText(NLS.GUI_MN_HELP_DOCUMENTATION.s());
        this.mniHelpWebsite      .setText(NLS.GUI_MN_HELP_WEBSITE.s());
        this.mniHelpLang         .setText(NLS.GUI_MN_HELP_LANGUAGE.s());
        this.mniHelpAbout        .setText(NLS.GUI_MN_HELP_ABOUT_1.fmt(PRODUCT_NAME));
    }

    public void onLoadedShell() {

        this.btnAddFiles         .setText(NLS.GUI_BTN_ADDFILES.s());
        this.btnAddFolder        .setText(NLS.GUI_BTN_ADDFOLDER.s());
        this.chkIncludeSubfolders.setText(NLS.GUI_CHK_INCLUDESUBFOLDERS.s());
        this.chkStoreFullPath    .setText(NLS.GUI_CHK_STOREFULLPATH.s());
        this.btnClear            .setText(NLS.GUI_BTN_CLEAR.s());
        this.lblFreeSpace        .setText(NLS.GUI_LBL_FREESPACE.s());
        this.lblLabel            .setText(NLS.GUI_LBL_LABEL.s());
        this.chkWipe             .setText(NLS.GUI_CHK_WIPE.s());
        this.chkWriteProtect     .setText(NLS.GUI_CHK_WRITEPROTECT.s());
        this.chkForVeraCrypt     .setText(NLS.GUI_CHK_FORVERACRYPT.s());
        this.btnMake             .setText(NLS.GUI_BTN_MAKE.s());

        // FIXME: this needs to be repeated to avoid proper resizing of the
        //        right size, after the language has been changed - but there
        //        must be a better way, somehow ...
        for (int i = 0; i < 2; i++) {
            this.cmbMerge.setItem(0, NLS.GUI_CMB_MERGE_NO    .s());
            this.cmbMerge.setItem(1, NLS.GUI_CMB_MERGE_YES   .s());
            this.cmbMerge.setItem(2, NLS.GUI_CMB_MERGE_YES_CS.s());
            this.cmbMerge.select(getPropertyFlag(Prg.Prop.ALLOW_MERGE) ?
                                (getPropertyFlag(Prg.Prop.CASE_MERGE) ? 2 : 1) : 0);
            this.cmbMerge.pack(true);
        }
        this.right.pack(true);
        this.right.layout();
    }

    public void onLoadedTableColumns() {
        this.tcFile.setText(NLS.GUI_TC_FILE.s());
        this.tcSize.setText(NLS.GUI_TC_SIZE.s());
        this.tcDate.setText(NLS.GUI_TC_DATE.s());
    }

    void addListeners() {
        this.toolTips.shellListen(this.shell);
        NLS.Reg.instance().addListener(this.toolTips);
        this.display.addFilter(SWT.KeyDown, new Listener() {
            public void handleEvent(Event evt) {
                if ('d' == evt.keyCode               &&
                    (evt.stateMask & SWT.CTRL ) != 0 &&
                    (evt.stateMask & SWT.SHIFT) != 0) {
                    if (GUI.this.debug.get()) {
                        Log.reset();
                        GUI.this.debug.set(false);
                        GUI.this.setInfo(NLS.GUI_DEBUG_DISABLED.s(), false);
                    }
                    else {
                        Log.level(Log.Level.TRACE);
                        try {
                            File logFile = new File(
                                    System.getProperty("user.home"),
                                    "trupax.log");

                            FileOutputStream fos = new FileOutputStream(logFile, true);
                            Log.addPrinter(new PrintStream(fos));
                        }
                        catch (Exception e) {
                        }
                        GUI.this.debug.set(true);
                        GUI.this.setInfo(NLS.GUI_DEBUG_ENABLED.s(), false);
                    }
                }
            }
        });
    }

    ///////////////////////////////////////////////////////////////////////////

    @Override
    protected void addCmdLnOptions() {
    }

    void initialize(String[] args) throws ExitError {
        Prp.global().clear();

        File propFile = MiscUtils.determinePropFile(getClass(), Exe._propFileName, true);

        Prg.Setup setup = new Prg.Setup();
        setup.args             = processArgs(args);
        setup.fromCommandLine  = false;
        setup.propertiesFile   = propFile.getAbsolutePath();
        setup.saveProperties   = true;
        setup.propFileExists   = propFile.exists();
        setup.resetOnLoadError = true;
        setup.cb = new Prg.Setup.Callback() {
            @Override
            public void onProps() {
            }
            @Override
            public void onLoadErrorReset(Result err) {
                GUI.this.setupError = err;
            }
        };

        this.setupResult = PrgImpl.init();
        if (this.setupResult.isSuccess()) {
            this.prg = new PrgImpl();
            this.setupResult = this.prg.ctor(this.props, setup);
        }
        if (this.setupResult.isFailure()) {
            this.prg = null;
            PrgImpl.cleanup();
            throw new ExitError(this.setupResult);
        }

        this.prg.setProperty(new Prg.NamedString(Prg.Prop.TRIM_PATH      , Boolean.TRUE .toString()));
        this.prg.setProperty(new Prg.NamedString(Prg.Prop.SKIP_EMPTY_DIRS, Boolean.FALSE.toString()));

        // HACK: enable recursive search if nothing's been set yet
        if (null == this.prg.getProperty(Prg.Prop.RECURSIVE_SEARCH)) {
            this.prg.setProperty(new Prg.NamedString(Prg.Prop.RECURSIVE_SEARCH,
                                                     Boolean.TRUE.toString()));
        }

        this.dateFmt = new SimpleDateFormat(NLS.GUI_DATEFORMAT.s());
    }

    public void run() {
        this.shell.open();

        if (null != this.setupError) {
            MessageBox2.standard(this.shell,
                SWT.ICON_WARNING | SWT.OK,
                resultToMsgBoxText(this.setupError),
                NLS.GUI_MSGBOX_PROPERTIES_LOADERROR.s());
        }

        if (this.setupResult.code == Prg.Result.Code.GOT_OBJECTS) {
            this.registerObjects(new String[] {});
        }

        while (!this.shell.isDisposed()) {
            if (!this.display.readAndDispatch()) {
                this.display.sleep();
            }
        }

        if (!this.shell.isDisposed()) {
            this.shell.dispose();
        }
        if (!this.display.isDisposed()) {
            this.display.dispose();
        }
        System.exit(0);
    }

    void cleanup() {
        GUI.this.storeProperties(false, true);
        this.pwcache.clear();
        if (this.prg.dtor().isFailure()) {
            // TODO: msgbox??
        }
        this.prg = null;
        if (PrgImpl.cleanup().isFailure()) {
            // can be ignored (for now)
        }
        for (File fl : GUI.this.manualFiles.values()) {
            fl.delete();
        }
    }

    // TODO: some mechanism to store properties automatically on every user
    //       action, so we don't miss changes anywhere ...
    boolean storeProperties(boolean validate, boolean storeLayout) {
        if (storeLayout) {
            GUIProps.COL_WIDTH_FILE.set(this.props, this.tcFile.getWidth());
            GUIProps.COL_WIDTH_SIZE.set(this.props, this.tcSize.getWidth());
            GUIProps.COL_WIDTH_DATE.set(this.props, this.tcDate.getWidth());

            TableColumn tc = this.objects.getSortColumn();
            GUIProps.COL_SORT_IDX.set(this.props, null == tc ? -1 : SWTUtil.getTableColumnIndex(this.objects, tc));
            GUIProps.COL_SORT_ASC.set(this.props, null == tc ? true : SWT.UP == this.objects.getSortDirection());
        }

        setPropertyFlag(Prg.Prop.WRITEPROTECT    , this.chkWriteProtect     .getSelection());
        setPropertyFlag(Prg.Prop.FOR_VERACRYPT   , this.chkForVeraCrypt     .getSelection());
        setPropertyFlag(Prg.Prop.STORE_FULL_PATH , this.chkStoreFullPath    .getSelection());
        setPropertyFlag(Prg.Prop.RECURSIVE_SEARCH, this.chkIncludeSubfolders.getSelection());
        setPropertyFlag(Prg.Prop.ALLOW_MERGE     , 0 <  this.cmbMerge.getSelectionIndex());
        setPropertyFlag(Prg.Prop.CASE_MERGE      , 2 == this.cmbMerge.getSelectionIndex());

        Prg.NamedString ns;
        ns = new Prg.NamedString(Prg.Prop.LABEL, this.txtLabel.getText());
        this.prg.setProperty(ns);

        try {
            String freeSpace = this.txtFreeSpace.getText().trim();
            if (validate && !GUIProps.PRG_FREESPACE.validate(freeSpace)) {
                throw new Exception(NLS.GUI_PROPERR_INVALID_FREESPACE.s());
            }
            this.props.put(GUIProps.PRG_FREESPACE.name(), freeSpace);
        }
        catch (Exception e) {
            MessageBox2.standard(this.shell,
                SWT.ICON_ERROR | SWT.OK,
                e.getMessage(),
                NLS.GUI_MSGBOX_INVALID_OPTION.s());
            return false;
        }
        GUIProps.PRG_WIPE.set(this.props, this.chkWipe.getSelection());

        return true;
    }

    void setProgramIcon() {
        try {
            this.shell.setImage(new Image(this.display,
                getClass().getResourceAsStream("resources/icon256x256.png")));
        }
        catch (Exception e) {
            e.printStackTrace();
            System.err.println(e.getMessage());
            System.exit(5);
        }
    }

    ///////////////////////////////////////////////////////////////////////////

    Listener onAddFiles = new Listener() {
        public void handleEvent(Event evt) {
            if (!storeProperties(true, false)) {
                return;
            }
            FileDialog2 fd2 = new FileDialog2(GUI.this.shell, SWT.OPEN | SWT.MULTI, GUI.this.props, "addfiles");
            fd2.setText(NLS.GUI_DLG_ADDFILES.s());
            if (null == fd2.open()) {
                return;
            }
            registerObjects(fd2.getFilePaths());
        }
    };

    Listener onAddFolder = new Listener() {
        public void handleEvent(Event evt) {
            if (!storeProperties(true, false)) {
                return;
            }
            DirectoryDialog2 dd = new DirectoryDialog2(GUI.this.shell, SWT.NONE, GUI.this.props, "addfolder");
            dd.setText(NLS.GUI_DLG_ADDFOLDER.s());
            dd.setMessage(NLS.GUI_DLG_ADDFOLDER_INFO_1.fmt(
                    getPropertyFlag(Prg.Prop.RECURSIVE_SEARCH) ?
                            NLS.GUI_DLG_ADDFOLDER_INFO_GOSUB.s() : ""));
            String dir = dd.open();
            if (null == dir) {
                return;
            }
            registerObjects(new String[] { dir });
        }
    };

    Listener onClear = new Listener() {
        public void handleEvent(Event evt) {
            if (!storeProperties(true, false)) {
                return;
            }
            clear(true);
        }
    };
    void clear(boolean refresh) {
        GUI.this.prg.registerClear();
        this.cmbMerge.setEnabled(true);
        updateViews(refresh, refresh);
    }

    Listener onMake = new Listener() {
        public void handleEvent(Event evt) {
            if (storeProperties(true, false)) {
                updateViews(true, true);
            }
            else {
                return;
            }
            make();
        }
    };

    Listener onWipe = new Listener() {
        public void handleEvent(Event evt) {
            wipe();
        }
    };

    Listener onExtract = new Listener() {
        public void handleEvent(Event evt) {
            extract();
        }
    };

    Listener onInvalidate = new Listener() {
        public void handleEvent(Event evt) {
            invalidate();
        }
    };

    Listener onQuit = new Listener() {
        public void handleEvent(Event evt) {
            GUI.this.shell.close();
        }
    };

    Listener onDocumentation = new Listener() {
        public void handleEvent(Event evt) {
            String name = String.format("trupax_%s.html",
                coderslagoon.baselib.util.NLS.Reg.instance().id().toUpperCase());
            File fl = SWTUtil.openFileFromResource(
                getClass(),
                "resources/" + name,
                name,
                GUI.this.manualFiles.containsKey(name));
            if (null != fl) {
                GUI.this.manualFiles.put(name, fl);
            }
        }
    };

    Listener onWebsite = new Listener() {
        public void handleEvent(Event evt) {
            try {
                String pname = PRODUCT_NAME.toLowerCase();
                String url = PRODUCT_SITE + 
                    "?version=" + URLEncoder.encode(Prg.version(), "UTF-8") +
                    "&name=" + pname +
                    "&lang=" + coderslagoon.baselib.util.NLS.Reg.instance().id().toLowerCase() +
                    "#" + pname;
                if (Program.launch(url)) {
                    return;
                }
            }
            catch (Throwable ignored) {
            }
            MessageBox2.standard(GUI.this.shell,
                SWT.ICON_WARNING | SWT.OK,
                NLS.GUI_MSGBOX_WEBSITE_1.fmt(PRODUCT_SITE),
                PRODUCT_NAME);
        }
    };

    Listener onLanguage = new Listener() {
        public void handleEvent(Event evt) {
            storeProperties(false, false);
            String oldid = NLS.Reg.instance().id();
            LangDialog ldlg = new LangDialog(GUI.this.shell, GUI.this.props, LANGS, PRODUCT_NAME, false);
            ldlg.open();
            ldlg.waitForClose();
            String newid = ldlg.id();
            if (null != newid && !newid.equals(oldid)) {
                ExeProps.Lang.set(Prp.global(), ldlg.id());
                clear(false);
                setInfo(NLS.GUI_INFO_WELCOME_1.fmt(PRODUCT_NAME), false);
            }
        }
    };

    Listener onAbout = new Listener() {
        public void handleEvent(Event evt) {
            About dlg = new About(GUI.this.shell, GUI.this.props,
                NLS.ABOUT_CAPTION_1.fmt(Exe.PRODUCT_NAME),
                Exe.PRODUCT_NAME + " " + Prg.version(),
                NLS.ABOUT_INTRO.s(),
                String.format(NLS.ABOUT_COPYRIGHT_1.s(),
                    MiscUtils.copyrightYear(Exe.COPYRIGHT_START_YEAR, Calendar.getInstance())),
                    GUI.class.getResourceAsStream("resources/icon48x48.png"),
                    GUI.this.display.getSystemColor(SWT.COLOR_BLACK));
            dlg.open();
        }
    };

    DropTargetAdapter onDrop = new DropTargetAdapter() {
        public void drop(DropTargetEvent evt) {
            if (!this.fxfer.isSupportedType(evt.currentDataType)) {
                return;
            }
            final String[] objs = (String[])evt.data;
            Runnable run = null;
            if (null != objs && storeProperties(true, false)) {
                if (1 == objs.length) {
                    final String obj = objs[0];
                    String lobj = obj.toLowerCase(); 
                    if  ((lobj.endsWith(EXT_TC.toLowerCase()) ||
                          lobj.endsWith(EXT_HC.toLowerCase())) &&
                         new File(obj).exists()) {
                        MessageBox2 mb = new MessageBox2(
                            GUI.this.shell, GUI.this.props,
                            "dropmsgbox", SWT.ICON_QUESTION,
                            NLS.GUI_MSGBOX_DROP.s(),
                            NLS.GUI_MSGBOX_DROP_TITLE.s(),
                            new String[] {
                                NLS.GUI_MSGBOX_DROP_EXTRACT   .s(),
                                NLS.GUI_MSGBOX_DROP_INVALIDATE.s(),
                                NLS.GUI_MSGBOX_DROP_ADD       .s()
                            },
                            null, 0);
                        Integer sel = mb.show();
                        if (null == sel) {
                            return;
                        }
                        switch(sel) {
                            case 0: run = new Runnable() { public void run() { extract   (obj); }}; break;
                            case 1: run = new Runnable() { public void run() { invalidate(obj); }}; break;
                        }
                    }
                }
                if (null == run) {
                    run = new Runnable() { public void run() { registerObjects(objs); }};
                }
                GUI.this.display.asyncExec(run);
            }
        }
        public void dragOver(DropTargetEvent evt) {
            if (!this.fxfer.isSupportedType(evt.currentDataType)) {
                evt.detail = DND.DROP_NONE;
            }
        }
        FileTransfer fxfer = FileTransfer.getInstance();
    };

    Listener onObjectsSetData = new Listener() {
        public void handleEvent(Event e) {
            TableItem ti = (TableItem)e.item;

            int idx = GUI.this.objects.indexOf(ti);
            if (-1 == idx) {
                return;
            }

            Prg.RegObj ro = GUI.this.prg.registerView(idx, idx + 1)[0];

            ti.setData(ro);
            ti.setText(0, ro.name);
            ti.setText(1, ro.isDir() ? "" : String.format("%,d", ro.length));
            ti.setText(2, GUI.this.dateFmt.format(ro.timestamp));
        }
    };

    MouseListener onObjectOpen = new MouseListener() {
        public void mouseDoubleClick(MouseEvent me) {
            TableItem ti = GUI.this.objects.getItem(new Point(me.x, me.y));
            if (null == ti) {
                return;
            }
            Prg.RegObj ro = (Prg.RegObj)ti.getData();
            SWTUtil.shellExecute(ro.path);
        }
        public void mouseDown(MouseEvent ignored) { }
        public void mouseUp  (MouseEvent ignored) { }
    };

    class FreeSpaceListener implements FocusListener, Listener {
        String old;
        public void focusGained(FocusEvent fe) {
            this.old = txt();
        }
        public void focusLost(FocusEvent fe) {
            String txt = txt();
            if (null == this.old || this.old.equals(txt)) {
                return;
            }
            if (storeProperties(true, false)) {
                updateViews(true, true);
            }
            else {
                GUI.this.txtFreeSpace.setText(this.old);
            }
        }
        public void handleEvent(Event evt) {
            if (evt.character == '\r' && storeProperties(true, false)) {
                this.old = txt();
                updateViews(true, true);
            }
        }
        private String txt() {
            return GUI.this.txtFreeSpace.getText().trim().toLowerCase();
        }
    };
    FreeSpaceListener onFreeSpaceListener = new FreeSpaceListener();

    ///////////////////////////////////////////////////////////////////////////

    boolean getPropertyFlag(String name) {
        return Boolean.parseBoolean(this.prg.getProperty(name));
    }
    void setPropertyFlag(String name, boolean flag) {
        this.prg.setProperty(new Prg.NamedString(name, Boolean.toString(flag)));
    }
    String getPropertyString(String name) {
        String result = this.prg.getProperty(name);
        return null == result ? this.prg.getPropertyInfo(name).dflt : result;
    }

    ///////////////////////////////////////////////////////////////////////////

    void registerObjects(String[] objs) {
        final VarRef<Progress> pdlg = new VarRef<>();

        Prg.RegisterObjectsCallback rocb = new
        Prg.RegisterObjectsCallback() {
            public Prg.Result onDirectory(String dir) {
                if (null == pdlg.v) {
                    pdlg.v = new Progress(GUI.this.shell, GUI.this.props, false, GUI.this.toolTips);
                    pdlg.v.setText(NLS.GUI_PROGRESS_REGISTERING.s());
                    pdlg.v.open();
                }
                SWTUtil.processEvents(GUI.this.display);
                if (pdlg.v.canceled()) {
                    return Prg.Result.aborted();
                }
                pdlg.v.setObject(dir);

                return Prg.Result.ok();
            }
            public void configLocked() {
                GUI.this.cmbMerge.setEnabled(false);
            }
        };

        for (String obj : objs) {
            this.prg.addObject(obj);
        }

        Prg.Result res = this.prg.registerObjects(rocb);
        if (null != pdlg.v) {
            pdlg.v.close();
        }

        if (res.isFailure()) {
            if (res != Prg.Result.aborted()) {
                MessageBox2.standard(this.shell,
                    SWT.ICON_ERROR | SWT.OK,
                    resultToMsgBoxText(res),
                    NLS.GUI_MSGBOX_ERROR.s());
            }
        }

        this.prg.clearObjects();
        updateViews(true, true);
    }

    void updateViews(boolean resolve, boolean setInfo) {
        int rwc = this.prg.registerViewCount();
        
        boolean haveObjects = 0 < rwc;
        
        this.chkWriteProtect.setEnabled(haveObjects);
        this.chkWipe.setEnabled(haveObjects);
        this.objects.setItemCount(rwc);

        if (!sortObjects(null, false)) {
            GUI.this.objects.clearAll();
        }

        if (!setInfo) {
            return;
        }

        Prg.RegSum rsum = this.prg.registerSummary();

        Long vbytes = null;
        if (resolve) {
            long fspc = GUIProps.PRG_FREESPACE.get(this.props);
            this.prg.setFreeSpace(fspc);

           // _log.debugf("resolving (freespace set to %d) ...", fspc);

            Prg.Result res = this.prg.resolve();
            if (res.isFailure()) {
                MessageBox2.standard(this.shell,
                    SWT.ICON_ERROR | SWT.OK,
                    resultToMsgBoxText(res),
                    NLS.GUI_MSGBOX_ERROR.s());
            }
            else {
                vbytes = this.prg.volumeBytes();
            }
        }
        String vbs;
        if (null != vbytes) {
            vbs = NLS.GUI_VBYTES_2.fmt(
                MiscUtils.uszToStr(vbytes, true, false, 1).toUpperCase(),
                vbytes);
        }
        else {
            vbs = NLS.GUI_UNKNOWN_VBYTES.s();
        }

        setInfo(NLS.GUI_INFO_SUMMARY_4.fmt(
                rsum.numberOfFiles,
                rsum.numberOfDirectories,
                rsum.bytesTotal,
                vbs), false);

        this.btnMake.setEnabled(true);
        this.chkForVeraCrypt.setEnabled(true);
        this.mniFileWipe.setEnabled(0 < this.objects.getItemCount());
    }

    class ProgressController {
        double   onePct;
        double   lastPctx100 = -1.0;
        long     lastRefresh = Clock._system.now();
        Progress pdlg;

        final long REFRESH_INTVL_MILLIS = 500;
        final int PBAR_MAX = 10000;

        public ProgressController(Progress pdlg, long max) {
            this.onePct = max / 100.0;
            this.pdlg = pdlg;
            this.pdlg.setProgressMax(max);
        }

        public Result onProgress(long pos) {
            long now = Clock._system.now();
            long past = now - this.lastRefresh;
            if (0 > past || past > this.REFRESH_INTVL_MILLIS) {
                this.pdlg.setProgress(pos);
                double pctx100 = Math.rint((pos / this.onePct) * 100);
                if (pctx100 > this.lastPctx100) {
                    String inf = NLS.GUI_PROGRESS_INFO_1.fmt(pctx100 / 100.0);
                    this.pdlg.setInfo(inf);
                    this.lastPctx100 = pctx100;
                }
                SWTUtil.processEvents(GUI.this.display);
                this.lastRefresh = now;
            }
            return result();
        }

        public void onFile(String fileName, long fileSize) {
            if (null == fileName) {
                this.pdlg.setObject(NLS.GUI_PROGRESS_FREESPACE.s());
            }
            else {
                this.pdlg.setObject(NLS.GUI_PROGRESS_OBJECT_2.fmt(
                        fileName,
                        MiscUtils.uszToStr(fileSize, true, true, 1).toUpperCase()));
            }
            SWTUtil.processEvents(GUI.this.display);
        }

        public Result result() {
            return this.pdlg.canceled() ? Prg.Result.aborted() : Prg.Result.ok();
        }
    }

    void make() {
        boolean forVeraCrypt = getPropertyFlag(Prg.Prop.FOR_VERACRYPT);
        
        FileDialog2 fd = new FileDialog2(this.shell, SWT.SAVE, this.props, "volume");
        fd.setOverwrite(true);
        setFileDialogFilters(fd, forVeraCrypt);
        fd.setText(NLS.GUI_DLG_VOLUME.s());
        String fname = fd.open();
        if (null == fname)
            return;
        
        if (0 == fd.getFilterIndex()) {
            String ext = forVeraCrypt ? EXT_HC : EXT_TC;
            if (!fname.toLowerCase().endsWith(ext))
                fname += ext;
        }

        Password pw = new Password(this.shell, this.props, this.pwcache, true, this.toolTips);
        pw.open();
        pw.waitForClose();
        if (!pw.gotPassword())
            return;

        this.prg.setProperty(new Prg.NamedString(Prg.Prop.OVERWRITE, Boolean.TRUE.toString()));

        final VarRef<Progress> pdlg = new VarRef<>();

        pdlg.v = new Progress(this.shell, this.props, true, GUI.this.toolTips);
        pdlg.v.setText(NLS.GUI_PROGRESS_MAKING.s());
        pdlg.v.setCloseConfirmation(NLS.GUI_PROGRESS_CONFIRM_CANCEL.s());
        Prg.MakeCallback mcb = new Prg.MakeCallback() {
            public void onFile(String fileName, long fileSize) {
                this.pc.onFile(fileName, fileSize);
            }
            public Prg.Result onVolumeWrite(long pos) {
                if (null != SLOMO) try { Thread.sleep(SLOMO); }
                catch (InterruptedException ie) { return Prg.Result.aborted(); }

                return this.pc.onProgress(pos);
            }
            ProgressController pc = new
            ProgressController(pdlg.v, GUI.this.prg.volumeBytes());
            @Override
            public Result onFreeSpace() {
                this.pc.onFile(null, -1L);
                return Result.ok();
            }
        };
        pdlg.v.open();

        this.prg.setVolumeFile(fname);

        final long tm = Clock._system.now();

        Prg.Result res = this.prg.make(pw.detachPassword(), mcb);

        pdlg.v.setCloseConfirmation(null);
        pdlg.v.close();

        if (res.isFailure()) {
            if (res.code != Prg.Result.Code.ABORTED) {
                MessageBox2.standard(this.shell,
                    SWT.ICON_ERROR | SWT.OK,
                    resultToMsgBoxText(res),
                    NLS.GUI_MSGBOX_ERROR.s());
            }
            return;
        }

        if (this.chkWipe.getSelection() &&
            this.chkWipe.getEnabled()) {
            wipe2(tm);
        }
        else {
            setInfo(NLS.GUI_INFO_DONE_1.fmt(MiscUtils.printTime(
                    Clock._system.now() - tm - pdlg.v.pausedMillis())), true);
        }
    }

    void wipe() {
        if (SWT.YES != MessageBox2.standard(this.shell,
            SWT.ICON_WARNING | SWT.YES | SWT.NO,
            NLS.GUI_MSGBOX_WIPE.s(),
            NLS.GUI_MSGBOX_WIPE_TITLE.s())) {
            return;
        }
        wipe2(Clock._system.now());
    }

    void wipe2(long startTm) {
        setInfo(NLS.GUI_PROGRESS_WIPING.s(), false);

        final VarRef<Progress> pdlg = new VarRef<>();

        pdlg.v = new Progress(this.shell, this.props, true, GUI.this.toolTips);
        pdlg.v.setText(NLS.GUI_PROGRESS_WIPING.s());
        pdlg.v.setCloseConfirmation(NLS.GUI_PROGRESS_CONFIRM_CANCEL.s());
        final VarInt concerns = new VarInt();
        final long PCT_MUL = 1000L;
        Prg.WipeCallback wcb = new Prg.WipeCallback() {
            public void onFile(String fileName, long fileSize) {
                this.pc.onFile(fileName, fileSize);
            }
            public Result onProgress(double percent) {
                return this.pc.onProgress((long)(percent * PCT_MUL));
            }
            public Result onConcern(Concern concern, String message) {
                concerns.v++;
                return this.pc.result();
            }
            ProgressController pc = new
            ProgressController(pdlg.v, 100 * PCT_MUL);
        };
        pdlg.v.open();

        Prg.Result res = this.prg.wipe(wcb);

        pdlg.v.setCloseConfirmation(null);
        pdlg.v.close();

        if (res.isFailure()) {
            if (res.code != Prg.Result.Code.ABORTED) {
                MessageBox2.standard(this.shell,
                    SWT.ICON_ERROR | SWT.OK,
                    resultToMsgBoxText(res),
                    NLS.GUI_MSGBOX_ERROR.s());
            }
            concerns.v++;
        }

        String wstatus;
        if (0 == concerns.v) {
            wstatus = NLS.GUI_INFO_WIPED_ALL.s();
        }
        else {

            wstatus = NLS.GUI_INFO_WIPE_CONCERNS_1.fmt(concerns.v);
        }
        clear(false);
        this.btnMake.setEnabled(false);
        this.chkForVeraCrypt.setEnabled(false);
        this.mniFileWipe.setEnabled(false);

        setInfo(NLS.GUI_INFO_DONE_2.fmt(MiscUtils.printTime(
            Clock._system.now() - startTm - pdlg.v.pausedMillis()), wstatus),
            true);
    }

    ///////////////////////////////////////////////////////////////////////////

    void extract() {
        FileDialog2 fd = new FileDialog2(this.shell, SWT.OPEN, this.props, "extractvol");
        setFileDialogFilters(fd, null);
        fd.setText(NLS.GUI_DLG_EXTRACT.s());
        String fname = fd.open();
        if (null == fname) {
            return;
        }
        extract(fname);
    }

    void extract(String fname) {
        DirectoryDialog2 dd = new DirectoryDialog2(GUI.this.shell, SWT.NONE, GUI.this.props, "extractfolder");
        dd.setText(NLS.GUI_DLG_EXTRACTFOLDER.s());
        dd.setMessage(NLS.GUI_DLG_EXTRACTFOLDER_INFO.s());
        String dir = dd.open();
        if (null == dir) {
            return;
        }

        Password pw = new Password(this.shell, this.props, this.pwcache, false, this.toolTips);
        pw.open();
        pw.waitForClose();
        if (!pw.gotPassword()) {
            return;
        }
        char[] passw = pw.detachPassword();

        PrgProps.NBool overwrite = new PrgProps.Overwrite();
        boolean overwriteBak = overwrite.get(this.props);
        overwrite.set(this.props, false);

        final Progress pdlg = new Progress(this.shell, this.props, true, GUI.this.toolTips);
        pdlg.setText(NLS.GUI_PROGRESS_EXTRACTING.s());
        pdlg.setCloseConfirmation(NLS.GUI_PROGRESS_CONFIRM_CANCEL.s());
        pdlg.open();

        Prg.Result res;
        try {
            final VarLong tm = new VarLong(Clock._system.now());
            if ((res = this.prg.setVolumeFile(fname)).isSuccess() &&
                (res = this.prg.extract(passw, dir, new Prg.ExtractCallback() {
                int fcount;
                boolean overwriteAll;
                boolean skipAll;
                long nextRefresh = Clock._system.now();
                public void onFile(String fileName, long fileSize) {
                    pdlg.setObject(NLS.GUI_PROGRESS_OBJECT_2.fmt(
                        fileName,
                        MiscUtils.uszToStr(fileSize, true, true, 1).toUpperCase()));
                    pdlg.setProgress(++this.fcount);
                    SWTUtil.processEvents(GUI.this.display);
                }
                public Result onConcern(Concern concern, String message) {
                    if (pdlg.canceled()) return Result.aborted();
                    if (Concern.SKIP == concern) {
                        return Result.ok();
                    }
                    else if (Concern.EXISTS == concern) {
                        if (this.overwriteAll) {
                            return Result.ok();
                        }
                        if (this.skipAll) {
                            return new Result(Result.Code.IGNORE, null, null);
                        }
                        MessageBox2 mb = new MessageBox2(
                            GUI.this.shell, GUI.this.props,
                            "extractmsgbox", SWT.ICON_WARNING,
                            message,
                            NLS.GUI_MSGBOX_EXTRACT_TITLE.s(),
                            new String[] {
                                NLS.GUI_MSGBOX_EXTRACT_OVERWRITE.s(),
                                NLS.GUI_MSGBOX_EXTRACT_SKIP     .s(),
                                NLS.GUI_MSGBOX_EXTRACT_ABORT    .s()
                            },
                            NLS.GUI_MSGBOX_EXTRACT_ALL.s(), 1);
                        long tm2 = Clock._system.now();
                        Integer res = mb.show();
                        tm.v += Clock._system.now() - tm2;
                        if (null == res) {
                            return Result.aborted();
                        }
                        switch(res) {
                            case 0: {
                                this.overwriteAll = mb.all();
                                return Result.ok();
                            }
                            case 1: {
                                this.skipAll = mb.all();
                                return new Result(Result.Code.IGNORE, null, null);
                            }
                        }
                    }
                    return Result.aborted();
                }
                public Result onOpening(int objs) {
                    if (pdlg.canceled()) return Result.aborted();
                    pdlg.setObject(NLS.GUI_PROGRESS_OPENING_1.fmt(objs));
                    return Result.ok();
                }
                public Result onOpen(int files, int dirs) {
                    if (pdlg.canceled()) return Result.aborted();
                    pdlg.setProgressMax(files);
                    return Result.ok();
                }
                public Result onFileWrite(long pos) {
                    refresh();
                    if (pdlg.canceled()) return Result.aborted();
                    if (null != SLOMO) try { Thread.sleep(SLOMO); }
                    catch (InterruptedException ignored) { return Result.aborted(); }
                    return Result.ok();
                }
                private void refresh() {
                    final int REFRESH_INTVL_MILLIS = 250;
                    long now = Clock._system.now();
                    if (now > this.nextRefresh) {
                        SWTUtil.processEvents(GUI.this.display);
                        this.nextRefresh = now + REFRESH_INTVL_MILLIS;
                    }
                }
            })).isSuccess()) {
                setInfo(NLS.GUI_INFO_DONE_1.fmt(MiscUtils.printTime(
                    Clock._system.now() - tm.v - pdlg.pausedMillis())), true);
                return;
            }
        }
        finally {
            Arrays.fill(passw, '\0');
            pdlg.setCloseConfirmation(null);
            pdlg.close();
            this.prg.setVolumeFile(null);
            overwrite.set(this.props, overwriteBak);
        }
        if (res.code != Prg.Result.Code.ABORTED) {
            MessageBox2.standard(this.shell,
                SWT.ICON_ERROR | SWT.OK,
                resultToMsgBoxText(res),
                NLS.GUI_MSGBOX_ERROR.s());
        }
    }

    ///////////////////////////////////////////////////////////////////////////

    static void setFileDialogFilters(FileDialog2 fd, Boolean veraCrypt) {
        if (null == veraCrypt) {
            fd.setFilterExtensions(new String[] { 
                    "*" + EXT_HC + ";*" + EXT_TC, 
                    "*.*" }); 
            fd.setFilterNames(new String[] {
                NLS.GUI_DLG_FILE_FILTER_XC .s(),
                NLS.GUI_DLG_FILE_FILTER_ALL.s() });
        }
        else if (veraCrypt) {
            fd.setFilterExtensions(new String[] {  "*" + EXT_HC, "*.*" }); 
            fd.setFilterNames(new String[] {
                NLS.GUI_DLG_FILE_FILTER_HC .s(),
                NLS.GUI_DLG_FILE_FILTER_ALL.s() });
        }
        else {
            fd.setFilterExtensions(new String[] { "*" + EXT_TC, "*.*" }); 
            fd.setFilterNames(new String[] {
                NLS.GUI_DLG_FILE_FILTER_TC .s(),
                NLS.GUI_DLG_FILE_FILTER_ALL.s() });
        }
    }
    
    void invalidate() {
        FileDialog2 fd = new FileDialog2(this.shell, SWT.OPEN, this.props, "invalidvol");
        setFileDialogFilters(fd, null);
        fd.setText(NLS.GUI_DLG_INVALIDATE.s());
        String fname = fd.open();
        if (null == fname) {
            return;
        }
        invalidate(fname);
    }

    void invalidate(String fname) {
        MessageBox2 mb = new MessageBox2(
                GUI.this.shell, GUI.this.props,
                "invalidmsgbox", SWT.ICON_WARNING,
                NLS.GUI_MSGBOX_INVALIDATE_1.fmt(fname),
                NLS.GUI_MSGBOX_INVALIDATE_TITLE.s(),
                new String[] {
                    NLS.GUI_MSGBOX_INVALIDATE_YES   .s(),
                    NLS.GUI_MSGBOX_INVALIDATE_YESDEL.s(),
                    NLS.GUI_MSGBOX_INVALIDATE_ABORT .s()
                },
                null, 2);

        Integer mbres = mb.show();
        if (null == mbres || 2 == mbres) {
            return;
        }
        PrgProps.NBool deleteAfter = new PrgProps.DeleteAfter();
        deleteAfter.set(this.props, 1 == mbres);

        final int PCTMUL = 1000;
        final Progress pdlg = new Progress(this.shell, this.props, true, GUI.this.toolTips);
        pdlg.setText(NLS.GUI_PROGRESS_INVALIDATING.s());
        pdlg.setObject(fname);
        pdlg.setProgressMax(100 * PCTMUL);
        pdlg.setCloseConfirmation(NLS.GUI_PROGRESS_CONFIRM_CANCEL.s());
        pdlg.open();

        this.prg.setVolumeFile(fname);
        long tm = Clock._system.now();
        Prg.Result res = GUI.this.prg.invalidate(new Prg.ProgressCallback() {
            public Result onProgress(double percent) {
                SWTUtil.processEvents(GUI.this.display);
                if (pdlg.canceled()) {
                    return Result.aborted();
                }
                pdlg.setProgress((long)(percent * PCTMUL));
                return Result.ok();
            }
        });
        pdlg.setCloseConfirmation(null);
        pdlg.close();

        if (res.isFailure()) {
            if (res.code != Prg.Result.Code.ABORTED) {
                MessageBox2.standard(this.shell,
                    SWT.ICON_ERROR | SWT.OK,
                    resultToMsgBoxText(res),
                    NLS.GUI_MSGBOX_ERROR.s());
            }
        }
        else {
            setInfo(NLS.GUI_INFO_DONE_1.fmt(MiscUtils.printTime(
                    Clock._system.now() - tm - pdlg.pausedMillis())), true);
        }
    }

    ///////////////////////////////////////////////////////////////////////////

    final static int DETAILS_LIMIT = 64;

    String resultToMsgBoxText(Prg.Result res) {
        StringBuilder result = new StringBuilder();
        result.append(NLS.GUI_MSGBOX_ERROR_TEXT_3.fmt(res.msg, res.code.value, res.code));
        if (null != res.details) {
            String details = res.details;
            if (!this.debug.get()) {
                details = MiscUtils.limitString(details, DETAILS_LIMIT, "...");
            }
            result.append(NLS.GUI_MSGBOX_ERROR_TEXT_DETAILS_1.fmt(details));
        }
        return result.toString();
    }

    void setInfo(String raw, boolean reversed) {
        Color c0 = new Color(this.display,  32,  32,  32);
        Color c1 = new Color(this.display, 224, 224, 224);
        Color c2 = this.display.getSystemColor(SWT.COLOR_WHITE);

        this.info.setText(MiscUtils.modifyLines(
                raw,
                INFO_LINES,
                new MiscUtils.LineModifier() {
                    public String modify(String rawLn) {
                        return ' ' + rawLn.trim();
                    }
                }));

        this.info.setBackground(reversed ? c1 : c0);
        this.info.setForeground(reversed ? c0 : c2);
    }

    boolean sortObjects(TableColumn tc, boolean init) {
        if (null == tc) {
            tc = GUI.this.objects.getSortColumn();
        }
        if (null == tc) {
            if (!init) {
                return false;
            }
            GUI.this.objects.setSortDirection(SWT.UP);
        }
        else {
            GUI.this.objects.setSortDirection(
            GUI.this.objects.getSortDirection() == SWT.UP ? SWT.DOWN : SWT.UP);
        }
        GUI.this.objects.setSortColumn(tc);
        GUI.this.prg.registerViewSort(
                (Prg.RegObj.Sort)tc.getData(),
                GUI.this.objects.getSortDirection() == SWT.UP);
        GUI.this.objects.clearAll();
        return true;
    }

    ///////////////////////////////////////////////////////////////////////////

    public static void main(String[] args) {
        try {
            GUI gui = new GUI(args);
            gui.run();
        }
        catch (ExitError ee) {
            System.exit(ee.result.code.ordinal());
        }
        catch (Throwable uncaught) {
            MiscUtils.dumpUncaughtError(uncaught, DEF_PROP_FILE_NAME);
        }
        System.exit(0);
    }
}
