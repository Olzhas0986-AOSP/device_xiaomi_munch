package org.lineageos.settings.hbm;
import android.content.Context;
public class HBMFragment {
 public static final String HBM_SWITCH_KEY="hbm",AUTO_HBM_SWITCH_KEY="auto_hbm";
 public static boolean isAUTOHBMEnabled(Context c) { return c.prefs.getBoolean(AUTO_HBM_SWITCH_KEY,false); }
}
