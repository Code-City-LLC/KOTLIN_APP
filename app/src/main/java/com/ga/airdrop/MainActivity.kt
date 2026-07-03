package com.ga.airdrop

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.navigation.AppRoot

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AirdropTheme {
                AppRoot()
            }
        }
    }
}
