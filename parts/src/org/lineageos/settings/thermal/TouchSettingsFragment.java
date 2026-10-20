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
        if (mGameMode != null) {
            mGameMode.setPersistent(false);
            mGameMode.setOnPreferenceChangeListener(this);
        }

        mTouchResistant = (SeekBarPreference) findPreference(Constants.PREF_TOUCH_RESISTANT);
        mTouchResponse = (SeekBarPreference) findPreference(Constants.PREF_TOUCH_RESPONSE);
        mTouchSensitivity = (SeekBarPreference) findPreference(Constants.PREF_TOUCH_SENSITIVITY);
        mTouchResistant.setPersistent(false);
        mTouchResponse.setPersistent(false);
        mTouchSensitivity.setPersistent(false);
        mTouchResistant.setOnPreferenceChangeListener(this);
        mTouchResponse.setOnPreferenceChangeListener(this);
        mTouchSensitivity.setOnPreferenceChangeListener(this);
        updateDefaults();
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
        if (Constants.PREF_TOUCH_GAME_MODE.equals(preference.getKey())) {
            boolean isChecked = (Boolean) newValue;
            mTouchSensitivity.setEnabled(isChecked);
            mTouchResponse.setEnabled(isChecked);
            mTouchResistant.setEnabled(isChecked);
            updateTouchModes(isChecked ? 1 : 0, Constants.TOUCH_GAME_MODE);
        }
        if (Constants.PREF_TOUCH_RESPONSE.equals(preference.getKey())) {
            updateTouchModes((Integer) newValue, Constants.TOUCH_RESPONSE);
        } else if (Constants.PREF_TOUCH_SENSITIVITY.equals(preference.getKey())) {
            updateTouchModes((Integer) newValue, Constants.TOUCH_SENSITIVITY);
        } else if (Constants.PREF_TOUCH_RESISTANT.equals(preference.getKey())) {
            updateTouchModes((Integer) newValue, Constants.TOUCH_RESISTANT);
        }
        return true;
    }

    private void updateDefaults() {
        String[] values = getTouchValues().split(",");
        boolean modeEnabled = Integer.parseInt(values[Constants.TOUCH_GAME_MODE]) == 1;
        mGameMode.setChecked(modeEnabled);

        mTouchSensitivity.setEnabled(modeEnabled);
        mTouchResponse.setEnabled(modeEnabled);
        mTouchResistant.setEnabled(modeEnabled);

        mTouchResponse.setProgress(Integer.parseInt(values[Constants.TOUCH_RESPONSE]));
        mTouchSensitivity.setProgress(Integer.parseInt(values[Constants.TOUCH_SENSITIVITY]));
        mTouchResistant.setProgress(Integer.parseInt(values[Constants.TOUCH_RESISTANT]));
    }

    private void writeTouchValues(String modes) {
        if (!packageName.isEmpty()) mSharedPrefs.edit().putString(packageName, modes).apply();
    }

    public String getTouchValues() {
        int[] values = ThermalUtils.parseTouchValues(mSharedPrefs.getString(packageName, null));
        return values[0] + "," + values[1] + "," + values[2] + "," + values[3];
    }

    public void updateTouchModes(int value, int mode) {
        String[] values = getTouchValues().split(",");
        values[mode] = String.valueOf(value);
        String finalValues = values[Constants.TOUCH_GAME_MODE] + "," + values[Constants.TOUCH_RESPONSE] + ","
                + values[Constants.TOUCH_SENSITIVITY] + "," + values[Constants.TOUCH_RESISTANT];
        writeTouchValues(finalValues);
    }
}
