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
import android.content.res.Resources;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.view.Display;
import android.view.DisplayCutout;
import android.view.Surface;
import android.view.View;

import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;
import androidx.preference.Preference.OnPreferenceChangeListener;
import androidx.preference.SwitchPreference;

import com.android.internal.logging.nano.MetricsProto;

import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;

import com.awaken.support.preferences.SecureSettingMasterSwitchPreference;
import com.awaken.support.preferences.SecureSettingListPreference;
import com.awaken.support.preferences.SystemSettingMasterSwitchPreference;

public class DisplayCustomizations extends SettingsPreferenceFragment
        implements Preference.OnPreferenceChangeListener {

    private static final String TAG = "Display Customizations";
    private static final String BRIGHTNESS_SLIDER = "qs_show_brightness";
    private static final String KEY_BATTERY_CHARGING_LIGHT = "battery_charging_light";
    private static final String KEY_CLOCK_POSITION = "status_bar_clock_position";
    private static final String KEY_CLOCK_AM_PM = "status_bar_am_pm";
    private static final String KEY_NETWORK_TRAFFIC = "network_traffic_state";

    private SecureSettingMasterSwitchPreference mBrightnessSlider;
    private SecureSettingListPreference mClockPositionPref;
    private SecureSettingListPreference mAmPmPref;
    private SystemSettingMasterSwitchPreference mNetworkTraffic;
    Preference mBatteryLightPref;

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

        mBatteryLightPref = (Preference) findPreference(KEY_BATTERY_CHARGING_LIGHT);
        if (!getResources()
                .getBoolean(com.android.internal.R.bool.config_intrusiveBatteryLed))
        {
			if (mBatteryLightPref != null) {
				prefSet.removePreference(mBatteryLightPref);
			}
        }

        mClockPositionPref = (SecureSettingListPreference) findPreference(KEY_CLOCK_POSITION);
        mAmPmPref = (SecureSettingListPreference) findPreference(KEY_CLOCK_AM_PM);

        boolean hasNotch = hasCenteredCutout(getActivity());
        boolean isRtl = getResources().getConfiguration().getLayoutDirection()
                == View.LAYOUT_DIRECTION_RTL;

        // Adjust clock position pref for RTL and center notch
        int entries = hasNotch ? R.array.status_bar_clock_position_entries_notch
                : R.array.status_bar_clock_position_entries;
        int values = hasNotch ? (isRtl ? R.array.status_bar_clock_position_values_notch_rtl
                : R.array.status_bar_clock_position_values_notch)
                : (isRtl ? R.array.status_bar_clock_position_values_rtl
                : R.array.status_bar_clock_position_values);
        mClockPositionPref.setEntries(entries);
        mClockPositionPref.setEntryValues(values);

        // Disable AM/PM for 24-hour format
        if (DateFormat.is24HourFormat(getActivity())) {
            mAmPmPref.setEnabled(false);
            mAmPmPref.setSummary(R.string.status_bar_am_pm_disabled);
        }

        mNetworkTraffic = (SystemSettingMasterSwitchPreference)
                findPreference(KEY_NETWORK_TRAFFIC);
        enabled = Settings.System.getIntForUser(resolver,
                KEY_NETWORK_TRAFFIC, 0, UserHandle.USER_CURRENT) == 1;
        mNetworkTraffic.setChecked(enabled);
        mNetworkTraffic.setOnPreferenceChangeListener(this);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        ContentResolver resolver = getActivity().getContentResolver();
        if (preference == mBrightnessSlider) {
            boolean value = (boolean) newValue;
            Settings.Secure.putInt(resolver,
                    BRIGHTNESS_SLIDER, value ? 1 : 0);
            return true;
        } else if (preference == mNetworkTraffic) {
            boolean value = (Boolean) newValue;
            Settings.System.putIntForUser(resolver, KEY_NETWORK_TRAFFIC,
                    value ? 1 : 0, UserHandle.USER_CURRENT);
            return true;
        }
        return false;
    }

    @Override
    public int getMetricsCategory() {
       return MetricsProto.MetricsEvent.AWAKEN;
    }

    /* returns whether the device has a centered display cutout or not. */
    private static boolean hasCenteredCutout(Context context) {
        Display display = context.getDisplay();
        DisplayCutout cutout = display.getCutout();
        if (cutout != null) {
            Point realSize = new Point();
            display.getRealSize(realSize);

            switch (display.getRotation()) {
                case Surface.ROTATION_0: {
                    Rect rect = cutout.getBoundingRectTop();
                    return !(rect.left <= 0 || rect.right >= realSize.x);
                }
                case Surface.ROTATION_90: {
                    Rect rect = cutout.getBoundingRectLeft();
                    return !(rect.top <= 0 || rect.bottom >= realSize.y);
                }
                case Surface.ROTATION_180: {
                    Rect rect = cutout.getBoundingRectBottom();
                    return !(rect.left <= 0 || rect.right >= realSize.x);
                }
                case Surface.ROTATION_270: {
                    Rect rect = cutout.getBoundingRectRight();
                    return !(rect.top <= 0 || rect.bottom >= realSize.y);
                }
            }
        }
        return false;
    }
}
