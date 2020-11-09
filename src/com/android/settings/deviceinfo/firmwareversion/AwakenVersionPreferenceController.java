/*
 * Copyright (C) 2019 The LineageOS Project
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

package com.android.settings.deviceinfo.firmwareversion;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.SystemProperties;
import android.text.TextUtils;
import android.util.Log;

import androidx.preference.Preference;

import com.android.settings.R;
import com.android.settings.core.BasePreferenceController;
import com.android.settings.slices.Sliceable;

public class AwakenVersionPreferenceController extends BasePreferenceController {

    private static final String TAG = "awakenVersionDialogCtrl";

    private static final String KEY_AWAKEN_VERSION_PROP = "ro.awaken.base.version";
    private static final String KEY_AWAKEN_CODENAME_PROP = "ro.awaken.base.codename";

    private final PackageManager mPackageManager;

    public AwakenVersionPreferenceController(Context context, String preferenceKey) {
        super(context, preferenceKey);
        mPackageManager = mContext.getPackageManager();
    }

    @Override
    public int getAvailabilityStatus() {
        return AVAILABLE;
    }

    @Override
    public boolean useDynamicSliceSummary() {
        return true;
    }

    @Override
    public boolean isSliceable() {
        return true;
    }

    @Override
    public CharSequence getSummary() {
        String awakenVersion = SystemProperties.get(KEY_AWAKEN_VERSION_PROP,
                mContext.getString(R.string.unknown));
        String awakenCodename = SystemProperties.get(KEY_AWAKEN_CODENAME_PROP,
                mContext.getString(R.string.unknown));
        if (!awakenVersion.isEmpty())
            return awakenVersion + " | " + awakenCodename;
        else
            return mContext.getString(R.string.awaken_version_default);
    }

    @Override
    public boolean handlePreferenceTreeClick(Preference preference) {
        if (!TextUtils.equals(preference.getKey(), getPreferenceKey())) {
            return false;
        }

        final Intent intent = new Intent();
        intent.setAction(Intent.ACTION_VIEW);
        intent.setData(Uri.parse(mContext.getString(R.string.awaken_uri)));
        if (mPackageManager.queryIntentActivities(intent, 0).isEmpty()) {
            // Don't send out the intent to stop crash
            Log.w(TAG, "queryIntentActivities() returns empty");
            return true;
        }

        mContext.startActivity(intent);
        return true;
    }

    public void copy() {
        Sliceable.setCopyContent(mContext, getSummary(),
                mContext.getText(R.string.awaken_version));
    }
}
