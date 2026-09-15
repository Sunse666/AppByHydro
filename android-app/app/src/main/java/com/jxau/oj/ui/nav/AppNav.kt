package com.jxau.oj.ui.nav

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.jxau.oj.ui.AppViewModel
import com.jxau.oj.ui.auth.LoginScreen
import com.jxau.oj.ui.contest.ContestDetailScreen
import com.jxau.oj.ui.contest.ContestDetailViewModel
import com.jxau.oj.ui.contest.ContestListScreen
import com.jxau.oj.ui.contest.ContestListViewModel
import com.jxau.oj.ui.contest.ContestScoreboardScreen
import com.jxau.oj.ui.contest.ContestScoreboardViewModel
import com.jxau.oj.ui.editor.EditorScreen
import com.jxau.oj.ui.editor.EditorViewModel
import com.jxau.oj.ui.homework.HomeworkDetailScreen
import com.jxau.oj.ui.homework.HomeworkDetailViewModel
import com.jxau.oj.ui.homework.HomeworkListScreen
import com.jxau.oj.ui.homework.HomeworkListViewModel
import com.jxau.oj.ui.problem.ProblemDetailScreen
import com.jxau.oj.ui.problem.ProblemDetailViewModel
import com.jxau.oj.ui.problem.ProblemListScreen
import com.jxau.oj.ui.problem.ProblemListViewModel
import com.jxau.oj.ui.profile.ProfileScreen
import com.jxau.oj.ui.ranking.RankingScreen
import com.jxau.oj.ui.ranking.RankingViewModel
import com.jxau.oj.ui.record.RecordListScreen
import com.jxau.oj.ui.record.RecordListViewModel
import com.jxau.oj.ui.record.RecordScope
import com.jxau.oj.ui.settings.SettingsScreen
import com.jxau.oj.ui.submission.SubmissionScreen
import com.jxau.oj.ui.submission.SubmissionViewModel
import com.jxau.oj.ui.user.UserProfileScreen
import com.jxau.oj.ui.user.UserProfileViewModel

object Routes {
    const val PROBLEMS = "problems"
    const val ME = "me"
    const val SETTINGS = "settings"
    const val LOGIN = "login"
    const val RANKING = "ranking"
    const val CONTESTS = "contests"
    const val HOMEWORKS = "homeworks"

    const val DOC_ID = "docId"
    const val RID = "rid"
    const val UID = "uid"
    const val TID = "tid"

    /**
     * 题目详情与编辑器，末尾的 `?tid=` 是**可选**的竞赛/作业上下文。
     *
     * ⚠️ 这条参数不是装饰 —— 竞赛与作业里的题目在 Hydro 里是 **hidden 题**
     * （`pdoc.hidden=true`），普通用户没有域级的「查看隐藏题目」权限。
     * 实测（2026-09-15，账号 114514 / role=default，已报名 Week3）：
     *   `GET /p/154`            → 403 `PermissionError: View hidden problems`
     *   `GET /p/154?tid=<竞赛id>` → 200，pdoc.title='拆机螺丝'
     * 缺了它，用户看到的是「你已在竞赛里看到题目名，但点进去被拒」——
     * 列表页正常、详情页报错，极易被误判成站点权限问题。
     */
    const val PROBLEM_DETAIL = "problem/{$DOC_ID}?$TID={$TID}"
    const val EDITOR = "editor/{$DOC_ID}?$TID={$TID}"
    const val SUBMISSION = "submission/{$RID}"
    const val USER_PROFILE = "user/{$UID}"
    const val CONTEST_DETAIL = "contest/{$TID}"
    const val HOMEWORK_DETAIL = "homework/{$TID}"
    const val CONTEST_SCOREBOARD = "contest/{$TID}/scoreboard"

    /** 记录列表：`uid` / `pid` 都是可选参数，-1 表示"不带这个条件"。 */
    const val RECORDS = "records?uid={$UID}&pid={$DOC_ID}"
    private const val NO_VALUE = -1

    /** tid 为空串 = 无竞赛上下文（从题库直接进来的题目）。 */
    fun problemDetail(docId: Int, tid: String = "") = "problem/$docId?tid=$tid"
    fun editor(docId: Int, tid: String = "") = "editor/$docId?tid=$tid"
    fun submission(rid: String) = "submission/$rid"
    fun userProfile(uid: Int) = "user/$uid"

    /** tid 用文档 `_id` 原样字符串（列表响应里 docId 缺失，实测教训）。 */
    fun contestDetail(tid: String) = "contest/$tid"
    fun homeworkDetail(tid: String) = "homework/$tid"
    fun contestScoreboard(tid: String) = "contest/$tid/scoreboard"
    fun records(uid: Int? = null, docId: Int? = null) =
        "records?uid=${uid ?: NO_VALUE}&pid=${docId ?: NO_VALUE}"
}

@Composable
fun AppNavHost(appVm: AppViewModel) {
    val nav = rememberNavController()
    val backStackEntry by nav.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val appState by appVm.state.collectAsStateWithLifecycle()

    val showBottomBar = currentRoute == Routes.PROBLEMS || currentRoute == Routes.ME

    // 登录成功后自动退回上一页 —— 登录页不该停留在返回栈里
    LaunchedEffect(appState.user.isLoggedIn, currentRoute) {
        if (appState.user.isLoggedIn && currentRoute == Routes.LOGIN) {
            nav.popBackStack()
        }
    }

    Column(Modifier.fillMaxSize()) {
        NavHost(
            navController = nav,
            startDestination = Routes.PROBLEMS,
            modifier = Modifier.weight(1f),
        ) {
            composable(Routes.PROBLEMS) {
                val vm: ProblemListViewModel = viewModel(
                    initializer = { ProblemListViewModel(appVm.container.problemRepository) },
                )
                val state by vm.state.collectAsStateWithLifecycle()

                LaunchedEffect(Unit) {
                    if (state.items.isEmpty() && !state.loading) vm.refresh()
                }

                ProblemListScreen(
                    state = state,
                    onQueryChange = vm::onQueryChange,
                    onSubmitSearch = { vm.refresh() },
                    onClearQuery = { vm.clearQuery() },
                    onRefresh = { vm.refresh() },
                    onLoadMore = { vm.loadMore() },
                    onOpenProblem = { docId -> nav.navigate(Routes.problemDetail(docId)) },
                )
            }

            composable(Routes.ME) {
                ProfileScreen(
                    user = appState.user,
                    onLogin = { nav.navigate(Routes.LOGIN) },
                    onLogout = { appVm.logout() },
                    onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                    onOpenRecords = {
                        nav.navigate(Routes.records(uid = appState.user.id.takeIf { it > 0 }))
                    },
                    onOpenRanking = { nav.navigate(Routes.RANKING) },
                    onOpenContests = { nav.navigate(Routes.CONTESTS) },
                    onOpenHomeworks = { nav.navigate(Routes.HOMEWORKS) },
                )
            }

            composable(Routes.CONTESTS) {
                val vm: ContestListViewModel = viewModel(
                    initializer = { ContestListViewModel(appVm.container.contestRepository) },
                )
                val state by vm.state.collectAsStateWithLifecycle()

                ContestListScreen(
                    state = state,
                    onBack = { nav.popBackStack() },
                    onRefresh = vm::refresh,
                    onLoadMore = vm::loadMore,
                    onOpenContest = { tid -> nav.navigate(Routes.contestDetail(tid)) },
                )
            }

            composable(Routes.HOMEWORKS) {
                val vm: HomeworkListViewModel = viewModel(
                    initializer = { HomeworkListViewModel(appVm.container.contestRepository) },
                )
                val state by vm.state.collectAsStateWithLifecycle()

                HomeworkListScreen(
                    state = state,
                    onBack = { nav.popBackStack() },
                    onRefresh = vm::refresh,
                    onLoadMore = vm::loadMore,
                    onOpenHomework = { tid -> nav.navigate(Routes.homeworkDetail(tid)) },
                )
            }

            composable(
                route = Routes.CONTEST_DETAIL,
                arguments = listOf(navArgument(Routes.TID) { type = NavType.StringType }),
            ) { entry ->
                val tid = entry.arguments?.getString(Routes.TID).orEmpty()
                val vm: ContestDetailViewModel = viewModel(
                    key = "contest-detail-$tid",
                    initializer = { ContestDetailViewModel(appVm.container.contestRepository, tid) },
                )
                val state by vm.state.collectAsStateWithLifecycle()

                ContestDetailScreen(
                    state = state,
                    onBack = { nav.popBackStack() },
                    onRetry = vm::load,
                    // 必须带上竞赛上下文：竞赛题是 hidden 题，不带 tid 会被 403 挡掉
                    onOpenProblem = { docId -> nav.navigate(Routes.problemDetail(docId, tid)) },
                    onOpenScoreboard = { nav.navigate(Routes.contestScoreboard(tid)) },
                )
            }

            composable(
                route = Routes.CONTEST_SCOREBOARD,
                arguments = listOf(navArgument(Routes.TID) { type = NavType.StringType }),
            ) { entry ->
                val tid = entry.arguments?.getString(Routes.TID).orEmpty()
                val vm: ContestScoreboardViewModel = viewModel(
                    key = "contest-scoreboard-$tid",
                    initializer = { ContestScoreboardViewModel(appVm.container.contestRepository, tid) },
                )
                val state by vm.state.collectAsStateWithLifecycle()

                ContestScoreboardScreen(
                    state = state,
                    selfUid = appState.user.id,
                    onBack = { nav.popBackStack() },
                    onRetry = { vm.refresh(initial = true) },
                    onOpenRecord = { rid -> nav.navigate(Routes.submission(rid)) },
                )
            }

            composable(
                route = Routes.HOMEWORK_DETAIL,
                arguments = listOf(navArgument(Routes.TID) { type = NavType.StringType }),
            ) { entry ->
                val tid = entry.arguments?.getString(Routes.TID).orEmpty()
                val vm: HomeworkDetailViewModel = viewModel(
                    key = "homework-detail-$tid",
                    initializer = { HomeworkDetailViewModel(appVm.container.contestRepository, tid) },
                )
                val state by vm.state.collectAsStateWithLifecycle()

                HomeworkDetailScreen(
                    state = state,
                    onBack = { nav.popBackStack() },
                    onRetry = vm::load,
                    onClaim = vm::claim,
                    onClaimMessageShown = vm::consumeClaimMessage,
                    // 同竞赛：作业题也在题库里隐藏，认领后需带 tid 才可读
                    onOpenProblem = { docId -> nav.navigate(Routes.problemDetail(docId, tid)) },
                )
            }

            composable(Routes.RANKING) {
                val vm: RankingViewModel = viewModel(
                    initializer = { RankingViewModel(appVm.container.userRepository) },
                )
                val state by vm.state.collectAsStateWithLifecycle()

                RankingScreen(
                    state = state,
                    onBack = { nav.popBackStack() },
                    onRefresh = vm::refresh,
                    onLoadMore = vm::loadMore,
                    onOpenUser = { uid -> nav.navigate(Routes.userProfile(uid)) },
                )
            }

            composable(
                route = Routes.USER_PROFILE,
                arguments = listOf(navArgument(Routes.UID) { type = NavType.IntType }),
            ) { entry ->
                val uid = entry.arguments?.getInt(Routes.UID) ?: 0
                val vm: UserProfileViewModel = viewModel(
                    key = "user-profile-$uid",
                    initializer = { UserProfileViewModel(appVm.container.userRepository, uid) },
                )
                val state by vm.state.collectAsStateWithLifecycle()

                UserProfileScreen(
                    state = state,
                    onBack = { nav.popBackStack() },
                    onRetry = vm::load,
                    onOpenRecords = { targetUid ->
                        nav.navigate(Routes.records(uid = targetUid))
                    },
                )
            }

            composable(
                route = Routes.RECORDS,
                arguments = listOf(
                    navArgument(Routes.UID) {
                        type = NavType.IntType
                        defaultValue = -1
                    },
                    navArgument(Routes.DOC_ID) {
                        type = NavType.IntType
                        defaultValue = -1
                    },
                ),
            ) { entry ->
                val uid = entry.arguments?.getInt(Routes.UID)?.takeIf { it > 0 }
                val pid = entry.arguments?.getInt(Routes.DOC_ID)?.takeIf { it > 0 }

                val vm: RecordListViewModel = viewModel(
                    key = "records-${uid ?: 0}-${pid ?: 0}",
                    initializer = {
                        RecordListViewModel(
                            repo = appVm.container.submissionRepository,
                            scope = when {
                                pid != null -> RecordScope.OfProblem(pid)
                                uid != null -> RecordScope.OfUser(uid)
                                else -> RecordScope.All
                            },
                        )
                    },
                )
                val state by vm.state.collectAsStateWithLifecycle()

                // 标题跟着范围走：这道题的、这个人的、还是全站的。
                // "我的"用站点下发的登录名判断，而不是硬编码 uid —— 换账号后依然正确。
                val selfUid = appState.user.id
                val title = when {
                    pid != null -> "本题提交"
                    uid != null && uid == selfUid -> "我的提交"
                    uid != null -> "TA 的提交记录"
                    else -> "全站提交"
                }

                RecordListScreen(
                    title = title,
                    state = state,
                    onBack = { nav.popBackStack() },
                    onRefresh = vm::refresh,
                    onLoadMore = vm::loadMore,
                    onOpenRecord = { rid -> nav.navigate(Routes.submission(rid)) },
                    onLogin = { nav.navigate(Routes.LOGIN) },
                )

                // 登录成功回到本页时自动重载：用户去登录页就是为了看这份列表，
                // 回来还要手动点一次刷新属于体验缺陷。
                // 只在"未登录 → 已登录"翻转时触发，首次进页面不重复请求。
                val loggedIn = appState.user.isLoggedIn
                var loggedInBefore by remember { mutableStateOf(loggedIn) }
                LaunchedEffect(loggedIn) {
                    if (loggedIn && !loggedInBefore) vm.refresh()
                    loggedInBefore = loggedIn
                }
            }

            composable(Routes.SETTINGS) {
                SettingsScreen(
                    themeId = appState.themeId,
                    darkMode = appState.darkMode,
                    editorFontId = appState.editorFontId,
                    editorFontSizeSp = appState.editorFontSizeSp,
                    onSelectTheme = appVm::setTheme,
                    onSelectDarkMode = appVm::setDarkMode,
                    onSelectEditorFont = appVm::setEditorFont,
                    onChangeEditorFontSize = appVm::setEditorFontSize,
                    onBack = { nav.popBackStack() },
                )
            }

            composable(Routes.LOGIN) {
                LoginScreen(
                    busy = appState.loginBusy,
                    error = appState.loginError,
                    onBack = { nav.popBackStack() },
                    onSubmit = { uname, password -> appVm.login(uname, password) },
                )
            }

            composable(
                route = Routes.PROBLEM_DETAIL,
                arguments = listOf(
                    navArgument(Routes.DOC_ID) { type = NavType.IntType },
                    navArgument(Routes.TID) {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                ),
            ) { entry ->
                val docId = entry.arguments?.getInt(Routes.DOC_ID) ?: 0
                // 空串 = 从题库进来的公开题，不带竞赛上下文
                val tid = entry.arguments?.getString(Routes.TID).orEmpty()
                val vm: ProblemDetailViewModel = viewModel(
                    key = "problem-detail-$docId-$tid",
                    initializer = {
                        ProblemDetailViewModel(appVm.container.problemRepository, docId, tid)
                    },
                )
                val state by vm.state.collectAsStateWithLifecycle()

                ProblemDetailScreen(
                    state = state,
                    onBack = { nav.popBackStack() },
                    onRetry = vm::load,
                    // 编辑器同样要带 tid：拉题面与提交都依赖它（否则题面 403、提交不计入竞赛）
                    onOpenEditor = { nav.navigate(Routes.editor(docId, tid)) },
                    onOpenRecords = { nav.navigate(Routes.records(docId = docId)) },
                )
            }

            composable(
                route = Routes.EDITOR,
                arguments = listOf(
                    navArgument(Routes.DOC_ID) { type = NavType.IntType },
                    navArgument(Routes.TID) {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                ),
            ) { entry ->
                val docId = entry.arguments?.getInt(Routes.DOC_ID) ?: 0
                val tid = entry.arguments?.getString(Routes.TID).orEmpty()
                val vm: EditorViewModel = viewModel(
                    key = "editor-$docId-$tid",
                    initializer = {
                        EditorViewModel(
                            problemRepository = appVm.container.problemRepository,
                            submissionRepository = appVm.container.submissionRepository,
                            sessionStore = appVm.container.sessionStore,
                            docId = docId,
                            tid = tid,
                        )
                    },
                )
                val state by vm.state.collectAsStateWithLifecycle()

                EditorScreen(
                    state = state,
                    onBack = { nav.popBackStack() },
                    onCodeChange = vm::onCodeChange,
                    onSelectLang = vm::selectLang,
                    onSubmit = vm::submit,
                    onSubmitted = { rid ->
                        vm.consumeSubmittedRid()
                        nav.navigate(Routes.submission(rid))
                    },
                    onErrorShown = vm::consumeError,
                    onDraftRestoredShown = vm::consumeDraftRestored,
                    onToggleStatement = vm::toggleStatement,
                    onExpandStatement = vm::expandStatement,
                    onCollapseStatement = vm::collapseStatement,
                    onTestInputChange = vm::setTestInput,
                    onAddTestCase = vm::addTestCase,
                    onRemoveTestCase = vm::removeTestCase,
                    onFillSamples = vm::fillFromSamples,
                    onRunSelfTest = vm::runSelfTest,
                    onPretestErrorShown = vm::consumePretestError,
                )
            }

            composable(
                route = Routes.SUBMISSION,
                arguments = listOf(navArgument(Routes.RID) { type = NavType.StringType }),
            ) { entry ->
                val rid = entry.arguments?.getString(Routes.RID).orEmpty()
                val vm: SubmissionViewModel = viewModel(
                    key = "submission-$rid",
                    initializer = { SubmissionViewModel(appVm.container.submissionRepository, rid) },
                )
                val state by vm.state.collectAsStateWithLifecycle()

                SubmissionScreen(
                    state = state,
                    onBack = { nav.popBackStack() },
                    onRefresh = vm::poll,
                )

                // 同记录页：登录成功回到本页自动重查一次评测状态
                val loggedIn = appState.user.isLoggedIn
                var loggedInBefore by remember { mutableStateOf(loggedIn) }
                LaunchedEffect(loggedIn) {
                    if (loggedIn && !loggedInBefore) vm.poll()
                    loggedInBefore = loggedIn
                }
            }
        }

        if (showBottomBar) {
            NavigationBar {
                NavigationBarItem(
                    selected = currentRoute == Routes.PROBLEMS,
                    onClick = { nav.navigateToTab(Routes.PROBLEMS) },
                    icon = { Icon(Icons.Outlined.Code, contentDescription = null) },
                    label = { Text("题库") },
                )
                NavigationBarItem(
                    selected = currentRoute == Routes.ME,
                    onClick = { nav.navigateToTab(Routes.ME) },
                    icon = { Icon(Icons.Outlined.Person, contentDescription = null) },
                    label = { Text("我的") },
                )
            }
        }
    }
}

/** 底部 tab 的标准跳转：不堆栈、且保留各自的滚动位置。 */
private fun NavHostController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
