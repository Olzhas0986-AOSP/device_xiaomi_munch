package org.lineageos.settings.hbm;

import android.provider.Settings;
import android.content.Context;
import android.content.SharedPreferences;
import androidx.preference.Preference;
import androidx.preference.Preference.OnPreferenceChangeListener;
import androidx.preference.PreferenceManager;
import android.widget.Toast;
import org.lineageos.settings.R;
import org.lineageos.settings.utils.FileUtils;

public class HBMModeSwitch implements OnPreferenceChangeListener {
    private static final String DC_DIMMING_KEY = "dc_dimming";
    private static final String HBM_NODE = "/sys/devices/platform/soc/soc:qcom,dsi-display-primary/hbm";
    private static final String BACKLIGHT_NODE = "/sys/class/backlight/panel0-backlight/brightness";
    private final Context mContext;

    public HBMModeSwitch(Context context) {
        mContext = context;
    }

    public static String getHBM() {
        return FileUtils.isFileWritable(HBM_NODE) ? HBM_NODE : null;
    }

    public static String getBACKLIGHT() {
        return FileUtils.isFileWritable(BACKLIGHT_NODE) ? BACKLIGHT_NODE : null;
    }

    public static boolean isEnabled(Context context) {
        boolean requested = PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(HBMFragment.HBM_SWITCH_KEY, false);
        return FileUtils.getFileValueAsBoolean(HBM_NODE, requested);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        boolean enabled = (Boolean) newValue;

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        boolean dcDimmingEnabled = prefs.getBoolean(DC_DIMMING_KEY, false);

        if (enabled && dcDimmingEnabled) {
            Toast.makeText(
                    mContext,
                    R.string.hbm_disable_dc_dimming_first,
                    Toast.LENGTH_SHORT
            ).show();
            return false;
        }

        if (!executeHBMAction(mContext, prefs, enabled)) return false;
        prefs.edit().putBoolean(HBMFragment.HBM_SWITCH_KEY, enabled).apply();
        FileUtils.enableService(mContext);
        return true;
    }

    public static boolean executeHBMAction(Context context, SharedPreferences prefs, boolean enabled) {
        return executeHBMAction(context, prefs, enabled, getHBM(), getBACKLIGHT());
    }

    static boolean executeHBMAction(Context context, SharedPreferences prefs, boolean enabled,
            String hbmNode, String backlightNode) {
        if (hbmNode == null) return false;

        if (enabled) {
            if (!prefs.contains("last_brightness")) {
                int brightness = Settings.System.getInt(context.getContentResolver(),
                        Settings.System.SCREEN_BRIGHTNESS, 128);
                int mode = Settings.System.getInt(context.getContentResolver(),
                        Settings.System.SCREEN_BRIGHTNESS_MODE,
                        Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
                String raw = backlightNode == null ? null : FileUtils.readOneLine(backlightNode);
                if (!prefs.edit().putInt("last_brightness", brightness)
                        .putInt("last_brightness_mode", mode)
                        .putString("last_raw_brightness", raw).commit()) return false;
            }
            if (!FileUtils.writeLine(hbmNode, "1")) return false;
            Settings.System.putInt(context.getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
            Settings.System.putInt(context.getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS, 255);
            if (backlightNode != null) FileUtils.writeLine(backlightNode, "2047");
        } else {
            if (!FileUtils.writeLine(hbmNode, "0")) return false;
            if (prefs.contains("last_brightness")) {
                Settings.System.putInt(context.getContentResolver(),
                        Settings.System.SCREEN_BRIGHTNESS, prefs.getInt("last_brightness", 128));
                String raw = prefs.getString("last_raw_brightness", null);
                if (backlightNode != null && raw != null) FileUtils.writeLine(backlightNode, raw);
                Settings.System.putInt(context.getContentResolver(),
                        Settings.System.SCREEN_BRIGHTNESS_MODE,
                        prefs.getInt("last_brightness_mode",
                                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL));
                prefs.edit().remove("last_brightness").remove("last_brightness_mode")
                        .remove("last_raw_brightness").apply();
            }
        }
        return true;
    }
}
