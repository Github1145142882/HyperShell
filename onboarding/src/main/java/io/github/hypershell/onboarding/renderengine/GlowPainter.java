/*
 * This file is adapted from HyperCeiler's provision module at commit
 * 7266aaa0d698ad10795381c5bf23651c2e1719d0.
 * Copyright (C) 2023-2026 HyperCeiler Contributions
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package io.github.hypershell.onboarding.renderengine;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.RenderEffect;
import android.graphics.RuntimeShader;
import android.util.Log;

import androidx.annotation.RequiresApi;

import io.github.hypershell.onboarding.R;

import java.io.InputStream;
import java.util.Scanner;

@RequiresApi(33)
final class GlowPainter {
    private final RuntimeShader shader;

    GlowPainter(Context context) {
        shader = new RuntimeShader(loadShader(context.getResources(), R.raw.glow));
        shader.setFloatUniform("uScale2", 0.82f);
        shader.setFloatUniform("uSpeed2", 0.49f);
        shader.setFloatUniform("uColorInMin", 0.3f);
        shader.setFloatUniform("uColorInMax", 1.0f);
        shader.setFloatUniform("uColorOutMin", 0.3f);
        shader.setFloatUniform("uColorOutMax", 0.86f);
        shader.setFloatUniform("uColorMidPoint", 0.47f);
        shader.setFloatUniform("uUseOklab", 1.0f);
        shader.setFloatUniform("uColorBlack", new float[]{0.961f, 0.157f, 0.157f});
        shader.setFloatUniform("uColorMid", new float[]{0.604f, 0.659f, 0.961f});
        shader.setFloatUniform("uColorWhite", new float[]{0.302f, 0.29f, 0.843f});
        shader.setFloatUniform("uScale", 1.3f);
        shader.setFloatUniform("uSpeed", 0.4f);
        shader.setFloatUniform("uBrightnessInMin", 0.25f);
        shader.setFloatUniform("uBrightnessInMax", 1.0f);
        shader.setFloatUniform("uBrightnessOutMin", 0.25f);
        shader.setFloatUniform("uBrightnessOutMax", 1.0f);
        shader.setFloatUniform("uShowCircle", 1.0f);
        shader.setFloatUniform("uCircleThickness", 0.4f);
        shader.setFloatUniform("uCircleFinalRadius", 1.0f);
        shader.setFloatUniform("uCircleYOffset", 0.1f);
        shader.setFloatUniform("uCircleSpeed", 0.9f);
        shader.setFloatUniform("uCircleColorFreq", 1.0f);
        shader.setFloatUniform("uCircleColorSpeed", 0.0f);
        shader.setFloatUniform("uCircleEasing", 1.4f);
        shader.setFloatUniform("uCircleAnimationOffset", 0.0f);
        shader.setFloatUniform("uMaskDelay", 0.3f);
        shader.setFloatUniform("uMaskThickness", 0.3f);
        shader.setFloatUniform("uCircleScreenBlend", 1.0f);
        shader.setFloatUniform("uCircleAddBlend", 0.04f);
        shader.setFloatUniform("uCircleColorOffset", 0.25f);
        shader.setFloatUniform("uCircleUVDistort", 0.0f);
        shader.setFloatUniform("uColorToDistortWidthRatio", 0.6f);
        shader.setFloatUniform("uDistortStartTime", 0.2f);
        shader.setFloatUniform("uDistortEndTime", 0.3f);
        shader.setFloatUniform("uDistortStart", 0.0f);
        shader.setFloatUniform("uDistortEnd", 1.0f);
        shader.setFloatUniform("uStripeFrequency", 0.0f);
        shader.setFloatUniform("uStripeStrengthX", 0.0f);
        shader.setFloatUniform("uStripeStrengthY", 0.0f);
        shader.setFloatUniform("uStripeUVDistort", 0.0f);
    }

    RenderEffect getRenderEffect() { return RenderEffect.createShaderEffect(shader); }
    void setAnimTime(float value) { shader.setFloatUniform("uTime", value); }
    void setResolution(float width, float height) { shader.setFloatUniform("uResolution", width, height); }
    void setCircleYOffset(float value) { shader.setFloatUniform("uCircleYOffset", value); }
    void needAdmission(boolean need) { shader.setFloatUniform("uShowCircle", need ? 1.0f : 0.0f); }

    private static String loadShader(Resources resources, int resourceId) {
        try (InputStream stream = resources.openRawResource(resourceId); Scanner scanner = new Scanner(stream)) {
            StringBuilder source = new StringBuilder();
            while (scanner.hasNextLine()) source.append(scanner.nextLine()).append('\n');
            return source.toString();
        } catch (Exception error) {
            Log.e("HyperShellGlow", "Unable to load welcome shader", error);
            throw new IllegalStateException("Unable to load welcome shader", error);
        }
    }
}
