package com.example.shisetup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import androidx.compose.material3.ButtonDefaults

@Composable
fun AndroidScreen(viewModel: AndroidScreenViewModel) {
    val context = LocalContext.current
    val scoping = rememberCoroutineScope()
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize()
    )
    {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color.Black)
                .padding(64.dp)
        ) {
            if (viewModel.loading.value) {
                CircularProgressIndicator(
                    strokeWidth = 24.dp,
                    modifier = Modifier.size(210.dp),
                )
            } else {
                CircularProgressIndicator(
                    progress = { 1.0f },
                    modifier = Modifier.size(210.dp),
                    strokeWidth = 24.dp,
                )
            }
            Spacer(modifier = Modifier.height(40.dp))
            Text(
                text = viewModel.error.value,
                style = TextStyle(
                    fontSize = 16.sp,
                    lineHeight = 32.sp,
                    textAlign = TextAlign.Center,
                    color = Color.Red
                ),
            )
            Text(
                text = viewModel.content.value,
                style = TextStyle(
                    fontSize = 24.sp,
                    lineHeight = 32.sp,
                    textAlign = TextAlign.Center,
                    color = Color.Yellow
                ),
            )
            Spacer(modifier = Modifier.height(40.dp))
            Text(
                text = if (viewModel.loading.value) "Work in progress" else "Completed successfully",
                style = TextStyle(
                    fontSize = 40.sp,
                    lineHeight = 64.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                ),
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.DarkGray)
                .padding(16.dp)
        ) {
            Button(
                onClick = {
                    scoping.launch {
                        val value = viewModel.onButtonClicked()
                        // Toast.makeText(context, value, Toast.LENGTH_LONG).show()
                    }
                },
                enabled = !viewModel.loading.value,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "",
                    tint = Color.White,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Button(
                onClick = {
                    scoping.launch {
                        viewModel.onContinueButtonClicked()
                    }
                },
                enabled = !viewModel.loading.value,
                modifier = Modifier
                    .weight(3f)
                    .height(50.dp),
            ) {
                Icon(
                    Icons.Default.ArrowForward,
                    contentDescription = "",
                    tint = Color.White,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}