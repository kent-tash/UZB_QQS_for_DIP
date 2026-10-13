package com.example.uzb_qqs_for_dip

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.uzb_qqs_for_dip.ui.AppViewModel
import com.example.uzb_qqs_for_dip.ui.navigation.AppNavHost
import com.example.uzb_qqs_for_dip.ui.theme.UZB_QQS_for_DIPTheme
import com.example.uzb_qqs_for_dip.util.ReceiptUrlExtractor
import com.example.uzb_qqs_for_dip.widget.QuickScanWidgetProvider

class MainActivity : ComponentActivity() {

    private val appViewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        setContent {
            UZB_QQS_for_DIPTheme {
                AppNavHost(appViewModel = appViewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.action == QuickScanWidgetProvider.ACTION_QUICK_SCAN ||
            intent.getBooleanExtra(QuickScanWidgetProvider.EXTRA_QUICK_SCAN, false)
        ) {
            appViewModel.requestQuickScan()
        }

        val receiptUrl = ReceiptUrlExtractor.extractFromIntent(intent)
        if (receiptUrl != null) {
            appViewModel.handleIncomingReceiptUrl(receiptUrl)
        } else if (intent.action == Intent.ACTION_SEND) {
            Toast.makeText(this, "В отправленном тексте не найдена ссылка на чек", Toast.LENGTH_SHORT).show()
        }
    }
}
