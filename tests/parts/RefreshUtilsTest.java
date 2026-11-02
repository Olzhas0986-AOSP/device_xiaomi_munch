package org.lineageos.settings.refreshrate;

import android.content.*;
import android.provider.Settings;
import org.lineageos.settings.refreshrate.RefreshUtils;
import java.util.*;

public class RefreshUtilsTest {
    static class Prefs implements SharedPreferences {
        Map<String,Object> values = new HashMap<>();
        public String getString(String k, String d) { return (String) values.getOrDefault(k, d); }
        public boolean getBoolean(String k, boolean d) { return (Boolean) values.getOrDefault(k, d); }
        public Editor edit() { return new Editor() {
            Map<String,Object> pending = new HashMap<>();
            public Editor putString(String k,String v) { pending.put(k,v); return this; }
            public Editor putBoolean(String k,boolean v) { pending.put(k,v); return this; }
            public Editor remove(String k) { pending.put(k,null); return this; }
            public boolean commit() { apply(); return true; }
            public void apply() { pending.forEach((k,v) -> { if(v==null) values.remove(k); else values.put(k,v); }); }
        }; }
    }
    static void rates(String min, String max) {
        if (!Objects.equals(min, Settings.System.values.get("min_refresh_rate"))
                || !Objects.equals(max, Settings.System.values.get("peak_refresh_rate"))) {
            throw new AssertionError(Settings.System.values.toString());
        }
    }
    public static void main(String[] args) {
        Context context = new Context(); context.prefs = new Prefs();
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
