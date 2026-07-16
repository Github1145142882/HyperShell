/*
 * HyperShell adaptation of HyperCeiler's permission page.
 * Original visual resources remain provided by HyperCeiler under AGPL-3.0-or-later.
 */
package com.sevtinge.hyperceiler.provision.fragment;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.sevtinge.hyperceiler.provision.R;
import com.sevtinge.hyperceiler.provision.widget.PermissionItemView;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class PermissionSettingsFragment extends BaseFragment {

    private PermissionItemView rootItem;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected int getLayoutId() {
        return R.layout.provision_permission_layout;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        PermissionItemView networkItem = view.findViewById(R.id.network);
        rootItem = view.findViewById(R.id.root);
        PermissionItemView localDataItem = view.findViewById(R.id.installed_apps);

        networkItem.setItemTitle(R.string.provision_permission_internet);
        rootItem.setItemTitle(R.string.provision_permission_root);
        localDataItem.setItemTitle(R.string.provision_permission_installed_apps);

        networkItem.setEnabled(false);
        networkItem.setChecked(hasValidatedNetwork(requireContext()));
        localDataItem.setEnabled(false);
        localDataItem.setChecked(true);

        rootItem.setOnClickListener(ignored -> verifyRoot());
    }

    private void verifyRoot() {
        if (rootItem == null || !rootItem.isEnabled()) return;
        rootItem.setEnabled(false);
        rootItem.setAlpha(0.5f);
        executor.execute(() -> {
            boolean verified = checkRootUid();
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                if (rootItem == null) return;
                rootItem.setChecked(verified);
                rootItem.setAlpha(1.0f);
                rootItem.setEnabled(true);
            });
        });
    }

    private static boolean hasValidatedNetwork(Context context) {
        ConnectivityManager manager =
            (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (manager == null) return false;
        Network network = manager.getActiveNetwork();
        NetworkCapabilities capabilities = manager.getNetworkCapabilities(network);
        return capabilities != null
            && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }

    private static boolean checkRootUid() {
        Process process = null;
        try {
            process = new ProcessBuilder("su", "-c", "id -u")
                .redirectErrorStream(true)
                .start();
            if (!process.waitFor(20, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return false;
            }
            try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
                return process.exitValue() == 0 && "0".equals(reader.readLine());
            }
        } catch (Exception ignored) {
            return false;
        } finally {
            if (process != null) process.destroy();
        }
    }

    @Override
    public void onDestroyView() {
        rootItem = null;
        executor.shutdownNow();
        super.onDestroyView();
    }
}
