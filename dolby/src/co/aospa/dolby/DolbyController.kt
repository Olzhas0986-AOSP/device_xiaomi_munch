/*
 * Copyright (C) 2023-24 Paranoid Android
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.dolby

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioManager.AudioPlaybackCallback
import android.media.AudioPlaybackConfiguration
import android.os.Handler
import android.util.Log
import androidx.preference.PreferenceManager
import co.aospa.dolby.DolbyConstants.DsParam

internal class DolbyController private constructor(private val context: Context) {
    private val lock = Any()
    private val audioManager = context.getSystemService(AudioManager::class.java)!!
    private val handler = Handler(context.mainLooper)
    private val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    private val profileIds =
        context.resources.getStringArray(R.array.dolby_profile_values).map { it.toInt() }
    private val stereoWideningSupported =
        context.resources.getBoolean(R.bool.dolby_stereo_widening_supported)

    private var dolbyEffect: DolbyAudioEffect? = null
    private var requestedEnabled = prefs.getBoolean(DolbyConstants.PREF_ENABLE, true)
    private var requestedProfile =
        prefs.getString(DolbyConstants.PREF_PROFILE, "0")?.toIntOrNull()
            ?.takeIf { it in profileIds } ?: 0
    private var callbacksRegistered = false
    private var retryScheduled = false
    private var retryAttempts = 0

    private val retry = Runnable {
        synchronized(lock) { retryScheduled = false }
        refreshEffect()
    }

    private val playbackCallback =
        object : AudioPlaybackCallback() {
            override fun onPlaybackConfigChanged(configs: List<AudioPlaybackConfiguration>) {
                if (configs.any {
                        it.playerState == AudioPlaybackConfiguration.PLAYER_STATE_STARTED
                    }) {
                    refreshEffect()
                }
            }
        }

    private val audioDeviceCallback =
        object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<AudioDeviceInfo>) {
                refreshEffect()
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<AudioDeviceInfo>) {
                refreshEffect()
            }
        }

    val isAvailable: Boolean
        get() = withEffect(false) { true }

    var dsOn: Boolean
        get() = withEffect(false) { it.dsOn }
        set(value) {
            synchronized(lock) {
                requestedEnabled = value
                withEffect(Unit) { effect ->
                    effect.dsOn = value
                    if (value) effect.profile = requestedProfile
                }
            }
        }

    var profile: Int
        get() = synchronized(lock) { withEffect(requestedProfile) { it.profile } }
        set(value) {
            if (value !in profileIds) return
            synchronized(lock) {
                requestedProfile = value
                withEffect(Unit) { it.profile = value }
            }
        }

    fun onBootCompleted() {
        refreshEffect()
    }

    private fun registerCallbacks() {
        if (callbacksRegistered) return
        audioManager.registerAudioPlaybackCallback(playbackCallback, handler)
        try {
            audioManager.registerAudioDeviceCallback(audioDeviceCallback, handler)
        } catch (e: RuntimeException) {
            audioManager.unregisterAudioPlaybackCallback(playbackCallback)
            throw e
        }
        callbacksRegistered = true
    }

    // All engine calls, including reads from the IO dispatcher, share the same lock.
    private fun <T> withEffect(fallback: T, block: (DolbyAudioEffect) -> T): T =
        synchronized(lock) {
            if (dolbyEffect == null && retryScheduled) return@synchronized fallback
            try {
                registerCallbacks()
                val effect = ensureEffect()
                block(effect)
            } catch (e: RuntimeException) {
                Log.w(TAG, "Dolby backend unavailable", e)
                releaseEffect()
                if (!retryScheduled && retryAttempts < MAX_RETRY_ATTEMPTS) {
                    retryAttempts++
                    retryScheduled = true
                    handler.postDelayed(retry, RETRY_DELAY_MS)
                }
                fallback
            }
        }

    private fun ensureEffect(): DolbyAudioEffect {
        dolbyEffect?.let { if (it.hasControl()) return it }
        releaseEffect()
        val effect = DolbyAudioEffect(EFFECT_PRIORITY, audioSession = 0)
        dolbyEffect = effect
        check(effect.hasControl()) { "Dolby effect has no control" }

        effect.dsOn = false
        profileIds.forEach { profile ->
            effect.resetProfileSpecificSettings(profile)
            restoreSettings(effect, profile)
        }
        effect.profile = requestedProfile
        effect.dsOn = requestedEnabled
        handler.removeCallbacks(retry)
        retryScheduled = false
        retryAttempts = 0
        return effect
    }

    private fun releaseEffect() {
        val effect = dolbyEffect
        dolbyEffect = null
        try {
            effect?.release()
        } catch (e: RuntimeException) {
            Log.w(TAG, "Could not release Dolby effect", e)
        }
    }

    private fun refreshEffect() {
        withEffect(Unit) { it.profile = requestedProfile }
    }

    private fun profilePrefs(profile: Int) =
        context.getSharedPreferences("profile_$profile", Context.MODE_PRIVATE)

    private fun restoreSettings(effect: DolbyAudioEffect, profile: Int) {
        val saved = profilePrefs(profile)
        saved.getString(DolbyConstants.PREF_PRESET, null)?.let { value ->
            parsePreset(value)?.let { effect.setDapParameter(DsParam.GEQ_BAND_GAINS, it, profile) }
        }
        BOOLEAN_PARAMS.forEach { (param, key) ->
            if (saved.contains(key)) {
                effect.setDapParameter(param, saved.getBoolean(key, false), profile)
            }
        }
        INTEGER_PARAMS.forEach { (param, key) ->
            if (param == DsParam.STEREO_WIDENING_AMOUNT && !stereoWideningSupported) {
                return@forEach
            }
            saved.getString(key, null)?.toIntOrNull()?.let { value ->
                if (param == DsParam.DIALOGUE_ENHANCER_AMOUNT) {
                    effect.setDapParameter(DsParam.DIALOGUE_ENHANCER_ENABLE, value > 0, profile)
                }
                effect.setDapParameter(param, value, profile)
            }
        }
    }

    fun setDsOnAndPersist(dsOn: Boolean) {
        synchronized(lock) {
            prefs.edit().putBoolean(DolbyConstants.PREF_ENABLE, dsOn).apply()
            this.dsOn = dsOn
        }
    }

    fun getProfileName(): String? {
        val index = profileIds.indexOf(profile)
        return if (index < 0) null
        else context.resources.getStringArray(R.array.dolby_profile_entries)[index]
    }

    fun resetProfileSpecificSettings() {
        synchronized(lock) {
            val profile = this.profile
            // Clear the existing object too: PreferenceDataStore may still hold it.
            profilePrefs(profile).edit().clear().apply()
            withEffect(Unit) { it.resetProfileSpecificSettings(profile) }
        }
    }

    fun getPreset(profile: Int = this.profile): String {
        val fallback = profilePrefs(profile).getString(DolbyConstants.PREF_PRESET, null)
            ?.let { parsePreset(it) }?.joinToString(",") ?: FLAT_PRESET
        return withEffect(fallback) {
            it.getDapParameter(DsParam.GEQ_BAND_GAINS, profile).joinToString(",")
        }
    }

    fun setPreset(value: String, profile: Int = this.profile) {
        val gains = parsePreset(value) ?: return
        withEffect(Unit) { it.setDapParameter(DsParam.GEQ_BAND_GAINS, gains, profile) }
    }

    fun setPresetAndPersist(value: String, profile: Int) {
        if (parsePreset(value) == null) return
        synchronized(lock) {
            profilePrefs(profile).edit().putString(DolbyConstants.PREF_PRESET, value).apply()
            setPreset(value, profile)
        }
    }

    fun getPresetName(): String {
        val values = context.resources.getStringArray(R.array.dolby_preset_values)
        val index = values.indexOf(getPreset())
        return if (index < 0) "Custom"
        else context.resources.getStringArray(R.array.dolby_preset_entries)[index]
    }

    private fun getBoolean(param: DsParam, profile: Int): Boolean {
        val fallback = profilePrefs(profile).getBoolean(BOOLEAN_PARAMS.getValue(param), false)
        return withEffect(fallback) { it.getDapParameterBool(param, profile) }
    }

    private fun getInt(param: DsParam, profile: Int): Int {
        val fallback = profilePrefs(profile).getString(INTEGER_PARAMS.getValue(param), null)
            ?.toIntOrNull() ?: 0
        return withEffect(fallback) { it.getDapParameterInt(param, profile) }
    }

    fun getHeadphoneVirtEnabled(profile: Int = this.profile) =
        getBoolean(DsParam.HEADPHONE_VIRTUALIZER, profile)

    fun setHeadphoneVirtEnabled(value: Boolean, profile: Int = this.profile) {
        withEffect(Unit) { it.setDapParameter(DsParam.HEADPHONE_VIRTUALIZER, value, profile) }
    }

    fun getSpeakerVirtEnabled(profile: Int = this.profile) =
        getBoolean(DsParam.SPEAKER_VIRTUALIZER, profile)

    fun setSpeakerVirtEnabled(value: Boolean, profile: Int = this.profile) {
        withEffect(Unit) { it.setDapParameter(DsParam.SPEAKER_VIRTUALIZER, value, profile) }
    }

    fun getBassEnhancerEnabled(profile: Int = this.profile) =
        getBoolean(DsParam.BASS_ENHANCER_ENABLE, profile)

    fun setBassEnhancerEnabled(value: Boolean, profile: Int = this.profile) {
        withEffect(Unit) { it.setDapParameter(DsParam.BASS_ENHANCER_ENABLE, value, profile) }
    }

    fun getVolumeLevelerEnabled(profile: Int = this.profile) =
        getBoolean(DsParam.VOLUME_LEVELER_ENABLE, profile)

    fun setVolumeLevelerEnabled(value: Boolean, profile: Int = this.profile) {
        withEffect(Unit) { it.setDapParameter(DsParam.VOLUME_LEVELER_ENABLE, value, profile) }
    }

    fun getStereoWideningAmount(profile: Int = this.profile) =
        if (stereoWideningSupported) getInt(DsParam.STEREO_WIDENING_AMOUNT, profile) else 0

    fun setStereoWideningAmount(value: Int, profile: Int = this.profile) {
        if (stereoWideningSupported) {
            withEffect(Unit) { it.setDapParameter(DsParam.STEREO_WIDENING_AMOUNT, value, profile) }
        }
    }

    fun getDialogueEnhancerAmount(profile: Int = this.profile): Int {
        val fallback = profilePrefs(profile).getString(DolbyConstants.PREF_DIALOGUE, null)
            ?.toIntOrNull() ?: 0
        return withEffect(fallback) {
            if (it.getDapParameterBool(DsParam.DIALOGUE_ENHANCER_ENABLE, profile)) {
                it.getDapParameterInt(DsParam.DIALOGUE_ENHANCER_AMOUNT, profile)
            } else 0
        }
    }

    fun setDialogueEnhancerAmount(value: Int, profile: Int = this.profile) {
        withEffect(Unit) {
            it.setDapParameter(DsParam.DIALOGUE_ENHANCER_ENABLE, value > 0, profile)
            it.setDapParameter(DsParam.DIALOGUE_ENHANCER_AMOUNT, value, profile)
        }
    }

    fun getIeqPreset(profile: Int = this.profile) = getInt(DsParam.IEQ_PRESET, profile)

    fun setIeqPreset(value: Int, profile: Int = this.profile) {
        withEffect(Unit) { it.setDapParameter(DsParam.IEQ_PRESET, value, profile) }
    }

    companion object {
        private const val TAG = "DolbyController"
        private const val EFFECT_PRIORITY = 100
        private const val MAX_RETRY_ATTEMPTS = 5
        private const val RETRY_DELAY_MS = 1000L
        private val FLAT_PRESET = List(20) { 0 }.joinToString(",")
        private val BOOLEAN_PARAMS = mapOf(
            DsParam.HEADPHONE_VIRTUALIZER to DolbyConstants.PREF_HP_VIRTUALIZER,
            DsParam.SPEAKER_VIRTUALIZER to DolbyConstants.PREF_SPK_VIRTUALIZER,
            DsParam.BASS_ENHANCER_ENABLE to DolbyConstants.PREF_BASS,
            DsParam.VOLUME_LEVELER_ENABLE to DolbyConstants.PREF_VOLUME,
        )
        private val INTEGER_PARAMS = mapOf(
            DsParam.IEQ_PRESET to DolbyConstants.PREF_IEQ,
            DsParam.STEREO_WIDENING_AMOUNT to DolbyConstants.PREF_STEREO,
            DsParam.DIALOGUE_ENHANCER_AMOUNT to DolbyConstants.PREF_DIALOGUE,
        )

        private fun parsePreset(value: String): IntArray? {
            val gains = value.split(",").map { it.toIntOrNull() }
            if (gains.size != 20 || gains.any { it == null || it !in -100..100 }) return null
            return gains.map { it!! }.toIntArray()
        }

        @Volatile private var instance: DolbyController? = null

        fun getInstance(context: Context): DolbyController =
            instance ?: synchronized(this) {
                instance ?: DolbyController(context.applicationContext).also { instance = it }
            }
    }
}
