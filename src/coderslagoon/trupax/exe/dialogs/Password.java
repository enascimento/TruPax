package coderslagoon.trupax.exe.dialogs;

import java.security.DigestException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Properties;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import coderslagoon.baselib.swt.dialogs.Dialog;
import coderslagoon.baselib.swt.util.SWTUtil;
import coderslagoon.baselib.swt.util.ToolTips;
import coderslagoon.baselib.util.MiscUtils;
import coderslagoon.baselib.util.Prp;
import coderslagoon.trupax.exe.GUI;
import coderslagoon.trupax.exe.GUIProps;
import coderslagoon.trupax.exe.NLS;
import coderslagoon.trupax.exe.util.PasswordCache;

public class Password extends Dialog {
    final boolean confirm;

    Label  lblInfo;
    Text   txtPassword;
    Text   txtPassword2;
    Button btnProceed;
    Button btnCancel;
    Button chkShowPassword;
    Button chkCachePassword;

    final PasswordCache pwcache;
    final Color[] defaultColors = new Color[2];

    public Password(Shell parent, Properties props, PasswordCache pwcache,
                    boolean confirm, ToolTips toolTips) {
        super(parent, props,
              "password." + (confirm ? "confirm" : "noconfirm"),
              SWT.DIALOG_TRIM | SWT.RESIZE);

        this.pwcache = pwcache;
        this.setText(NLS.PASSWORD_CAPTION.s());
        this.confirm = confirm;

        createControls(toolTips);

        setImage(parent.getImage());
        setMinimumSize(computeSize(SWT.DEFAULT, SWT.DEFAULT));
    }

    void createControls(ToolTips toolTips) {
        GridLayout gridLayout = new GridLayout(1, false);
        setLayout(gridLayout);

        if (this.confirm) {
            this.lblInfo = new Label(this, SWT.NONE);
            this.lblInfo.setText(this.confirm ? NLS.PASSWORD_INFO2.s() : NLS.PASSWORD_INFO.s());
            this.lblInfo.setLayoutData(new GridData (SWT.FILL, SWT.CENTER, true, false));
        }

        this.txtPassword = new Text(this, SWT.BORDER);
        this.txtPassword.setLayoutData(new GridData (SWT.FILL, SWT.CENTER, true, false));
        this.txtPassword.addModifyListener(this.onPasswordChange);

        this.defaultColors[1] = this.txtPassword.getForeground();
        this.defaultColors[0] = this.txtPassword.getBackground();

        if (this.confirm) {
            this.txtPassword2 = new Text(this, SWT.BORDER);
            this.txtPassword2.setLayoutData(new GridData (SWT.FILL, SWT.CENTER, true, false));
            this.txtPassword2.addModifyListener(this.onPasswordChange);
            toolTips.add(this.lblInfo, NLS.PASSWORD_TOOLTIP_TXT_PASSWORD);
        }

        gridLayout = new GridLayout(1, false);
        gridLayout.marginHeight = 0;
        gridLayout.marginWidth  = 0;

        GridLayout gridLayout2 = new GridLayout(2, false);
        gridLayout2.marginHeight = 0;
        gridLayout2.marginWidth  = 0;

        Composite ctrls = new Composite(this, SWT.NONE);
        ctrls.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        ctrls.setLayout(gridLayout2);

        Composite lctrls = new Composite(ctrls, SWT.NONE);
        lctrls.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        lctrls.setLayout(gridLayout);

        Composite rctrls = new Composite(ctrls, SWT.NONE);
        rctrls.setLayoutData(new GridData(SWT.FILL, SWT.FILL, false, true));
        rctrls.setLayout(gridLayout2);

        this.btnCancel = new Button(rctrls, SWT.PUSH);
        this.btnCancel.setText(NLS.PASSWORD_BTN_CANCEL.s());
        this.btnCancel.setLayoutData(new GridData(SWT.FILL, SWT.END, true, true));
        this.btnCancel.addListener(SWT.Selection, this.onCancel);
        this.btnCancel.pack();
        SWTUtil.adjustButtonSize(this.btnCancel, GUI.BTN_ADJ_FCT);

        this.btnProceed = new Button(rctrls, SWT.PUSH);
        this.btnProceed.setText(NLS.PASSWORD_BTN_PROCEED.s());
        this.btnProceed.setLayoutData(new GridData(SWT.FILL, SWT.END, true, true));
        this.btnProceed.addListener(SWT.Selection, this.onProceed);
        this.btnProceed.pack();
        toolTips.add(this.btnProceed, NLS.PASSWORD_TOOLTIP_BTN_PROCEED);
        SWTUtil.adjustButtonSize(this.btnProceed, GUI.BTN_ADJ_FCT);

        this.chkShowPassword = new Button(lctrls, SWT.CHECK);
        this.chkShowPassword.setText(NLS.PASSWORD_CHK_SHOWPASSWORD.s());
        this.chkShowPassword.setLayoutData(new GridData(SWT.FILL, SWT.BEGINNING, true, true));
        this.chkShowPassword.addListener(SWT.Selection, this.onShowPassword);
        this.chkShowPassword.pack();
        this.chkShowPassword.setSelection(GUIProps.OPT_SHOWPASSWORD.get());
        toolTips.add(this.chkShowPassword, NLS.PASSWORD_TOOLTIP_CHK_SHOWPASSWORD);
        this.onShowPassword.handleEvent(null);

        boolean cpflag = GUIProps.OPT_CACHEPASSWORD.get();

        this.chkCachePassword = new Button(lctrls, SWT.CHECK);
        this.chkCachePassword.setText(NLS.PASSWORD_CHK_CACHEPASSWORD.s());
        this.chkCachePassword.setLayoutData(new GridData(SWT.FILL, SWT.BEGINNING, true, true));
        this.chkCachePassword.addListener(SWT.Selection, this.onShowPassword);
        this.chkCachePassword.pack();
        this.chkCachePassword.setSelection(cpflag);
        toolTips.add(this.chkCachePassword, NLS.PASSWORD_TOOLTIP_CHK_CACHEPASSWORD);
        this.onShowPassword.handleEvent(null);

        if (cpflag) {
            String lastPassw = this.pwcache.get();
            if (null != lastPassw) {
                this.txtPassword.setText(lastPassw);
                colorPassword(this.txtPassword);
                if (null != this.txtPassword2) {
                    this.txtPassword2.setText(lastPassw);
                    colorPassword(this.txtPassword2);
                }
            }
        }

        toolTips.shellListen(this);
        ctrls.pack();

        setDefaultButton(this.btnProceed);
    }

    ///////////////////////////////////////////////////////////////////////////

    char[] password;

    void verify() {
        boolean proceed = true;
        if (this.confirm) {
            if (!this.txtPassword .getText().equals(
                 this.txtPassword2.getText())) {
                proceed = false;
            }
        }
        if (proceed) {
            proceed = 0 < this.txtPassword.getText().length();
        }
        this.btnProceed.setEnabled(proceed);
    }

    public boolean gotPassword() {
        return null != this.password;
    }

    public char[] detachPassword() {
        char[] result = this.password;
        this.password = null;
        return result;
    }

    public void clearPassword(boolean reflect) {
        if (null != this.password) {
            Arrays.fill(this.password, '\0');
            this.password = null;
        }
        if (reflect) {
            this.txtPassword.setText("");
            if (this.confirm) {
                this.txtPassword2.setText("");
            }
        }
        verify();
    }

    ///////////////////////////////////////////////////////////////////////////

    @Override
    public void open() {
        clearPassword(false);
        super.open();
    }

    ///////////////////////////////////////////////////////////////////////////

    Listener onCancel = new Listener() {
        public void handleEvent(Event evt) {
            close();
        }
    };

    Listener onProceed = new Listener() {
        public void handleEvent(Event evt) {
            Password.this.password =
            Password.this.txtPassword.getText().toCharArray();
            storeState();
            close();
        }
    };

    void storeState() {
        boolean cpflag = this.chkCachePassword.getSelection();
        GUIProps.OPT_SHOWPASSWORD .set(Prp.global(), this.chkShowPassword.getSelection());
        GUIProps.OPT_CACHEPASSWORD.set(Prp.global(), cpflag);
        if (cpflag) {
            this.pwcache.set(new String(this.password));
        }
        else {
            this.pwcache.clear();
        }
    }

    Listener onShowPassword = new Listener() {
        public void handleEvent(Event evt) {
            char c = Password.this.chkShowPassword.getSelection() ? '\0' : '*';
            Password.this.txtPassword.setEchoChar(c);
            if (null != Password.this.txtPassword2) {
                Password.this.txtPassword2.setEchoChar(c);
            }
        }
    };
    
    ModifyListener onPasswordChange = new ModifyListener() {
        public void modifyText(ModifyEvent e) {
            colorPassword((Text)e.widget);
        }
    };
    
    void colorPassword(Text txt) {
        Color[] colors = computeColors(txt.getText());
        txt.setBackground(colors[0]);
        txt.setForeground(colors[1]);
        if (MiscUtils.underOSX()) {
           txt.setVisible(false);
           txt.setVisible(true);
           txt.setFocus();
        }
        verify();
    }

    ///////////////////////////////////////////////////////////////////////////

    final static int COMPUTE_COLOR_LOOPS = 1000;

    Color[] computeColors(String passw) {
        if (0 == passw.length() ||
            !GUIProps.OPT_COLORPASSWORD.get()) {
            return this.defaultColors;
        }

        byte[] digest = new byte[0];
        byte[] pbytes = passw.getBytes();
        try {
            MessageDigest md;
            try {
                md = MessageDigest.getInstance("MD5");
            }
            catch (NoSuchAlgorithmException nsae) {
                return this.defaultColors;
            }

            digest = new byte[] {
                0x72,0x65,(byte)0x8E,0x76,0x78,0x52,(byte)0xF1,0x66,(byte)0x9F,
                0x01,(byte)0xAD,0x5E,(byte)0xC0,0x10,(byte)0xE0,0x2C };

            for (int i = 0; i < COMPUTE_COLOR_LOOPS; i++) {
                md.update(digest);
                md.update(pbytes);
                try {
                    md.digest(digest, 0, digest.length);
                }
                catch (DigestException de) {
                    return this.defaultColors;
                }
                finally {
                    md.reset();
                }
            }

            int r = digest[0] & 0x0ff;
            int g = digest[1] & 0x0ff;
            int b = digest[2] & 0x0ff;

            Color[] result = new Color[2];
            result[0] = new Color(this.getDisplay(), r, g, b);
            result[1] = r+g*2+b>512 ? new Color(this.getDisplay(),   0,   0,   0) :
                                      new Color(this.getDisplay(), 255, 255, 255);
            return result;
        }
        finally {
            Arrays.fill(pbytes, (byte)0);
            Arrays.fill(digest, (byte)0);
        }
    }
}
