package com.example.gitview.ui.navigation

sealed class Screen(val route: String) {
    data object RepoList : Screen("repo_list")
    data object AddRepo : Screen("add_repo?repoId={repoId}") {
        fun createRoute() = "add_repo"
        fun createRoute(repoId: Long) = "add_repo?repoId=$repoId"
    }
    data object Settings : Screen("settings")
    data object FileTree : Screen("file_tree/{repoId}") {
        fun createRoute(repoId: Long) = "file_tree/$repoId"
    }
    data object MarkdownViewer : Screen("markdown_viewer/{repoId}/{filePath}") {
        fun createRoute(repoId: Long, filePath: String) = "markdown_viewer/$repoId/$filePath"
    }
}
