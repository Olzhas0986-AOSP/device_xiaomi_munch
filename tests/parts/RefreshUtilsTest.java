package org.lineageos.settings.refreshrate;

import android.content.*;
import android.provider.Settings;
import org.lineageos.settings.refreshrate.RefreshUtils;
import java.util.*;

public class RefreshUtilsTest {
    static void rates(String min, String max) {
        if (!Objects.equals(min, Settings.System.values.get("min_refresh_rate"))
                || !Objects.equals(max, Settings.System.values.get("peak_refresh_rate"))) {
            throw new AssertionError(Settings.System.values.toString());
        }
    }
    public static void main(String[] args) {
        Context context = new Context(); context.prefs = new InMemoryPreferences();
        context.prefs.edit().putString("refresh_control",
                "refresh.standard=com.low,:refresh.extreme=com.high,:refresh.land=com.land,").commit();
        Settings.System.values.put("min_refresh_rate", "90");
        Settings.System.values.put("peak_refresh_rate", "90");
        RefreshUtils utils = new RefreshUtils(context);
        utils.setRefreshRate("com.low"); rates("60.0", "60.0");
        utils.setRefreshRate("com.high"); rates("90.0", "120.0");
        utils.setRefreshRate("com.other"); rates("90", "90");
        utils.setRefreshRate("com.low");
        new RefreshUtils(context).setRefreshRate("com.other"); rates("90", "90");
        context.resources.configuration.orientation = 2;
        utils = new RefreshUtils(context); utils.setRefreshRate("com.land"); rates("60.0", "60.0");
        context.resources.configuration.orientation = 1;
        utils.setRefreshRate("com.land"); rates("90.0", "120.0");
        utils.setRefreshRate("com.other"); rates("90", "90");
        Settings.System.values.clear();
        utils.setRefreshRate("com.low"); rates("0.0", "60.0");
        utils.setRefreshRate("com.other"); rates(null, null);
        System.out.println("RefreshUtils transition/restart/orientation tests passed");
    }
}
