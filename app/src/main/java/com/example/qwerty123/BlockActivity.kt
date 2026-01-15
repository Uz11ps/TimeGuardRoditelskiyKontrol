package com.example.qwerty123

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.OnBackPressedCallback
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.qwerty123.data.Prefs
import com.example.qwerty123.ui.theme.Qwerty123Theme

class BlockActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val isUninstall = intent.getBooleanExtra("isUninstallAttempt", false)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                moveTaskToBack(true)
            }
        })

        setContent {
            Qwerty123Theme {
                BlockScreen(isUninstall)
            }
        }
    }
}

@Composable
fun BlockScreen(isUninstall: Boolean = false) {
    val context = LocalContext.current
    var pin by remember { mutableStateOf("") }
    val correctPin = remember { Prefs.getPin(context) ?: "0000" }
    var error by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isUninstall) Color(0xFFD32F2F) else Color.Red)
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (isUninstall) "Защита от удаления" else "Доступ ограничен!",
            style = MaterialTheme.typography.displaySmall,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = if (isUninstall) 
                "Для удаления приложения TimeGuard введите ПИН-код родителя." 
                else "Это приложение или сайт заблокированы родительским контролем.",
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        OutlinedTextField(
            value = pin,
            onValueChange = { 
                if (it.length <= 4) {
                    pin = it
                    error = false
                }
            },
            label = { Text("Введите ПИН родителя", color = Color.White) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            visualTransformation = PasswordVisualTransformation(),
            isError = error,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color.White,
                unfocusedBorderColor = Color.White.copy(alpha = 0.7f),
                cursorColor = Color.White
            )
        )
        
        if (error) {
            Text("Неверный ПИН-код", color = Color.Yellow, style = MaterialTheme.typography.labelSmall)
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(
            onClick = {
                if (pin == correctPin) {
                    (context as? ComponentActivity)?.finish()
                } else {
                    error = true
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Red),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("РАЗБЛОКИРОВАТЬ")
        }
    }
}




