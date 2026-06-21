package com.example.gitview.data.git

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class GitManagerUnitTest {

    private val gitManager = GitManager()

    @Test
    fun fileTree_buildsCorrectStructure() {
        val tempDir = createTempDir("test_repo")
        val srcDir = File(tempDir, "src")
        srcDir.mkdirs()
        val docsDir = File(tempDir, "docs")
        docsDir.mkdirs()

        File(srcDir, "Main.kt").createNewFile()
        File(srcDir, "Utils.kt").createNewFile()
        File(docsDir, "README.md").createNewFile()
        File(tempDir, ".gitignore").createNewFile()
        File(tempDir, "README.md").createNewFile()

        val tree = gitManager.getFileTree(tempDir)

        assertEquals(3, tree.size)

        val readme = tree.find { it.name == "README.md" }
        assertTrue(readme != null)
        assertFalse(readme!!.isDirectory)

        val srcNode = tree.find { it.name == "src" }
        assertTrue(srcNode != null)
        assertTrue(srcNode!!.isDirectory)
        assertEquals(2, srcNode.children.size)

        val docsNode = tree.find { it.name == "docs" }
        assertTrue(docsNode != null)
        assertTrue(docsNode!!.isDirectory)
        assertEquals(1, docsNode.children.size)

        assertTrue(tree.none { it.name == ".gitignore" })

        tempDir.deleteRecursively()
    }

    @Test
    fun fileTree_emptyDirectory() {
        val tempDir = createTempDir("empty_repo")
        val gitDir = File(tempDir, ".git")
        gitDir.mkdirs()

        val tree = gitManager.getFileTree(tempDir)

        assertTrue(tree.isEmpty())

        tempDir.deleteRecursively()
    }

    @Test
    fun readFileContent_readsCorrectly() {
        val tempFile = createTempFile("test.txt", "Hello, World!")

        val content = gitManager.readFileContent(tempFile)

        assertEquals("Hello, World!", content)

        tempFile.delete()
    }

    @Test
    fun fileNode_properties() {
        val node = GitManager.FileNode(
            name = "test.md",
            path = "docs/test.md",
            isDirectory = false
        )

        assertEquals("test.md", node.name)
        assertEquals("docs/test.md", node.path)
        assertFalse(node.isDirectory)
        assertTrue(node.children.isEmpty())
    }

    private fun createTempDir(prefix: String): File {
        val baseDir = File(System.getProperty("java.io.tmpdir"))
        val dir = File(baseDir, "${prefix}_${System.nanoTime()}")
        dir.mkdirs()
        return dir
    }

    private fun createTempFile(name: String, content: String): File {
        val baseDir = File(System.getProperty("java.io.tmpdir"))
        val file = File(baseDir, name)
        file.writeText(content)
        return file
    }
}
