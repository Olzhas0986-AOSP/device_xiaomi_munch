/**
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

import android.content.SharedPreferences;
import android.os.Bundle;

import android.view.MenuItem;

import androidx.preference.Preference;
import androidx.preference.PreferenceManager;

import com.android.settingslib.widget.MainSwitchPreference;
import com.android.settingslib.widget.SettingsBasePreferenceFragment;

import org.lineageos.settings.R;
import org.lineageos.settings.utils.ProfileUtils;
import org.lineageos.settings.widget.SeekBarPreference;

public class TouchSettingsFragment extends SettingsBasePreferenceFragment
        implements Preference.OnPreferenceChangeListener {

    private SharedPreferences mSharedPrefs;
    private SeekBarPreference mTouchSensitivity;
    private SeekBarPreference mTouchResponse;
    private SeekBarPreference mTouchResistant;
    private MainSwitchPreference mGameMode;

    private String packageName = "";

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.touch_settings, rootKey);
        mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(getContext());

        Bundle bundle = getArguments();
        String appName = "";
        if (bundle != null) {
            appName = bundle.getString("appName", "");
            packageName = bundle.getString("packageName", "");
        }

        getActivity().setTitle(getResources().getString(R.string.touch_control_title));

        mGameMode = (MainSwitchPreference) findPreference(Constants.PREF_TOUCH_GAME_MODE);
        mTouchResistant = (SeekBarPreference) findPreference(Constants.PREF_TOUCH_RESISTANT);
        mTouchResponse = (SeekBarPreference) findPreference(Constants.PREF_TOUCH_RESPONSE);
        mTouchSensitivity = (SeekBarPreference) findPreference(Constants.PREF_TOUCH_SENSITIVITY);
        for (Preference preference : new Preference[] {
                mGameMode, mTouchResponse, mTouchSensitivity, mTouchResistant}) {
            preference.setPersistent(false);
        }
        updateDefaults();
        for (Preference preference : new Preference[] {
                mGameMode, mTouchResponse, mTouchSensitivity, mTouchResistant}) {
            preference.setOnPreferenceChangeListener(this);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            getActivity().onBackPressed();
            return true;
        }
        return false;
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        String key = preference.getKey();
        if (Constants.PREF_TOUCH_GAME_MODE.equals(key)) {
            boolean enabled = (Boolean) newValue;
            mTouchSensitivity.setEnabled(enabled);
            mTouchResponse.setEnabled(enabled);
            mTouchResistant.setEnabled(enabled);
            updateTouchModes(enabled ? 1 : 0, Constants.TOUCH_GAME_MODE);
        } else if (Constants.PREF_TOUCH_RESPONSE.equals(key)) {
            updateTouchModes((Integer) newValue, Constants.TOUCH_RESPONSE);
        } else if (Constants.PREF_TOUCH_SENSITIVITY.equals(key)) {
            updateTouchModes((Integer) newValue, Constants.TOUCH_SENSITIVITY);
        } else if (Constants.PREF_TOUCH_RESISTANT.equals(key)) {
            updateTouchModes((Integer) newValue, Constants.TOUCH_RESISTANT);
        }
        return true;
    }

    private void updateDefaults() {
        int[] values = ProfileUtils.parseTouchProfile(getTouchValues());
        boolean modeEnabled = values[Constants.TOUCH_GAME_MODE] == 1;
        mGameMode.setChecked(modeEnabled);

        mTouchSensitivity.setEnabled(modeEnabled);
        mTouchResponse.setEnabled(modeEnabled);
        mTouchResistant.setEnabled(modeEnabled);

        mTouchResponse.setProgress(values[Constants.TOUCH_RESPONSE]);
        mTouchSensitivity.setProgress(values[Constants.TOUCH_SENSITIVITY]);
        mTouchResistant.setProgress(values[Constants.TOUCH_RESISTANT]);
    }

    private void writeTouchValues(String modes) {
        if (!packageName.isEmpty()) {
            mSharedPrefs.edit().putString(packageName, modes).apply();
        }
    }

    public String getTouchValues() {
        String values = mSharedPrefs.getString(packageName, null);
        if (values == null || values.isEmpty()) {
            values = "0,0,0,0";
        }
        return values;
    }

    public void updateTouchModes(int value, int mode) {
        int[] values = ProfileUtils.parseTouchProfile(getTouchValues());
        values[mode] = value;
        String finalValues = String.valueOf(values[Constants.TOUCH_GAME_MODE]) + "," + values[Constants.TOUCH_RESPONSE] + ","
                + values[Constants.TOUCH_SENSITIVITY] + "," + values[Constants.TOUCH_RESISTANT];
        writeTouchValues(finalValues);
    }
}
