package org.lineageos.settings.thermal;
import android.content.*;
import android.view.WindowManager;
import vendor.xiaomi.hardware.touchfeature.V1_0.ITouchFeature;

public class ThermalUtilsTest {
 public static void main(String[] args) {
  Context context=new Context(); context.prefs=new InMemoryPreferences();
  context.services.put(WindowManager.class,new WindowManager());
  context.prefs.edit().putString("thermal_control", "thermal.benchmark=:thermal.browser=:thermal.camera=:thermal.dialer=:thermal.gaming=com.game,:thermal.streaming=")
    .putString("com.game","0,3,3,3").commit();
  ThermalUtils utils=new ThermalUtils(context);
  utils.setThermalProfile("com.game"); // Missing HAL must not crash.
  ITouchFeature.service=new ITouchFeature();
  utils.setThermalProfile("com.game");
  if(ITouchFeature.service.modes.get(Constants.MODE_TOUCH_ACTIVE_MODE)!=0)
   throw new AssertionError("Disabled game mode still enabled active touch mode");
  context.prefs.edit().putString("com.game","1,3,2,1").commit();
  utils.setThermalProfile("com.game");
  if(ITouchFeature.service.modes.get(Constants.MODE_TOUCH_GAME_MODE)!=1)
   throw new AssertionError("Game profile not applied after HAL becomes available");
  utils.setThermalProfile("com.other");
  if(!ITouchFeature.service.modes.isEmpty()) throw new AssertionError("Touch profile leaked into another app");
  context.prefs.edit().putString("com.game","1,bad").commit();
  utils.setThermalProfile("com.game");
  utils.resetTouchModes();
  ITouchFeature.service.failAt=ITouchFeature.service.calls+1;
  utils.setThermalProfile("com.game"); // One HAL write succeeds before the next fails.
  utils.setThermalProfile("com.other");
  if(!ITouchFeature.service.modes.isEmpty()) throw new AssertionError("Partially applied modes were not reset");
  utils.setThermalProfile("com.game");
  ITouchFeature.service.failAt=ITouchFeature.service.calls+1;
  utils.resetTouchModes(); // Retry a partially failed reset on the next update.
  utils.setThermalProfile("com.other");
  if(!ITouchFeature.service.modes.isEmpty()) throw new AssertionError("Failed reset was not retried");
  System.out.println("ThermalUtils missing-HAL/game-toggle/reset tests passed");
 }
}
