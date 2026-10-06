package com.tcnunes.szokert.data

import android.content.Context
import android.os.storage.StorageManager
import androidx.core.content.edit
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.tcnunes.szokert.db.DB_SCHEMA
import com.tcnunes.szokert.db.SqliteDictionarySource
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The installed dictionary. */
data class Installed(val version: String, val built: String, val bytes: Long, val source: SqliteDictionarySource)

/** A download or update in progress, or the reason the last one failed. */
sealed interface Task {
    /** progress is null while waiting (e.g. for a connection). */
    data class Running(val progress: Progress?) : Task
    data class Failed(val message: String) : Task
}

/** A newer dictionary on the server. */
data class Available(val version: String, val bytes: Long)

data class DataState(val installed: Installed?, val task: Task?, val update: Available?, val loading: Boolean)

/**
 * Owns the dictionary database on the phone: opens the installed version, downloads the first one
 * when asked, checks for updates, and swaps a new version in without interrupting searches.
 */
class DataManager(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val work = WorkManager.getInstance(context)
    private val prefs = context.getSharedPreferences("dictionary", Context.MODE_PRIVATE)

    val store = DictionaryStore(File(context.filesDir, "dictionary"))
    val updater = DictionaryUpdater(DATA_URL, store, DB_SCHEMA, validate = ::isValid, freeSpace = ::freeSpace)

    private val installed = MutableStateFlow<Installed?>(null)
    private val loading = MutableStateFlow(true)
    private val update = MutableStateFlow<Available?>(null)

    val state: StateFlow<DataState> = combine(
        installed, update, loading, work.getWorkInfosForUniqueWorkFlow(DOWNLOAD),
    ) { inst, upd, load, infos ->
        DataState(inst, taskOf(infos.firstOrNull()), upd?.takeIf { it.version != inst?.version }, load)
    }.stateIn(scope, SharingStarted.Eagerly, DataState(null, null, null, loading = true))

    init {
        scope.launch {
            reload()
            loading.value = false
        }
    }

    /** Open the installed database (after a download, switch to it); the old one closes a little later. */
    @Synchronized
    fun reload() {
        val current = store.current()
        val old = installed.value
        if (current == null) {
            installed.value = null
        } else if (old?.version != current.version) {
            installed.value = runCatching { open(current) }.getOrNull()
        }
        if (old != null && old !== installed.value) {
            scope.launch {
                delay(10_000) // let searches already running on it finish
                old.source.close()
            }
        }
    }

    /** Start (or continue) downloading the dictionary; shown with a progress notification. */
    fun download() {
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        work.enqueueUniqueWork(DOWNLOAD, ExistingWorkPolicy.REPLACE, request)
    }

    /** Remove the dictionary and download it again from scratch. */
    fun downloadAgain() {
        work.cancelUniqueWork(DOWNLOAD)
        val old = installed.value
        installed.value = null
        update.value = null
        scope.launch {
            delay(1_000)
            old?.source?.close()
            store.clear()
            download()
        }
    }

    /** Ask the server whether a newer dictionary exists (at most every few hours unless [force]). */
    suspend fun checkForUpdate(force: Boolean = false): Available? {
        val now = System.currentTimeMillis()
        if (!force && now - prefs.getLong(LAST_CHECK, 0) < CHECK_EVERY_MS) return update.value
        val manifest = updater.fetchManifest()
        prefs.edit { putLong(LAST_CHECK, now) }
        val db = updater.pick(manifest)
        update.value = if (db != null && manifest.version != store.current()?.version) Available(manifest.version, db.bytes) else null
        return update.value
    }

    /** Daily, on unmetered networks only: download new dictionary versions in the background. */
    fun scheduleBackgroundUpdates() {
        val request = PeriodicWorkRequestBuilder<UpdateWorker>(1, TimeUnit.DAYS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.UNMETERED)
                    .setRequiresBatteryNotLow(true)
                    .build(),
            )
            .build()
        work.enqueueUniquePeriodicWork(BACKGROUND_UPDATE, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    private fun open(current: DictionaryStore.Installed): Installed {
        val source = SqliteDictionarySource(AndroidSQLiteDriver().open(current.file.path))
        check(source.schema == DB_SCHEMA) { "unexpected schema" }
        return Installed(current.version, source.meta["built"] ?: "", current.file.length(), source)
    }

    /** Free space including cached data Android would clear to make room. */
    private fun freeSpace(): Long {
        val storage = context.getSystemService(StorageManager::class.java)
        return storage.getAllocatableBytes(storage.getUuidForPath(store.dir))
    }

    private fun isValid(file: File, version: String): Boolean = runCatching {
        SqliteDictionarySource(AndroidSQLiteDriver().open(file.path)).use { it.schema == DB_SCHEMA && it.meta["version"] == version }
    }.getOrDefault(false)

    private fun taskOf(info: WorkInfo?): Task? = when (info?.state) {
        WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> Task.Running(null)
        WorkInfo.State.RUNNING -> Task.Running(info.progress.toProgress())
        WorkInfo.State.FAILED -> Task.Failed(info.outputData.getString(DownloadWorker.MESSAGE) ?: "The download failed. Try again.")
        else -> null
    }

    companion object {
        const val DATA_URL = "https://szokert.org/data/"
        const val DOWNLOAD = "dictionary-download"
        const val BACKGROUND_UPDATE = "dictionary-background-update"
        private const val LAST_CHECK = "lastCheck"
        private const val CHECK_EVERY_MS = 6L * 60 * 60 * 1000
    }
}
