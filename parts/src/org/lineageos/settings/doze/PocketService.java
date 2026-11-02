/*
 * Copyright (C) 2024 Paranoid Android
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.doze;

import android.app.KeyguardManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.UserHandle;
import android.util.Log;

public class PocketService extends Service {

    private static final String TAG = "PocketService";

    /* xiaomi.sensor.large_area_detect */
    private static final int TYPE_LARGE_AREA_TOUCH_SENSOR = 33171031;

    private PowerManager mPowerManager;
    private KeyguardManager mKeyguardManager;
    private SensorManager mSensorManager;
    private Sensor mTouchSensor;

    private boolean mReceiverRegistered;

    @Override
    public void onCreate() {
        super.onCreate();
        dlog("Creating service");
        mPowerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        mKeyguardManager = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mTouchSensor = mSensorManager == null ? null
                : mSensorManager.getDefaultSensor(TYPE_LARGE_AREA_TOUCH_SENSOR);

        IntentFilter screenStateFilter = new IntentFilter();
        screenStateFilter.addAction(Intent.ACTION_SCREEN_ON);
        screenStateFilter.addAction(Intent.ACTION_SCREEN_OFF);
        screenStateFilter.addAction(Intent.ACTION_USER_PRESENT);
        if (mTouchSensor != null) {
            registerReceiver(mScreenStateReceiver, screenStateFilter);
            mReceiverRegistered = true;
            updateListening();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        dlog("Starting service");
        if (mTouchSensor == null) {
            stopSelf();
            return START_NOT_STICKY;
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        dlog("Destroying service");
        if (mReceiverRegistered) unregisterReceiver(mScreenStateReceiver);
        if (mSensorManager != null) mSensorManager.unregisterListener(mSensorListener);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public static void startService(Context context) {
         context.startServiceAsUser(new Intent(context, PocketService.class), UserHandle.CURRENT);
    }

    private void updateListening() {
        if (mTouchSensor == null) return;
        if (mPowerManager.isInteractive() && mKeyguardManager.isKeyguardLocked()) {
            mSensorManager.registerListener(mSensorListener, mTouchSensor,
                    SensorManager.SENSOR_DELAY_NORMAL);
        } else {
            mSensorManager.unregisterListener(mSensorListener);
        }
    }

    private final BroadcastReceiver mScreenStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())
                    || Intent.ACTION_USER_PRESENT.equals(intent.getAction())) {
                mSensorManager.unregisterListener(mSensorListener);
            } else {
                updateListening();
            }
        }
    };

    private SensorEventListener mSensorListener = new SensorEventListener() {
        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            // no-op
        }

        @Override
        public void onSensorChanged(SensorEvent event) {
            if (event.values.length == 0 || !mPowerManager.isInteractive()) return;
            boolean isTouchDetected = event.values[0] == 1;
            boolean isOnKeyguard = mKeyguardManager.isKeyguardLocked();

            dlog("onSensorChanged type=" + event.sensor.getType()
                    + " value=" + event.values[0] + " isTouchDetected="
                    + isTouchDetected + " isOnKeyguard=" + isOnKeyguard);

            if (isTouchDetected && isOnKeyguard) {
                Log.i(TAG, "In pocket, going to sleep");
                mPowerManager.goToSleep(SystemClock.uptimeMillis());
            }
        }
    };

    private static void dlog(String msg) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, msg);
        }
    }
}
