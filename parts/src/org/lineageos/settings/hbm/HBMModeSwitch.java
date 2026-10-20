package org.lineageos.settings.hbm;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.widget.Toast;

import androidx.preference.Preference;
import androidx.preference.PreferenceManager;

import org.lineageos.settings.R;
import org.lineageos.settings.utils.FileUtils;

public class HBMModeSwitch implements Preference.OnPreferenceChangeListener {
    public static final String HBM_NODE =
            "/sys/devices/platform/soc/soc:qcom,dsi-display-primary/hbm";
    private static final String BACKLIGHT_NODE = "/sys/class/backlight/panel0-backlight/brightness";
    private static final String MAX_BACKLIGHT_NODE = "/sys/class/backlight/panel0-backlight/max_brightness";
    private final Context mContext;

    public HBMModeSwitch(Context context) { mContext = context; }

    public static String getHBM() {
        return FileUtils.isFileWritable(HBM_NODE) ? HBM_NODE : null;
    }

    public static String getBACKLIGHT() {
        return FileUtils.isFileWritable(BACKLIGHT_NODE) ? BACKLIGHT_NODE : null;
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        boolean enabled = (Boolean) newValue;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        if (enabled && prefs.getBoolean("dc_dimming", false)) {
            Toast.makeText(mContext, R.string.hbm_disable_dc_dimming_first, Toast.LENGTH_SHORT).show();
            return false;
        }
        if (enabled && prefs.getBoolean(HBMFragment.AUTO_HBM_SWITCH_KEY, false)) return false;
        if (!executeHBMAction(mContext, prefs, enabled)) return false;
        prefs.edit().putBoolean(HBMFragment.HBM_SWITCH_KEY, enabled).apply();
        FileUtils.enableService(mContext);
        return true;
    }

    public static boolean isEnabled() {
        return FileUtils.getFileValueAsBoolean(HBM_NODE, false);
    }

    public static synchronized boolean executeHBMAction(
            Context context, SharedPreferences prefs, boolean enabled) {
        if (getHBM() == null) return false;
        if (enabled) {
            if (prefs.getBoolean("dc_dimming", false)) return false;
            if (isEnabled()) return true;
            String rawBrightness = FileUtils.readOneLine(BACKLIGHT_NODE);
            String maxBrightness = FileUtils.readOneLine(MAX_BACKLIGHT_NODE);
            try {
                if (getBACKLIGHT() == null || Integer.parseInt(maxBrightness) <= 0
                        || Integer.parseInt(rawBrightness) < 0) return false;
            } catch (NumberFormatException e) {
                return false;
            }
            prefs.edit()
                    .putInt("last_brightness", Settings.System.getInt(context.getContentResolver(),
                            Settings.System.SCREEN_BRIGHTNESS, 128))
                    .putInt("last_brightness_mode", Settings.System.getInt(context.getContentResolver(),
                            Settings.System.SCREEN_BRIGHTNESS_MODE,
                            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL))
                    .putString("last_raw_brightness", rawBrightness).apply();
            if (!FileUtils.writeLine(HBM_NODE, "1")) {
                clearSnapshot(prefs);
                return false;
            }
            Settings.System.putInt(context.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
            Settings.System.putInt(context.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, 255);
            if (!FileUtils.writeLine(BACKLIGHT_NODE, maxBrightness)) {
                executeHBMAction(context, prefs, false);
                return false;
            }
            return true;
        }
        if (!FileUtils.writeLine(HBM_NODE, "0")) return false;
        if (prefs.contains("last_brightness")) {
            Settings.System.putInt(context.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS,
                    prefs.getInt("last_brightness", 128));
            // Framework brightness and raw panel backlight use different scales.
            String raw = prefs.getString("last_raw_brightness", null);
            if (raw != null) FileUtils.writeLine(BACKLIGHT_NODE, raw);
            Settings.System.putInt(context.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE,
                    prefs.getInt("last_brightness_mode", Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL));
            clearSnapshot(prefs);
        }
        return true;
    }

    private static void clearSnapshot(SharedPreferences prefs) {
        prefs.edit().remove("last_brightness").remove("last_brightness_mode")
                .remove("last_raw_brightness").apply();
    }
}
