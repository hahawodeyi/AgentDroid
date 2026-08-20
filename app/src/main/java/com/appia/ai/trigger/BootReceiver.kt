package com.appia.ai.trigger

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val config = TriggerStore(context).load()
        if (config.enabled) {
            TriggerScheduler.scheduleNext(context, config)
        }
    }
}
