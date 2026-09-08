package com.netraze.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Assignment
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.netraze.app.data.local.entity.BuildingEntity
import com.netraze.app.data.local.entity.FloorEntity
import com.netraze.app.data.local.entity.ProjectEntity
import com.netraze.app.data.local.entity.SurveyAreaEntity
import com.netraze.app.data.local.entity.SurveyEntity
import com.netraze.app.data.remote.api.AuthApi
import com.netraze.app.data.repository.AuthRepository
import com.netraze.app.data.repository.HierarchyRepository
import com.netraze.app.data.repository.SurveyRepository
import com.netraze.app.data.security.SecureSessionStore
import com.netraze.app.ui.account.AccountScreen
import com.netraze.app.ui.auth.CreateUserScreen
import com.netraze.app.ui.auth.LoginRoute
import com.netraze.app.ui.auth.LoginViewModel
import com.netraze.app.ui.auth.ResetPasswordScreen
import com.netraze.app.ui.canvas.SurveyCanvasScreen
import com.netraze.app.ui.canvas.SurveyCanvasViewModel
import com.netraze.app.ui.components.FloatingBottomNav
import com.netraze.app.ui.components.NavItem
import com.netraze.app.ui.dashboard.DashboardHomeScreen
import com.netraze.app.ui.hierarchy.BuildingDetailScreen
import com.netraze.app.ui.hierarchy.FloorDetailScreen
import com.netraze.app.ui.hierarchy.HierarchyViewModel
import com.netraze.app.ui.hierarchy.LocationsTabScreen
import com.netraze.app.ui.hierarchy.ProjectDetailScreen
import com.netraze.app.ui.hierarchy.ProjectsScreen
import com.netraze.app.ui.survey.AllSurveysTabScreen
import com.netraze.app.ui.survey.StartSurveyFlowDialog
import com.netraze.app.ui.survey.SurveyViewModel
import com.netraze.app.ui.survey.SurveysScreen
import com.netraze.app.ui.theme.NetrazeTheme
import com.netraze.app.ui.theme.SurfaceLight
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

sealed class ScreenState {

object Dashboard : ScreenState()

object AllSurveys : ScreenState()

object Locations : ScreenState()

object Account : ScreenState()

object Projects : ScreenState()

data class ProjectDetail(
    val project: ProjectEntity
) : ScreenState()

data class BuildingDetail(
    val building: BuildingEntity,
    val project: ProjectEntity
) : ScreenState()

data class FloorDetail(
    val floor: FloorEntity,
    val building: BuildingEntity,
    val project: ProjectEntity
) : ScreenState()

data class Surveys(
    val surveyArea: SurveyAreaEntity,
    val floor: FloorEntity,
    val building: BuildingEntity,
    val project: ProjectEntity
) : ScreenState()

data class SurveyCanvas(
    val survey: SurveyEntity,
    val surveyArea: SurveyAreaEntity?,
    val floor: FloorEntity?,
    val building: BuildingEntity?,
    val project: ProjectEntity?
) : ScreenState()

}

data class SurveyLocationContext(
val surveyArea: SurveyAreaEntity,
val floor: FloorEntity,
val building: BuildingEntity,
val project: ProjectEntity
)

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

@Inject
lateinit var authRepository: AuthRepository

@Inject
lateinit var authApi: AuthApi

@Inject
lateinit var secureSessionStore: SecureSessionStore

@Inject
lateinit var hierarchyRepository: HierarchyRepository

@Inject
lateinit var surveyRepository: SurveyRepository

private val surveyCanvasViewModel: SurveyCanvasViewModel by viewModels()

private val loginViewModel: LoginViewModel by lazy {
    LoginViewModel(
        authRepository,
        authApi
    ).apply {
        setDependencies(
            authRepository,
            authApi
        )
    }
}

private val hierarchyViewModel: HierarchyViewModel by lazy {
    HierarchyViewModel(
        hierarchyRepository
    ).apply {
        setRepository(
            hierarchyRepository
        )
    }
}

private val surveyViewModel: SurveyViewModel by lazy {
    SurveyViewModel(
        surveyRepository
    ).apply {
        setRepository(
            surveyRepository
        )
    }
}

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    setContent {

        NetrazeTheme {

            Surface(
                modifier = Modifier.fillMaxSize(),
                color = SurfaceLight
            ) {

                val authState by loginViewModel.authState.collectAsStateWithLifecycle()
                val surveysState by surveyViewModel.uiState.collectAsStateWithLifecycle()

                var currentScreen by remember {
                    mutableStateOf<ScreenState>(
                        ScreenState.Dashboard
                    )
                }

                var showStartSurveyFlow by remember {
                    mutableStateOf(false)
                }

                var isCreateUserFlowActive by remember {
                    mutableStateOf(false)
                }

                var isResetPasswordFlowActive by remember {
                    mutableStateOf(false)
                }

                LaunchedEffect(
                    authState.isAuthenticated,
                    authState.isCheckingSession
                ) {

                    if (authState.isCheckingSession) {
                        return@LaunchedEffect
                    }

                    if (authState.isAuthenticated) {

                        surveyViewModel.loadAllSurveys()

                    } else {

                        currentScreen = ScreenState.Dashboard
                        showStartSurveyFlow = false
                    }
                }

                when {

                    /*
                     * Session restoration is still running.
                     *
                     * Do NOT render LoginRoute here.
                     * This prevents the Login screen from flashing briefly
                     * while /auth/me validates an existing stored session.
                     *
                     * A proper Netraze splash/loading screen can replace
                     * this neutral surface during the UI redesign.
                     */
                    authState.isCheckingSession -> {

                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = SurfaceLight
                        ) {
                            // Intentionally blank during session validation.
                        }
                    }

                    authState.isAuthenticated -> {

                        val email =
                            authState.userProfile?.email
                                ?: authState.session?.email
                                ?: "Unknown"

                        val role =
                            authState.userProfile?.role
                                ?: authState.session?.role
                                ?: "Unknown"

                        val userId =
                            (
                                authState.userProfile?.id
                                    ?: authState.session?.userId
                                )
                                ?.toString()
                                ?: "Unknown"

                        val recentSurvey =
                            surveysState.surveys.firstOrNull {
                                it.status.equals(
                                    "in_progress",
                                    ignoreCase = true
                                )
                            }

                        val recentSurveys =
                            surveysState.surveys.take(3)

                        val allSynced =
                            surveysState.surveys.isNotEmpty() &&
                                surveysState.surveys.all {
                                    it.syncState.equals(
                                        "synced",
                                        ignoreCase = true
                                    )
                                }

                        val isTopLevelScreen =
                            currentScreen is ScreenState.Dashboard ||
                                currentScreen is ScreenState.AllSurveys ||
                                currentScreen is ScreenState.Locations ||
                                currentScreen is ScreenState.Account

                        Scaffold(
                            containerColor = SurfaceLight
                        ) { paddingValues ->

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(paddingValues)
                            ) {

                                when (val screen = currentScreen) {

                                    is ScreenState.Dashboard -> {

                                        DashboardHomeScreen(
                                            email = email,
                                            role = role,
                                            recentSurvey = recentSurvey,
                                            recentSurveys = recentSurveys,
                                            allSynced = allSynced,

                                            onContinueSurveyClick = { survey ->

                                                currentScreen =
                                                    ScreenState.SurveyCanvas(
                                                        survey = survey,
                                                        surveyArea = null,
                                                        floor = null,
                                                        building = null,
                                                        project = null
                                                    )
                                            },

                                            onBrowseLocations = {
                                                currentScreen =
                                                    ScreenState.Locations
                                            },

                                            onViewAllSurveys = {
                                                currentScreen =
                                                    ScreenState.AllSurveys
                                            }
                                        )
                                    }

                                    is ScreenState.AllSurveys -> {

                                        AllSurveysTabScreen(
                                            viewModel = surveyViewModel,

                                            onSurveyClick = { survey ->

                                                currentScreen =
                                                    ScreenState.SurveyCanvas(
                                                        survey = survey,
                                                        surveyArea = null,
                                                        floor = null,
                                                        building = null,
                                                        project = null
                                                    )
                                            },

                                            onStartSurveyClick = {
                                                showStartSurveyFlow = true
                                            }
                                        )
                                    }

                                    is ScreenState.Locations -> {

                                        LocationsTabScreen(
                                            viewModel = hierarchyViewModel,
                                            userRole = role,
                                            currentUserId = userId,

                                            onProjectClick = { project ->

                                                currentScreen =
                                                    ScreenState.ProjectDetail(
                                                        project
                                                    )
                                            },

                                            onManageLocationsClick = {
                                                currentScreen =
                                                    ScreenState.Projects
                                            }
                                        )
                                    }

                                    is ScreenState.Account -> {

                                        AccountScreen(
                                            email = email,
                                            role = role,

                                            onSignOut = {
                                                loginViewModel.logout()
                                            }
                                        )
                                    }

                                    is ScreenState.Projects -> {

                                        ProjectsScreen(
                                            viewModel = hierarchyViewModel,
                                            userRole = role,
                                            currentUserId = userId,

                                            onProjectClick = { project ->

                                                currentScreen =
                                                    ScreenState.ProjectDetail(
                                                        project
                                                    )
                                            },

                                            onBackClick = {
                                                currentScreen =
                                                    ScreenState.Locations
                                            }
                                        )
                                    }

                                    is ScreenState.ProjectDetail -> {

                                        ProjectDetailScreen(
                                            viewModel = hierarchyViewModel,
                                            project = screen.project,
                                            userRole = role,
                                            currentUserId = userId,

                                            onBuildingClick = { building ->

                                                currentScreen =
                                                    ScreenState.BuildingDetail(
                                                        building = building,
                                                        project = screen.project
                                                    )
                                            },

                                            onBackClick = {
                                                currentScreen =
                                                    ScreenState.Locations
                                            }
                                        )
                                    }

                                    is ScreenState.BuildingDetail -> {

                                        BuildingDetailScreen(
                                            viewModel = hierarchyViewModel,
                                            building = screen.building,
                                            project = screen.project,
                                            userRole = role,
                                            currentUserId = userId,

                                            onFloorClick = { floor ->

                                                currentScreen =
                                                    ScreenState.FloorDetail(
                                                        floor = floor,
                                                        building = screen.building,
                                                        project = screen.project
                                                    )
                                            },

                                            onBackClick = {

                                                currentScreen =
                                                    ScreenState.ProjectDetail(
                                                        screen.project
                                                    )
                                            }
                                        )
                                    }

                                    is ScreenState.FloorDetail -> {

                                        FloorDetailScreen(
                                            viewModel = hierarchyViewModel,
                                            floor = screen.floor,
                                            building = screen.building,
                                            project = screen.project,
                                            userRole = role,
                                            currentUserId = userId,

                                            onSurveyAreaClick = { area ->

                                                currentScreen =
                                                    ScreenState.Surveys(
                                                        surveyArea = area,
                                                        floor = screen.floor,
                                                        building = screen.building,
                                                        project = screen.project
                                                    )
                                            },

                                            onBackClick = {

                                                currentScreen =
                                                    ScreenState.BuildingDetail(
                                                        building = screen.building,
                                                        project = screen.project
                                                    )
                                            }
                                        )
                                    }

                                    is ScreenState.Surveys -> {

                                        SurveysScreen(
                                            viewModel = surveyViewModel,
                                            surveyArea = screen.surveyArea,
                                            project = screen.project,
                                            userRole = role,
                                            currentUserId = userId,

                                            onSurveyClick = { survey ->

                                                currentScreen =
                                                    ScreenState.SurveyCanvas(
                                                        survey = survey,
                                                        surveyArea = screen.surveyArea,
                                                        floor = screen.floor,
                                                        building = screen.building,
                                                        project = screen.project
                                                    )
                                            },

                                            onBackClick = {

                                                currentScreen =
                                                    ScreenState.FloorDetail(
                                                        floor = screen.floor,
                                                        building = screen.building,
                                                        project = screen.project
                                                    )
                                            }
                                        )
                                    }

                                    is ScreenState.SurveyCanvas -> {

                                        SurveyCanvasScreen(
                                            viewModel =
                                                surveyCanvasViewModel,

                                            surveyId =
                                                screen.survey.id,

                                            onBackClick = {
                                                currentScreen =
                                                    ScreenState.AllSurveys
                                            }
                                        )
                                    }
                                }

                                if (showStartSurveyFlow) {

                                    StartSurveyFlowDialog(
                                        hierarchyViewModel = hierarchyViewModel,
                                        existingSurveys = surveysState.surveys,
                                        onDismiss = {
                                            showStartSurveyFlow = false
                                        },
                                        onContinueSurvey = { survey ->
                                            showStartSurveyFlow = false
                                            currentScreen =
                                                ScreenState.SurveyCanvas(
                                                    survey = survey,
                                                    surveyArea = null,
                                                    floor = null,
                                                    building = null,
                                                    project = null
                                                )
                                        },
                                        onCreateSurvey = { location, title, mode ->
                                            surveyViewModel.createSurvey(
                                                surveyAreaId = location.surveyArea.id,
                                                title = title,
                                                mode = mode,
                                                onSuccess = { newSurvey ->
                                                    showStartSurveyFlow = false
                                                    currentScreen =
                                                        ScreenState.SurveyCanvas(
                                                            survey = newSurvey,
                                                            surveyArea = location.surveyArea,
                                                            floor = location.floor,
                                                            building = location.building,
                                                            project = location.project
                                                        )
                                                }
                                            )
                                        }
                                    )
                                }

                                /*
                                 * Floating navigation appears only on
                                 * the four top-level application screens.
                                 */
                                if (isTopLevelScreen) {

                                    FloatingBottomNav(
                                        modifier =
                                            Modifier.align(
                                                Alignment.BottomCenter
                                            ),

                                        items = listOf(

                                            NavItem(
                                                id = "dashboard",
                                                icon = Icons.Rounded.Home,
                                                contentDescription = "Home"
                                            ),

                                            NavItem(
                                                id = "surveys",
                                                icon = Icons.Rounded.Assignment,
                                                contentDescription = "Surveys"
                                            ),

                                            NavItem(
                                                id = "create_survey",
                                                icon = Icons.Rounded.WifiTethering,
                                                contentDescription = "Create new survey",
                                                isPrimaryAction = true
                                            ),

                                            NavItem(
                                                id = "locations",
                                                icon = Icons.Rounded.LocationOn,
                                                contentDescription = "Locations"
                                            ),

                                            NavItem(
                                                id = "account",
                                                icon = Icons.Rounded.Person,
                                                contentDescription = "Account"
                                            )
                                        ),

                                        selectedId =
                                            when (currentScreen) {

                                                is ScreenState.Dashboard ->
                                                    "dashboard"

                                                is ScreenState.AllSurveys ->
                                                    "surveys"

                                                is ScreenState.Locations ->
                                                    "locations"

                                                is ScreenState.Account ->
                                                    "account"

                                                else ->
                                                    "dashboard"
                                            },

                                        onItemSelected = { id ->

                                            if (id == "create_survey") {
                                                showStartSurveyFlow = true
                                            } else {
                                                currentScreen =
                                                    when (id) {

                                                        "dashboard" ->
                                                            ScreenState.Dashboard

                                                        "surveys" ->
                                                            ScreenState.AllSurveys

                                                        "locations" ->
                                                            ScreenState.Locations

                                                        "account" ->
                                                            ScreenState.Account

                                                        else ->
                                                            ScreenState.Dashboard
                                                    }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }

                    isCreateUserFlowActive -> {

                        CreateUserScreen(
                            viewModel = loginViewModel,

                            onBackToLogin = {
                                isCreateUserFlowActive = false
                            }
                        )
                    }

                    isResetPasswordFlowActive -> {

                        ResetPasswordScreen(
                            viewModel = loginViewModel,

                            onBackToLogin = {
                                isResetPasswordFlowActive = false
                            }
                        )
                    }

                    else -> {

                        LoginRoute(
                            viewModel = loginViewModel,

                            onCreateUserClick = {
                                isCreateUserFlowActive = true
                            },

                            onForgotPasswordClick = {
                                isResetPasswordFlowActive = true
                            },

                            onLoginSubmitted = { _, _ ->
                                // Authentication state is updated
                                // by LoginViewModel.
                            }
                        )
                    }
                }
            }
        }
    }
}

}
