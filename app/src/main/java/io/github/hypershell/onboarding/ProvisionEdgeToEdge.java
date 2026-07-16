package io.github.hypershell.onboarding;

import android.app.Activity;
import android.graphics.Color;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * Prevents fan AppCompat's decor from applying the gesture inset twice on the two full-screen
 * shader pages. HyperCeiler's original Fragment, shader and Folme animation remain unchanged.
 */
public final class ProvisionEdgeToEdge {
    private ProvisionEdgeToEdge() {}

    public static void apply(Activity activity) {
        Window window = activity.getWindow();
        WindowCompat.setDecorFitsSystemWindows(window, false);
        window.setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.setNavigationBarContrastEnforced(false);
        }
        ViewCompat.setOnApplyWindowInsetsListener(window.getDecorView(), (view, insets) -> {
            extendContent(activity);
            return WindowInsetsCompat.CONSUMED;
        });
        window.getDecorView().post(() -> extendContent(activity));
        ViewCompat.requestApplyInsets(window.getDecorView());
    }

    private static void extendContent(Activity activity) {
        View content = activity.findViewById(android.R.id.content);
        if (content == null || !(content.getParent() instanceof View)) return;
        View parent = (View) content.getParent();
        content.setFitsSystemWindows(false);
        parent.setFitsSystemWindows(false);
        ViewGroup.LayoutParams params = content.getLayoutParams();
        if (params.height != ViewGroup.LayoutParams.MATCH_PARENT) {
            params.height = ViewGroup.LayoutParams.MATCH_PARENT;
            content.setLayoutParams(params);
        }
    }
}
