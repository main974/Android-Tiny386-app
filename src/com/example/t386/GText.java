package com.example.t386;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.widget.TextView;

public class GText extends TextView {
    public GText(Context c) { super(c); }
    public GText(Context c, AttributeSet a) { super(c, a); }
    @Override protected void onDraw(Canvas cv) {
        if (getWidth() > 0) {
            getPaint().setShader(new LinearGradient(
                0, 0, getWidth(), getHeight(),
                Ui.BLUE, Ui.PURPLE, Shader.TileMode.CLAMP));
        }
        super.onDraw(cv);
    }
}
