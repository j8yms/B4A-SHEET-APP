package com.barbershop.daily.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AdminScreen() {
    Surface(Modifier.fillMaxSize().padding(16.dp)) {
        Column {
            Text("Admin", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(
                "MVP note: admin management screens (staff PIN reset, dropdown options, reports) can be added next.\n\n" +
                    "For now, admin actions are done in Firebase Console:\n" +
                    "• Create/reset user passwords (setup codes)\n" +
                    "• Update userProfiles/{email} docs (role, displayName)\n"
            )
        }
    }
}

