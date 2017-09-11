/*
 * Copyright (C) 2018 ArrowOS
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

package com.android.settings.notification;

import android.content.ContentResolver;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.UserHandle;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;
import android.provider.Settings;

import com.android.internal.logging.nano.MetricsProto;

import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;

import com.awaken.support.preferences.SystemSettingSwitchPreference;

public class BatteryLightSettings extends SettingsPreferenceFragment implements
        Preference.OnPreferenceChangeListener {

    private SystemSettingSwitchPreference mLowBatteryBlinking;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.configure_notification_settings);

        PreferenceScreen prefSet = getPreferenceScreen();

        mLowBatteryBlinking = (SystemSettingSwitchPreference)prefSet.findPreference("battery_light_low_blinking");
        if (getResources().getBoolean(
                        com.android.internal.R.bool.config_ledCanPulse)) {
            mLowBatteryBlinking.setChecked(Settings.System.getIntForUser(getContentResolver(),
                            Settings.System.BATTERY_LIGHT_LOW_BLINKING, 0, UserHandle.USER_CURRENT) == 1);
            mLowBatteryBlinking.setOnPreferenceChangeListener(this);
        } else {
            prefSet.removePreference(mLowBatteryBlinking);
        }
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.AWAKEN;
    }

    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference == mLowBatteryBlinking) {
            boolean value = (Boolean) newValue;
            Settings.System.putIntForUser(getActivity().getContentResolver(),
                    Settings.System.BATTERY_LIGHT_LOW_BLINKING, value ? 1 : 0,
                    UserHandle.USER_CURRENT);
            mLowBatteryBlinking.setChecked(value);
        }
        return false;
    }
}
