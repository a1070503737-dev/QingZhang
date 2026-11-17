package com.zhuangxiu.ledger

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.zhuangxiu.ledger.data.db.AppDatabase
import com.zhuangxiu.ledger.data.repository.DailyRepository
import com.zhuangxiu.ledger.data.repository.SettingsRepository
import com.zhuangxiu.ledger.ui.daily.DailyEditViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class DailyEditViewModelTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: AppDatabase
    @Before fun setup() {
        Dispatchers.setMain(StandardTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).addCallback(AppDatabase.SEED_CALLBACK).build()
    }
    @After fun cleanup() { db.close(); Dispatchers.resetMain() }
    @Test fun rapidSaveAndRestoredDraftCannotCreateDuplicateRecord() = runTest {
        val repo = DailyRepository(db)
        val handle = SavedStateHandle(mapOf("bookId" to 1L))
        val vm = DailyEditViewModel(repo, handle)
        vm.state.first { !it.loading }
        vm.change { it.copy(amount = "12.34") }
        vm.save(); vm.save()
        vm.state.first { it.complete }
        assertEquals(1, db.dailyDao().all().size)
        val restored = SavedStateHandle(mapOf("bookId" to 1L, "draftId" to handle.get<String>("draftId"), "amount" to "12.34"))
        val other = DailyEditViewModel(repo, restored)
        other.state.first { !it.loading }; other.save(); other.state.first { it.complete }
        assertEquals(1, db.dailyDao().all().size)
        vm.viewModelScope.cancel(); other.viewModelScope.cancel()
    }
    @Test fun missingHistoricalEntryCannotBecomeANewEntry() = runTest {
        val vm = DailyEditViewModel(DailyRepository(db), SavedStateHandle(mapOf("bookId" to 1L, "entryId" to "deleted")))
        assertTrue(vm.state.first { !it.loading }.missing)
        vm.save(); assertTrue(db.dailyDao().all().isEmpty()); vm.viewModelScope.cancel()
    }
    @Test fun selectedBookSurvivesClosingAndReopeningDataStore() = runTest {
        val file = File(context.cacheDir, "selected-book.preferences_pb").apply { delete() }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val first = SettingsRepository(PreferenceDataStoreFactory.create(scope = scope) { file })
        assertEquals(1L, first.selectedBook.first())
        first.selectBook(2)
        scope.coroutineContext[Job]!!.cancelAndJoin()
        val secondScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val second = SettingsRepository(PreferenceDataStoreFactory.create(scope = secondScope) { file })
            assertEquals(2L, second.selectedBook.first())
        } finally { secondScope.coroutineContext[Job]!!.cancelAndJoin() }
    }
}
