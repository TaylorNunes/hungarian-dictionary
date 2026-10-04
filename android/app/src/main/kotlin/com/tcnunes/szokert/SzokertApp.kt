package com.tcnunes.szokert

import android.app.Application
import com.tcnunes.szokert.core.Glossary
import com.tcnunes.szokert.data.DataManager
import com.tcnunes.szokert.saved.SavedStore
import com.tcnunes.szokert.settings.Settings

class SzokertApp : Application() {
    val data: DataManager by lazy { DataManager(this) }
    val saved: SavedStore by lazy { SavedStore(this) }
    val settings: Settings by lazy { Settings(this) }
    val glossary: Glossary by lazy { Glossary.parse(assets.open("glossary.json").bufferedReader().readText()) }

    override fun onCreate() {
        super.onCreate()
        data.scheduleBackgroundUpdates()
    }
}
