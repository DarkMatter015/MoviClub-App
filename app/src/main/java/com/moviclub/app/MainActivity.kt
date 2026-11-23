package com.moviclub.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.moviclub.app.core.theme.MoviClubTheme
import com.moviclub.app.navigation.MoviClubApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MoviClubTheme {
                MoviClubApp()
            }
        }
    }
}
