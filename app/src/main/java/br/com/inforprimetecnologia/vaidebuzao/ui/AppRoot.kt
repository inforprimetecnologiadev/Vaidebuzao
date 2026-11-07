package br.com.inforprimetecnologia.vaidebuzao.ui

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import br.com.inforprimetecnologia.vaidebuzao.data.LegalDocType
import br.com.inforprimetecnologia.vaidebuzao.ui.components.EmptyState
import br.com.inforprimetecnologia.vaidebuzao.ui.screens.AboutScreen
import br.com.inforprimetecnologia.vaidebuzao.ui.screens.ConsentHistoryScreen
import br.com.inforprimetecnologia.vaidebuzao.ui.screens.ConsentScreen
import br.com.inforprimetecnologia.vaidebuzao.ui.screens.DeclinedScreen
import br.com.inforprimetecnologia.vaidebuzao.ui.screens.DesignSystemScreen
import br.com.inforprimetecnologia.vaidebuzao.ui.screens.FavoritesScreen
import br.com.inforprimetecnologia.vaidebuzao.ui.screens.HomeScreen
import br.com.inforprimetecnologia.vaidebuzao.ui.screens.ItineraryScreen
import br.com.inforprimetecnologia.vaidebuzao.ui.screens.LegalDocScreen
import br.com.inforprimetecnologia.vaidebuzao.ui.screens.LineDetailScreen
import br.com.inforprimetecnologia.vaidebuzao.ui.screens.LinesScreen
import br.com.inforprimetecnologia.vaidebuzao.ui.screens.OnboardingScreen
import br.com.inforprimetecnologia.vaidebuzao.ui.screens.PrivacyCenterScreen
import br.com.inforprimetecnologia.vaidebuzao.ui.screens.RouteResultsScreen
import br.com.inforprimetecnologia.vaidebuzao.ui.screens.SearchScreen
import br.com.inforprimetecnologia.vaidebuzao.ui.screens.SettingsScreen
import br.com.inforprimetecnologia.vaidebuzao.ui.screens.StopDetailScreen
import br.com.inforprimetecnologia.vaidebuzao.ui.screens.sendEmail
import br.com.inforprimetecnologia.vaidebuzao.ui.state.AppViewModel
import br.com.inforprimetecnologia.vaidebuzao.ui.state.Gate
import br.com.inforprimetecnologia.vaidebuzao.ui.state.TransitViewModel

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("inicio", "Início", Icons.Filled.Map),
    Tab("linhas", "Linhas", Icons.AutoMirrored.Filled.List),
    Tab("favoritos", "Favoritos", Icons.Filled.Star),
    Tab("ajustes", "Ajustes", Icons.Filled.Settings),
)

@Composable
fun AppRoot(app: AppViewModel, transit: TransitViewModel) {
    val gate by app.gate.collectAsState()
    val legal by app.legal.collectAsState()
    when (val g = gate) {
        Gate.Loading -> Box(Modifier.fillMaxSize())
        is Gate.NeedsConsent -> legal?.let { ConsentScreen(it, g.isUpdate, onAccept = { app.accept(it) }, onDecline = { app.decline() }) }
        Gate.Declined -> DeclinedScreen(onReview = app::reviewTermsAgain)
        Gate.Onboarding -> OnboardingScreen(onPermissionResult = { transit.refreshLocation() }, onFinish = { app.finishOnboarding() })
        Gate.Ready -> MainNav(app, transit)
    }
}

@Composable
private fun MainNav(app: AppViewModel, transit: TransitViewModel) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route
    val showBar = tabs.any { it.route == current }
    val settings by app.settings.collectAsState()
    val legal by app.legal.collectAsState()
    val network by transit.network.collectAsState()

    Scaffold(
        bottomBar = {
            if (showBar) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = current == tab.route,
                            onClick = { nav.navigateTab(tab.route) },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label) },
                            colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.secondaryContainer),
                        )
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        NavHost(
            navController = nav,
            startDestination = "inicio",
            modifier = Modifier.padding(bottom = pad.calculateBottomPadding()),
            enterTransition = { fadeIn() },
            exitTransition = { fadeOut() },
        ) {
            composable("inicio") {
                HomeScreen(transit, onOpenStop = { nav.navigate("ponto/$it") }, onOpenSearch = { nav.navigate("buscar") })
            }
            composable("linhas") { LinesScreen(transit, onOpenLine = { nav.navigate("linha/${android.net.Uri.encode(it)}") }) }
            composable("favoritos") {
                FavoritesScreen(
                    transit,
                    settings,
                    onOpenStop = { nav.navigate("ponto/$it") },
                    onOpenLine = { nav.navigate("linha/${android.net.Uri.encode(it)}") },
                    onPlanTo = { nav.navigate("rotas") },
                    onOpenPrivacy = { nav.navigate("privacidade") },
                )
            }
            composable("ajustes") {
                SettingsScreen(
                    app,
                    onOpenPrivacy = { nav.navigate("privacidade") },
                    onOpenDoc = { nav.navigate("documento/$it") },
                    onOpenAbout = { nav.navigate("sobre") },
                    onOpenDesignSystem = { nav.navigate("design-system") },
                )
            }
            composable("ponto/{id}", arguments = listOf(navArgument("id") { type = NavType.IntType })) { entry ->
                val id = entry.arguments?.getInt("id") ?: 0
                val context = androidx.compose.ui.platform.LocalContext.current
                StopDetailScreen(
                    transit,
                    id,
                    onBack = { nav.popBackStack() },
                    onOpenPattern = { pid ->
                        transit.network.value?.patternById(pid)?.let { nav.navigate("linha/${android.net.Uri.encode(it.ref.ifBlank { it.name })}") }
                    },
                    onGoFromHere = { nav.navigate("buscar") },
                    onReport = {
                        sendEmail(context, legal?.contactEmail ?: "", "Vai de Buzão: linhas do ponto $id")
                    },
                )
            }
            composable("linha/{ref}") { entry ->
                val ref = entry.arguments?.getString("ref")?.let { android.net.Uri.decode(it) } ?: ""
                LineDetailScreen(transit, ref, onBack = { nav.popBackStack() }, onOpenStop = { nav.navigate("ponto/$it") })
            }
            composable("buscar") {
                SearchScreen(transit, onBack = { nav.popBackStack() }, onPlanned = {
                    nav.navigate("rotas") { popUpTo("buscar") { inclusive = true } }
                })
            }
            composable("rotas") {
                RouteResultsScreen(
                    transit,
                    onBack = { nav.popBackStack() },
                    onEdit = { nav.navigate("buscar") { popUpTo("rotas") { inclusive = true } } },
                    onOpen = { nav.navigate("trajeto/$it") },
                )
            }
            composable("trajeto/{i}", arguments = listOf(navArgument("i") { type = NavType.IntType })) { entry ->
                ItineraryScreen(transit, entry.arguments?.getInt("i") ?: 0, onBack = { nav.popBackStack() }, onOpenStop = { nav.navigate("ponto/$it") })
            }
            composable("privacidade") {
                PrivacyCenterScreen(
                    app,
                    onBack = { nav.popBackStack() },
                    onOpenHistory = { nav.navigate("consentimentos") },
                    onOpenDoc = { nav.navigate("documento/$it") },
                )
            }
            composable("consentimentos") { ConsentHistoryScreen(app, onBack = { nav.popBackStack() }) }
            composable("documento/{tipo}") { entry ->
                val l = legal
                val doc = when (entry.arguments?.getString("tipo")) {
                    "termos" -> l?.terms
                    else -> l?.privacy
                }
                if (doc != null) {
                    LegalDocScreen(doc, onBack = { nav.popBackStack() })
                } else {
                    EmptyState(LegalDocType.TERMOS.title, "Carregando…")
                }
            }
            composable("sobre") {
                AboutScreen(network?.meta, onBack = { nav.popBackStack() }, onOpenDoc = { nav.navigate("documento/$it") })
            }
            composable("design-system") { DesignSystemScreen(onBack = { nav.popBackStack() }) }
        }
    }
}

private fun NavHostController.navigateTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
