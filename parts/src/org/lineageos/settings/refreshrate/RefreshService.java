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
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY Refresh, either express or implied.
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
import android.content.res.Configuration;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.RemoteException;
import android.util.Log;

public class RefreshService extends Service {
    private static final String TAG = "RefreshService";
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Runnable mUpdateProfile = this::updateProfile;
    private RefreshUtils mRefreshUtils;
    private IActivityTaskManager mActivityTaskManager;
    private boolean mScreenOn;
    private boolean mDestroyed;

    private final BroadcastReceiver mScreenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            mScreenOn = Intent.ACTION_SCREEN_ON.equals(intent.getAction());
            scheduleUpdate();
        }
    };

    private final TaskStackListener mTaskListener = new TaskStackListener() {
        @Override
        public void onTaskStackChanged() {
            scheduleUpdate();
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        mRefreshUtils = new RefreshUtils(this);
        mScreenOn = getSystemService(PowerManager.class).isInteractive();
        mActivityTaskManager = ActivityTaskManager.getService();
        try {
            mActivityTaskManager.registerTaskStackListener(mTaskListener);
        } catch (RemoteException e) {
            Log.w(TAG, "Unable to register task listener", e);
        }
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(mScreenReceiver, filter);
        scheduleUpdate();
    }

    private void scheduleUpdate() {
        mHandler.removeCallbacks(mUpdateProfile);
        mHandler.post(mUpdateProfile);
    }

    private void updateProfile() {
        if (mDestroyed) return;
        if (!mScreenOn) {
            mRefreshUtils.restoreRefreshRate();
            return;
        }
        try {
            RootTaskInfo info = mActivityTaskManager.getFocusedRootTaskInfo();
            String packageName = info == null || info.topActivity == null
                    ? "" : info.topActivity.getPackageName();
            mRefreshUtils.setRefreshRate(packageName);
        } catch (RemoteException e) {
            Log.w(TAG, "Unable to read the foreground task", e);
        }
    }

    @Override
    public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
        scheduleUpdate();
    }

    @Override
    public void onDestroy() {
        mDestroyed = true;
        try {
            mActivityTaskManager.unregisterTaskStackListener(mTaskListener);
        } catch (RemoteException e) {
            Log.w(TAG, "Unable to unregister task listener", e);
        }
        unregisterReceiver(mScreenReceiver);
        mHandler.removeCallbacksAndMessages(null);
        mRefreshUtils.restoreRefreshRate();
        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
