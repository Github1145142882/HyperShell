package io.github.hypershell.onboarding;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.sevtinge.hyperceiler.provision.activity.DefaultActivity;
import com.sevtinge.hyperceiler.provision.utils.Utils;

import io.github.hypershell.MainActivity;

/** Hosts the unmodified HyperCeiler welcome fragment with HyperShell window behavior. */
public final class HyperShellProvisionActivity extends DefaultActivity {
    public static final String EXTRA_WELCOME_ONLY = "io.github.hypershell.extra.WELCOME_ONLY";
    private final FragmentManager.FragmentLifecycleCallbacks welcomeButtonUnlocker =
            new FragmentManager.FragmentLifecycleCallbacks() {
                @Override
                public void onFragmentViewCreated(
                        @NonNull FragmentManager manager,
                        @NonNull Fragment fragment,
                        @NonNull View view,
                        @Nullable Bundle state
                ) {
                    if (fragment.getClass().getName().endsWith(".StartupFragment")) {
                        unlockWelcomeButtons(view);
                    }
                }
            };

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        if (isWelcomeOnly()) {
            Utils.isFirstBoot = true;
            Utils.IS_START_ANIMA = false;
        }
        super.onCreate(savedInstanceState);
        getSupportFragmentManager().registerFragmentLifecycleCallbacks(welcomeButtonUnlocker, true);
        for (Fragment fragment : getSupportFragmentManager().getFragments()) {
            if (fragment.getClass().getName().endsWith(".StartupFragment") && fragment.getView() != null) {
                unlockWelcomeButtons(fragment.getView());
            }
        }
        ProvisionEdgeToEdge.apply(this);
    }

    private static void unlockWelcomeButtons(@NonNull View root) {
        root.post(() -> {
            int[] ids = {
                    com.sevtinge.hyperceiler.provision.R.id.next_layout,
                    com.sevtinge.hyperceiler.provision.R.id.next,
                    com.sevtinge.hyperceiler.provision.R.id.next_arrow
            };
            for (int id : ids) {
                View button = root.findViewById(id);
                if (button != null) {
                    // Keep the original visibility/alpha animation, but accept the tap as soon
                    // as the button appears instead of waiting for its entrance transition.
                    button.setEnabled(true);
                    button.setClickable(true);
                }
            }
        });
    }

    @Override
    public void onDestroy() {
        getSupportFragmentManager().unregisterFragmentLifecycleCallbacks(welcomeButtonUnlocker);
        super.onDestroy();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
    }

    @Override
    public void run(int code) {
        if (isWelcomeOnly() && code == -1) {
            return;
        }
        super.run(code);
    }

    @Override
    public void enterCurrentState(@Nullable Bundle activityOptions) {
        if (!isWelcomeOnly()) {
            super.enterCurrentState(activityOptions);
            return;
        }
        Intent target = new Intent(this, MainActivity.class)
                .putExtra(MainActivity.EXTRA_WELCOME_COMPLETE, true);
        if (activityOptions == null) {
            startActivity(target);
        } else {
            startActivity(target, activityOptions);
        }
        finish();
    }

    private boolean isWelcomeOnly() {
        return getIntent().getBooleanExtra(EXTRA_WELCOME_ONLY, false);
    }
}
