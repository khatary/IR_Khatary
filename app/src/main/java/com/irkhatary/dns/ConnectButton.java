package com.irkhatary.dns;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

/** Big round power button with a pulse animation while connected and a spinner while connecting. */
public class ConnectButton extends View {
    static final int OFF = 0, BUSY = 1, ON = 2;

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Shader[] shaders = new Shader[3];
    private int state = OFF;
    private final long t0 = SystemClock.uptimeMillis();

    public ConnectButton(Context c) {
        super(c);
        setClickable(true);
        setFocusable(true);
    }

    void setState(int s) {
        if (s == state) return;
        state = s;
        invalidate();
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        float r = Math.min(w, h) / 2f * 0.58f;
        float cx = w / 2f, cy = h / 2f;
        shaders[OFF] = new RadialGradient(cx, cy, r, 0xFF3A4478, 0xFF1C2350, Shader.TileMode.CLAMP);
        shaders[BUSY] = new RadialGradient(cx, cy, r, 0xFFFFC857, 0xFFE08A00, Shader.TileMode.CLAMP);
        shaders[ON] = new RadialGradient(cx, cy, r, 0xFF2BF0B0, 0xFF00A872, Shader.TileMode.CLAMP);
    }

    @Override protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        float cx = w / 2f, cy = h / 2f, r = Math.min(w, h) / 2f;
        float stroke = getResources().getDisplayMetrics().density;
        long t = SystemClock.uptimeMillis() - t0;

        if (state == ON) {
            p.setShader(null);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(stroke * 2f);
            for (int k = 0; k < 2; k++) {
                float ph = ((t % 2600) / 2600f + k * 0.5f) % 1f;
                p.setColor(Color.argb((int) (110 * (1f - ph)), 0, 229, 160));
                c.drawCircle(cx, cy, r * 0.60f + r * 0.40f * ph, p);
            }
        } else {
            p.setShader(null);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(stroke * 2f);
            p.setColor(state == BUSY ? 0x55FFC857 : 0x33FFFFFF);
            c.drawCircle(cx, cy, r * 0.68f, p);
        }

        if (state == BUSY) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(stroke * 4f);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setColor(0xFFFFC857);
            float rr = r * 0.68f;
            rect.set(cx - rr, cy - rr, cx + rr, cy + rr);
            c.drawArc(rect, (t % 1100) / 1100f * 360f, 90f, false, p);
            p.setStrokeCap(Paint.Cap.BUTT);
        }

        p.setStyle(Paint.Style.FILL);
        p.setShader(shaders[state]);
        c.drawCircle(cx, cy, r * 0.58f, p);
        p.setShader(null);

        // power glyph
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(r * 0.065f);
        p.setColor(state == OFF ? 0xFFC9D2FF : Color.WHITE);
        float gr = r * 0.22f;
        rect.set(cx - gr, cy - gr, cx + gr, cy + gr);
        c.drawArc(rect, -55f, 290f, false, p);
        c.drawLine(cx, cy - gr * 1.15f, cx, cy - gr * 0.1f, p);
        p.setStrokeCap(Paint.Cap.BUTT);

        if (state != OFF) postInvalidateOnAnimation();
    }
}
