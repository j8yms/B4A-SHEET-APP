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
import androidx.compose.material.icons.filled.Fingerprint
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
import androidx.fragment.app.FragmentActivity
import com.barbershop.daily.data.BiometricHelper
import com.barbershop.daily.data.SessionStore
import com.barbershop.daily.data.SheetsRepository
import kotlinx.coroutines.launch

private data class StaffOption(
    val displayName: String,
    val initials: String,
    val color: Color
)

private val brandColors = listOf(
    Color(0xFFE91E63), // Pink
    Color(0xFF39FF14), // Neon Green
    Color(0xFF6200EA), // Deep Purple
    Color(0xFFFF5722), // Orange
    Color(0xFF00E5FF), // Cyan
    Color(0xFF00BCD4), // Teal
)

private val defaultStaff = listOf(
    StaffOption("Ng'ash", "NG", Color(0xFFE91E63)),
    StaffOption("Jay", "JA", Color(0xFF39FF14)),
    StaffOption("Esther", "ES", Color(0xFF6200EA)),
    StaffOption("Cate", "CA", Color(0xFFFF5722))
)

private fun initialsFor(name: String): String =
    name.trim().split(" ").mapNotNull { it.firstOrNull() }.take(2).joinToString("").uppercase()
        .ifBlank { name.take(2).uppercase() }

private fun colorFor(name: String, index: Int): Color {
    val known = mapOf(
        "Ng'ash" to Color(0xFFE91E63),
        "Jay" to Color(0xFF39FF14),
        "Esther" to Color(0xFF6200EA),
        "Cate" to Color(0xFFFF5722)
    )
    known[name]?.let { return it }
    val hash = name.hashCode()
    val idx = (index.coerceAtLeast(0) + hash) % brandColors.size
    return brandColors[(idx + brandColors.size) % brandColors.size]
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(onSignedIn: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var staffOptions by remember { mutableStateOf(defaultStaff) }
    var selected by remember { mutableStateOf<StaffOption?>(null) }
    var pin by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var rememberPin by remember { mutableStateOf(SessionStore.isRememberEnabled(context)) }

    val canUseBiometrics = remember(context) { BiometricHelper.isAvailable(context) }

    // Load the live staff roster from the backend (supports admin renames).
    LaunchedEffect(Unit) {
        try {
            val res = SheetsRepository.bootstrap()
            val arr = res.optJSONArray("staff")
            if (arr != null && arr.length() > 0) {
                val opts = (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    val name = o.optString("name")
                    if (name.isBlank()) null
                    else StaffOption(name, initialsFor(name), colorFor(name, i))
                }.filterNotNull()
                if (opts.isNotEmpty()) staffOptions = opts
            }
        } catch (_: Throwable) {
            // keep the default hardcoded list
        }
    }

    // If a PIN is remembered, auto-select that staff and pre-fill the PIN.
    LaunchedEffect(staffOptions) {
        val remembered = SessionStore.loadRemembered(context) ?: return@LaunchedEffect
        selected = staffOptions.find { it.displayName == remembered.first } ?: selected
        pin = remembered.second
    }

    val performLogin: (String, String) -> Unit = { name, pinValue ->
        scope.launch {
            error = null
            loading = true
            try {
                val deviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
                val session = SheetsRepository.login(name, pinValue, deviceId)
                SessionStore.save(context, session)
                if (rememberPin) {
                    SessionStore.saveRemembered(context, session.name, pinValue, true)
                } else {
                    SessionStore.clearRemembered(context)
                }
                onSignedIn()
            } catch (t: Throwable) {
                error = t.message ?: "Login failed"
            } finally {
                loading = false
            }
        }
    }

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

            Spacer(Modifier.height(32.dp))

            // Staff Grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                horizontalArrangement = Arrangement.spacedBy(32.dp)
            ) {
                items(staffOptions) { item ->
                    StaffCircle(
                        option = item,
                        isSelected = selected == item,
                        onClick = {
                            selected = item
                            pin = ""
                            error = null
                        }
                    )
                }
            }

            if (selected != null) {
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
                        onDone = { if (pin.isNotBlank()) performLogin(selected!!.displayName, pin) }
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF00E5FF),
                        unfocusedBorderColor = Color.Gray,
                        cursorColor = Color(0xFF00E5FF)
                    )
                )

                Spacer(Modifier.height(12.dp))

                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { rememberPin = !rememberPin },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = rememberPin,
                        onCheckedChange = { rememberPin = it }
                    )
                    Text("Remember PIN for easy login", color = Color.White, style = MaterialTheme.typography.bodyMedium)
                }

                val remembered = SessionStore.loadRemembered(context)
                if (canUseBiometrics && remembered != null) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        enabled = !loading,
                        onClick = {
                            val activity = context as? FragmentActivity
                            if (activity != null) {
                                BiometricHelper.prompt(
                                    activity = activity,
                                    onSuccess = { performLogin(remembered.first, remembered.second) },
                                    onError = { error = "Fingerprint not recognised" }
                                )
                            }
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            Icons.Default.Fingerprint,
                            contentDescription = null,
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("LOGIN WITH FINGERPRINT", color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(Modifier.height(8.dp))
                Button(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    enabled = !loading && pin.isNotBlank(),
                    onClick = { performLogin(selected!!.displayName, pin) },
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
