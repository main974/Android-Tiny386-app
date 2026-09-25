package com.example.t386;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Bundle;
import android.widget.Button;
import android.view.KeyEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.ByteBuffer;

public class MainActivity extends Activity {
    private static native int nativeInit(String iniPath);
    private static native int nativeWidth();
    private static native int nativeHeight();
    private static native boolean nativeTakeDirty();
    private static native ByteBuffer nativeFb();
    private static native void nativeKey(int scan, boolean down);
    private static native String nativeStats();
    private static native void nativeStop();

    private Bitmap bmp;
    private ByteBuffer bb;
    private TextView st;
    private Scr scr;
    private int frames = 0;
    private static File LOG;

    static void log(String s) {
        try {
            if (LOG == null) return;
            FileOutputStream o = new FileOutputStream(LOG, true);
            o.write((System.currentTimeMillis() % 100000 + "  " + s + "\n").getBytes());
            o.close();
        } catch (Throwable t) {}
    }

    private class Scr extends View {
        private final Paint p = new Paint();
        Scr(android.content.Context c) { super(c); }
        @Override protected void onDraw(Canvas cv) {
            cv.drawColor(0xff101010);
            if (bmp != null && bb != null) {
                try {
                    if (nativeTakeDirty()) { bb.rewind(); bmp.copyPixelsFromBuffer(bb); }
                    float s = Math.min((float) getWidth() / bmp.getWidth(), (float) getHeight() / bmp.getHeight());
                    int dw = (int) (bmp.getWidth() * s), dh = (int) (bmp.getHeight() * s);
                    int l = (getWidth() - dw) / 2, t = (getHeight() - dh) / 2;
                    cv.drawBitmap(bmp, null, new Rect(l, t, l + dw, t + dh), p);
                } catch (Throwable t) { log("onDraw: " + t); }
            }
            if (bmp != null && (++frames % 30) == 0) {
                final String s2 = nativeStats();
                post(new Runnable() { public void run() { st.setText(s2); } });
            }
            invalidate();
        }
    }

    private File asset(String name) throws Exception {
        File f = new File(getFilesDir(), name);
        int vc = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
        File stamp = new File(getFilesDir(), name + ".v" + vc);
        if (f.exists() && f.length() > 0 && stamp.exists()) { log("asset cached: " + name); return f; }
        InputStream in = getAssets().open(name);
        FileOutputStream out = new FileOutputStream(f);
        byte[] buf = new byte[65536];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        out.close(); in.close();
        log("asset extracted: " + name + " " + f.length() + "B");
        try { stamp.createNewFile(); } catch (Throwable t) {}
        return f;
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        File dir = getExternalFilesDir(null);
        if (dir == null) dir = getFilesDir();
        LOG = new File(dir, "t386.log");
        log("=== onCreate ===");

        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            public void uncaughtException(Thread t, Throwable e) {
                StringWriter sw = new StringWriter();
                e.printStackTrace(new PrintWriter(sw));
                log("!!! UNCAUGHT on " + t.getName() + ": " + sw);
                android.os.Process.killProcess(android.os.Process.myPid());
            }
        });

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xff000000);

        scr = new Scr(this);
        root.addView(scr, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        st = new TextView(this);
        st.setTextSize(11);
        st.setTextColor(0xffc9d1d9);
        st.setText("启动中…");
        st.setPadding(12, 0, 0, 0);
        bar.addView(st, new LinearLayout.LayoutParams(0, -2, 1f));
        Button kbb = new Button(this);
        kbb.setText("⌨ 键盘");
        kbb.setTextSize(12);
        bar.addView(kbb, new LinearLayout.LayoutParams(-2, -2));

        Button pwr = new Button(this);
        pwr.setText("\u23FB");
        pwr.setTextSize(14);
        pwr.setAllCaps(false);
        pwr.setBackground(Ui.pressable(0xff7f1d1d, 0xff7f1d1d, 0xffdc2626, 0xffdc2626, 10));
        pwr.setTextColor(0xffffffff);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(-2, -2);
        plp.leftMargin = Ui.dp(this, 6);
        pwr.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { finish(); }
        });
        bar.addView(pwr, plp);
        root.addView(bar);

        final LinearLayout kpad = buildKeyboard();
        kpad.setVisibility(View.GONE);
        root.addView(kpad);
        kbb.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                kpad.setVisibility(kpad.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
            }
        });
        setContentView(root);

        new Thread(new Runnable() { public void run() {
            String msg;
            try {
                log("step1: loadLibrary");
                System.loadLibrary("tiny386");
                log("step2: loadLibrary ok");
                File bios = asset("bios.bin");
                File vga  = asset("vgabios.bin");
                File disk = asset("disk.img");
                File ini  = new File(getFilesDir(), "tiny.ini");
                String MEM = getIntent().getStringExtra("mem");
                if (MEM == null) MEM = "8M";
                int GEN = getIntent().getIntExtra("gen", 4);
                int DW  = getIntent().getIntExtra("w", 720);
                int DH  = getIntent().getIntExtra("h", 480);
                String FDA = getIntent().getStringExtra("fda"); if (FDA == null) FDA = "";
                String HDA = getIntent().getStringExtra("hda"); if (HDA == null) HDA = "";
                String CDA = getIntent().getStringExtra("cda"); if (CDA == null) CDA = "";
                int BOOT = getIntent().getIntExtra("boot", 0);
                String s = "[pc]\nbios = " + bios.getAbsolutePath() + "\n"
                    + "vga_bios = " + vga.getAbsolutePath() + "\n"
                    + "mem_size = " + MEM + "\nvga_mem_size = 256K\n"
                    + (FDA.length() > 0 ? "fda = " + FDA + "\n"
                       : (HDA.length() == 0 && CDA.length() == 0 ? "fda = " + disk.getAbsolutePath() + "\n" : ""))
                    /* 槽位决定 SeaBIOS 的启动优先顺序（它按 disk@0 → disk@1 找）
                       boot=2（光驱优先）→ 光盘占槽位0，硬盘挪到槽位1
                       否则           → 硬盘占槽位0，光盘挪到槽位1
                       注意：hda 和 cda 是同一个槽位，不能共存 */
                    + (HDA.length() > 0 ? ((BOOT == 2 && CDA.length() > 0 ? "hdb" : "hda") + " = " + HDA + "\n") : "")
                    + (CDA.length() > 0 ? ((HDA.length() > 0 && BOOT != 2 ? "cdb" : "cda") + " = " + CDA + "\n") : "")
                    + "fill_cmos = 1\n"
                    + "\n[display]\nwidth = " + DW + "\nheight = " + DH + "\n"
                    + "\n[cpu]\ngen = " + GEN + "\nfpu = 0\n";
                FileOutputStream o = new FileOutputStream(ini);
                o.write(s.getBytes()); o.close();
                log("step3: ini written");
                int rc = nativeInit(ini.getAbsolutePath());
                log("step4: nativeInit rc=" + rc);
                if (rc != 0) { msg = "nativeInit 失败 rc=" + rc; }
                else {
                    bmp = Bitmap.createBitmap(nativeWidth(), nativeHeight(), Bitmap.Config.ARGB_8888);
                    bmp.setHasAlpha(false);
                    bb = nativeFb();
                    msg = "运行中 " + nativeWidth() + "x" + nativeHeight() + " | 日志: " + LOG.getAbsolutePath();
                }
            } catch (Throwable t) {
                StringWriter sw = new StringWriter();
                t.printStackTrace(new PrintWriter(sw));
                log("!!! 异常: " + sw);
                msg = "异常: " + t;
            }
            final String m = msg;
            runOnUiThread(new Runnable() { public void run() { st.setText(m); } });
        }}).start();
    }

    private Button mkey(String label, final int code) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(13);
        b.setAllCaps(false);
        b.setPadding(0, 0, 0, 0);
        b.setMinHeight(0); b.setMinimumHeight(0);
        b.setBackground(Ui.pressable(0xff2b3557, 0xff1b2237, 0xff4b5c8a, 0xff3a4770, 10));
        b.setTextColor(0xffdbe3f5);
        b.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                nativeKey(code, true);
                nativeKey(code, false);
            }
        });
        return b;
    }

    private LinearLayout krow(int[] codes, String[] labels) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < codes.length; i++)
            r.addView(mkey(labels[i], codes[i]), new LinearLayout.LayoutParams(0, Ui.dp(this, 56), 1f));
        return r;
    }

    private LinearLayout buildKeyboard() {
        LinearLayout k = new LinearLayout(this);
        k.setOrientation(LinearLayout.VERTICAL);
        k.setBackground(Ui.gradV(0xff161d33, 0xff0e1322, 0));
        k.addView(krow(new int[]{0x01,0x02,0x03,0x04,0x05,0x06,0x07,0x08,0x09,0x0a,0x0b,0x0e},
                       new String[]{"Esc","1","2","3","4","5","6","7","8","9","0","\u232b"}));
        k.addView(krow(new int[]{0x0f,0x10,0x11,0x12,0x13,0x14,0x15,0x16,0x17,0x18,0x19},
                       new String[]{"Tab","q","w","e","r","t","y","u","i","o","p"}));
        k.addView(krow(new int[]{0x1d,0x1e,0x1f,0x20,0x21,0x22,0x23,0x24,0x25,0x26,0x1c},
                       new String[]{"Ctrl","a","s","d","f","g","h","j","k","l","\u23ce"}));
        k.addView(krow(new int[]{0x2a,0x2c,0x2d,0x2e,0x2f,0x30,0x31,0x32,0xE048,0xE050,0xE04B,0xE04D},
                       new String[]{"\u21e7","z","x","c","v","b","n","m","\u2191","\u2193","\u2190","\u2192"}));
        k.addView(krow(new int[]{0x39,0x3b,0x3c,0x3d,0x3e,0x3f,0x40,0x41,0x42,0x43,0x44},
                       new String[]{"\u7a7a\u683c","F1","F2","F3","F4","F5","F6","F7","F8","F9","F10"}));
        return k;
    }

    private static int scan(int kc) {
        switch (kc) {
            case KeyEvent.KEYCODE_ESCAPE: return 0x01;
            case KeyEvent.KEYCODE_1: return 0x02; case KeyEvent.KEYCODE_2: return 0x03;
            case KeyEvent.KEYCODE_3: return 0x04; case KeyEvent.KEYCODE_4: return 0x05;
            case KeyEvent.KEYCODE_5: return 0x06; case KeyEvent.KEYCODE_6: return 0x07;
            case KeyEvent.KEYCODE_7: return 0x08; case KeyEvent.KEYCODE_8: return 0x09;
            case KeyEvent.KEYCODE_9: return 0x0a; case KeyEvent.KEYCODE_0: return 0x0b;
            case KeyEvent.KEYCODE_DEL: return 0x0e;
            case KeyEvent.KEYCODE_TAB: return 0x0f;
            case KeyEvent.KEYCODE_Q: return 0x10; case KeyEvent.KEYCODE_W: return 0x11;
            case KeyEvent.KEYCODE_E: return 0x12; case KeyEvent.KEYCODE_R: return 0x13;
            case KeyEvent.KEYCODE_T: return 0x14; case KeyEvent.KEYCODE_Y: return 0x15;
            case KeyEvent.KEYCODE_U: return 0x16; case KeyEvent.KEYCODE_I: return 0x17;
            case KeyEvent.KEYCODE_O: return 0x18; case KeyEvent.KEYCODE_P: return 0x19;
            case KeyEvent.KEYCODE_A: return 0x1e; case KeyEvent.KEYCODE_S: return 0x1f;
            case KeyEvent.KEYCODE_D: return 0x20; case KeyEvent.KEYCODE_F: return 0x21;
            case KeyEvent.KEYCODE_G: return 0x22; case KeyEvent.KEYCODE_H: return 0x23;
            case KeyEvent.KEYCODE_J: return 0x24; case KeyEvent.KEYCODE_K: return 0x25;
            case KeyEvent.KEYCODE_L: return 0x26;
            case KeyEvent.KEYCODE_ENTER: return 0x1c;
            case KeyEvent.KEYCODE_Z: return 0x2c; case KeyEvent.KEYCODE_X: return 0x2d;
            case KeyEvent.KEYCODE_C: return 0x2e; case KeyEvent.KEYCODE_V: return 0x2f;
            case KeyEvent.KEYCODE_B: return 0x30; case KeyEvent.KEYCODE_N: return 0x31;
            case KeyEvent.KEYCODE_M: return 0x32;
            case KeyEvent.KEYCODE_SPACE: return 0x39;
            case KeyEvent.KEYCODE_SHIFT_LEFT: case KeyEvent.KEYCODE_SHIFT_RIGHT: return 0x2a;
            default: return -1;
        }
    }

    @Override protected void onDestroy() {
        try { nativeStop(); } catch (Throwable t) { }
        super.onDestroy();
    }

    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        int sc = scan(e.getKeyCode());
        if (sc > 0) { nativeKey(sc, e.getAction() == KeyEvent.ACTION_DOWN); return true; }
        return super.dispatchKeyEvent(e);
    }
}
