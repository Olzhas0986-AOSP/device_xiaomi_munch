package android.content;
import android.content.res.Resources;
public class Context {
 public SharedPreferences prefs; public Resources resources = new Resources();
 public Object getContentResolver() { return this; }
 public Resources getResources() { return resources; }
 public void startServiceAsUser(Intent intent, Object user) {}
}
