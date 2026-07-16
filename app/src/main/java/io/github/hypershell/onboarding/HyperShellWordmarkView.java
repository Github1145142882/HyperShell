package io.github.hypershell.onboarding;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.widget.ImageView;

import androidx.annotation.Nullable;

/**
 * Keeps HyperCeiler's ImageView/blur pipeline while drawing the HyperShell wordmark.
 */
public final class HyperShellWordmarkView extends ImageView {
    private static final String WORDMARK = "HyperShell";

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private boolean lite;

    public HyperShellWordmarkView(Context context) {
        this(context, null);
    }

    public HyperShellWordmarkView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public HyperShellWordmarkView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        paint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        paint.setTextScaleX(1.035f);
        paint.setTextAlign(Paint.Align.CENTER);
        setWillNotDraw(false);
    }

    @Override
    public void setImageResource(int resId) {
        try {
            lite = "provision_text_logo_image_lite".equals(
                getResources().getResourceEntryName(resId)
            );
        } catch (Exception ignored) {
            lite = false;
        }
        super.setImageDrawable(null);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (getWidth() <= 0 || getHeight() <= 0) return;

        paint.setColor(lite ? Color.BLACK : Color.WHITE);
        float targetTextHeight = getHeight() * 0.80f;
        paint.setTextSize(targetTextHeight);
        float width = paint.measureText(WORDMARK);
        if (width > getWidth() * 0.95f) {
            paint.setTextSize(targetTextHeight * (getWidth() * 0.95f / width));
        }

        Paint.FontMetrics metrics = paint.getFontMetrics();
        float baseline = getHeight() * 0.5f - (metrics.ascent + metrics.descent) * 0.5f;
        canvas.drawText(WORDMARK, getWidth() * 0.5f, baseline, paint);
    }
}
