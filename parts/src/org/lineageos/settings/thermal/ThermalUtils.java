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

        if (value != null) {
            String[] modes = value.split(":", -1);
            // Preserve assignments made before the streaming profile was added.
            if (modes.length == 5) {
                value += ":" + THERMAL_STREAMING;
                modes = value.split(":", -1);
            }
            String[] prefixes = {THERMAL_BENCHMARK, THERMAL_BROWSER, THERMAL_CAMERA,
                    THERMAL_DIALER, THERMAL_GAMING, THERMAL_STREAMING};
            if (modes.length != prefixes.length) {
                value = null;
            } else {
                for (int i = 0; i < prefixes.length; i++) {
                    if (!modes[i].startsWith(prefixes[i])) {
                        value = null;
                        break;
                    }
                }
            }
            if (value != null && !value.equals(mSharedPrefs.getString(THERMAL_CONTROL, null))) {
                writeValue(value);
            }
        }

        if (value == null || value.isEmpty()) {
            value = THERMAL_BENCHMARK + ":" + THERMAL_BROWSER + ":" + THERMAL_CAMERA + ":" +
                    THERMAL_DIALER + ":" + THERMAL_GAMING + ":" + THERMAL_STREAMING;
            writeValue(value);
        }
        return value;
    }

    protected void writePackage(String packageName, int mode) {
        String value = getValue();
        String[] modes = value.split(":", -1);
        for (int i = 0; i < modes.length; i++) {
            int separator = modes[i].indexOf('=');
            if (separator < 0) {
                return;
            }
            String packages = "," + modes[i].substring(separator + 1);
            packages = packages.replace("," + packageName + ",", ",");
            modes[i] = modes[i].substring(0, separator + 1) + packages.substring(1);
        }
        String finalString;

        switch (mode) {
            case STATE_BENCHMARK:
                modes[0] = modes[0] + packageName + ",";
                break;
            case STATE_BROWSER:
                modes[1] = modes[1] + packageName + ",";
                break;
            case STATE_CAMERA:
                modes[2] = modes[2] + packageName + ",";
                break;
            case STATE_DIALER:
                modes[3] = modes[3] + packageName + ",";
                break;
            case STATE_GAMING:
                modes[4] = modes[4] + packageName + ",";
                break;
            case STATE_STREAMING:
                modes[5] = modes[5] + packageName + ",";
                break;
        }

        finalString = modes[0] + ":" + modes[1] + ":" + modes[2] + ":" + modes[3] + ":" +
                modes[4] + ":" + modes[5];

        writeValue(finalString);
    }

    protected int getStateForPackage(String packageName) {
        String value = getValue();
        String[] modes = value.split(":", -1);
        int state = STATE_DEFAULT;
        if (containsPackage(modes[0], packageName)) {
            state = STATE_BENCHMARK;
        } else if (containsPackage(modes[1], packageName)) {
            state = STATE_BROWSER;
        } else if (containsPackage(modes[2], packageName)) {
            state = STATE_CAMERA;
        } else if (containsPackage(modes[3], packageName)) {
            state = STATE_DIALER;
        } else if (containsPackage(modes[4], packageName)) {
            state = STATE_GAMING;
        } else if (containsPackage(modes[5], packageName)) {
            state = STATE_STREAMING;
        }

        return state;
    }

    private static boolean containsPackage(String mode, String packageName) {
        int separator = mode.indexOf('=');
        return separator >= 0 && ("," + mode.substring(separator + 1))
                .contains("," + packageName + ",");
    }

    protected boolean setDefaultThermalProfile() {
        return FileUtils.writeLine(THERMAL_SCONFIG, THERMAL_STATE_DEFAULT);
    }

    protected boolean setThermalProfile(String packageName) {
        String value = getValue();
        String modes[];
        String state = THERMAL_STATE_DEFAULT;

        if (value != null) {
            modes = value.split(":", -1);

            if (containsPackage(modes[0], packageName)) {
                state = THERMAL_STATE_BENCHMARK;
            } else if (containsPackage(modes[1], packageName)) {
                state = THERMAL_STATE_BROWSER;
            } else if (containsPackage(modes[2], packageName)) {
                state = THERMAL_STATE_CAMERA;
            } else if (containsPackage(modes[3], packageName)) {
                state = THERMAL_STATE_DIALER;
            } else if (containsPackage(modes[4], packageName)) {
                state = THERMAL_STATE_GAMING;
            } else if (containsPackage(modes[5], packageName)) {
                state = THERMAL_STATE_STREAMING;
            }
        }
        return FileUtils.writeLine(THERMAL_SCONFIG, state);
    }
}
