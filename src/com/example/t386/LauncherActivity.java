package com.example.t386;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.List;

public class LauncherActivity extends Activity {
    private List<VM> vms;
    private LinearLayout listBox;
    private int sel = -1;
    private int PAD, R;

    private String ver() {
        try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionName; }
        catch (Throwable t) { return "?"; }
    }
    static String shortName(String p) {
        int i = p.lastIndexOf('/');
        return i < 0 ? p : p.substring(i + 1);
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        PAD = Ui.dp(this, 18);
        R = Ui.dp(this, 18);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(Ui.gradV(Ui.BG_A, Ui.BG_B, 0));
        root.setPadding(PAD, Ui.dp(this, 44), PAD, Ui.dp(this, 20));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);

        TextView logo = Ui.text(this, "\uD83D\uDDA5", 26, Ui.INK);
        head.addView(logo);

        LinearLayout ht = new LinearLayout(this);
        ht.setOrientation(LinearLayout.VERTICAL);
        ht.setPadding(Ui.dp(this, 10), 0, 0, 0);
        GText title = new GText(this);
        title.setText("Tiny386");
        title.setTextSize(28);
        ht.addView(title);
        ht.addView(Ui.text(this, "x86 \u865a\u62df\u673a\u7ba1\u7406\u5668  \u00b7  v" + ver(), 11.5f, Ui.MUTED));
        head.addView(ht);
        root.addView(head);

        LinearLayout btns = new LinearLayout(this);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        Button mk = Ui.action(this, "\uFF0B  \u521b\u5efa\u865a\u62df\u673a", Ui.BLUE, Ui.PURPLE, R);
        mk.setTextSize(14f);
        mk.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startActivity(new Intent(LauncherActivity.this, CreateVmActivity.class));
            }
        });
        LinearLayout.LayoutParams m1 = new LinearLayout.LayoutParams(0, -2, 1f);
        m1.rightMargin = Ui.dp(this, 6);
        btns.addView(mk, m1);

        Button go = Ui.action(this, "\u25B6  \u542f\u52a8\u9009\u4e2d", Ui.GREEN, Ui.GREEN2, R);
        go.setTextSize(14f);
        go.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (vms == null || sel < 0 || sel >= vms.size()) return;
                VM vm = vms.get(sel);
                Intent it = new Intent(LauncherActivity.this, MainActivity.class);
                it.putExtra("name", vm.name); it.putExtra("mem", vm.mem);
                it.putExtra("gen", vm.gen);   it.putExtra("w", vm.w); it.putExtra("h", vm.h);
                it.putExtra("fda", vm.fda);   it.putExtra("hda", vm.hda); it.putExtra("cda", vm.cda);
                it.putExtra("boot", vm.boot);
                startActivity(it);
            }
        });
        LinearLayout.LayoutParams m2 = new LinearLayout.LayoutParams(0, -2, 1f);
        m2.leftMargin = Ui.dp(this, 6);
        btns.addView(go, m2);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, -2);
        bp.topMargin = Ui.dp(this, 26);
        root.addView(btns, bp);

        TextView lbl = Ui.label(this, "\u865a\u62df\u673a\u5217\u8868");
        lbl.setPadding(0, Ui.dp(this, 24), 0, Ui.dp(this, 10));
        root.addView(lbl);

        ScrollView sv = new ScrollView(this);
        listBox = new LinearLayout(this);
        listBox.setOrientation(LinearLayout.VERTICAL);
        sv.addView(listBox);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);
    }

    private Button smallBtn(String t, int color) {
        Button b = new Button(this);
        b.setText(t);
        b.setTextSize(11f);
        b.setAllCaps(false);
        b.setBackground(Ui.pressable(color, color, 0xff4b5c8a, 0xff4b5c8a, Ui.dp(this, 10)));
        b.setTextColor(0xffe8ecf7);
        b.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 12), 0);
        b.setMinWidth(0); b.setMinimumWidth(0);
        b.setMinHeight(0); b.setMinimumHeight(0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, Ui.dp(this, 34));
        lp.rightMargin = Ui.dp(this, 8);
        b.setLayoutParams(lp);
        return b;
    }

    private void deleteOwnDisks(VM vm) {
        String[] ps = { vm.fda, vm.hda, vm.cda };
        for (int i = 0; i < ps.length; i++) {
            if (ps[i] == null || ps[i].length() == 0) continue;
            java.io.File f = new java.io.File(ps[i]);
            String n = f.getName();
            if (n.startsWith("disk_") && n.endsWith(".img")) f.delete();
        }
    }

    private void confirmDelete(final int idx) {
        if (idx < 0 || idx >= vms.size()) return;
        final VM vm = vms.get(idx);
        new android.app.AlertDialog.Builder(this)
            .setTitle("\u5220\u9664\u865a\u62df\u673a")
            .setMessage("\u786e\u5b9a\u5220\u9664\u300c" + vm.name + "\u300d\uFF1F\n\n"
                      + "\u5b83\u81ea\u5df1\u521b\u5efa\u7684\u865a\u62df\u786c\u76d8\u4e5f\u4f1a\u4e00\u8d77\u5220\u6389\u3002\n"
                      + "\uFF08\u4f60\u81ea\u5df1\u9009\u7684 ISO/IMG \u4e0d\u4f1a\u88ab\u5220\uFF09")
            .setPositiveButton("\u5220\u9664", new android.content.DialogInterface.OnClickListener() {
                public void onClick(android.content.DialogInterface d, int w) {
                    deleteOwnDisks(vm);
                    vms.remove(idx);
                    VM.save(LauncherActivity.this, vms);
                    if (sel >= vms.size()) sel = -1;
                    refresh();
                }
            })
            .setNegativeButton("\u53d6\u6d88", null)
            .show();
    }

    @Override protected void onResume() { super.onResume(); refresh(); }

    private void refresh() {
        vms = VM.load(this);
        if (vms.isEmpty()) {
            VM f = new VM();
            f.name = "\u5185\u7f6e\u5f15\u5bfc\u76d8\uff08\u5f00\u7bb1\u5373\u7528\uff09";
            f.builtin = true;
            f.fda  = new java.io.File(getFilesDir(), "disk.img").getAbsolutePath();
            vms.add(f);
            VM.save(this, vms);
        }
        listBox.removeAllViews();
        if (sel >= vms.size()) sel = -1;

        for (int i = 0; i < vms.size(); i++) {
            final int ii = i;
            final VM vm = vms.get(i);
            boolean on = (i == sel);

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setBackground(Ui.edge(
                Ui.gradV(on ? 0xff25326e : Ui.CARD_A, on ? 0xff1e1b4b : Ui.CARD_B, R),
                on ? 0xff60a5fa : Ui.BORDER, Ui.dp(this, 1)));
            card.setPadding(0, Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 14));

            View bar = new View(this);
            bar.setBackground(Ui.grad(on ? Ui.BLUE : 0xff39415e, on ? Ui.PURPLE : 0xff232a42, 99));
            LinearLayout.LayoutParams barlp = new LinearLayout.LayoutParams(Ui.dp(this, 4), -1);
            barlp.rightMargin = Ui.dp(this, 14);
            card.addView(bar, barlp);

            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);

            LinearLayout r1 = new LinearLayout(this);
            r1.setOrientation(LinearLayout.HORIZONTAL);
            r1.setGravity(Gravity.CENTER_VERTICAL);
            TextView nm = Ui.text(this, vm.name, 16.5f, Ui.INK);
            nm.setSingleLine(true);
            LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(0, -2, 1f);
            r1.addView(nm, nlp);
            if (on) r1.addView(Ui.text(this, "\u25C6 \u5df2\u9009\u4e2d", 10.5f, 0xff93c5fd));
            col.addView(r1);

            TextView d = Ui.text(this, vm.summary(), 11, Ui.MUTED);
            d.setPadding(0, Ui.dp(this, 5), 0, 0);
            col.addView(d);

            String dev = "";
            if (vm.fda.length() > 0) dev += "\uD83D\uDCBE " + shortName(vm.fda) + "  ";
            if (vm.hda.length() > 0) dev += "\uD83D\uDCBD " + shortName(vm.hda) + "  ";
            if (vm.cda.length() > 0) dev += "\uD83D\uDCC0 " + shortName(vm.cda);
            if (dev.length() > 0) {
                TextView dv = Ui.text(this, dev, 10.5f, 0xff6f7d99);
                dv.setPadding(0, Ui.dp(this, 5), 0, 0);
                col.addView(dv);
            }
            card.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));

            boolean locked = vm.builtin || vm.name.startsWith("\u5185\u7f6e\u5f15\u5bfc\u76d8");
            if (locked) {
                TextView lock = Ui.text(this, "\uD83D\uDD12 \u5185\u7f6e\uFF08\u4e0d\u53ef\u4fee\u6539/\u5220\u9664\uFF09", 10.5f, 0xff8b93a7);
                col.addView(lock);
            } else {
                LinearLayout acts = new LinearLayout(this);
                acts.setOrientation(LinearLayout.HORIZONTAL);
                acts.setPadding(0, Ui.dp(this, 10), 0, 0);
                Button ed = smallBtn("\u270E \u7f16\u8f91", 0xff334155);
                ed.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        Intent it = new Intent(LauncherActivity.this, CreateVmActivity.class);
                        it.putExtra("edit", ii);
                        startActivity(it);
                    }
                });
                Button dl = smallBtn("\uD83D\uDDD1 \u5220\u9664", 0xff7f1d1d);
                dl.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) { confirmDelete(ii); }
                });
                acts.addView(ed);
                acts.addView(dl);
                col.addView(acts);
            }
            card.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { sel = ii; refresh(); }
            });
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
            cp.bottomMargin = Ui.dp(this, 12);
            listBox.addView(card, cp);
        }
    }
}
