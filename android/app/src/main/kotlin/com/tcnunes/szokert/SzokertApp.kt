package com.tcnunes.szokert

import android.app.Application
import com.tcnunes.szokert.data.DataManager

class SzokertApp : Application() {
    val data: DataManager by lazy { DataManager(this) }

    override fun onCreate() {
        super.onCreate()
        data.scheduleBackgroundUpdates()
    }
}
