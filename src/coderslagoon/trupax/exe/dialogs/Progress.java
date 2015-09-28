package coderslagoon.trupax.exe.dialogs;

import java.util.Properties;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.ProgressBar;
import org.eclipse.swt.widgets.Shell;

import coderslagoon.baselib.swt.dialogs.Dialog;
import coderslagoon.baselib.swt.dialogs.MessageBox2;
import coderslagoon.baselib.swt.util.SWTUtil;
import coderslagoon.baselib.swt.util.ToolTips;
import coderslagoon.baselib.util.Clock;
import coderslagoon.trupax.exe.Exe;
import coderslagoon.trupax.exe.GUI;
import coderslagoon.trupax.exe.NLS;

public class Progress extends Dialog {
    Label       lblObject;
    Label       lblInfo;
    ProgressBar pbar;
    Button      btnCancel;
    String      closeConfirmation;
    boolean     canceled;
    long        pausedMillis;

    public Progress(Shell parent, Properties props, boolean bar, ToolTips toolTips) {
        super(parent, props,
              "progress." + (bar ? "bar" : "nobar"),
              SWT.DIALOG_TRIM | SWT.RESIZE | SWT.BORDER);

        addListener(SWT.Close, new ClosingListener());

        GridLayout gl = new GridLayout(1, false);
        setLayout(gl);

        this.lblObject = new Label(this, SWT.NONE);
        this.lblObject.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        SWTUtil.adjustFontSize(this.lblObject, 1);

        if (bar) {
            this.pbar = new ProgressBar(this, SWT.NONE);
            this.pbar.setMinimum(0);
            this.pbar.setMaximum(PROGRESS_MAX);
            this.pbar.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        }

        Composite bottom = new Composite(this, SWT.NONE);
        bottom.setLayoutData(new GridData (SWT.FILL, SWT.FILL, true, true));
        gl = new GridLayout(2, false);
        gl.marginWidth = 0;
        gl.marginHeight = 0;
        bottom.setLayout(gl);

        this.lblInfo = new Label(bottom, SWT.WRAP);
        this.lblInfo.setLayoutData(new GridData (SWT.FILL, SWT.FILL, true, true));

        this.btnCancel = new Button(bottom, SWT.PUSH);
        this.btnCancel.setText(NLS.PROGRESS_BTN_CANCEL.s());
        this.btnCancel.setLayoutData(new GridData(SWT.BEGINNING, SWT.END, false, false));
        this.btnCancel.addListener(SWT.Selection, this.onCancel);
        this.btnCancel.pack();
        toolTips.add(this.btnCancel, NLS.PROGRESS_TOOLTIP_BTN_CANCEL);
        SWTUtil.adjustButtonSize(this.btnCancel, GUI.BTN_ADJ_FCT);

        setImage(parent.getImage());
        setMinimumSize(computeSize(SWT.DEFAULT, SWT.DEFAULT));

        toolTips.shellListen(this);
    }

    ///////////////////////////////////////////////////////////////////////////

    public void setCaption(String caption) {
        if (this.isDisposed()) {
            return;
        }
        setText(NLS.PROGRESS_CAPTION_2.fmt(Exe.PRODUCT_NAME, caption));
    }

    public void setInfo(String info) {
        if (this.isDisposed()) {
            return;
        }
        this.lblInfo.setText(info);
    }

    public void setObject(String obj) {
        if (this.isDisposed()) {
            return;
        }
        this.lblObject.setText(obj);
    }

    public void setCloseConfirmation(String text) {
        this.closeConfirmation = text;
    }

    public boolean canceled() {
        return this.canceled;
    }

    public long pausedMillis() {
        return this.pausedMillis;
    }

    ///////////////////////////////////////////////////////////////////////////

    final static int PROGRESS_MAX = 1000 * 1000;

    public void setProgressMax(long max) {
        this.punit = (double)max / (double)PROGRESS_MAX;
        this.lastPBarSel = -1;
    }
    double punit;

    public void setProgress(long pos) {
        if (this.isDisposed() || null == this.pbar) {
            return;
        }
        double dpos = pos;
        dpos /= this.punit;
        int pbarSel = (int)dpos;
        if (this.lastPBarSel != pbarSel) { // avoids widget thrashing
            this.pbar.setSelection(this.lastPBarSel = pbarSel);
        }
    }
    int lastPBarSel = -1;

    ///////////////////////////////////////////////////////////////////////////

    Listener onCancel = new Listener() {
        public void handleEvent(Event evt) {
            Progress.this.close();
        }
    };

    ///////////////////////////////////////////////////////////////////////////

    class ClosingListener implements Listener {
        public void handleEvent(Event evt) {
            if (null != Progress.this.closeConfirmation) {
                long tm = Clock._system.now();

                evt.doit = SWT.YES == MessageBox2.standard(Progress.this,
                    SWT.ICON_QUESTION | SWT.YES | SWT.NO,
                    Progress.this.closeConfirmation,
                    NLS.PROGRESS_MSGBOX_CONFIRM.s());

                Progress.this.pausedMillis += Clock._system.now() - tm;
            }
            else {
                evt.doit = true;
            }
            if (evt.doit) {
                Progress.this.canceled = true;
            }
        }
    }
}
