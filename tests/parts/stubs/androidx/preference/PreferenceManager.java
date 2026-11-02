package androidx.preference; import android.content.*; public class PreferenceManager { public static SharedPreferences getDefaultSharedPreferences(Context c) { return c.prefs; } }
