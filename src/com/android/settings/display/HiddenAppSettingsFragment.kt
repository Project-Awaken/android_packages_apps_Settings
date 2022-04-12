/*
 * Copyright (C) 2021-2022 AOSP-Krypton Project
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

package com.android.settings.display

import android.os.UserHandle
import android.provider.Settings

import com.android.settings.R
import com.android.settings.awaken.fragments.AppListFragment

class HiddenAppSettingsFragment : AppListFragment() {

    override protected fun getTitle(): Int = R.string.hidden_apps_title

    override protected fun getInitialCheckedList(): List<String> {
        val packageList = Settings.Secure.getStringForUser(
            context?.contentResolver,
            Settings.Secure.LAUNCHER_HIDDEN_APPS,
            UserHandle.USER_CURRENT
        )
        return packageList?.takeIf { it.isNotBlank() }?.split(";") ?: emptyList()
    }

    override protected fun onListUpdate(list: List<String>) {
        Settings.Secure.putStringForUser(
            context?.contentResolver,
            Settings.Secure.LAUNCHER_HIDDEN_APPS,
            list.joinToString(";"),
            UserHandle.USER_CURRENT
        )
    }
}
