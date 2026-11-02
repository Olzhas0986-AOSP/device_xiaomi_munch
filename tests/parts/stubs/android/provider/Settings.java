package android.provider;
public class Settings {
 public static class System {
  public static final String SCREEN_BRIGHTNESS="screen_brightness",SCREEN_BRIGHTNESS_MODE="screen_brightness_mode";
  public static final int SCREEN_BRIGHTNESS_MODE_MANUAL=0;
  public static final java.util.Map<String,String> values=new java.util.HashMap<>();
  public static String getString(Object resolver, String key) { return values.get(key); }
  public static boolean putString(Object resolver, String key, String value) { if(value==null) values.remove(key); else values.put(key,value); return true; }
  public static boolean putFloat(Object resolver, String key, float value) { return putString(resolver,key,String.valueOf(value)); }
  public static boolean putInt(Object resolver,String key,int v) { return putString(resolver,key,String.valueOf(v)); }
  public static int getInt(Object resolver,String key,int d) { return values.containsKey(key) ? Integer.parseInt(values.get(key)) : d; }
 }
}
