package io.github.hypershell;

import android.app.Application;
import android.app.Activity;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.sevtinge.hyperceiler.common.utils.PrefsBridge;

import io.github.hypershell.onboarding.ProvisionEdgeToEdge;

/** Makes the original fan.miuix PreferenceDataStore available to every provision Activity. */
public final class HyperShellApplication extends Application implements Application.ActivityLifecycleCallbacks {
    @Override
    public void onCreate() {
        super.onCreate();
        PrefsBridge.initForApp(this);
        registerActivityLifecycleCallbacks(this);
    }

    private static void adaptFullScreenProvisionPage(Activity activity) {
        if (activity.getClass().getName().equals(
            "com.sevtinge.hyperceiler.provision.activity.CongratulationActivity"
        )) {
            ProvisionEdgeToEdge.apply(activity);
        }
    }

    @Override public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle state) {
        adaptFullScreenProvisionPage(activity);
    }
    @Override public void onActivityResumed(@NonNull Activity activity) {
        adaptFullScreenProvisionPage(activity);
    }
    @Override public void onActivityStarted(@NonNull Activity activity) {}
    @Override public void onActivityPaused(@NonNull Activity activity) {}
    @Override public void onActivityStopped(@NonNull Activity activity) {}
    @Override public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle state) {}
    @Override public void onActivityDestroyed(@NonNull Activity activity) {}
}
