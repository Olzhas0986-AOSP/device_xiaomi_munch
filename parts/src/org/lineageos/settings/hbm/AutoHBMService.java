package org.lineageos.settings.hbm;

import android.app.KeyguardManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import androidx.preference.PreferenceManager;
import org.lineageos.settings.utils.ProfileUtils;

public class AutoHBMService extends Service {
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private Runnable mDisableRunnable;
    private SensorManager mSensorManager;
    private Sensor mLightSensor;
    private SharedPreferences mPrefs;
    private boolean mAutoHBMActive;
    private boolean mSensorRegistered;
    private boolean mScreenOn;

    private boolean manualEnabled() {
        return mPrefs.getBoolean(HBMFragment.HBM_SWITCH_KEY, false);
    }

    private void cancelTimeout() {
        if (mDisableRunnable != null) {
            mHandler.removeCallbacks(mDisableRunnable);
            mDisableRunnable = null;
        }
    }

    private void disableAutomaticHBM() {
        cancelTimeout();
        if (mAutoHBMActive && !manualEnabled()) {
            HBMModeSwitch.executeHBMAction(this, mPrefs, false);
        }
        mAutoHBMActive = false;
    }

    private void updateState() {
        boolean manual = manualEnabled();
        boolean automatic = mPrefs.getBoolean(HBMFragment.AUTO_HBM_SWITCH_KEY, false);
        boolean listen = mScreenOn && automatic && !manual
                && !mPrefs.getBoolean("dc_dimming", false);
        if (listen && mSensorManager != null && mLightSensor != null) {
            if (!mSensorRegistered) {
                mSensorRegistered = mSensorManager.registerListener(mSensorListener,
                        mLightSensor, SensorManager.SENSOR_DELAY_NORMAL);
            }
        } else {
            if (mSensorRegistered) {
                mSensorManager.unregisterListener(mSensorListener);
                mSensorRegistered = false;
            }
            disableAutomaticHBM();
        }
        if (!manual && !automatic) {
            if (mPrefs.contains("last_brightness")) {
                HBMModeSwitch.executeHBMAction(this, mPrefs, false);
            }
            stopSelf();
        }
    }

    private final SharedPreferences.OnSharedPreferenceChangeListener mPrefsListener =
            (prefs, key) -> {
                if (HBMFragment.HBM_SWITCH_KEY.equals(key)
                        || HBMFragment.AUTO_HBM_SWITCH_KEY.equals(key)
                        || "dc_dimming".equals(key)) {
                    updateState();
                }
            };

    private final SensorEventListener mSensorListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            if (manualEnabled() || !mScreenOn || event.values.length == 0) return;
            KeyguardManager keyguard = getSystemService(KeyguardManager.class);
            if (keyguard.isKeyguardLocked() || mPrefs.getBoolean("dc_dimming", false)) {
                disableAutomaticHBM();
                return;
            }
            int threshold = ProfileUtils.boundedInt(mPrefs.getString(
                    HBMFragment.AUTO_HBM_THRESHOLD_KEY, "7000"), 7000, 0, 60000);
            long delay = ProfileUtils.boundedInt(mPrefs.getString(
                    HBMFragment.HBM_DISABLE_TIME_KEY, "1"), 1, 1, 10);
            if (event.values[0] > threshold) {
                cancelTimeout();
                if (!mAutoHBMActive) {
                    mAutoHBMActive = HBMModeSwitch.executeHBMAction(AutoHBMService.this,
                            mPrefs, true);
                }
            } else if (mAutoHBMActive && mDisableRunnable == null) {
                mDisableRunnable = () -> {
                    mDisableRunnable = null;
                    disableAutomaticHBM();
                };
                mHandler.postDelayed(mDisableRunnable, delay * 1000);
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {}
    };

    private final BroadcastReceiver mScreenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            mScreenOn = Intent.ACTION_SCREEN_ON.equals(intent.getAction());
            if (!mScreenOn && manualEnabled()
                    && mPrefs.getBoolean(HBMFragment.HBM_TURN_OFF_ON_SCREEN_OFF_KEY, false)
                    && HBMModeSwitch.executeHBMAction(context, mPrefs, false)) {
                mPrefs.edit().putBoolean(HBMFragment.HBM_SWITCH_KEY, false).apply();
            }
            updateState();
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        mPrefs.registerOnSharedPreferenceChangeListener(mPrefsListener);
        mSensorManager = getSystemService(SensorManager.class);
        mLightSensor = mSensorManager == null ? null
                : mSensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        mScreenOn = getSystemService(PowerManager.class).isInteractive();
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(mScreenReceiver, filter);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        updateState();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        mPrefs.unregisterOnSharedPreferenceChangeListener(mPrefsListener);
        unregisterReceiver(mScreenReceiver);
        if (mSensorRegistered) mSensorManager.unregisterListener(mSensorListener);
        disableAutomaticHBM();
        mHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
