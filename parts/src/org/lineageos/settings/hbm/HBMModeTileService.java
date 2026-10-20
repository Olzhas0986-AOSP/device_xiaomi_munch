package org.lineageos.settings.hbm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import androidx.preference.PreferenceManager;
import android.widget.Toast;
import org.lineageos.settings.R;
import org.lineageos.settings.utils.FileUtils;

public class HBMModeTileService extends TileService {

    private static final String DC_DIMMING_KEY = "dc_dimming";
    private static final String HBM_KEY = "hbm";

    private final SharedPreferences.OnSharedPreferenceChangeListener mPrefsListener =
            (prefs, key) -> {
                if (HBM_KEY.equals(key)) {
                    updateUI(prefs.getBoolean(HBM_KEY, false));
                }
            };

    private void updateUI(boolean enabled) {
        final Tile tile = getQsTile();
        if (tile != null) {
            tile.setState(HBMModeSwitch.getHBM() == null ? Tile.STATE_UNAVAILABLE
                    : enabled ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
            tile.updateTile();
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        PreferenceManager.getDefaultSharedPreferences(this)
                .registerOnSharedPreferenceChangeListener(mPrefsListener);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        PreferenceManager.getDefaultSharedPreferences(this)
                .unregisterOnSharedPreferenceChangeListener(mPrefsListener);
    }

    @Override
    public void onStartListening() {
        super.onStartListening();
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        updateUI(sharedPrefs.getBoolean(HBM_KEY, false));
    }

    @Override
    public void onStopListening() {
        super.onStopListening();
    }

    @Override
    public void onClick() {
        super.onClick();

        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean dcDimmingEnabled = sharedPrefs.getBoolean(DC_DIMMING_KEY, false);

        if (dcDimmingEnabled && !sharedPrefs.getBoolean(HBM_KEY, false)) {
            Toast.makeText(
                    this,
                    R.string.hbm_disable_dc_dimming_first,
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        final boolean enabled = !sharedPrefs.getBoolean(HBM_KEY, false);

        if (enabled && sharedPrefs.getBoolean(HBMFragment.AUTO_HBM_SWITCH_KEY, false)) return;
        if (HBMModeSwitch.executeHBMAction(this, sharedPrefs, enabled)) {
            sharedPrefs.edit().putBoolean(HBM_KEY, enabled).apply();
            FileUtils.enableService(this);
        }
    }
}
