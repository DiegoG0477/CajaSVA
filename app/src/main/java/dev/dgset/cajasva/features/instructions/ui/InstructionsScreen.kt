package dev.dgset.cajasva.features.instructions.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.dgset.cajasva.R
import dev.dgset.cajasva.core.navigation.NavigationEvent
import dev.dgset.cajasva.core.ui.theme.CajaSVATheme
import dev.dgset.cajasva.features.instructions.viewmodel.InstructionsViewModel

@Composable
fun InstructionsScreen(
    onNavigateToScanning: () -> Unit,
    viewModel: InstructionsViewModel = hiltViewModel()
) {
    LaunchedEffect(key1 = Unit) {
        viewModel.navigationEvent.collect { event ->
            when (event) {
                is NavigationEvent.NavigateToScanning -> onNavigateToScanning()
                else -> {}
            }
        }
    }

    CajaSVATheme {
        InstructionsContent(onScanNowClick = viewModel::onScanNowClick)
    }
}

@Composable
fun InstructionsContent(onScanNowClick: () -> Unit) {
    Scaffold { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Text("Cash Scan Buddy", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
            Text("Detecta y clasifica tu dinero al instante", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(24.dp))

            // Ilustración principal
            Surface(shape = RoundedCornerShape(16.dp), shadowElevation = 4.dp) {
                 Image(painter = painterResource(id = R.drawable.ic_launcher_foreground), contentDescription = "Escaneo Inteligente", modifier = Modifier.size(150.dp).padding(16.dp))
            }
            Spacer(Modifier.height(24.dp))

            // Tarjetas de características con emojis en lugar de íconos
            InfoCard(emoji = "📷", title = "Escaneo Preciso", subtitle = "Detecta billetes y monedas con alta precisión")
            Spacer(Modifier.height(16.dp))
            InfoCard(emoji = "💰", title = "Múltiples Denominaciones", subtitle = "Reconoce diferentes tipos de billetes y monedas")
            Spacer(Modifier.height(16.dp))
            InfoCard(emoji = "✅", title = "Resultados Instantáneos", subtitle = "Obtén el total y desglose en segundos")
            Spacer(Modifier.height(24.dp))

            // Cómo usar
            Text("¿Cómo usar?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            InstructionStep(number = 1, text = "Coloca billetes y monedas sobre una superficie plana")
            Spacer(Modifier.height(8.dp))
            InstructionStep(number = 2, text = "Asegúrate de que no se sobrepongan entre sí")
            Spacer(Modifier.height(8.dp))
            InstructionStep(number = 3, text = "Toca \"Iniciar Escaneo\" y captura la imagen")

            Spacer(Modifier.weight(1f))

            // Botón de acción
            Button(
                onClick = onScanNowClick,
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text("📸", fontSize = 20.sp)
                Spacer(Modifier.width(8.dp))
                Text("Iniciar Escaneo", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun InfoCard(emoji: String, title: String, subtitle: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
            Text(
                text = emoji,
                fontSize = 32.sp,
                modifier = Modifier.size(40.dp)
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun InstructionStep(number: Int, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary)) {
            Text(number.toString(), color = Color.White, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(16.dp))
        Text(text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
    }
}
