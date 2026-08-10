package com.barbershop.daily.ui.screens

import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.barbershop.daily.data.SessionStore
import com.barbershop.daily.data.SheetsRepository
import kotlinx.coroutines.launch

private data class StaffOption(
    val displayName: String,
    val initials: String,
    val color: Color
)

private val staff = listOf(
    StaffOption("Ng'ash", "NG", Color(0xFFE91E63)), // Pink
    StaffOption("Jay", "JA", Color(0xFF39FF14)),    // Neon Green
    StaffOption("Esther", "ES", Color(0xFF6200EA)), // Deep Purple
    StaffOption("Cate", "CA", Color(0xFFFF5722))    // Orange
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(onSignedIn: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf<StaffOption?>(null) }
    var pin by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF121212)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(40.dp))
            
            // Logo Icon with Glow
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .shadow(elevation = 20.dp, shape = CircleShape, ambientColor = Color(0xFF00E5FF), spotColor = Color(0xFF00E5FF))
                    .background(Color(0xFF1E1E1E), CircleShape)
                    .border(1.dp, Color(0xFF00E5FF).copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Star,
                    contentDescription = null,
                    tint = Color(0xFF00E5FF),
                    modifier = Modifier.size(30.dp)
                )
            }

            Spacer(Modifier.height(24.dp))

            // Title
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "B4A",
                    style = MaterialTheme.typography.displaySmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "APP",
                    style = MaterialTheme.typography.displaySmall,
                    color = Color(0xFF00E5FF),
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
            }
            Text(
                "BARBER 4 ALL",
                style = MaterialTheme.typography.labelLarge,
                color = Color.Gray,
                letterSpacing = 1.sp
            )

            Spacer(Modifier.height(48.dp))

            // Staff Grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(32.dp),
                horizontalArrangement = Arrangement.spacedBy(32.dp)
            ) {
                items(staff) { item ->
                    StaffCircle(
                        option = item,
                        isSelected = selected == item,
                        onClick = { 
                            selected = item 
                            pin = "" // clear pin when changing staff
                            error = null
                        }
                    )
                }
            }

            if (selected != null) {
                val handleLogin = {
                    error = null
                    loading = true
                    scope.launch {
                        try {
                            val deviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
                            val session = SheetsRepository.login(selected!!.displayName, pin, deviceId)
                            SessionStore.save(context, session)
                            onSignedIn()
                        } catch (t: Throwable) {
                            error = t.message ?: "Login failed"
                        } finally {
                            loading = false
                        }
                    }
                }

                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = pin,
                    onValueChange = { pin = it },
                    label = { Text("Enter PIN for ${selected?.displayName}", color = Color.Gray) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = androidx.compose.ui.text.input.ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { if (pin.isNotBlank()) handleLogin() }
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF00E5FF),
                        unfocusedBorderColor = Color.Gray,
                        cursorColor = Color(0xFF00E5FF)
                    )
                )

                Spacer(Modifier.height(16.dp))
                Button(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    enabled = !loading && pin.isNotBlank(),
                    onClick = { handleLogin() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (loading) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.Black)
                    } else {
                        Text("LOGIN", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (error != null) {
                Spacer(Modifier.height(12.dp))
                Text(error!!, color = Color.Red, style = MaterialTheme.typography.bodySmall)
            }
            
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun StaffCircle(
    option: StaffOption,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(option.color)
                .border(
                    width = if (isSelected) 4.dp else 0.dp,
                    color = Color.White,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                option.initials,
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            option.displayName,
            color = if (isSelected) Color.White else Color.Gray,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
