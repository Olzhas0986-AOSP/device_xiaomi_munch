/*
 * Copyright (C) 2020 The LineageOS Project
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

package org.lineageos.settings.refreshrate;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.UserHandle;
import android.view.Display;

import android.provider.Settings;
import android.util.Log;
import android.content.res.Configuration;
import androidx.preference.PreferenceManager;

import org.lineageos.settings.utils.ProfileUtils;

public final class RefreshUtils {

    private static final String REFRESH_CONTROL = "refresh_control";

    private static final String OVERRIDE_ACTIVE = "refresh_override_active";
    private static final String ORIGINAL_MAX = "refresh_original_max";
    private static final String ORIGINAL_MIN = "refresh_original_min";
    private String mOriginalMax;
    private String mOriginalMin;
    private static final String KEY_PEAK_REFRESH_RATE = "peak_refresh_rate";
    private static final String KEY_MIN_REFRESH_RATE = "min_refresh_rate";
    private Context mContext;
    protected boolean isAppInList;

    protected static final int STATE_DEFAULT = 0;
    protected static final int STATE_STANDARD = 1;
    protected static final int STATE_EXTREME = 2;
    protected static final int STATE_LAND = 3;

    private static final float REFRESH_STATE_DEFAULT = 120f;
    private static final float REFRESH_STATE_STANDARD = 60f;
    private static final float REFRESH_STATE_EXTREME = 120f;
    private static final float REFRESH_STATE_LAND = 60f;

    private static final String REFRESH_STANDARD = "refresh.standard=";
    private static final String REFRESH_EXTREME = "refresh.extreme=";
    private static final String REFRESH_LAND = "refresh.land=";

    private SharedPreferences mSharedPrefs;


    protected RefreshUtils(Context context) {
        mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        mContext = context;
        isAppInList = mSharedPrefs.getBoolean(OVERRIDE_ACTIVE, false);
        mOriginalMax = mSharedPrefs.getString(ORIGINAL_MAX, null);
        mOriginalMin = mSharedPrefs.getString(ORIGINAL_MIN, null);
    }

    public static void startService(Context context) {
        context.startServiceAsUser(new Intent(context, RefreshService.class),
                UserHandle.CURRENT);
    }

    private void writeValue(String profiles) {
        mSharedPrefs.edit().putString(REFRESH_CONTROL, profiles).apply();
    }

    protected void getOldRate() {
        if (!isAppInList) {
            mOriginalMax = Settings.System.getString(mContext.getContentResolver(), KEY_PEAK_REFRESH_RATE);
            mOriginalMin = Settings.System.getString(mContext.getContentResolver(), KEY_MIN_REFRESH_RATE);
        }
    }

    protected void restoreRefreshRate() {
        if (!isAppInList) return;
        Settings.System.putString(mContext.getContentResolver(), KEY_MIN_REFRESH_RATE, mOriginalMin);
        Settings.System.putString(mContext.getContentResolver(), KEY_PEAK_REFRESH_RATE, mOriginalMax);
        isAppInList = false;
        mSharedPrefs.edit().remove(OVERRIDE_ACTIVE).remove(ORIGINAL_MAX).remove(ORIGINAL_MIN).apply();
    }

    protected void checkOrientationAndSetRate(String packageName) {
        setRefreshRate(packageName);
    }

    private float getOriginalMinRate() {
        try {
            return mOriginalMin == null ? 0f : Float.parseFloat(mOriginalMin);
        } catch (NumberFormatException e) {
            return 0f;
        }
    }

    private String getValue() {
        String value = mSharedPrefs.getString(REFRESH_CONTROL, null);
        String normalized = ProfileUtils.normalize(value,
                REFRESH_STANDARD, REFRESH_EXTREME, REFRESH_LAND);
        if (!normalized.equals(value)) {
            writeValue(normalized);
        }
        return normalized;
    }

    protected void writePackage(String packageName, int mode) {
        String value = getValue();
        value = ProfileUtils.removePackage(value, packageName);
        String[] modes = value.split(":");
        String finalString;

        switch (mode) {
            case STATE_STANDARD:
                modes[0] = modes[0] + packageName + ",";
                break;
            case STATE_EXTREME:
                modes[1] = modes[1] + packageName + ",";
                break;
            case STATE_LAND:
                modes[2] = modes[2] + packageName + ",";
                break;

        }

        finalString = modes[0] + ":" + modes[1] + ":" + modes[2];

        writeValue(finalString);
    }

    protected int getStateForPackage(String packageName) {
        String value = getValue();
        String[] modes = value.split(":");
        int state = STATE_DEFAULT;
        if (ProfileUtils.contains(modes[0], packageName)) {
            state = STATE_STANDARD;
        } else if (ProfileUtils.contains(modes[1], packageName)) {
            state = STATE_EXTREME;
        } else if (ProfileUtils.contains(modes[2], packageName)) {
            state = STATE_LAND;
        }
        return state;
    }

    protected void setRefreshRate(String packageName) {
        int state = getStateForPackage(packageName);
        if (state == STATE_DEFAULT) {
            restoreRefreshRate();
            return;
        }
        if (!isAppInList) {
            getOldRate();
            // Persist the original values before overriding global display settings.
            if (!mSharedPrefs.edit().putString(ORIGINAL_MAX, mOriginalMax)
                    .putString(ORIGINAL_MIN, mOriginalMin)
                    .putBoolean(OVERRIDE_ACTIVE, true).commit()) {
                return;
            }
            isAppInList = true;
        }
        boolean landscape = mContext.getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
        float maxRate = state == STATE_STANDARD || (state == STATE_LAND && landscape)
                ? REFRESH_STATE_STANDARD : REFRESH_STATE_EXTREME;
        float minRate = Math.max(0f, Math.min(getOriginalMinRate(), maxRate));
        Settings.System.putFloat(mContext.getContentResolver(), KEY_MIN_REFRESH_RATE, minRate);
        Settings.System.putFloat(mContext.getContentResolver(), KEY_PEAK_REFRESH_RATE, maxRate);
    }
}
