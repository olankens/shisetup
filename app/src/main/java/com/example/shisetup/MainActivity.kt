package com.example.shisetup

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.shisetup.ui.theme.ShisetupTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val model: AndroidScreenViewModel by viewModels()
//        val model: MachineScreenViewModel by viewModels()
        super.onCreate(savedInstanceState)
        setContent {
            ShisetupTheme() {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AndroidScreen(model)
//                    MachineScreen(model)
                }
            }
        }
    }
}