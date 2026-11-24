package org.tasks.di

import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.core.okio.OkioStorage
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.PreferencesSerializer
import androidx.datastore.preferences.core.emptyPreferences
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okio.FileSystem
import okio.ForwardingFileSystem
import okio.Path
import okio.Path.Companion.toOkioPath
import java.io.File
import java.nio.file.AccessDeniedException

internal fun createWindowsPreferenceStore(
    file: File,
    scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
) = PreferenceDataStoreFactory.create(
    storage = OkioStorage(WindowsPreferenceFileSystem, PreferencesSerializer) { file.toOkioPath() },
    corruptionHandler = ReplaceFileCorruptionHandler {
        Logger.e { "Preferences datastore corrupted, resetting to defaults" }
        emptyPreferences()
    },
    scope = scope,
)

private object WindowsPreferenceFileSystem : ForwardingFileSystem(FileSystem.SYSTEM) {
    override fun atomicMove(source: Path, target: Path) {
        // A reader without delete sharing can briefly prevent replacement. Retry the move,
        // not the preference transform, and never delete the previous file to make room.
        for (delayMillis in listOf(25L, 50L, 100L, 200L, 400L)) {
            try {
                return super.atomicMove(source, target)
            } catch (_: AccessDeniedException) {
                Thread.sleep(delayMillis)
            }
        }
        super.atomicMove(source, target)
    }
}
