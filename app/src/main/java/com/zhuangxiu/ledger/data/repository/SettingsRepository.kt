package com.zhuangxiu.ledger.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** 提醒相关的设置项 */
data class ReminderSettings(
    /** 是否开启待付款到期提醒 */
    val paymentReminderEnabled: Boolean = true,
    /** 到期前几天开始提醒 */
    val remindDaysBefore: Int = 3,
    /** 是否开启预算预警通知 */
    val budgetAlertEnabled: Boolean = true,
)

/** 设置仓库（DataStore） */
@Singleton
class SettingsRepository @Inject constructor(private val dataStore: DataStore<Preferences>) {

    val selectedBook: Flow<Long> = dataStore.data.map { it[androidx.datastore.preferences.core.longPreferencesKey("selected_book")] ?: 1L }

    suspend fun selectBook(id: Long) {
        dataStore.edit { it[androidx.datastore.preferences.core.longPreferencesKey("selected_book")] = id }
    }

    val reminderSettings: Flow<ReminderSettings> = dataStore.data.map { prefs ->
        ReminderSettings(
            paymentReminderEnabled = prefs[KEY_PAYMENT_REMINDER] ?: true,
            remindDaysBefore = prefs[KEY_REMIND_DAYS] ?: 3,
            budgetAlertEnabled = prefs[KEY_BUDGET_ALERT] ?: true,
        )
    }

    suspend fun setPaymentReminderEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_PAYMENT_REMINDER] = enabled }
    }

    suspend fun setRemindDaysBefore(days: Int) {
        dataStore.edit { it[KEY_REMIND_DAYS] = days.coerceIn(0, 30) }
    }

    suspend fun setBudgetAlertEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_BUDGET_ALERT] = enabled }
    }

    /**
     * 已经发过通知的预算预警标记（形如 "分类id:WARNING"），
     * 用来保证同一档预警只通知一次。
     */
    suspend fun notifiedBudgetKeys(): Set<String> = dataStore.data.first()[KEY_NOTIFIED_BUDGETS].orEmpty()

    suspend fun setNotifiedBudgetKeys(keys: Set<String>) {
        dataStore.edit { it[KEY_NOTIFIED_BUDGETS] = keys }
    }

    private companion object {
        val KEY_PAYMENT_REMINDER = booleanPreferencesKey("payment_reminder_enabled")
        val KEY_REMIND_DAYS = intPreferencesKey("remind_days_before")
        val KEY_BUDGET_ALERT = booleanPreferencesKey("budget_alert_enabled")
        val KEY_NOTIFIED_BUDGETS = stringSetPreferencesKey("notified_budget_keys")
    }
}
