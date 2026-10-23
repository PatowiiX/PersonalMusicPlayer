package com.example.personalmusicplayer

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow

class BluetoothMonitor(ctx: Context) {
    private val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val device = MutableStateFlow<String?>(null)

    private fun refresh() {
        val types = mutableSetOf(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO)
        if (Build.VERSION.SDK_INT >= 31) {
            types += AudioDeviceInfo.TYPE_BLE_HEADSET
            types += AudioDeviceInfo.TYPE_BLE_SPEAKER
        }
        device.value = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .firstOrNull { it.type in types }?.productName?.toString()
    }

    init {
        refresh()
        am.registerAudioDeviceCallback(object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(d: Array<out AudioDeviceInfo>?) = refresh()
            override fun onAudioDevicesRemoved(d: Array<out AudioDeviceInfo>?) = refresh()
        }, null)
    }
}
