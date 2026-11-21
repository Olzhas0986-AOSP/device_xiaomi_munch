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

package org.lineageos.settings.thermal;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.UserHandle;

import androidx.preference.PreferenceManager;

import org.lineageos.settings.utils.FileUtils;

import java.util.Arrays;

public final class ThermalUtils {

    protected static final int STATE_DEFAULT = 0;
    protected static final int STATE_BENCHMARK = 1;
    protected static final int STATE_BROWSER = 2;
    protected static final int STATE_CAMERA = 3;
    protected static final int STATE_DIALER = 4;
    protected static final int STATE_GAMING = 5;
    protected static final int STATE_STREAMING = 6;
    private static final String THERMAL_CONTROL = "thermal_control";
    private static final String THERMAL_STATE_DEFAULT = "0";
    private static final String THERMAL_STATE_BENCHMARK = "10";
    private static final String THERMAL_STATE_BROWSER = "11";
    private static final String THERMAL_STATE_CAMERA = "12";
    private static final String THERMAL_STATE_DIALER = "8";
    private static final String THERMAL_STATE_GAMING = "9";
    private static final String THERMAL_STATE_STREAMING = "14";

    private static final String THERMAL_BENCHMARK = "thermal.benchmark=";
    private static final String THERMAL_BROWSER = "thermal.browser=";
    private static final String THERMAL_CAMERA = "thermal.camera=";
    private static final String THERMAL_DIALER = "thermal.dialer=";
    private static final String THERMAL_GAMING = "thermal.gaming=";
    private static final String THERMAL_STREAMING = "thermal.streaming=";

    private static final String THERMAL_SCONFIG = "/sys/class/thermal/thermal_message/sconfig";

    // sconfig values, in the same order as the modes stored in THERMAL_CONTROL
    private static final String[] THERMAL_STATES = {
            THERMAL_STATE_BENCHMARK,
            THERMAL_STATE_BROWSER,
            THERMAL_STATE_CAMERA,
            THERMAL_STATE_DIALER,
            THERMAL_STATE_GAMING,
            THERMAL_STATE_STREAMING,
    };
    private static final int NUM_MODES = THERMAL_STATES.length;

    private SharedPreferences mSharedPrefs;

    protected ThermalUtils(Context context) {
        mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
    }

    public static void startService(Context context) {
        if (FileUtils.fileExists(THERMAL_SCONFIG)) {
            context.startServiceAsUser(new Intent(context, ThermalService.class),
                    UserHandle.CURRENT);
        }
    }

    private void writeValue(String profiles) {
        mSharedPrefs.edit().putString(THERMAL_CONTROL, profiles).apply();
    }

    private String getValue() {
        String value = mSharedPrefs.getString(THERMAL_CONTROL, null);

        if (value != null && value.split(":").length < NUM_MODES) {
            value = null;
        }

        if (value == null || value.isEmpty()) {
            value = THERMAL_BENCHMARK + ":" + THERMAL_BROWSER + ":" + THERMAL_CAMERA + ":" +
                    THERMAL_DIALER + ":" + THERMAL_GAMING + ":" + THERMAL_STREAMING;
            writeValue(value);
        }
        return value;
    }

    private static boolean hasPackage(String mode, String packageName) {
        String packages = mode.substring(mode.indexOf('=') + 1);
        for (String pkg : packages.split(",")) {
            if (pkg.equals(packageName)) {
                return true;
            }
        }
        return false;
    }

    private static String removePackage(String mode, String packageName) {
        int split = mode.indexOf('=') + 1;
        StringBuilder sb = new StringBuilder(mode.substring(0, split));
        for (String pkg : mode.substring(split).split(",")) {
            if (!pkg.isEmpty() && !pkg.equals(packageName)) {
                sb.append(pkg).append(',');
            }
        }
        return sb.toString();
    }

    /** Returns the index of the mode containing the package, or -1 if none. */
    private int getModeIndexForPackage(String packageName) {
        String[] modes = getValue().split(":");
        for (int i = 0; i < NUM_MODES; i++) {
            if (hasPackage(modes[i], packageName)) {
                return i;
            }
        }
        return -1;
    }

    protected void writePackage(String packageName, int mode) {
        String[] modes = getValue().split(":");

        for (int i = 0; i < NUM_MODES; i++) {
            modes[i] = removePackage(modes[i], packageName);
        }

        // STATE_DEFAULT is 0, the remaining states map to modes[state - 1]
        if (mode > STATE_DEFAULT && mode <= NUM_MODES) {
            modes[mode - 1] = modes[mode - 1] + packageName + ",";
        }

        writeValue(String.join(":", Arrays.copyOf(modes, NUM_MODES)));
    }

    protected int getStateForPackage(String packageName) {
        // States are ordered the same way as modes, offset by STATE_DEFAULT
        return getModeIndexForPackage(packageName) + 1;
    }

    protected void setDefaultThermalProfile() {
        FileUtils.writeLine(THERMAL_SCONFIG, THERMAL_STATE_DEFAULT);
    }

    protected void setThermalProfile(String packageName) {
        int index = getModeIndexForPackage(packageName);
        String state = index >= 0 ? THERMAL_STATES[index] : THERMAL_STATE_DEFAULT;
        FileUtils.writeLine(THERMAL_SCONFIG, state);
    }
}
