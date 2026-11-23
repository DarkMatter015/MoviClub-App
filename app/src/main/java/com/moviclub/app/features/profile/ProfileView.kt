package com.moviclub.app.features.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.moviclub.app.features.profile.components.ProfileOption

@Composable
fun ProfileView(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("SUA JORNADA", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
            Text("Perfil", style = MaterialTheme.typography.headlineMedium)
        }
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        "MC",
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.secondary, CircleShape)
                            .padding(14.dp),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSecondary,
                        fontWeight = FontWeight.Bold
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("Atleta MoviClub", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimary)
                        Text("@seu_usuario", color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f))
                    }
                }
                Text("NOTA DO PERFIL", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "—",
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "/ 100",
                        modifier = Modifier.padding(bottom = 8.dp),
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                    )
                }
                Text(
                    "Sua constância e intensidade constroem sua história.",
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.78f)
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Treino e hardware", style = MaterialTheme.typography.titleLarge)
            listOf(
                "Metas e preferências" to "Ajuste sua rotina semanal",
                "Sensores e calibração" to "Confira os sensores do aparelho",
                "Sincronização offline" to "Treinos salvos neste dispositivo"
            ).forEach { (title, subtitle) -> ProfileOption(title, subtitle) }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Aplicativo", style = MaterialTheme.typography.titleLarge)
            listOf(
                "Notificações" to "Lembretes e novidades",
                "Privacidade" to "Controle a visibilidade do perfil",
                "Ajuda e sobre" to "Pontuação, ligas e MoviClub"
            ).forEach { (title, subtitle) -> ProfileOption(title, subtitle) }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ProfileViewPreview() {
    ProfileView()
}
