/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.settings.system;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.UserManager;
import android.os.SystemProperties;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceScreen;

import com.android.settings.R;
import com.android.settings.Utils;
import com.android.settings.core.BasePreferenceController;

public class SystemUpdatePreferenceController extends BasePreferenceController {

    private static final String KEY_SYSTEM_UPDATE_SETTINGS = "system_update_settings";
    private static final String OTA_APP = "mx.xperience.updater";
    private static final String RELEASE_TYPE = "ro.xpe.releasetype";

    private final UserManager mUm;

    public SystemUpdatePreferenceController(Context context) {
        super(context, KEY_SYSTEM_UPDATE_SETTINGS);
        mUm = UserManager.get(context);
    }

    @Override
    public int getAvailabilityStatus() {
       String buildtype = SystemProperties.get(RELEASE_TYPE,"unofficial");
       //Check if is supported if not return not supported
          if (!mUm.isAdminUser() || !buildtype.equalsIgnoreCase("official")){
            return UNSUPPORTED_ON_DEVICE;
          }
          //Catch exceptions
          try {
            PackageManager pm = mContext.getPackageManager();
            pm.getPackageInfo(OTA_APP, PackageManager.GET_ACTIVITIES);
          }catch (Exception e){
            return UNSUPPORTED_ON_DEVICE;
          }
          return AVAILABLE;
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);
        if (isAvailable()) {
            Utils.updatePreferenceToSpecificActivityOrRemove(mContext, screen,
                    getPreferenceKey(),
                    Utils.UPDATE_PREFERENCE_FLAG_SET_TITLE_TO_MATCHING_ACTIVITY);
        }
    }

    @Override
    public boolean handlePreferenceTreeClick(Preference preference) {//remove carrier verification
        // always return false here because this handler does not want to block other handlers.
        return false;
    }

    @Override
    public CharSequence getSummary() {
        CharSequence summary = mContext.getString(R.string.summary_empty);
        return summary;
    }
}
