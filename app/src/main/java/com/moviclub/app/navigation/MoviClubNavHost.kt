package com.moviclub.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.moviclub.app.core.domain.model.Modality
import com.moviclub.app.features.home.HomeView
import com.moviclub.app.features.profile.ProfileView
import com.moviclub.app.features.social.SocialView
import com.moviclub.app.features.training.TrainingView
import com.moviclub.app.features.workout.WorkoutActiveView

@Composable
fun MoviClubApp() {
    val navController = rememberNavController()
    MoviClubAppScaffold(navController = navController) { modifier ->
        NavHost(navController = navController, startDestination = BottomNavItem.HOME.route, modifier = modifier) {
            composable(BottomNavItem.HOME.route) {
                HomeView(
                    onStartWorkout = { modality -> navController.navigate("workout/${modality.name}") },
                    onOpenTraining = { navController.navigate(BottomNavItem.TRAINING.route) }
                )
            }
            composable(BottomNavItem.TRAINING.route) {
                TrainingView(onStartWorkout = { modality -> navController.navigate("workout/${modality.name}") })
            }
            composable(BottomNavItem.SOCIAL.route) { SocialView() }
            composable(BottomNavItem.PROFILE.route) { ProfileView() }
            composable(
                route = "workout/{modality}",
                arguments = listOf(navArgument("modality") { type = NavType.StringType })
            ) { entry ->
                val modalityName = entry.arguments?.getString("modality")
                val modality = Modality.values().firstOrNull { it.name == modalityName }
                    ?: error("Unknown workout modality: $modalityName")
                var paused by remember { mutableStateOf(false) }
                WorkoutActiveView(
                    modality = modality,
                    isPaused = paused,
                    onTogglePause = { paused = !paused },
                    onFinish = { navController.popBackStack() }
                )
            }
        }
    }
}
