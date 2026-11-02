package android.content;
import android.content.res.Resources;
public class Context {
 public SharedPreferences prefs; public Resources resources = new Resources();
 public java.util.Map<Class<?>,Object> services = new java.util.HashMap<>();
 public <T> T getSystemService(Class<T> cls) { return cls.cast(services.get(cls)); }
 public Object getContentResolver() { return this; }
 public Resources getResources() { return resources; }
 public void startServiceAsUser(Intent intent, Object user) {}
 public void stopServiceAsUser(Intent intent, Object user) {}
}
