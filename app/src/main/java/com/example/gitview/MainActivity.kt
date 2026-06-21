package com.example.gitview

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.example.gitview.data.SyncService
import com.example.gitview.data.crypto.CryptoManager
import com.example.gitview.data.crypto.EncryptionLevel
import com.example.gitview.data.db.AppDatabase
import com.example.gitview.data.git.GitManager
import com.example.gitview.data.repository.RepoRepository
import com.example.gitview.ui.navigation.AppNavGraph
import com.example.gitview.ui.theme.GitViewTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private lateinit var cryptoManager: CryptoManager
    private lateinit var syncService: SyncService
    private lateinit var repoRepository: RepoRepository
    private var isInitialized = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val db = AppDatabase.getInstance(this)
        val repoDao = db.repoDao()
        repoRepository = RepoRepository(repoDao)

        cryptoManager = CryptoManager(applicationContext)
        val gitManager = GitManager()
        syncService = SyncService(cryptoManager, gitManager, repoRepository)

        setContent {
            GitViewTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    val cryptoReady = remember { mutableStateOf(false) }
                    val cryptoLevel = remember { mutableStateOf<EncryptionLevel?>(null) }

                    LaunchedEffect(Unit) {
                        withContext(Dispatchers.IO) {
                            val level = cryptoManager.initialize()
                            isInitialized = true
                            cryptoLevel.value = level
                            cryptoReady.value = true
                        }
                    }

                    if (cryptoReady.value) {
                        AppNavGraph(
                            navController = navController,
                            repoRepository = repoRepository,
                            syncService = syncService,
                            cryptoManager = cryptoManager
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isInitialized) {
            cryptoManager.cleanAllTempDirs()
        }
    }
}
