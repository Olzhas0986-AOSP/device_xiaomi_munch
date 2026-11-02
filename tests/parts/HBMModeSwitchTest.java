package org.lineageos.settings.hbm;
import android.content.*;
import android.provider.Settings;
import java.nio.file.*;
public class HBMModeSwitchTest {
 public static void main(String[] args) throws Exception {
  Context context=new Context(); context.prefs=new InMemoryPreferences();
  Path hbm=Files.createTempFile("hbm-", ".txt"), backlight=Files.createTempFile("backlight-", ".txt");
  try {
   Files.write(backlight,"777".getBytes("UTF-8"));
   Settings.System.putInt(context,Settings.System.SCREEN_BRIGHTNESS,90);
   Settings.System.putInt(context,Settings.System.SCREEN_BRIGHTNESS_MODE,1);
   if(!HBMModeSwitch.executeHBMAction(context,context.prefs,true,hbm.toString(),backlight.toString())) throw new AssertionError();
   HBMModeSwitch.executeHBMAction(context,context.prefs,true,hbm.toString(),backlight.toString());
   if(!HBMModeSwitch.executeHBMAction(context,context.prefs,false,hbm.toString(),backlight.toString())) throw new AssertionError();
   if(Settings.System.getInt(context,Settings.System.SCREEN_BRIGHTNESS,-1)!=90 || Settings.System.getInt(context,Settings.System.SCREEN_BRIGHTNESS_MODE,-1)!=1) throw new AssertionError("Brightness settings were lost");
   if(!"777".equals(new String(Files.readAllBytes(backlight),"UTF-8"))) throw new AssertionError("Raw backlight was not restored");
   if(context.prefs.contains("last_brightness")) throw new AssertionError("Stale brightness backup");
   if(Files.exists(Paths.get("/dev/full"))) {
    if(HBMModeSwitch.executeHBMAction(context,context.prefs,true,"/dev/full",backlight.toString())) throw new AssertionError("Failed HBM write reported success");
    if(context.prefs.contains("last_brightness")) throw new AssertionError("Failed enable left a stale brightness backup");
   }
   if(HBMModeSwitch.executeHBMAction(context,context.prefs,true,null,null)) throw new AssertionError("Unsupported HBM reported success");
  } finally { Files.delete(hbm); Files.delete(backlight); }
  System.out.println("HBM brightness/idempotence/unsupported-node tests passed");
 }
}
