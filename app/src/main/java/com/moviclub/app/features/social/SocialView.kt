package com.moviclub.app.features.social

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.moviclub.app.features.social.components.SocialCard
import com.moviclub.app.features.social.components.SocialRow

private val socialTabs = listOf("Rankings", "Grupos", "Amigos")

@Composable
fun SocialView(modifier: Modifier = Modifier) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("COMUNIDADE", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
            Text("Social", style = MaterialTheme.typography.headlineMedium)
            Text("A motivação cresce quando é compartilhada.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            socialTabs.forEachIndexed { index, label ->
                FilterChip(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    label = { Text(label) },
                    shape = RoundedCornerShape(50),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        }
        when (selectedTab) {
            0 -> {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("TEMPORADA ATUAL", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                        Text("Ranking mensal", style = MaterialTheme.typography.titleLarge)
                    }
                    Text("LIGA BRONZE", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                }
                SocialCard {
                    Text("—", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.secondary)
                    Text("Seu lugar ainda está em aberto.", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Registre seu primeiro treino para entrar na classificação.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                listOf("01", "02", "03").forEach { place ->
                    SocialRow(place, if (place == "01") "Lidere a temporada" else "Sua próxima meta", "— pts")
                }
            }
            1 -> {
                Text("Seus grupos", style = MaterialTheme.typography.titleLarge)
                SocialCard {
                    Text("TREINE EM EQUIPE", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                    Text("Ainda sem grupos", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Crie um grupo ou entre com um convite para acompanhar o progresso da equipe.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            else -> {
                Text("Amigos", style = MaterialTheme.typography.titleLarge)
                SocialCard {
                    Text("MOVIMENTO COMPARTILHADO", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                    Text("Encontre sua turma", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Adicione amigos para acompanhar treinos e manter a motivação em alta.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SocialViewPreview() {
    SocialView()
}
