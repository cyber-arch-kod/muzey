package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.domain.model.ShareLinkBuilder
import com.example.ui.screens.CreateEditMuseumScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.MuseumViewerScreen
import com.example.ui.screens.MyMuseumsScreen
import com.example.ui.screens.PricingScreen
import com.example.ui.screens.ShareMuseumScreen
import com.example.ui.theme.MuzeychaTheme
import com.example.ui.viewmodel.MyMuseumsViewModel
import kotlinx.serialization.Serializable

@Serializable
object HomeRoute

@Serializable
data class CreateEditRoute(val slug: String? = null)

@Serializable
data class ViewerRoute(val slug: String)

@Serializable
data class ShareRoute(val slug: String)

@Serializable
object MyMuseumsRoute

@Serializable
object PricingRoute

class MainActivity : ComponentActivity() {
    private var pendingDeepLinkSlug by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingDeepLinkSlug = extractSlugFromIntent(intent)

        setContent {
            MuzeychaTheme {
                val navController = rememberNavController()
                val myMuseumsViewModel: MyMuseumsViewModel = viewModel()
                val ownedMuseums by myMuseumsViewModel.museums.collectAsStateWithLifecycle()
                val subscription by myMuseumsViewModel.subscription.collectAsStateWithLifecycle()

                LaunchedEffect(pendingDeepLinkSlug) {
                    val slug = pendingDeepLinkSlug
                    if (!slug.isNullOrBlank()) {
                        pendingDeepLinkSlug = null
                        navController.navigate(ViewerRoute(slug))
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = HomeRoute
                ) {
                    composable<HomeRoute> {
                        HomeScreen(
                            ownedCount = ownedMuseums.size,
                            subscription = subscription,
                            onCreateMuseum = { navController.navigate(CreateEditRoute()) },
                            onOpenMyMuseums = { navController.navigate(MyMuseumsRoute) },
                            onOpenPricing = { navController.navigate(PricingRoute) },
                            onOpenSlug = { slug -> navController.navigate(ViewerRoute(slug)) }
                        )
                    }

                    composable<CreateEditRoute> { backStackEntry ->
                        val route: CreateEditRoute = backStackEntry.toRoute()
                        CreateEditMuseumScreen(
                            editSlug = route.slug,
                            onBack = { navController.popBackStack() },
                            onPublished = { slug ->
                                navController.navigate(ShareRoute(slug)) {
                                    popUpTo<HomeRoute>()
                                }
                            },
                            onOpenPricing = { navController.navigate(PricingRoute) }
                        )
                    }

                    composable<ViewerRoute> { backStackEntry ->
                        val route: ViewerRoute = backStackEntry.toRoute()
                        MuseumViewerScreen(
                            slug = route.slug,
                            onBack = { navController.popBackStack() },
                            onOpenPricing = { navController.navigate(PricingRoute) }
                        )
                    }

                    composable<ShareRoute> { backStackEntry ->
                        val route: ShareRoute = backStackEntry.toRoute()
                        ShareMuseumScreen(
                            slug = route.slug,
                            onBackToHome = {
                                navController.popBackStack(HomeRoute, inclusive = false)
                            },
                            onOpenViewer = { slug ->
                                navController.navigate(ViewerRoute(slug))
                            }
                        )
                    }

                    composable<MyMuseumsRoute> {
                        MyMuseumsScreen(
                            onBack = { navController.popBackStack() },
                            onCreateNew = { navController.navigate(CreateEditRoute()) },
                            onOpenViewer = { slug -> navController.navigate(ViewerRoute(slug)) },
                            onShareMuseum = { slug -> navController.navigate(ShareRoute(slug)) },
                            onEditMuseum = { slug -> navController.navigate(CreateEditRoute(slug)) },
                            onOpenPricing = { navController.navigate(PricingRoute) }
                        )
                    }

                    composable<PricingRoute> {
                        PricingScreen(
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractSlugFromIntent(intent)?.let { slug ->
            pendingDeepLinkSlug = slug
        }
    }

    private fun extractSlugFromIntent(intent: Intent?): String? {
        val dataString = intent?.dataString ?: return null
        return ShareLinkBuilder.extractSlug(dataString)
    }
}
