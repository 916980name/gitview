package com.example.gitview.data.git

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.PersonIdent
import org.eclipse.jgit.lib.Ref
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.transport.URIish
import java.io.File
import java.util.Date

class GitManager {

    data class FileNode(
        val name: String,
        val path: String,
        val isDirectory: Boolean,
        val children: List<FileNode> = emptyList()
    )

    data class CommitInfo(
        val message: String,
        val author: String,
        val timestamp: Long
    )

    fun cloneRepo(url: String, targetDir: File) {
        targetDir.mkdirs()
        Git.cloneRepository()
            .setURI(url)
            .setDirectory(targetDir)
            .setBranchesToClone(listOf("refs/heads/*:refs/remotes/origin/*"))
            .setCloneAllBranches(true)
            .call()
            .use { git ->
                checkoutDefaultBranch(git)
            }
    }

    fun pullRepo(repoDir: File): List<String> {
        val changedFiles = mutableListOf<String>()
        Git.open(repoDir).use { git ->
            val oldHead = git.repository.findRef("HEAD")?.objectId

            git.pull().call()

            val newHead = git.repository.findRef("HEAD")?.objectId
            if (oldHead != null && newHead != null && oldHead != newHead) {
                val reader = git.repository.newObjectReader()
                val oldTree = org.eclipse.jgit.treewalk.CanonicalTreeParser()
                oldTree.reset(reader, git.repository.parseCommit(oldHead).tree)
                val newTree = org.eclipse.jgit.treewalk.CanonicalTreeParser()
                newTree.reset(reader, git.repository.parseCommit(newHead).tree)

                val diff = org.eclipse.jgit.diff.DiffFormatter(
                    org.eclipse.jgit.util.io.DisabledOutputStream.INSTANCE
                )
                diff.setRepository(git.repository)
                diff.scan(oldTree, newTree).forEach { diffEntry ->
                    changedFiles.add(diffEntry.newPath)
                }
                diff.close()
                reader.close()
            }
        }
        return changedFiles
    }

    fun getFileTree(repoDir: File): List<FileNode> {
        val gitDir = File(repoDir, ".git")
        return repoDir.walkTopDown()
            .filter { !it.path.startsWith(gitDir.path) }
            .filter { it.isDirectory || !it.name.startsWith(".") }
            .toList()
            .let { buildFileTree(repoDir, repoDir, it) }
    }

    private fun buildFileTree(repoRoot: File, currentDir: File, allFiles: List<File>): List<FileNode> {
        val dirPath = currentDir.absolutePath
        val directChildren = allFiles
            .filter { it.parentFile?.absolutePath == dirPath }
            .sortedWith(compareBy<File> { if (it.isDirectory) 0 else 1 }.thenBy { it.name })

        return directChildren.map { file ->
            if (file.isDirectory) {
                FileNode(
                    name = file.name,
                    path = file.relativeTo(repoRoot).path,
                    isDirectory = true,
                    children = buildFileTree(repoRoot, file, allFiles)
                )
            } else {
                FileNode(
                    name = file.name,
                    path = file.relativeTo(repoRoot).path,
                    isDirectory = false
                )
            }
        }
    }

    fun readFileContent(file: File): String {
        return file.readText()
    }

    fun getLastCommitInfo(repoDir: File): CommitInfo {
        Git.open(repoDir).use { git ->
            val head = git.repository.findRef("HEAD") ?: return CommitInfo("", "", 0)
            val commit = git.repository.parseCommit(head.objectId)
            return CommitInfo(
                message = commit.shortMessage,
                author = commit.authorIdent.name,
                timestamp = commit.commitTime.toLong() * 1000
            )
        }
    }

    private fun checkoutDefaultBranch(git: Git) {
        val headRef = git.repository.exactRef("refs/remotes/origin/HEAD")
            ?: git.repository.exactRef("refs/remotes/origin/main")
            ?: git.repository.exactRef("refs/remotes/origin/master")

        if (headRef != null) {
            val branchName = headRef.name.removePrefix("refs/remotes/origin/")
            val existingRef = git.repository.exactRef("refs/heads/$branchName")
            if (existingRef != null) {
                git.checkout()
                    .setName(branchName)
                    .call()
            } else {
                git.checkout()
                    .setName(branchName)
                    .setCreateBranch(true)
                    .setStartPoint("origin/$branchName")
                    .call()
            }
        }
    }
}
