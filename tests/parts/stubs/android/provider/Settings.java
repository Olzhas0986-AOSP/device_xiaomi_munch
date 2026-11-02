package android.provider;
public class Settings {
 public static class System {
  public static final java.util.Map<String,String> values=new java.util.HashMap<>();
  public static String getString(Object resolver, String key) { return values.get(key); }
  public static boolean putString(Object resolver, String key, String value) { if(value==null) values.remove(key); else values.put(key,value); return true; }
  public static boolean putFloat(Object resolver, String key, float value) { return putString(resolver,key,String.valueOf(value)); }
 }
}
