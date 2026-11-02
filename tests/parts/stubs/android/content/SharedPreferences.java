package android.content;
public interface SharedPreferences {
 String getString(String key, String fallback); boolean getBoolean(String key, boolean fallback);
 int getInt(String key, int fallback); boolean contains(String key);
 void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener);
 void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener);
 interface OnSharedPreferenceChangeListener { void onSharedPreferenceChanged(SharedPreferences p, String k); }
 Editor edit();
 interface Editor { Editor putString(String k, String v); Editor putBoolean(String k, boolean v); Editor putInt(String k,int v); Editor remove(String k); void apply(); boolean commit(); }
}
