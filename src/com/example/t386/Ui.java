package com.example.t386;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class Ui {
    /* ---- 配色 ---- */
    public static final int BG_A      = 0xff090d18, BG_B      = 0xff141a2e;
    public static final int CARD_A    = 0xff1c2340, CARD_B    = 0xff131829;
    public static final int BORDER    = 0xff2a3352;
    public static final int BLUE      = 0xff3b82f6, PURPLE    = 0xff8b5cf6;
    public static final int GREEN     = 0xff22c55e, GREEN2    = 0xff10b981;
    public static final int INK       = 0xffe8ecf7;
    public static final int MUTED     = 0xff8592ad;
    public static final int CHIP_OFF  = 0xff1a2136;
    public static final int CHIP_TXT  = 0xffc3cde3;

    public static int dp(Context c, float v) {
        return (int) (v * c.getResources().getDisplayMetrics().density + 0.5f);
    }

    /** 对角渐变 + 圆角 */
    public static GradientDrawable grad(int a, int b, int r) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{a, b});
        g.setCornerRadius(r);
        return g;
    }
    /** 竖直渐变 + 圆角（用于背景/卡片） */
    public static GradientDrawable gradV(int a, int b, int r) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{a, b});
        g.setCornerRadius(r);
        return g;
    }
    /** 加描边 */
    public static GradientDrawable edge(GradientDrawable g, int color, int w) {
        g.setStroke(w, color);
        return g;
    }

    /** 带按下态的背景（自定义背景会干掉按钮反馈，必须自己加） */
    public static StateListDrawable pressable(int a, int b, int pa, int pb, int r) {
        StateListDrawable d = new StateListDrawable();
        d.addState(new int[]{android.R.attr.state_pressed}, grad(pa, pb, r));
        d.addState(new int[]{}, grad(a, b, r));
        return d;
    }

    /** 卡片容器 */
    public static LinearLayout card(Context c, int a, int b, int r, int borderW) {
        LinearLayout v = new LinearLayout(c);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setBackground(edge(gradV(a, b, r), BORDER, borderW));
        return v;
    }

    /** 选项胶囊 */
    public static Button chip(Context c, String text, boolean on, int r) {
        Button b = new Button(c);
        b.setText(text);
        b.setTextSize(12.5f);
        b.setAllCaps(false);
        b.setPadding(0, dp(c, 6), 0, dp(c, 6));
        b.setMinHeight(0); b.setMinimumHeight(0);
        b.setBackground(on ? pressable(BLUE, PURPLE, 0xff6366f1, 0xffa78bfa, r)
                             : pressable(CHIP_OFF, CHIP_OFF, 0xff2b3557, 0xff2b3557, r));
        b.setTextColor(on ? 0xffffffff : CHIP_TXT);
        return b;
    }

    public static void repaintChips(Button[] arr, int sel, Context c, int r) {
        for (int i = 0; i < arr.length; i++) {
            boolean on = (i == sel);
            arr[i].setBackground(on ? pressable(BLUE, PURPLE, 0xff6366f1, 0xffa78bfa, r)
                                    : pressable(CHIP_OFF, CHIP_OFF, 0xff2b3557, 0xff2b3557, r));
            arr[i].setTextColor(on ? 0xffffffff : CHIP_TXT);
        }
    }

    /** 大动作按钮 */
    public static Button action(Context c, String text, int a, int b, int r) {
        Button bt = new Button(c);
        bt.setText(text);
        bt.setTextSize(15f);
        bt.setAllCaps(false);
        bt.setBackground(pressable(a, b, a == BLUE ? 0xff60a5fa : 0xff4ade80, b, r));
        bt.setTextColor(0xffffffff);
        bt.setPadding(0, dp(c, 12), 0, dp(c, 12));
        return bt;
    }

    public static TextView text(Context c, String s, float size, int color) {
        TextView t = new TextView(c);
        t.setText(s); t.setTextSize(size); t.setTextColor(color);
        return t;
    }

    public static TextView label(Context c, String s) {
        TextView t = text(c, s, 11.5f, MUTED);
        int p = dp(c, 16);
        t.setPadding(0, p, 0, dp(c, 7));
        return t;
    }
}
