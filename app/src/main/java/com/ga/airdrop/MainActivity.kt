package com.ga.airdrop

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.navigation.AppRoot
import com.ga.airdrop.core.push.PushDeepLink

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        PushDeepLink.capture(intent)
        setContent {
            AirdropTheme {
                AppRoot()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        PushDeepLink.capture(intent)
    }
}
