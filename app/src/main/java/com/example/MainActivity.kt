package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Leaderboard
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.ai.CourseEvaluatorAi
import com.example.data.firebase.authStateFlow
import com.example.data.firebase.configureAppCheck
import com.example.data.preferences.AppPreferences
import com.example.data.preferences.PreferencesRepository
import com.example.data.preferences.ThemePreference
import com.example.data.repository.CourseRepository
import com.example.data.youtube.YouTubeEvidenceFetcher
import com.example.ui.SkillRankViewModel
import com.example.ui.screens.AddCourseScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.CourseDetailScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.SkillRankTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

private const val ROUTE_HOME = "home"
private const val ROUTE_ADD_BASE = "add"
private const val ROUTE_ADD = "add?url={url}&auto={auto}"
private const val ROUTE_LIBRARY = "library"
private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_DETAIL_BASE = "course"
private const val ROUTE_DETAIL = "course/{courseId}"

sealed interface LaunchTarget {
    data class SharedUrl(val rawText: String, val nonce: Long) : LaunchTarget
    data class DeepLinkCourse(val courseId: String, val nonce: Long) : LaunchTarget
}

class MainActivity : ComponentActivity() {

    private var pendingLaunchTarget by mutableStateOf<LaunchTarget?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Mandatory Firebase App Check wiring before any UI, ViewModels, or Firebase calls
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        handleIncomingIntent(intent)

        setContent {
            val prefsRepo = remember(applicationContext) { PreferencesRepository(applicationContext) }
            val prefs by prefsRepo.preferencesFlow.collectAsStateWithLifecycle(initialValue = AppPreferences())
            val darkTheme = when (prefs.themePreference) {
                ThemePreference.SYSTEM -> isSystemInDarkTheme()
                ThemePreference.LIGHT -> false
                ThemePreference.DARK -> true
            }

            SkillRankTheme(darkTheme = darkTheme) {
                SkillRankRoot(
                    pendingLaunchTarget = pendingLaunchTarget,
                    onConsumeLaunchTarget = { pendingLaunchTarget = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        val nonce = System.nanoTime()
        when (intent.action) {
            Intent.ACTION_SEND -> {
                if (intent.type == "text/plain") {
                    val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
                    if (!sharedText.isNullOrBlank()) {
                        pendingLaunchTarget = LaunchTarget.SharedUrl(sharedText, nonce)
                    }
                }
            }
            Intent.ACTION_VIEW -> {
                val data = intent.data ?: return
                if (data.scheme == "skillrank" && data.host == "course") {
                    val courseId = data.lastPathSegment?.takeIf { it.isNotBlank() }
                    if (courseId != null) {
                        pendingLaunchTarget = LaunchTarget.DeepLinkCourse(courseId, nonce)
                    }
                }
            }
        }
    }
}

@Composable
private fun SkillRankRoot(
    pendingLaunchTarget: LaunchTarget?,
    onConsumeLaunchTarget: () -> Unit
) {
    val auth = Firebase.auth
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)

    val user = currentUser
    if (user == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        key(user.uid) {
            MainAuthenticatedApp(
                currentUserId = user.uid,
                pendingLaunchTarget = pendingLaunchTarget,
                onConsumeLaunchTarget = onConsumeLaunchTarget
            )
        }
    }
}

private data class NavTab(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val tag: String
)

private val TOP_TABS = listOf(
    NavTab(ROUTE_HOME, "Rankings", Icons.Outlined.Leaderboard, "nav_home"),
    NavTab(ROUTE_ADD_BASE, "Add", Icons.Outlined.AddCircleOutline, "nav_add"),
    NavTab(ROUTE_LIBRARY, "Library", Icons.Outlined.BookmarkBorder, "nav_library"),
    NavTab(ROUTE_SETTINGS, "Settings", Icons.Outlined.Settings, "nav_settings")
)

@Composable
private fun MainAuthenticatedApp(
    currentUserId: String,
    pendingLaunchTarget: LaunchTarget?,
    onConsumeLaunchTarget: () -> Unit
) {
    val viewModel: SkillRankViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                SkillRankViewModel(
                    courseRepository = CourseRepository(db),
                    preferencesRepository = PreferencesRepository(app),
                    youtubeFetcher = YouTubeEvidenceFetcher(),
                    evaluatorAi = CourseEvaluatorAi(),
                    currentUserId = currentUserId
                )
            }
        }
    )

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val coursesState by viewModel.coursesState.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val libraryCategory by viewModel.libraryCategory.collectAsStateWithLifecycle()
    val librarySort by viewModel.librarySort.collectAsStateWithLifecycle()
    val addState by viewModel.addState.collectAsStateWithLifecycle()
    val dailyQuota by viewModel.dailyQuota.collectAsStateWithLifecycle()
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val activeCourse by viewModel.activeCourse.collectAsStateWithLifecycle()
    val activeLessons by viewModel.activeCourseLessons.collectAsStateWithLifecycle()
    val isRescoring by viewModel.isRescoring.collectAsStateWithLifecycle()
    val detailMessage by viewModel.detailActionMessage.collectAsStateWithLifecycle()

    LaunchedEffect(pendingLaunchTarget) {
        when (pendingLaunchTarget) {
            is LaunchTarget.SharedUrl -> {
                val encoded = Uri.encode(pendingLaunchTarget.rawText)
                viewModel.resetAddState()
                navController.navigate("$ROUTE_ADD_BASE?url=$encoded&auto=true") {
                    launchSingleTop = true
                }
                onConsumeLaunchTarget()
            }
            is LaunchTarget.DeepLinkCourse -> {
                viewModel.setActiveCourse(pendingLaunchTarget.courseId)
                navController.navigate("$ROUTE_DETAIL_BASE/${pendingLaunchTarget.courseId}") {
                    launchSingleTop = true
                }
                onConsumeLaunchTarget()
            }
            null -> Unit
        }
    }

    val isDetailRoute = currentRoute?.startsWith(ROUTE_DETAIL_BASE) == true

    fun navigateToTab(route: String) {
        val target = if (route == ROUTE_ADD_BASE) "$ROUTE_ADD_BASE?url=&auto=false" else route
        navController.navigate(target) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun openAddWithOptionalUrl(url: String?, autoStart: Boolean) {
        val encoded = if (url.isNullOrBlank()) "" else Uri.encode(url)
        viewModel.resetAddState()
        navController.navigate("$ROUTE_ADD_BASE?url=$encoded&auto=$autoStart") {
            launchSingleTop = true
        }
    }

    fun openCourseDetail(courseId: String) {
        viewModel.setActiveCourse(courseId)
        navController.navigate("$ROUTE_DETAIL_BASE/$courseId")
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 700.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                if (!useRail && !isDetailRoute) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 0.dp
                    ) {
                        TOP_TABS.forEach { tab ->
                            val selected = when (tab.route) {
                                ROUTE_ADD_BASE -> currentRoute?.startsWith(ROUTE_ADD_BASE) == true
                                else -> currentRoute == tab.route
                            }
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateToTab(tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = tab.icon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag(tab.tag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail && !isDetailRoute) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        TOP_TABS.forEach { tab ->
                            val selected = when (tab.route) {
                                ROUTE_ADD_BASE -> currentRoute?.startsWith(ROUTE_ADD_BASE) == true
                                else -> currentRoute == tab.route
                            }
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateToTab(tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = tab.icon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag(tab.tag)
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    NavHost(
                        navController = navController,
                        startDestination = ROUTE_HOME
                    ) {
                        composable(ROUTE_HOME) {
                            HomeScreen(
                                coursesState = coursesState,
                                selectedCategory = selectedCategory,
                                reduceMotionPref = prefs.reduceMotion,
                                onSelectCategory = viewModel::selectCategory,
                                onCourseClick = ::openCourseDetail,
                                onAddClick = { quickUrl ->
                                    openAddWithOptionalUrl(quickUrl, autoStart = !quickUrl.isNullOrBlank())
                                }
                            )
                        }

                        composable(
                            route = ROUTE_ADD,
                            arguments = listOf(
                                navArgument("url") {
                                    type = NavType.StringType
                                    defaultValue = ""
                                },
                                navArgument("auto") {
                                    type = NavType.BoolType
                                    defaultValue = false
                                }
                            )
                        ) { entry ->
                            val rawUrl = entry.arguments?.getString("url").orEmpty()
                            val auto = entry.arguments?.getBoolean("auto") ?: false
                            AddCourseScreen(
                                initialUrl = rawUrl.takeIf { it.isNotBlank() },
                                autoStart = auto,
                                addState = addState,
                                dailyQuota = dailyQuota,
                                onEvaluate = { url -> viewModel.addOrEvaluateCourse(url) },
                                onReset = viewModel::resetAddState,
                                onOpenCourse = ::openCourseDetail
                            )
                        }

                        composable(ROUTE_LIBRARY) {
                            LibraryScreen(
                                coursesState = coursesState,
                                selectedCategory = libraryCategory,
                                selectedSort = librarySort,
                                onSelectCategory = viewModel::selectLibraryCategory,
                                onSelectSort = viewModel::selectLibrarySort,
                                onCourseClick = ::openCourseDetail
                            )
                        }

                        composable(ROUTE_SETTINGS) {
                            SettingsScreen(
                                preferences = prefs,
                                dailyQuota = dailyQuota,
                                hasYouTubeApiKey = viewModel.hasYouTubeApiKey,
                                onThemeChange = viewModel::setThemePreference,
                                onReduceMotionChange = viewModel::setReduceMotion,
                                onSignedOut = {}
                            )
                        }

                        composable(
                            route = ROUTE_DETAIL,
                            arguments = listOf(
                                navArgument("courseId") { type = NavType.StringType }
                            )
                        ) { entry ->
                            val courseId = entry.arguments?.getString("courseId").orEmpty()
                            LaunchedEffect(courseId) {
                                if (courseId.isNotBlank()) {
                                    viewModel.setActiveCourse(courseId)
                                }
                            }
                            CourseDetailScreen(
                                course = activeCourse,
                                lessons = activeLessons,
                                isRescoring = isRescoring,
                                actionMessage = detailMessage,
                                reduceMotion = prefs.reduceMotion,
                                onBack = { navController.popBackStack() },
                                onToggleSave = viewModel::toggleSaved,
                                onRescore = viewModel::rescoreActiveCourse,
                                onDelete = { id ->
                                    viewModel.deleteCourse(id) {
                                        navController.popBackStack()
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
