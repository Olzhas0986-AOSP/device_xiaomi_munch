/*
 * Copyright (C) 2020 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.lineageos.settings.refreshrate;

import android.app.ActivityTaskManager;
import android.app.ActivityTaskManager.RootTaskInfo;
import android.app.IActivityTaskManager;
import android.app.Service;
import android.app.TaskStackListener;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.RemoteException;
import android.util.Log;

import androidx.preference.PreferenceManager;

public class RefreshService extends Service {
    private static final String TAG = "RefreshService";
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private RefreshUtils mRefreshUtils;
    private IActivityTaskManager mActivityTaskManager;
    private SharedPreferences mPreferences;
    private PowerManager mPowerManager;
    private boolean mDestroyed;

    private final SharedPreferences.OnSharedPreferenceChangeListener mPreferenceListener =
            (prefs, key) -> {
                if ("refresh_control".equals(key)) mHandler.post(this::updateRefreshRate);
            };
    private final BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) { updateRefreshRate(); }
    };
    private final TaskStackListener mTaskListener = new TaskStackListener() {
        @Override
        public void onTaskStackChanged() { mHandler.post(RefreshService.this::updateRefreshRate); }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        mRefreshUtils = new RefreshUtils(this);
        mPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        mPowerManager = getSystemService(PowerManager.class);
        mActivityTaskManager = ActivityTaskManager.getService();
        mPreferences.registerOnSharedPreferenceChangeListener(mPreferenceListener);
        try {
            mActivityTaskManager.registerTaskStackListener(mTaskListener);
        } catch (RemoteException e) {
            Log.e(TAG, "Could not register task listener", e);
        }
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        registerReceiver(mIntentReceiver, filter);
        updateRefreshRate();
    }

    private void updateRefreshRate() {
        if (mDestroyed) return;
        if (!mPowerManager.isInteractive()) {
            mRefreshUtils.restoreDefaultRate();
            return;
        }
        try {
            RootTaskInfo info = mActivityTaskManager.getFocusedRootTaskInfo();
            mRefreshUtils.setRefreshRate(info == null || info.topActivity == null
                    ? null : info.topActivity.getPackageName());
        } catch (RemoteException e) {
            Log.e(TAG, "Could not query foreground task", e);
            mRefreshUtils.restoreDefaultRate();
        }
    }

    @Override
    public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
        updateRefreshRate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        updateRefreshRate();
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        mDestroyed = true;
        mPreferences.unregisterOnSharedPreferenceChangeListener(mPreferenceListener);
        unregisterReceiver(mIntentReceiver);
        try {
            mActivityTaskManager.unregisterTaskStackListener(mTaskListener);
        } catch (RemoteException e) {
            Log.e(TAG, "Could not unregister task listener", e);
        }
        mHandler.removeCallbacksAndMessages(null);
        mRefreshUtils.restoreDefaultRate();
        super.onDestroy();
    }
}
