package com.helga.android

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.helga.android.data.preferences.AppPreferences
import com.helga.android.ui.onboarding.OnboardingScreen
import com.helga.android.ui.recipes.RecipeDetailScreen
import com.helga.android.ui.recipes.RecipeFormScreen
import com.helga.android.ui.recipes.RecipeListScreen
import com.helga.android.ui.settings.SettingsScreen
import kotlinx.coroutines.flow.first

internal const val ROUTE_ONBOARDING = "onboarding"
internal const val ROUTE_RECIPES = "recipes"
internal const val ROUTE_RECIPE_DETAIL = "recipe/{recipeId}"
internal const val ROUTE_RECIPE_CREATE = "recipe/new"
internal const val ROUTE_RECIPE_EDIT = "recipe/{recipeId}/edit"
internal const val ROUTE_SETTINGS = "settings"

internal fun recipeDetailRoute(id: String) = "recipe/$id"
internal fun recipeEditRoute(id: String) = "recipe/$id/edit"

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun HelgaNavGraph(preferences: AppPreferences) {
    val navController: NavHostController = rememberNavController()

    LaunchedEffect(Unit) {
        val conn = preferences.connection.first()
        if (conn.isConfigured) {
            navController.navigate(ROUTE_RECIPES) {
                popUpTo(ROUTE_ONBOARDING) { inclusive = true }
            }
        }
    }

    SharedTransitionLayout {
        NavHost(navController = navController, startDestination = ROUTE_ONBOARDING) {
            composable(ROUTE_ONBOARDING) {
                OnboardingScreen(
                    onContinue = {
                        navController.navigate(ROUTE_RECIPES) {
                            popUpTo(ROUTE_ONBOARDING) { inclusive = true }
                        }
                    },
                )
            }
            composable(ROUTE_RECIPES) {
                RecipeListScreen(
                    onRecipeClick = { id -> navController.navigate(recipeDetailRoute(id)) },
                    onCreateClick = { navController.navigate(ROUTE_RECIPE_CREATE) },
                    onSettingsClick = { navController.navigate(ROUTE_SETTINGS) },
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedVisibilityScope = this,
                )
            }
            composable(ROUTE_SETTINGS) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onLoggedOut = {
                        navController.navigate(ROUTE_ONBOARDING) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                )
            }
            composable(
                route = ROUTE_RECIPE_DETAIL,
                arguments = listOf(navArgument("recipeId") { type = NavType.StringType }),
            ) {
                RecipeDetailScreen(
                    onBack = { navController.popBackStack() },
                    onEdit = { id -> navController.navigate(recipeEditRoute(id)) },
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedVisibilityScope = this,
                )
            }
            composable(ROUTE_RECIPE_CREATE) {
                RecipeFormScreen(onBack = { navController.popBackStack() })
            }
            composable(
                route = ROUTE_RECIPE_EDIT,
                arguments = listOf(navArgument("recipeId") { type = NavType.StringType }),
            ) {
                RecipeFormScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
