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
import android.content.res.Configuration;
import android.os.UserHandle;
import android.provider.Settings;

import androidx.preference.PreferenceManager;

public final class RefreshUtils {
    private static final String REFRESH_CONTROL = "refresh_control";
    private static final String KEY_PEAK_REFRESH_RATE = "peak_refresh_rate";
    private static final String KEY_MIN_REFRESH_RATE = "min_refresh_rate";
    private static final String SAVED_PEAK = "refresh_saved_peak";
    private static final String SAVED_MIN = "refresh_saved_min";
    private static final String[] PREFIXES = {
        "refresh.standard=", "refresh.extreme=", "refresh.land="
    };

    protected static final int STATE_DEFAULT = 0;
    protected static final int STATE_STANDARD = 1;
    protected static final int STATE_EXTREME = 2;
    protected static final int STATE_LAND = 3;

    private final Context mContext;
    private final SharedPreferences mSharedPrefs;
    private boolean mOverrideActive;

    protected RefreshUtils(Context context) {
        mContext = context;
        mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
    }

    public static void startService(Context context) {
        context.startServiceAsUser(new Intent(context, RefreshService.class), UserHandle.CURRENT);
    }

    private String[] getProfiles() {
        String value = mSharedPrefs.getString(REFRESH_CONTROL, "");
        String[] modes = value.split(":", -1);
        if (modes.length == PREFIXES.length) {
            boolean valid = true;
            for (int i = 0; i < modes.length; i++) {
                valid &= modes[i].startsWith(PREFIXES[i]);
            }
            if (valid) return modes;
        }
        modes = PREFIXES.clone();
        mSharedPrefs.edit().putString(REFRESH_CONTROL, String.join(":", modes)).apply();
        return modes;
    }

    private boolean containsPackage(String mode, int index, String packageName) {
        for (String entry : mode.substring(PREFIXES[index].length()).split(",")) {
            if (entry.equals(packageName)) return true;
        }
        return false;
    }

    protected void writePackage(String packageName, int mode) {
        if (packageName == null || packageName.isEmpty() || mode < 0 || mode > STATE_LAND) return;
        String[] modes = getProfiles();
        for (int i = 0; i < modes.length; i++) {
            StringBuilder result = new StringBuilder(PREFIXES[i]);
            for (String entry : modes[i].substring(PREFIXES[i].length()).split(",")) {
                if (!entry.isEmpty() && !entry.equals(packageName)) result.append(entry).append(',');
            }
            if (mode == i + 1) result.append(packageName).append(',');
            modes[i] = result.toString();
        }
        mSharedPrefs.edit().putString(REFRESH_CONTROL, String.join(":", modes)).apply();
    }

    protected int getStateForPackage(String packageName) {
        if (packageName == null || packageName.isEmpty()) return STATE_DEFAULT;
        String[] modes = getProfiles();
        for (int i = 0; i < modes.length; i++) {
            if (containsPackage(modes[i], i, packageName)) return i + 1;
        }
        return STATE_DEFAULT;
    }

    protected void setRefreshRate(String packageName) {
        int state = getStateForPackage(packageName);
        if (state == STATE_DEFAULT) {
            restoreDefaultRate();
            return;
        }
        if (!mOverrideActive) {
            // Persist the original strings, including an absent setting, across process death.
            if (!mSharedPrefs.contains(SAVED_PEAK)) {
                mSharedPrefs.edit()
                        .putString(SAVED_PEAK, readSetting(KEY_PEAK_REFRESH_RATE))
                        .putString(SAVED_MIN, readSetting(KEY_MIN_REFRESH_RATE)).apply();
            }
            mOverrideActive = true;
        }
        boolean landscape = mContext.getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
        float max = state == STATE_STANDARD || (state == STATE_LAND && landscape) ? 60f : 120f;
        float min = 0f;
        try {
            String savedMin = mSharedPrefs.getString(SAVED_MIN, "");
            if (!savedMin.isEmpty()) min = Float.parseFloat(savedMin);
        } catch (NumberFormatException ignored) { }
        if (!Float.isFinite(min) || min < 0f) min = 0f;
        putSetting(KEY_MIN_REFRESH_RATE, Float.toString(Math.min(min, max)));
        putSetting(KEY_PEAK_REFRESH_RATE, Float.toString(max));
    }

    private String readSetting(String key) {
        String value = Settings.System.getString(mContext.getContentResolver(), key);
        return value == null ? "" : value;
    }

    private void putSetting(String key, String value) {
        if (!value.equals(readSetting(key))) {
            Settings.System.putString(mContext.getContentResolver(), key,
                    value.isEmpty() ? null : value);
        }
    }

    protected void restoreDefaultRate() {
        if (mSharedPrefs.contains(SAVED_PEAK)) {
            putSetting(KEY_MIN_REFRESH_RATE, mSharedPrefs.getString(SAVED_MIN, ""));
            putSetting(KEY_PEAK_REFRESH_RATE, mSharedPrefs.getString(SAVED_PEAK, ""));
            mSharedPrefs.edit().remove(SAVED_MIN).remove(SAVED_PEAK).apply();
        }
        mOverrideActive = false;
    }
}
