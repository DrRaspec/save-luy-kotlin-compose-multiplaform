package com.bunleng.saving_luy

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

class MainActivity : ComponentActivity() {

    private var addTransactionRequests by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Only on a fresh start, so rotating the screen does not reopen the form.
        if (savedInstanceState == null) handleIntent(intent)

        val container = (application as MoneyApplication).container
        setContent {
            App(container, addTransactionRequests)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == ACTION_ADD_TRANSACTION) addTransactionRequests++
    }

    companion object {
        /** Sent by the home screen widget. */
        const val ACTION_ADD_TRANSACTION = "com.bunleng.saving_luy.ADD_TRANSACTION"
    }
}
