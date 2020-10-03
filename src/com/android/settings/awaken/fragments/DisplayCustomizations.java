/*
 * Copyright (C) 2021 Wave-OS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settings.awaken.fragments;

import android.app.Activity;
import android.content.Context;
import android.content.ContentResolver;
import android.os.Bundle;
import android.provider.Settings;

import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;
import androidx.preference.Preference.OnPreferenceChangeListener;
import androidx.preference.SwitchPreference;

import com.android.internal.logging.nano.MetricsProto;

import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;

import com.awaken.support.preferences.SecureSettingMasterSwitchPreference;

public class DisplayCustomizations extends SettingsPreferenceFragment
        implements Preference.OnPreferenceChangeListener {

    private static final String TAG = "Display Customizations";
    private static final String BRIGHTNESS_SLIDER = "qs_show_brightness";

    private SecureSettingMasterSwitchPreference mBrightnessSlider;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.display_customizations);
        PreferenceScreen prefSet = getPreferenceScreen();
        final ContentResolver resolver = getActivity().getContentResolver();

        mBrightnessSlider = (SecureSettingMasterSwitchPreference)
                findPreference(BRIGHTNESS_SLIDER);
        mBrightnessSlider.setOnPreferenceChangeListener(this);
        boolean enabled = Settings.Secure.getInt(resolver,
                BRIGHTNESS_SLIDER, 1) == 1;
        mBrightnessSlider.setChecked(enabled);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        ContentResolver resolver = getActivity().getContentResolver();
        if (preference == mBrightnessSlider) {
            boolean value = (boolean) newValue;
            Settings.Secure.putInt(resolver,
                    BRIGHTNESS_SLIDER, value ? 1 : 0);
            return true;
        }
        return false;
    }

    @Override
    public int getMetricsCategory() {
       return MetricsProto.MetricsEvent.AWAKEN;
    }
}
