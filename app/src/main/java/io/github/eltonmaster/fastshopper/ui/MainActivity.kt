package io.github.eltonmaster.fastshopper.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.eltonmaster.fastshopper.FastShopperApp
import io.github.eltonmaster.fastshopper.data.SettingsStore
import io.github.eltonmaster.fastshopper.data.ThemeMode
import io.github.eltonmaster.fastshopper.ui.list.ShoppingListScreen
import io.github.eltonmaster.fastshopper.ui.lists.ListPickerScreen
import io.github.eltonmaster.fastshopper.ui.settings.SettingsScreen
import io.github.eltonmaster.fastshopper.ui.setup.SetupScreen
import io.github.eltonmaster.fastshopper.ui.theme.FastShopperTheme
import io.github.eltonmaster.fastshopper.ui.update.UpdatePrompt
import io.github.eltonmaster.fastshopper.ui.update.UpdateViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first

object Routes {
    const val SETUP = "setup"
    const val LISTS = "lists"
    const val LIST = "list/{listId}"
    const val SETTINGS = "settings"
    fun list(id: String) = "list/$id"
}

class MainActivity : ComponentActivity() {
    /** A list to open, from a tapped change notification; cleared once opened. */
    private val openListRequest = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) openListRequest.value = intent.listIdToOpen()
        enableEdgeToEdge()
        val settingsStore = (application as FastShopperApp).container.settingsStore
        setContent {
            // One instance for the whole activity, shared by the update dialog and Settings.
            val updateViewModel: UpdateViewModel = viewModel(factory = UpdateViewModel.Factory)
            val themeMode by settingsStore.themeMode.collectAsStateWithLifecycle<ThemeMode?>(initialValue = null)
            val systemDark = isSystemInDarkTheme()
            // Draw nothing for the moment it takes to read the theme, rather than flash the wrong one.
            themeMode?.let { mode ->
                val dark = mode.isDark(systemDark)
                SystemBarsFor(dark)
                FastShopperTheme(darkTheme = dark) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        AppRoot(settingsStore, updateViewModel, openListRequest) { openListRequest.value = null }
                        UpdatePrompt(updateViewModel)
                    }
                }
            }
        }
    }

    /** Status and navigation bar icons follow the app's theme, which can differ from the system's. */
    @Composable
    private fun SystemBarsFor(dark: Boolean) {
        DisposableEffect(dark) {
            enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                navigationBarStyle = SystemBarStyle.auto(LightScrim, DarkScrim) { dark },
            )
            onDispose {}
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.listIdToOpen()?.let { openListRequest.value = it }
    }

    private fun Intent.listIdToOpen(): String? =
        if (action == ACTION_OPEN_LIST) getStringExtra(EXTRA_LIST_ID)?.takeIf { it.isNotBlank() } else null

    companion object {
        /** Opens the list named by [EXTRA_LIST_ID]; used by change notifications. */
        const val ACTION_OPEN_LIST = "io.github.eltonmaster.fastshopper.OPEN_LIST"
        const val EXTRA_LIST_ID = "list_id"

        // The scrims enableEdgeToEdge() uses by default for 3-button navigation.
        private val LightScrim = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        private val DarkScrim = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
    }
}

private sealed interface Launch {
    data object Setup : Launch
    data class Lists(val openListId: String?) : Launch
}

@Composable
private fun AppRoot(
    settingsStore: SettingsStore,
    updateViewModel: UpdateViewModel,
    openListRequest: StateFlow<String?>,
    onListRequestHandled: () -> Unit,
) {
    var launch by rememberSaveable(stateSaver = launchSaver) { mutableStateOf<Launch?>(null) }
    LaunchedEffect(Unit) {
        if (launch == null) {
            val settings = settingsStore.settings.first()
            launch = if (settings.isConfigured) Launch.Lists(settings.defaultListId) else Launch.Setup
        }
    }
    when (val l = launch) {
        null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else -> AppNavHost(l, updateViewModel, openListRequest, onListRequestHandled)
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
private fun AppNavHost(
    launch: Launch,
    updateViewModel: UpdateViewModel,
    openListRequest: StateFlow<String?>,
    onListRequestHandled: () -> Unit,
) {
    val navController = rememberNavController()
    val start = if (launch is Launch.Setup) Routes.SETUP else Routes.LISTS

    NavHost(navController = navController, startDestination = start) {
        composable(Routes.SETUP) {
            SetupScreen(onConnected = { navController.goToLists() })
        }
        composable(Routes.LISTS) {
            ListPickerScreen(
                onOpenList = { id -> navController.navigate(Routes.list(id)) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) { launchSingleTop = true } },
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
                onAllLists = {
                    if (!navController.popBackStack(Routes.LISTS, inclusive = false)) {
                        navController.navigate(Routes.LISTS)
                    }
                },
                onSwitchList = { id ->
                    navController.navigate(Routes.list(id)) {
                        popUpTo(Routes.LISTS)
                    }
                },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                updateViewModel = updateViewModel,
                onBack = dropUnlessResumed { navController.popBackStack() },
            )
        }
    }

    // Open the default list straight away on launch; back returns to the picker.
    val openId = (launch as? Launch.Lists)?.openListId
    LaunchedEffect(openId) {
        if (openId != null) navController.navigate(Routes.list(openId))
    }

    // A tapped change notification opens its list on top of the overview.
    val requestedList by openListRequest.collectAsStateWithLifecycle()
    LaunchedEffect(requestedList) {
        val id = requestedList ?: return@LaunchedEffect
        if (launch is Launch.Lists) {
            navController.navigate(Routes.list(id)) {
                popUpTo(Routes.LISTS)
            }
        }
        onListRequestHandled()
    }
}

private fun NavHostController.goToLists() {
    navigate(Routes.LISTS) {
        popUpTo(graph.id) { inclusive = true }
    }
}
