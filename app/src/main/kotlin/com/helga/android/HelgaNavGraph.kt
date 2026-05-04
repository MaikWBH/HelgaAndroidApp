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
import com.helga.android.ui.ai.AiGenerateScreen
import com.helga.android.ui.ai.AiRemixScreen
import com.helga.android.ui.onboarding.OnboardingScreen
import com.helga.android.ui.recipes.RecipeCookScreen
import com.helga.android.ui.recipes.RecipeDetailScreen
import com.helga.android.ui.recipes.RecipeFormScreen
import com.helga.android.ui.recipes.RecipeListScreen
import com.helga.android.ui.recipes.UrlImportScreen
import com.helga.android.ui.settings.SettingsScreen
import com.helga.android.ui.shopping.ShoppingListScreen
import com.helga.android.ui.stores.StoreListScreen
import com.helga.android.ui.weekplan.WeekplanScreen
import kotlinx.coroutines.flow.first

internal const val ROUTE_ONBOARDING = "onboarding"
internal const val ROUTE_RECIPES = "recipes"
internal const val ROUTE_SHOPPING = "shopping"
internal const val ROUTE_STORES = "stores"
internal const val ROUTE_WEEKPLAN = "weekplan"
internal const val ROUTE_RECIPE_DETAIL = "recipe/{recipeId}"
internal const val ROUTE_RECIPE_CREATE = "recipe/new"
internal const val ROUTE_RECIPE_EDIT = "recipe/{recipeId}/edit"
internal const val ROUTE_RECIPE_COOK = "recipe/{recipeId}/cook"
internal const val ROUTE_RECIPE_REMIX = "recipe/{recipeId}/remix"
internal const val ROUTE_RECIPE_URL_IMPORT = "recipe/url-import"
internal const val ROUTE_AI_GENERATE = "ai/generate"
internal const val ROUTE_SETTINGS = "settings"

internal fun recipeDetailRoute(id: String) = "recipe/$id"
internal fun recipeEditRoute(id: String) = "recipe/$id/edit"
internal fun recipeCookRoute(id: String) = "recipe/$id/cook"
internal fun recipeRemixRoute(id: String) = "recipe/$id/remix"

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun HelgaNavGraph(preferences: AppPreferences, initialImportUrl: String? = null) {
    val navController: NavHostController = rememberNavController()

    LaunchedEffect(Unit) {
        val conn = preferences.connection.first()
        if (conn.isConfigured) {
            val dest = if (initialImportUrl != null) ROUTE_RECIPE_URL_IMPORT else ROUTE_RECIPES
            navController.navigate(dest) {
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
                    onImportClick = { navController.navigate(ROUTE_RECIPE_URL_IMPORT) },
                    onAiGenerateClick = { navController.navigate(ROUTE_AI_GENERATE) },
                    onSettingsClick = { navController.navigate(ROUTE_SETTINGS) },
                    onShoppingClick = { navController.navigate(ROUTE_SHOPPING) },
                    onWeekplanClick = { navController.navigate(ROUTE_WEEKPLAN) },
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedVisibilityScope = this,
                )
            }
            composable(ROUTE_SHOPPING) {
                ShoppingListScreen(onBack = { navController.popBackStack() })
            }
            composable(ROUTE_RECIPE_URL_IMPORT) {
                UrlImportScreen(
                    onBack = { navController.popBackStack() },
                    initialUrl = initialImportUrl,
                )
            }
            composable(ROUTE_AI_GENERATE) {
                AiGenerateScreen(
                    onBack = { navController.popBackStack() },
                    onSaved = { id ->
                        navController.navigate(recipeDetailRoute(id)) {
                            popUpTo(ROUTE_RECIPES)
                        }
                    },
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
                    onStoresClick = { navController.navigate(ROUTE_STORES) },
                )
            }
            composable(ROUTE_STORES) {
                StoreListScreen(onBack = { navController.popBackStack() })
            }
            composable(ROUTE_WEEKPLAN) {
                WeekplanScreen(onBack = { navController.popBackStack() })
            }
            composable(
                route = ROUTE_RECIPE_DETAIL,
                arguments = listOf(navArgument("recipeId") { type = NavType.StringType }),
            ) {
                RecipeDetailScreen(
                    onBack = { navController.popBackStack() },
                    onEdit = { id -> navController.navigate(recipeEditRoute(id)) },
                    onCook = { id -> navController.navigate(recipeCookRoute(id)) },
                    onNewRecipe = { navController.navigate(ROUTE_RECIPE_CREATE) },
                    onAiGenerate = { navController.navigate(ROUTE_AI_GENERATE) },
                    onImport = { navController.navigate(ROUTE_RECIPE_URL_IMPORT) },
                    onRemix = { id -> navController.navigate(recipeRemixRoute(id)) },
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedVisibilityScope = this,
                )
            }
            composable(
                route = ROUTE_RECIPE_COOK,
                arguments = listOf(navArgument("recipeId") { type = NavType.StringType }),
            ) {
                RecipeCookScreen(onBack = { navController.popBackStack() })
            }
            composable(
                route = ROUTE_RECIPE_REMIX,
                arguments = listOf(navArgument("recipeId") { type = NavType.StringType }),
            ) {
                AiRemixScreen(
                    onBack = { navController.popBackStack() },
                    onSaved = { id ->
                        navController.navigate(recipeDetailRoute(id)) {
                            popUpTo(ROUTE_RECIPES)
                        }
                    },
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
