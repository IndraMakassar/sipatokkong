package com.antoniusindra.sipatokkong

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.antoniusindra.sipatokkong.ui.SipatokkongMain
import com.antoniusindra.sipatokkong.ui.theme.SipatokkongTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SipatokkongTheme {
                SipatokkongMain()
            }
        }
    }
}
