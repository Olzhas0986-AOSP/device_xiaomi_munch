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

public class AutoHBMService extends Service {
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private SensorManager mSensorManager;
    private Sensor mLightSensor;
    private SharedPreferences mSharedPrefs;
    private boolean mAutoHBMActive;
    private boolean mDisablePending;
    private boolean mSensorRegistered;

    private final Runnable mDisableRunnable = () -> {
        mDisablePending = false;
        disableAutomaticHBM();
    };

    static int getPreferenceInt(SharedPreferences prefs, String key, int def, int min, int max) {
        try {
            return Math.max(min, Math.min(max, Integer.parseInt(prefs.getString(key, "" + def))));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public void activateLightSensorRead() {
        if (!mSensorRegistered && mSensorManager != null && mLightSensor != null
                && mSharedPrefs.getBoolean(HBMFragment.AUTO_HBM_SWITCH_KEY, false)
                && !mSharedPrefs.getBoolean("dc_dimming", false)) {
            mSensorRegistered = mSensorManager.registerListener(
                    mSensorEventListener, mLightSensor, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    public void deactivateLightSensorRead() {
        if (mSensorRegistered) {
            mSensorManager.unregisterListener(mSensorEventListener);
            mSensorRegistered = false;
        }
        disableAutomaticHBM();
    }

    private void disableAutomaticHBM() {
        mHandler.removeCallbacks(mDisableRunnable);
        mDisablePending = false;
        if (mAutoHBMActive) {
            if (HBMModeSwitch.executeHBMAction(this, mSharedPrefs, false)) mAutoHBMActive = false;
        }
    }

    private void updateMode() {
        if (!mSharedPrefs.getBoolean(HBMFragment.AUTO_HBM_SWITCH_KEY, false)
                || mSharedPrefs.getBoolean("dc_dimming", false)
                || !getSystemService(PowerManager.class).isInteractive()) {
            deactivateLightSensorRead();
        } else {
            activateLightSensorRead();
        }
    }

    private final SharedPreferences.OnSharedPreferenceChangeListener mPrefsListener = (prefs, key) -> {
        if (HBMFragment.AUTO_HBM_SWITCH_KEY.equals(key) || "dc_dimming".equals(key)) updateMode();
    };

    private final SensorEventListener mSensorEventListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            if (event.values.length == 0 || !Float.isFinite(event.values[0])) return;
            if (!mSharedPrefs.getBoolean(HBMFragment.AUTO_HBM_SWITCH_KEY, false)
                    || mSharedPrefs.getBoolean("dc_dimming", false)
                    || getSystemService(KeyguardManager.class).isKeyguardLocked()) {
                disableAutomaticHBM();
                return;
            }
            int threshold = getPreferenceInt(mSharedPrefs,
                    HBMFragment.AUTO_HBM_THRESHOLD_KEY, 7000, 0, 60000);
            if (event.values[0] > threshold) {
                mHandler.removeCallbacks(mDisableRunnable);
                mDisablePending = false;
                if (!mAutoHBMActive) {
                    mAutoHBMActive = HBMModeSwitch.executeHBMAction(
                            AutoHBMService.this, mSharedPrefs, true);
                }
            } else if (mAutoHBMActive && !mDisablePending) {
                // Do not postpone the deadline on every light-sensor sample.
                mDisablePending = true;
                int seconds = getPreferenceInt(mSharedPrefs,
                        HBMFragment.HBM_DISABLE_TIME_KEY, 1, 1, 10);
                mHandler.postDelayed(mDisableRunnable, seconds * 1000L);
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) { }
    };

    private final BroadcastReceiver mScreenStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                deactivateLightSensorRead();
                if (mSharedPrefs.getBoolean(HBMFragment.HBM_SWITCH_KEY, false)
                        && mSharedPrefs.getBoolean(HBMFragment.HBM_TURN_OFF_ON_SCREEN_OFF_KEY, false)
                        && HBMModeSwitch.executeHBMAction(context, mSharedPrefs, false)) {
                    mSharedPrefs.edit().putBoolean(HBMFragment.HBM_SWITCH_KEY, false).apply();
                    if (!mSharedPrefs.getBoolean(HBMFragment.AUTO_HBM_SWITCH_KEY, false)) stopSelf();
                }
            } else {
                updateMode();
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        mSensorManager = getSystemService(SensorManager.class);
        mLightSensor = mSensorManager == null ? null : mSensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        mAutoHBMActive = mSharedPrefs.contains("last_brightness")
                && !mSharedPrefs.getBoolean(HBMFragment.HBM_SWITCH_KEY, false);
        mSharedPrefs.registerOnSharedPreferenceChangeListener(mPrefsListener);
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        registerReceiver(mScreenStateReceiver, filter);
        updateMode();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        updateMode();
        if (!mSharedPrefs.getBoolean(HBMFragment.AUTO_HBM_SWITCH_KEY, false)
                && !mSharedPrefs.getBoolean(HBMFragment.HBM_SWITCH_KEY, false)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        mSharedPrefs.unregisterOnSharedPreferenceChangeListener(mPrefsListener);
        unregisterReceiver(mScreenStateReceiver);
        deactivateLightSensorRead();
        mHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
