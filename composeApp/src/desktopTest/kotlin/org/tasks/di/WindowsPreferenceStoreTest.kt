package org.tasks.di

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.sun.nio.file.ExtendedOpenOption.NOSHARE_DELETE
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption.READ
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class WindowsPreferenceStoreTest {
    @get:Rule val folder = TemporaryFolder()
    private val key = intPreferencesKey("counter")

    @Test fun transientLockDoesNotLoseTheUpdate() = checkLockedWrite(release = true)
    @Test fun persistentLockReportsFailureAndPreservesOldValue() = checkLockedWrite(release = false)

    private fun checkLockedWrite(release: Boolean) = runBlocking {
        assumeTrue(platform() == Platform.WINDOWS)
        val file = folder.root.resolve("tasks.preferences_pb")
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val old = PreferenceDataStoreFactory.create(scope = scope) { file }
        try {
            old.edit { it[key] = 10 }
        } finally {
            scope.coroutineContext[Job]!!.cancelAndJoin()
        }
        val writeScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val store = createWindowsPreferenceStore(file, writeScope)
        val executor = Executors.newSingleThreadScheduledExecutor()
        try {
            FileChannel.open(file.toPath(), READ, NOSHARE_DELETE).use { handle ->
                if (release) {
                    executor.schedule({ handle.close() }, 150, TimeUnit.MILLISECONDS)
                    store.edit { it[key] = it[key]!! + 1 }
                } else {
                    assertThrows(IOException::class.java) { runBlocking { store.edit { it[key] = 99 } } }
                }
            }
        } finally {
            executor.shutdownNow()
            writeScope.coroutineContext[Job]!!.cancelAndJoin()
        }
        val readScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        try {
            val reopened = PreferenceDataStoreFactory.create(scope = readScope) { file }
            assertEquals(if (release) 11 else 10, reopened.data.first()[key])
        } finally {
            readScope.coroutineContext[Job]!!.cancelAndJoin()
        }
    }
}
