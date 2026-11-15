package com.zhuangxiu.ledger

import android.app.Application
import com.zhuangxiu.ledger.notification.Notifier
import com.zhuangxiu.ledger.notification.ReminderWorker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/** 应用入口：初始化 Hilt、通知渠道和每日提醒任务 */
@HiltAndroidApp
class LedgerApp : Application() {

    @Inject
    lateinit var notifier: Notifier

    override fun onCreate() {
        super.onCreate()
        notifier.ensureChannels()
        ReminderWorker.schedule(this)
    }
}
