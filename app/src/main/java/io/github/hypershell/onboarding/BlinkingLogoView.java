package io.github.hypershell.onboarding;

import android.content.Context;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.widget.ImageView;

import androidx.annotation.Nullable;

/** Keeps the terminal cursor animation alive after HyperCeiler reapplies the logo drawable. */
public final class BlinkingLogoView extends ImageView {
    public BlinkingLogoView(Context context) {
        super(context);
    }

    public BlinkingLogoView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public BlinkingLogoView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        startLogoAnimation();
    }

    @Override
    public void setImageResource(int resId) {
        super.setImageResource(resId);
        startLogoAnimation();
    }

    @Override
    public void setImageDrawable(@Nullable Drawable drawable) {
        super.setImageDrawable(drawable);
        startLogoAnimation();
    }

    private void startLogoAnimation() {
        Drawable drawable = getDrawable();
        if (isAttachedToWindow() && drawable instanceof Animatable) {
            ((Animatable) drawable).start();
        }
    }
}
