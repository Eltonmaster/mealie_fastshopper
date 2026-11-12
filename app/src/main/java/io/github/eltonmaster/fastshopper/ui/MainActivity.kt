package io.github.eltonmaster.fastshopper.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.eltonmaster.fastshopper.FastShopperApp
import io.github.eltonmaster.fastshopper.data.SettingsStore
import io.github.eltonmaster.fastshopper.ui.list.ShoppingListScreen
import io.github.eltonmaster.fastshopper.ui.lists.ListPickerScreen
import io.github.eltonmaster.fastshopper.ui.setup.SetupScreen
import io.github.eltonmaster.fastshopper.ui.theme.FastShopperTheme
import kotlinx.coroutines.flow.first

object Routes {
    const val SETUP = "setup"
    const val LISTS = "lists"
    const val LIST = "list/{listId}"
    fun list(id: String) = "list/$id"
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val settingsStore = (application as FastShopperApp).container.settingsStore
        setContent {
            FastShopperTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppRoot(settingsStore)
                }
            }
        }
    }
}

private sealed interface Launch {
    data object Setup : Launch
    data class Lists(val openListId: String?) : Launch
}

@Composable
private fun AppRoot(settingsStore: SettingsStore) {
    var launch by rememberSaveable(stateSaver = launchSaver) { mutableStateOf<Launch?>(null) }
    LaunchedEffect(Unit) {
        if (launch == null) {
            val settings = settingsStore.settings.first()
            launch = if (settings.isConfigured) Launch.Lists(settings.defaultListId) else Launch.Setup
        }
    }
    when (val l = launch) {
        null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else -> AppNavHost(l)
    }
}

private val launchSaver = androidx.compose.runtime.saveable.Saver<Launch?, String>(
    save = { l ->
        when (l) {
            null -> ""
            Launch.Setup -> "setup"
            is Launch.Lists -> "lists:" + (l.openListId ?: "")
        }
    },
    restore = { s ->
        when {
            s == "setup" -> Launch.Setup
            s.startsWith("lists:") -> Launch.Lists(null) // default list was already opened before
            else -> null
        }
    },
)

@Composable
private fun AppNavHost(launch: Launch) {
    val navController = rememberNavController()
    val start = if (launch is Launch.Setup) Routes.SETUP else Routes.LISTS

    NavHost(navController = navController, startDestination = start) {
        composable(Routes.SETUP) {
            SetupScreen(onConnected = { navController.goToLists() })
        }
        composable(Routes.LISTS) {
            ListPickerScreen(
                onOpenList = { id -> navController.navigate(Routes.list(id)) },
                onSignedOut = {
                    navController.navigate(Routes.SETUP) {
                        popUpTo(navController.graph.id) { inclusive = true }
                    }
                },
            )
        }
        composable(
            Routes.LIST,
            arguments = listOf(navArgument("listId") { type = NavType.StringType }),
        ) { entry ->
            ShoppingListScreen(
                listId = entry.arguments?.getString("listId").orEmpty(),
                onBack = { navController.popBackStack() },
            )
        }
    }

    // Open the default list straight away on launch; back returns to the picker.
    val openId = (launch as? Launch.Lists)?.openListId
    LaunchedEffect(openId) {
        if (openId != null) navController.navigate(Routes.list(openId))
    }
}

private fun NavHostController.goToLists() {
    navigate(Routes.LISTS) {
        popUpTo(graph.id) { inclusive = true }
    }
}
