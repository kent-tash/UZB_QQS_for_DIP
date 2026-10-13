package com.example.uzb_qqs_for_dip.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.uzb_qqs_for_dip.MainActivity
import com.example.uzb_qqs_for_dip.R

/**
 * Виджет рабочего стола для быстрого добавления чека по QR-коду.
 * При клике открывает приложение и сразу запускает сканер QR.
 */
class QuickScanWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {
        const val ACTION_QUICK_SCAN = "com.example.uzb_qqs_for_dip.ACTION_QUICK_SCAN"
        const val EXTRA_QUICK_SCAN = "extra_quick_scan"

        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_quick_scan)

            val intent = Intent(context, MainActivity::class.java).apply {
                action = ACTION_QUICK_SCAN
                putExtra(EXTRA_QUICK_SCAN, true)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                appWidgetId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
