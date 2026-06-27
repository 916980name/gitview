package com.example.gitview.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.gitview.data.SyncService
import com.example.gitview.data.crypto.CryptoManager
import com.example.gitview.data.repository.RepoRepository
import com.example.gitview.ui.addrepo.AddRepoViewModel
import com.example.gitview.ui.addrepo.AddRepoScreen
import com.example.gitview.ui.filetree.FileTreeViewModel
import com.example.gitview.ui.filetree.FileTreeScreen
import com.example.gitview.ui.markdown.MarkdownViewerViewModel
import com.example.gitview.ui.markdown.MarkdownViewerScreen
import com.example.gitview.ui.repolist.RepoListViewModel
import com.example.gitview.ui.repolist.RepoListScreen
import com.example.gitview.ui.settings.SettingsViewModel
import com.example.gitview.ui.settings.SettingsScreen
import java.net.URLDecoder
import java.net.URLEncoder

@Composable
fun AppNavGraph(
    navController: NavHostController,
    repoRepository: RepoRepository,
    syncService: SyncService,
    cryptoManager: CryptoManager
) {
    NavHost(
        navController = navController,
        startDestination = Screen.RepoList.route
    ) {
        composable(Screen.RepoList.route) {
            val viewModel: RepoListViewModel = viewModel(
                factory = RepoListViewModel.Factory(repoRepository, syncService)
            )
            RepoListScreen(
                viewModel = viewModel,
                onAddRepo = { navController.navigate(Screen.AddRepo.createRoute()) },
                onRepoClick = { repoId ->
                    navController.navigate(Screen.FileTree.createRoute(repoId))
                },
                onEditRepo = { repoId ->
                    navController.navigate(Screen.AddRepo.createRoute(repoId))
                },
                onSettings = { navController.navigate(Screen.Settings.route) }
            )
        }

        composable(
            route = Screen.AddRepo.route,
            arguments = listOf(navArgument("repoId") { type = NavType.LongType; defaultValue = -1L })
        ) { backStackEntry ->
            val repoId = backStackEntry.arguments?.getLong("repoId") ?: -1L
            val editRepoId = if (repoId == -1L) null else repoId
            val viewModel: AddRepoViewModel = viewModel(
                factory = AddRepoViewModel.Factory(editRepoId, repoRepository, syncService)
            )
            AddRepoScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onSuccess = { navController.popBackStack() }
            )
        }

        composable(Screen.Settings.route) {
            val viewModel = SettingsViewModel(cryptoManager)
            SettingsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.FileTree.route,
            arguments = listOf(navArgument("repoId") { type = NavType.LongType })
        ) { backStackEntry ->
            val repoId = backStackEntry.arguments?.getLong("repoId") ?: return@composable
            val viewModel: FileTreeViewModel = viewModel(
                factory = FileTreeViewModel.Factory(repoId, repoRepository, syncService)
            )
            FileTreeScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onFileClick = { filePath ->
                    navController.navigate(
                        Screen.MarkdownViewer.createRoute(
                            repoId,
                            URLEncoder.encode(filePath, "UTF-8")
                        )
                    )
                }
            )
        }

        composable(
            route = Screen.MarkdownViewer.route,
            arguments = listOf(
                navArgument("repoId") { type = NavType.LongType },
                navArgument("filePath") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val repoId = backStackEntry.arguments?.getLong("repoId") ?: return@composable
            val encodedPath = backStackEntry.arguments?.getString("filePath") ?: return@composable
            val filePath = URLDecoder.decode(encodedPath, "UTF-8")
            val viewModel: MarkdownViewerViewModel = viewModel(
                factory = MarkdownViewerViewModel.Factory(repoId, filePath, repoRepository, syncService)
            )
            MarkdownViewerScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
