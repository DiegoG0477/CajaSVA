package dev.dgset.cajasva

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import dev.dgset.cajasva.core.ui.theme.CajaSVATheme
import dev.dgset.cajasva.features.instructions.ui.InstructionsScreen
import dev.dgset.cajasva.features.scanning.ui.ScanningScreen
import dev.dgset.cajasva.features.results.ui.ResultsScreen

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CajaSVATheme {
                CajaSVAApp()
            }
        }
    }
}

@Composable
fun CajaSVAApp() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "instructions"
    ) {
        composable("instructions") {
            InstructionsScreen(
                onNavigateToScanning = {
                    navController.navigate("scanning")
                }
            )
        }

        composable("scanning") {
            ScanningScreen(
                onNavigateToResults = { imageUri ->
                    navController.navigate("results/${Uri.encode(imageUri.toString())}")
                },
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable("results/{imageUri}") { backStackEntry ->
            val imageUriString = backStackEntry.arguments?.getString("imageUri") ?: ""
            val imageUri = Uri.parse(Uri.decode(imageUriString))

            ResultsScreen(
                imageUri = imageUri,
                onScanAgain = {
                    navController.navigate("scanning") {
                        popUpTo("instructions") { inclusive = false }
                    }
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}