package com.flaviocecca.ondalunga.update

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: ReleaseInfo) : UpdateState
    data class Downloading(val release: ReleaseInfo, val progress: Float) : UpdateState
    data class Ready(val release: ReleaseInfo, val file: File) : UpdateState

    /** [release] is set when it was the download that failed, so it can be retried directly. */
    data class Failed(val release: ReleaseInfo?) : UpdateState
}

/** Looks for a newer release, downloads its APK and hands it to the system installer. */
class Updater(private val context: Context, private val scope: CoroutineScope) {
    val installedVersion: String =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0"

    var state by mutableStateOf<UpdateState>(UpdateState.Idle)
        private set

    private val busy get() = state is UpdateState.Checking || state is UpdateState.Downloading

    fun check() {
        if (busy) return
        state = UpdateState.Checking
        scope.launch {
            state = try {
                val latest = withContext(Dispatchers.IO) { Releases.fetchLatest() }
                if (latest != null && Releases.isNewer(latest.version, installedVersion)) {
                    UpdateState.Available(latest)
                } else {
                    UpdateState.UpToDate
                }
            } catch (_: Exception) {
                UpdateState.Failed(null)
            }
        }
    }

    fun download(release: ReleaseInfo) {
        if (busy) return
        state = UpdateState.Downloading(release, 0f)
        scope.launch {
            try {
                val file = withContext(Dispatchers.IO) {
                    val folder = File(context.cacheDir, "updates").apply {
                        deleteRecursively()
                        mkdirs()
                    }
                    File(folder, "OndaLunga-${release.version}.apk").also { target ->
                        var shown = 0f
                        Releases.download(release, target) { progress ->
                            if (progress - shown >= 0.01f || progress >= 1f) {
                                shown = progress
                                state = UpdateState.Downloading(release, progress)
                            }
                        }
                    }
                }
                state = UpdateState.Ready(release, file)
                install(file)
            } catch (_: Exception) {
                state = UpdateState.Failed(release)
            }
        }
    }

    /** Opens Android's own install screen; the system checks the APK is signed like this app. */
    fun install(file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
