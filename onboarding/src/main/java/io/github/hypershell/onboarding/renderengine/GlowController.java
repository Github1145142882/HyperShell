/*
 * This file is adapted from HyperCeiler's provision module at commit
 * 7266aaa0d698ad10795381c5bf23651c2e1719d0.
 * Copyright (C) 2023-2026 HyperCeiler Contributions
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package io.github.hypershell.onboarding.renderengine;

import android.os.Build;
import android.view.View;

public final class GlowController implements Runnable {
    private GlowPainter painter;
    private float time;
    private float direction = 1.0f;
    private long lastGlobalTime;
    private final View target;

    public GlowController(View target) { this.target = target; }

    public void start(boolean admission) {
        if (Build.VERSION.SDK_INT < 33 || painter != null) return;
        painter = new GlowPainter(target.getContext());
        painter.needAdmission(admission);
        resetTime();
        target.post(this);
    }

    @Override public void run() {
        if (painter == null) return;
        long now = System.nanoTime();
        float delta = (float) ((now - lastGlobalTime) * 1.0E-9d);
        time += delta * direction;
        if (direction > 0.0f && time >= 120.0f) direction = -1.0f;
        else if (direction < 0.0f && time <= 2.0f) direction = 1.0f;
        lastGlobalTime = now;
        painter.setAnimTime(time);
        painter.setResolution(target.getWidth(), target.getHeight());
        target.setRenderEffect(painter.getRenderEffect());
        target.postDelayed(this, 16L);
    }

    public void resetTime() { lastGlobalTime = System.nanoTime(); time = 0.0f; }

    public void setCircleCenterY(float normalizedY) {
        if (painter != null) painter.setCircleYOffset(0.5f - normalizedY);
    }

    public void stop() {
        if (painter == null) return;
        target.removeCallbacks(this);
        painter = null;
        target.setRenderEffect(null);
    }
}
