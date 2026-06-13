package com.helga.android

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
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
import com.helga.android.ui.pantry.PantryScreen
import com.helga.android.ui.products.MyProductsScreen
import com.helga.android.ui.products.IngredientMappingScreen
import com.helga.android.ui.stats.StatsScreen
import com.helga.android.ui.weekplan.WeekplanRecipePickerScreen
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
internal const val ROUTE_PANTRY = "pantry"
internal const val ROUTE_STATS = "stats"
internal const val ROUTE_MY_PRODUCTS = "my-products"
internal const val ROUTE_INGREDIENT_MAPPING = "ingredient-mapping"
internal const val ROUTE_PURCHASE_STATS = "purchase-stats"
internal const val ROUTE_WEEKPLAN_PICK_RECIPE = "weekplan/pick-recipe/{dayId}"

internal fun recipeDetailRoute(id: String) = "recipe/$id"
internal fun recipeEditRoute(id: String) = "recipe/$id/edit"
internal fun recipeCookRoute(id: String) = "recipe/$id/cook"
internal fun recipeRemixRoute(id: String) = "recipe/$id/remix"
internal fun weekplanPickRecipeRoute(dayId: String) = "weekplan/pick-recipe/$dayId"

private val ROOT_ROUTES = setOf(ROUTE_SHOPPING, ROUTE_RECIPES, ROUTE_WEEKPLAN)

private data class BottomNavItem(val route: String, val icon: ImageVector, val labelRes: Int)

private val bottomNavItems = listOf(
    BottomNavItem(ROUTE_SHOPPING, Icons.Filled.ShoppingCart, R.string.nav_shopping),
    BottomNavItem(ROUTE_RECIPES, Icons.Filled.Restaurant, R.string.nav_recipes),
    BottomNavItem(ROUTE_WEEKPLAN, Icons.Filled.CalendarMonth, R.string.nav_weekplan),
)

@Composable
fun HelgaNavGraph(preferences: AppPreferences, initialImportUrl: String? = null) {
    val navController: NavHostController = rememberNavController()
    val navBackStack by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStack?.destination?.route
    val showBottomNav = currentRoute in ROOT_ROUTES

    LaunchedEffect(Unit) {
        val conn = preferences.connection.first()
        if (conn.isConfigured) {
            val dest = if (initialImportUrl != null) ROUTE_RECIPE_URL_IMPORT else ROUTE_SHOPPING
            navController.navigate(dest) {
                popUpTo(ROUTE_ONBOARDING) { inclusive = true }
            }
        }
    }

    Scaffold(
        bottomBar = {
            if (showBottomNav) {
                NavigationBar {
                    bottomNavItems.forEach { item ->
                        NavigationBarItem(
                            selected = currentRoute == item.route,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(ROUTE_SHOPPING) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(imageVector = item.icon, contentDescription = stringResource(item.labelRes))
                            },
                            label = { Text(stringResource(item.labelRes)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = ROUTE_ONBOARDING,
        ) {
                composable(ROUTE_ONBOARDING) {
                    OnboardingScreen(
                        onContinue = {
                            navController.navigate(ROUTE_SHOPPING) {
                                popUpTo(ROUTE_ONBOARDING) { inclusive = true }
                            }
                        },
                    )
                }
                composable(ROUTE_SHOPPING) {
                    ShoppingListScreen(
                        bottomPadding = padding.calculateBottomPadding(),
                        onNavigateToWeekplan = { navController.navigate(ROUTE_WEEKPLAN) },
                    )
                }
                composable(ROUTE_RECIPES) {
                    RecipeListScreen(
                        onRecipeClick = { id -> navController.navigate(recipeDetailRoute(id)) },
                        onCreateClick = { navController.navigate(ROUTE_RECIPE_CREATE) },
                        onImportClick = { navController.navigate(ROUTE_RECIPE_URL_IMPORT) },
                        onAiGenerateClick = { navController.navigate(ROUTE_AI_GENERATE) },
                        onSettingsClick = { navController.navigate(ROUTE_SETTINGS) },
                        onCookClick = { id -> navController.navigate(recipeCookRoute(id)) },
                        bottomPadding = padding.calculateBottomPadding(),
                    )
                }
                composable(ROUTE_WEEKPLAN) {
                    WeekplanScreen(
                        bottomPadding = padding.calculateBottomPadding(),
                        onAddRecipeForDay = { dayId ->
                            navController.navigate(weekplanPickRecipeRoute(dayId))
                        },
                        onNavigateToRecipe = { id -> navController.navigate(recipeDetailRoute(id)) },
                    )
                }
                composable(
                    route = ROUTE_WEEKPLAN_PICK_RECIPE,
                    arguments = listOf(navArgument("dayId") { type = NavType.StringType }),
                ) {
                    WeekplanRecipePickerScreen(
                        onBack = { navController.popBackStack() },
                        onRecipePicked = { navController.popBackStack() },
                    )
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
                        onPantryClick = { navController.navigate(ROUTE_PANTRY) },
                        onStatsClick = { navController.navigate(ROUTE_STATS) },
                        onMyProductsClick = { navController.navigate(ROUTE_MY_PRODUCTS) },
                        onIngredientMappingClick = { navController.navigate(ROUTE_INGREDIENT_MAPPING) },
                    )
                }
                composable(ROUTE_STORES) {
                    StoreListScreen(onBack = { navController.popBackStack() })
                }
                composable(ROUTE_PANTRY) {
                    PantryScreen(onBack = { navController.popBackStack() })
                }
                composable(ROUTE_STATS) {
                    StatsScreen(onBack = { navController.popBackStack() })
                }
                composable(ROUTE_MY_PRODUCTS) {
                    MyProductsScreen(onBack = { navController.popBackStack() })
                }
                composable(ROUTE_INGREDIENT_MAPPING) {
                    IngredientMappingScreen(onBack = { navController.popBackStack() })
                }
                composable(ROUTE_PURCHASE_STATS) {
                    com.helga.android.ui.stats.PurchaseStatsScreen(onBack = { navController.popBackStack() })
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
