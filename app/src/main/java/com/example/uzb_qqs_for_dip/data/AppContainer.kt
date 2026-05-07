package com.example.uzb_qqs_for_dip.data

import android.content.Context
import com.example.uzb_qqs_for_dip.data.backup.AppBackup
import com.example.uzb_qqs_for_dip.data.db.DbHelper
import com.example.uzb_qqs_for_dip.data.repository.ReceiptRepository
import com.example.uzb_qqs_for_dip.data.repository.UserRepository
import com.example.uzb_qqs_for_dip.data.session.SessionManager
import com.example.uzb_qqs_for_dip.data.settings.ReportSettingsHolder
import com.example.uzb_qqs_for_dip.network.ReceiptParser

/**
 * Простейший Service Locator. Создаётся в [com.example.uzb_qqs_for_dip.QqsApp]
 * и используется ViewModel-ями через application-контекст.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    private val dbHelper: DbHelper = DbHelper(appContext)

    val userRepository: UserRepository = UserRepository(dbHelper)
    val receiptRepository: ReceiptRepository = ReceiptRepository(dbHelper)
    val sessionManager: SessionManager = SessionManager(appContext)
    val receiptParser: ReceiptParser = ReceiptParser()

    /** Общие настройки отчёта (фильтр + сортировка), общие для вкладок «Отчёт» и «Чеки». */
    val reportSettings: ReportSettingsHolder = ReportSettingsHolder()

    /** Экспорт/импорт одного JSON-файла: профили, чеки, настройки отчёта. */
    val appBackup: AppBackup = AppBackup(
        dbHelper = dbHelper,
        reportSettingsHolder = reportSettings,
        userRepository = userRepository,
        receiptRepository = receiptRepository,
    )
}
