/*
 * Adapted from HyperCeiler's provision module for HyperShell.
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.sevtinge.hyperceiler.provision.fragment;

import android.os.Bundle;

import androidx.annotation.Nullable;

import com.sevtinge.hyperceiler.common.utils.PrefsConfigurator;
import com.sevtinge.hyperceiler.provision.R;

import fan.preference.PreferenceFragment;

/** Keeps HyperCeiler's preference page while exposing only effective HyperShell defaults. */
public final class BasicSettingsFragment extends PreferenceFragment {
    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        PrefsConfigurator.setup(this);
        setPreferencesFromResource(R.xml.provision_basic_settings, rootKey);
    }
}
