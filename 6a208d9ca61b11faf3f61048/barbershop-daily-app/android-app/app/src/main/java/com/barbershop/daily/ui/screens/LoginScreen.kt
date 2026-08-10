package com.barbershop.daily.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.barbershop.daily.data.FirebaseRepository
import kotlinx.coroutines.launch

private data class StaffOption(val displayName: String, val email: String)

private val staff = listOf(
    StaffOption("Ng'ash", "ngash@barbershop.local"),
    StaffOption("Jay", "jay@barbershop.local"),
    StaffOption("Esther", "esther@barbershop.local"),
    StaffOption("Cate", "cate@barbershop.local"),
)

@Composable
fun LoginScreen(onSignedIn: () -> Unit) {
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf(staff.first()) }
    var expanded by remember { mutableStateOf(false) }
    var pin by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text("Barbershop Daily", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(12.dp))
            Text("Select your name and enter your PIN (or setup code).")
            Spacer(Modifier.height(24.dp))

            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    readOnly = true,
                    value = selected.displayName,
                    onValueChange = {},
                    label = { Text("Name") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    staff.forEach {
                        DropdownMenuItem(
                            text = { Text(it.displayName) },
                            onClick = {
                                selected = it
                                expanded = false
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = pin,
                onValueChange = { pin = it },
                label = { Text("PIN / Setup code") },
                visualTransformation = PasswordVisualTransformation()
            )

            Spacer(Modifier.height(16.dp))
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = !loading && pin.isNotBlank(),
                onClick = {
                    error = null
                    loading = true
                    scope.launch {
                        try {
                            FirebaseRepository.signIn(selected.email, pin)
                            onSignedIn()
                        } catch (t: Throwable) {
                            error = t.message ?: "Login failed"
                        } finally {
                            loading = false
                        }
                    }
                }
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text("Login")
            }

            if (error != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    error!!,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(Modifier.height(24.dp))
            AssistChip(
                onClick = { /* informational */ },
                label = { Text("Admin can reset setup codes in Firebase Auth") }
            )
        }
    }
}

