/*
 * Copyright (C) 2018,2020 The LineageOS Project
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

package org.lineageos.settings.dirac;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.media.AudioManager;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import java.util.List;

public class DiracUtils {

    private static DiracUtils mInstance;
    private DiracSound mDiracSound;
    private MediaSessionManager mMediaSessionManager;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private Context mContext;

    private boolean mEnabled;
    private Integer mHeadset;
    private String mPreset;
    private Integer mScene;
    private Integer mHifi;

    public DiracUtils(Context context) {
        mContext = context.getApplicationContext();
        mMediaSessionManager = mContext.getSystemService(MediaSessionManager.class);
    }

    private interface EffectAction { void run(DiracSound effect); }

    private synchronized boolean withEffect(EffectAction action) {
        try {
            if (mDiracSound == null || !mDiracSound.hasControl()) {
                if (mDiracSound != null) mDiracSound.release();
                mDiracSound = null;
                mDiracSound = new DiracSound(0, 0);
                if (mHeadset != null) mDiracSound.setHeadsetType(mHeadset);
                if (mPreset != null) applyLevel(mDiracSound, mPreset);
                if (mScene != null) mDiracSound.setScenario(mScene);
                if (mHifi != null) {
                    mDiracSound.setHifiMode(mHifi);
                    mContext.getSystemService(AudioManager.class)
                            .setParameters("hifi_mode=" + (mHifi == 1));
                }
                mDiracSound.setEffectEnabled(mEnabled);
                mDiracSound.setMusic(mEnabled ? 1 : 0);
            }
            action.run(mDiracSound);
            return true;
        } catch (RuntimeException e) {
            android.util.Log.w("DiracUtils", "MiSound effect unavailable", e);
            DiracSound failed = mDiracSound;
            mDiracSound = null;
            if (failed != null) {
                try { failed.release(); } catch (RuntimeException ignored) { }
            }
            return false;
        }
    }

    public boolean isAvailable() { return withEffect(effect -> { }); }

    public static synchronized DiracUtils getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new DiracUtils(context);
        }

        return mInstance;
    }

    private void triggerPlayPause(MediaController controller) {
        long when = SystemClock.uptimeMillis();
        final KeyEvent evDownPause = new KeyEvent(when, when, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE, 0);
        final KeyEvent evUpPause = KeyEvent.changeAction(evDownPause, KeyEvent.ACTION_UP);
        final KeyEvent evDownPlay = new KeyEvent(when, when, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY, 0);
        final KeyEvent evUpPlay = KeyEvent.changeAction(evDownPlay, KeyEvent.ACTION_UP);
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                controller.dispatchMediaButtonEvent(evDownPause);
            }
        });
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                controller.dispatchMediaButtonEvent(evUpPause);
            }
        }, 20);
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                controller.dispatchMediaButtonEvent(evDownPlay);
            }
        }, 1000);
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                controller.dispatchMediaButtonEvent(evUpPlay);
            }
        }, 1020);
    }

    private int getMediaControllerPlaybackState(MediaController controller) {
        if (controller != null) {
            final PlaybackState playbackState = controller.getPlaybackState();
            if (playbackState != null) {
                return playbackState.getState();
            }
        }
        return PlaybackState.STATE_NONE;
    }

    private void refreshPlaybackIfNecessary(){
        if (mMediaSessionManager == null) return;

        final List<MediaController> sessions
                = mMediaSessionManager.getActiveSessionsForUser(
                null, UserHandle.ALL);
        for (MediaController aController : sessions) {
            if (PlaybackState.STATE_PLAYING ==
                    getMediaControllerPlaybackState(aController)) {
                triggerPlayPause(aController);
                break;
            }
        }
    }

    public boolean setEnabled(boolean enable) {
        boolean applied = withEffect(effect -> {
            effect.setEffectEnabled(enable);
            effect.setMusic(enable ? 1 : 0);
        });
        if (applied) {
            mEnabled = enable;
            if (enable) {
                try { refreshPlaybackIfNecessary(); } catch (RuntimeException e) {
                    android.util.Log.w("DiracUtils", "Could not refresh playback", e);
                }
            }
        }
        return applied;
    }

    public boolean isDiracEnabled() {
        boolean[] enabled = {false};
        withEffect(effect -> enabled[0] = effect.getMusic() == 1);
        return enabled[0];
    }

    private static void applyLevel(DiracSound effect, String preset) {
        String[] levels = preset.split(",", -1);
        if (levels.length != 7) throw new IllegalArgumentException("Expected seven EQ bands");
        float[] parsed = new float[levels.length];
        for (int band = 0; band < parsed.length; band++) {
            parsed[band] = Float.parseFloat(levels[band].trim());
            if (!Float.isFinite(parsed[band])) throw new IllegalArgumentException("Invalid EQ gain");
        }
        for (int band = 0; band < parsed.length; band++) effect.setLevel(band, parsed[band]);
    }

    public boolean setLevel(String preset) {
        boolean applied = withEffect(effect -> applyLevel(effect, preset));
        if (applied) mPreset = preset;
        return applied;
    }

    public boolean setHeadsetType(int type) {
        boolean applied = withEffect(effect -> effect.setHeadsetType(type));
        if (applied) mHeadset = type;
        return applied;
    }

    public boolean getHifiMode() {
        AudioManager manager = mContext.getSystemService(AudioManager.class);
        String value = manager.getParameters("hifi_mode");
        return value != null && value.contains("true");
    }

    public boolean setHifiMode(int mode) {
        boolean applied = withEffect(effect -> effect.setHifiMode(mode));
        if (applied) {
            try {
                mContext.getSystemService(AudioManager.class).setParameters("hifi_mode=" + (mode == 1));
                mHifi = mode;
            } catch (RuntimeException e) {
                android.util.Log.w("DiracUtils", "Could not set Hi-Fi mode", e);
                return false;
            }
        }
        return applied;
    }

    public boolean setScenario(int scene) {
        boolean applied = withEffect(effect -> effect.setScenario(scene));
        if (applied) mScene = scene;
        return applied;
    }
}
