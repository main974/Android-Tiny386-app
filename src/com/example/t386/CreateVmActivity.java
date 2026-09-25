package com.example.t386;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

public class CreateVmActivity extends Activity {
    private static final String[] MEMS  = {"4M","8M","16M","32M","64M"};
    private static final String[] CPUS  = {"386","486","586"};
    private static final int[]    GENS  = {3,4,5};
    private static final String[] SIZES = {"640\u00d7480","720\u00d7480","800\u00d7600"};
    private static final int[][]  WH    = {{640,480},{720,480},{800,600}};
    private static final String[] BOOTS = {"\u8f6f\u76d8","\u786c\u76d8","\u5149\u9a71"};
    private static final String NONE = "\u672a\u9009\u62e9";

    private int PAD, R, CH;
    private int memIdx = 1, cpuIdx = 1, sizeIdx = 1, bootIdx = 0;
    private Button[] mB = new Button[MEMS.length], cB = new Button[CPUS.length],
                     sB = new Button[SIZES.length], bB = new Button[BOOTS.length];
    private EditText nameEd;
    private int editIdx = -1;
    private VM editVm = null;
    private TextView fdaT, hdaT, cdaT;

    private String cur(TextView t) {
        String s = t.getText().toString();
        return (s.equals(NONE) || s.length() == 0) ? "" : s;
    }

    private void setIdx(int kind, int v) {
        if (kind == 0) memIdx = v;
        else if (kind == 1) cpuIdx = v;
        else if (kind == 2) sizeIdx = v;
        else bootIdx = v;
    }

    private void refreshChips() {
        Ui.repaintChips(mB, memIdx, this, CH);
        Ui.repaintChips(cB, cpuIdx, this, CH);
        Ui.repaintChips(sB, sizeIdx, this, CH);
        Ui.repaintChips(bB, bootIdx, this, CH);
    }

    private LinearLayout chipRow(String[] labels, Button[] arr, final int[] holder, final int kind) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < labels.length; i++) {
            final int ii = i;
            final Button b = Ui.chip(this, labels[i], i == holder[0], CH);
            b.setTag(Integer.valueOf(i));
            b.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    setIdx(kind, ii);
                    refreshChips();
                }
            });
            arr[i] = b;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
            lp.rightMargin = (i < labels.length - 1) ? Ui.dp(this, 6) : 0;
            r.addView(b, lp);
        }
        return r;
    }

    private LinearLayout group(String title) {
        LinearLayout c = Ui.card(this, Ui.CARD_A, Ui.CARD_B, R, Ui.dp(this, 1));
        int p = Ui.dp(this, 16);
        c.setPadding(p, p, p, p);
        c.addView(Ui.text(this, title, 12f, 0xff8fa2c9));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = Ui.dp(this, 14);
        c.setLayoutParams(lp);
        return c;
    }

    private void askPerm() {
        if (Build.VERSION.SDK_INT >= 23) {
            if (checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{
                        android.Manifest.permission.READ_EXTERNAL_STORAGE,
                        android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
            }
        }
    }

    private void browse(final File dir, final String[] exts, final TextView target) {
        File[] fs = dir.listFiles();
        final List<File> all = new ArrayList<File>();
        final List<String> names = new ArrayList<String>();
        if (dir.getParentFile() != null) { all.add(dir.getParentFile()); names.add("\u2B06  \u4e0a\u4e00\u7ea7"); }
        if (fs != null) {
            for (int i = 0; i < fs.length; i++)
                if (fs[i].isDirectory()) { all.add(fs[i]); names.add("\uD83D\uDCC1  " + fs[i].getName()); }
            for (int i = 0; i < fs.length; i++) {
                File f = fs[i];
                if (f.isDirectory()) continue;
                String n = f.getName().toLowerCase();
                for (int k = 0; k < exts.length; k++)
                    if (n.endsWith(exts[k])) { all.add(f); names.add("\uD83D\uDCBE  " + f.getName()); break; }
            }
        }
        if (all.isEmpty()) { Toast.makeText(this, "\u76ee\u5f55\u91cc\u6ca1\u6709\u53ef\u9009\u6587\u4ef6", Toast.LENGTH_SHORT).show(); return; }
        new AlertDialog.Builder(this)
            .setTitle(dir.getAbsolutePath())
            .setItems(names.toArray(new String[0]), new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) {
                    File f = all.get(w);
                    if (f.isDirectory()) browse(f, exts, target);
                    else target.setText(f.getAbsolutePath());
                }
            })
            .setNegativeButton("\u53d6\u6d88", null).show();
    }

    private void pick(final TextView target, final String[] exts) {
        askPerm();
        File s = new File(Environment.getExternalStorageDirectory(), "Download");
        if (!s.isDirectory()) s = Environment.getExternalStorageDirectory();
        browse(s, exts, target);
    }

    /** 把镜像从 /sdcard 拷贝到 App 私有目录 —— 那里没有 FUSE，读得快得多 */
    private void importImage(final String src, final TextView target) {
        final java.io.File dir = new java.io.File(getFilesDir(), "images");
        dir.mkdirs();
        String name = src.substring(src.lastIndexOf('/') + 1);
        final java.io.File out = new java.io.File(dir, name);
        if (out.exists() && out.length() > 0) {
            target.setText(out.getAbsolutePath());
            Toast.makeText(this, "已在内部存储中，直接使用", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, "\u6b63\u5728\u5bfc\u5165 " + name + " ...", Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            public void run() {
                try {
                    java.io.FileInputStream in = new java.io.FileInputStream(src);
                    java.io.FileOutputStream o = new java.io.FileOutputStream(out);
                    byte[] buf = new byte[1 << 20];
                    int k; long total = 0;
                    while ((k = in.read(buf)) > 0) { o.write(buf, 0, k); total += k; }
                    o.close(); in.close();
                    final String path = out.getAbsolutePath();
                    final long sz = total;
                    runOnUiThread(new Runnable() {
                        public void run() {
                            target.setText(path);
                            Toast.makeText(CreateVmActivity.this,
                                "\u2705 \u5dfc\u5165\u5b8c\u6210\uff0c\u4e0d\u518d\u8d70 FUSE",
                                Toast.LENGTH_SHORT).show();
                        }
                    });
                } catch (Throwable t) {
                    runOnUiThread(new Runnable() {
                        public void run() {
                            Toast.makeText(CreateVmActivity.this, "\u5bfc\u5165\u5931\u8d25", Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        }).start();
    }

    private void createDisk(final int mb, final TextView target) {
        try {
            File f = new File(getFilesDir(), "disk_" + System.currentTimeMillis() + ".img");
            RandomAccessFile raf = new RandomAccessFile(f, "rw");
            raf.setLength((long) mb * 1024 * 1024);
            raf.close();
            target.setText(f.getAbsolutePath());
            Toast.makeText(this, "\u5df2\u65b0\u5efa " + mb + "MB \u865a\u62df\u786c\u76d8", Toast.LENGTH_SHORT).show();
        } catch (Throwable t) {
            Toast.makeText(this, "\u521b\u5efa\u5931\u8d25: " + t, Toast.LENGTH_LONG).show();
        }
    }

    private void devRow(LinearLayout card, String icon, String title, final String[] exts,
                        final TextView tv, boolean canCreate) {
        card.addView(Ui.label(this, icon + " " + title));
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        Button p = Ui.action(this, "\u9009\u62e9\u6587\u4ef6\u2026", 0xff334155, 0xff1e293b, Ui.dp(this, 12));
        p.setTextSize(12f);
        p.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { pick(tv, exts); }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, canCreate ? 1.4f : 1f);
        lp.rightMargin = Ui.dp(this, 6);
        r.addView(p, lp);
        if (canCreate) {
            Button c1 = Ui.action(this, "32M", 0xff334155, 0xff1e293b, Ui.dp(this, 12));
            c1.setTextSize(12f);
            c1.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { createDisk(32, tv); }
            });
            r.addView(c1, new LinearLayout.LayoutParams(0, -2, 1f));
            Button c2 = Ui.action(this, "256M", 0xff334155, 0xff1e293b, Ui.dp(this, 12));
            c2.setTextSize(12f);
            c2.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { createDisk(256, tv); }
            });
            LinearLayout.LayoutParams l2 = new LinearLayout.LayoutParams(0, -2, 1f);
            l2.leftMargin = Ui.dp(this, 6);
            r.addView(c2, l2);
        }
        Button imp = Ui.action(this, "\u2b07 \u5bfc\u5165", 0xff3f2d6b, 0xff2a1f4a, Ui.dp(this, 12));
        imp.setTextSize(12f);
        imp.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                String cur = tv.getText().toString();
                if (cur.equals(NONE) || cur.length() == 0) {
                    Toast.makeText(CreateVmActivity.this,
                        "\u5148\u70b9\u300c\u9009\u62e9\u6587\u4ef6\u2026\u300d\u9009\u4e00\u4e2a\u955c\u50cf",
                        Toast.LENGTH_LONG).show();
                    return;
                }
                importImage(cur, tv);
            }
        });
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(0, -2, 1f);
        ilp.leftMargin = Ui.dp(this, 6);
        r.addView(imp, ilp);
        card.addView(r);
        tv.setText(NONE);
        tv.setTextColor(0xff7f8ca8);
        tv.setTextSize(10.5f);
        tv.setPadding(Ui.dp(this, 2), Ui.dp(this, 7), 0, 0);
        card.addView(tv);
    }

    @Override protected void onCreate(Bundle st) {
        super.onCreate(st);
        askPerm();
        PAD = Ui.dp(this, 18); R = Ui.dp(this, 20); CH = Ui.dp(this, 14);

        /* 编辑模式：读入已有的虚拟机并预填 */
        editIdx = getIntent().getIntExtra("edit", -1);
        if (editIdx >= 0) {
            List<VM> all0 = VM.load(this);
            if (editIdx < all0.size()) {
                editVm = all0.get(editIdx);
                for (int i = 0; i < MEMS.length; i++)
                    if (Integer.parseInt(MEMS[i].replace("M", "")) == editVm.mem) memIdx = i;
                for (int i = 0; i < GENS.length; i++) if (GENS[i] == editVm.gen) cpuIdx = i;
                for (int i = 0; i < WH.length; i++)
                    if (WH[i][0] == editVm.w && WH[i][1] == editVm.h) sizeIdx = i;
                bootIdx = editVm.boot;
            } else { editIdx = -1; }
        }

        ScrollView sv = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(Ui.gradV(Ui.BG_A, Ui.BG_B, 0));
        root.setPadding(PAD, Ui.dp(this, 42), PAD, Ui.dp(this, 24));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        Button back = new Button(this);
        back.setText("\u2190");
        back.setTextSize(18);
        back.setAllCaps(false);
        back.setBackground(Ui.grad(0xff1a2136, 0xff1a2136, Ui.dp(this, 12)));
        back.setTextColor(Ui.INK);
        back.setPadding(0, 0, 0, 0);
        back.setMinWidth(Ui.dp(this, 42)); back.setMinimumWidth(Ui.dp(this, 42));
        back.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { finish(); } });
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(Ui.dp(this, 42), Ui.dp(this, 42));
        blp.rightMargin = Ui.dp(this, 12);
        head.addView(back, blp);
        GText t = new GText(this);
        t.setText(editIdx >= 0 ? "\u4fee\u6539\u865a\u62df\u673a" : "\u521b\u5efa\u865a\u62df\u673a");
        t.setTextSize(22);
        head.addView(t);
        root.addView(head);

        /* ===== 卡1：基本配置 ===== */
        LinearLayout c1 = group("\u57fa\u672c\u914d\u7f6e");
        c1.addView(Ui.label(this, "\u540d\u79f0"));
        nameEd = new EditText(this);
        nameEd.setText("DOS");
        nameEd.setTextColor(Ui.INK);
        nameEd.setTextSize(14f);
        nameEd.setBackground(Ui.grad(0xff121828, 0xff121828, Ui.dp(this, 12)));
        nameEd.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
        c1.addView(nameEd);
        c1.addView(Ui.label(this, "\u5185\u5b58"));
        c1.addView(chipRow(MEMS, mB, new int[]{memIdx}, 0));
        c1.addView(Ui.label(this, "CPU"));
        c1.addView(chipRow(CPUS, cB, new int[]{cpuIdx}, 1));
        c1.addView(Ui.label(this, "\u663e\u793a\u5206\u8fa8\u7387"));
        c1.addView(chipRow(SIZES, sB, new int[]{sizeIdx}, 2));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-1, -2);
        clp.bottomMargin = Ui.dp(this, 14);
        root.addView(c1, clp);

        /* ===== 卡2：引导与设备 ===== */
        LinearLayout c2 = group("\u5f15\u5bfc\u4e0e\u8bbe\u5907");
        c2.addView(Ui.label(this, "\u7b2c\u4e00\u5f15\u5bfc"));
        c2.addView(chipRow(BOOTS, bB, new int[]{bootIdx}, 3));
        fdaT = new TextView(this); hdaT = new TextView(this); cdaT = new TextView(this);
        devRow(c2, "\uD83D\uDCBE", "\u8f6f\u76d8\u955c\u50cf  .img .ima .flp", new String[]{".img",".ima",".flp"}, fdaT, false);
        devRow(c2, "\uD83D\uDCBD", "\u786c\u76d8\u955c\u50cf  .img .raw .vhd", new String[]{".img",".raw",".vhd",".qcow2"}, hdaT, true);
        devRow(c2, "\uD83D\uDCC0", "\u5149\u9a71\u955c\u50cf  .iso .img", new String[]{".iso",".img"}, cdaT, false);
        root.addView(c2, clp);

        Button ok = Ui.action(this, editIdx >= 0 ? "\u2714   \u4fdd\u5b58\u4fee\u6539" : "\u2714   \u521b\u5efa\u5e76\u4fdd\u5b58", Ui.GREEN, Ui.GREEN2, R);
        ok.setTextSize(16f);
        LinearLayout.LayoutParams op = new LinearLayout.LayoutParams(-1, -2);
        op.topMargin = Ui.dp(this, 10);
        ok.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                String nm = nameEd.getText().toString().trim();
                if (nm.length() == 0) nm = "\u672a\u547d\u540d";
                String a = cur(fdaT), b = cur(hdaT), c = cur(cdaT);
                if (a.length() == 0 && b.length() == 0 && c.length() == 0) {
                    Toast.makeText(CreateVmActivity.this,
                        "\u81f3\u5c11\u9009\u4e00\u4e2a\u5f15\u5bfc\u8bbe\u5907", Toast.LENGTH_LONG).show();
                    return;
                }
                VM vm = new VM();
                vm.name = nm;
                vm.mem  = Integer.parseInt(MEMS[memIdx].replace("M",""));
                vm.gen  = GENS[cpuIdx];
                vm.w    = WH[sizeIdx][0];
                vm.h    = WH[sizeIdx][1];
                vm.boot = bootIdx;
                vm.fda = a; vm.hda = b; vm.cda = c;
                List<VM> all = VM.load(CreateVmActivity.this);
                if (editIdx >= 0 && editIdx < all.size()) {
                    vm.builtin = all.get(editIdx).builtin;
                    all.set(editIdx, vm);
                } else {
                    all.add(vm);
                }
                VM.save(CreateVmActivity.this, all);
                Toast.makeText(CreateVmActivity.this, "\u5df2\u521b\u5efa: " + vm.name, Toast.LENGTH_SHORT).show();
                finish();
            }
        });
        root.addView(ok, op);

        sv.addView(root);
        setContentView(sv);

        if (editVm != null) {
            nameEd.setText(editVm.name);
            if (editVm.fda.length() > 0) fdaT.setText(editVm.fda);
            if (editVm.hda.length() > 0) hdaT.setText(editVm.hda);
            if (editVm.cda.length() > 0) cdaT.setText(editVm.cda);
        }
        refreshChips();
    }
}
