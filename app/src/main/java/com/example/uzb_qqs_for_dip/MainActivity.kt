package com.example.uzb_qqs_for_dip

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.uzb_qqs_for_dip.ui.navigation.AppNavHost
import com.example.uzb_qqs_for_dip.ui.theme.UZB_QQS_for_DIPTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            UZB_QQS_for_DIPTheme {
                AppNavHost()
            }
        }
    }
}
