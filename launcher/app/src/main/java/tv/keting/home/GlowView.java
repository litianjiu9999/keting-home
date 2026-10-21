package tv.keting.home;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/** 焦点应用背后的一块色晕。用径向渐变画，不做模糊。 */
public class GlowView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int color = 0xFFD7D2C8;
    private RadialGradient gradient;
    private int cachedWidth;
    private int cachedHeight;
    private int cachedColor;

    public GlowView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setGlow(int color) {
        this.color = color;
        gradient = null;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int width = getWidth();
        int height = getHeight();
        if (width == 0 || height == 0) return;
        if (gradient == null || width != cachedWidth || height != cachedHeight || color != cachedColor) {
            int glow = (0x66 << 24) | (color & 0x00FFFFFF);
            gradient = new RadialGradient(
                    width * 0.5f,
                    height * 0.72f,
                    width * 0.42f,
                    new int[] {glow, 0x00000000},
                    new float[] {0f, 1f},
                    Shader.TileMode.CLAMP);
            cachedWidth = width;
            cachedHeight = height;
            cachedColor = color;
        }
        paint.setShader(gradient);
        canvas.drawRect(0, 0, width, height, paint);
    }
}
