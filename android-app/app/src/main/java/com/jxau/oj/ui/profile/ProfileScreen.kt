package com.jxau.oj.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Leaderboard
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jxau.oj.data.model.User
import com.jxau.oj.ui.component.UserAvatar
import com.jxau.oj.ui.util.roleLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    user: User,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenRecords: () -> Unit,
    onOpenRanking: () -> Unit,
    onOpenContests: () -> Unit,
    onOpenHomeworks: () -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("我的") }) },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    UserAvatar(avatarUrl = user.avatarUrl, size = 56.dp, name = user.uname)
                    Spacer(Modifier.size(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = user.uname,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = if (user.isLoggedIn) roleLabel(user.role) else "未登录",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (!user.isLoggedIn) {
                    Box(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                        OutlinedButton(
                            onClick = onLogin,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.AutoMirrored.Outlined.Login, contentDescription = null)
                            Spacer(Modifier.size(8.dp))
                            Text("登录")
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            ) {
                // 未登录时不显示"我的提交"：点进去必然是登录跳转，
                // 不如把入口收起来，避免用户白点一次
                if (user.isLoggedIn) {
                    ListItem(
                        headlineContent = { Text("我的提交") },
                        supportingContent = { Text("提交记录与评测结果") },
                        leadingContent = {
                            Icon(Icons.Outlined.History, contentDescription = null)
                        },
                        trailingContent = {
                            Icon(Icons.Outlined.ChevronRight, contentDescription = null)
                        },
                        modifier = Modifier.clickableRow(onOpenRecords),
                    )
                    HorizontalDivider()
                }

                ListItem(
                    headlineContent = { Text("排行榜") },
                    supportingContent = { Text("全站用户") },
                    leadingContent = {
                        Icon(Icons.Outlined.Leaderboard, contentDescription = null)
                    },
                    trailingContent = {
                        Icon(Icons.Outlined.ChevronRight, contentDescription = null)
                    },
                    modifier = Modifier.clickableRow(onOpenRanking),
                )

                HorizontalDivider()

                ListItem(
                    headlineContent = { Text("竞赛") },
                    supportingContent = { Text("进行中与已结束的比赛") },
                    leadingContent = {
                        Icon(Icons.Outlined.EmojiEvents, contentDescription = null)
                    },
                    trailingContent = {
                        Icon(Icons.Outlined.ChevronRight, contentDescription = null)
                    },
                    modifier = Modifier.clickableRow(onOpenContests),
                )

                HorizontalDivider()

                ListItem(
                    headlineContent = { Text("作业") },
                    supportingContent = { Text("题单与截止时间") },
                    leadingContent = {
                        Icon(Icons.AutoMirrored.Outlined.Assignment, contentDescription = null)
                    },
                    trailingContent = {
                        Icon(Icons.Outlined.ChevronRight, contentDescription = null)
                    },
                    modifier = Modifier.clickableRow(onOpenHomeworks),
                )

                HorizontalDivider()

                ListItem(
                    headlineContent = { Text("外观设置") },
                    supportingContent = { Text("主题、深色模式") },
                    leadingContent = {
                        Icon(Icons.Outlined.Palette, contentDescription = null)
                    },
                    trailingContent = {
                        Icon(Icons.Outlined.ChevronRight, contentDescription = null)
                    },
                    modifier = Modifier.clickableRow(onOpenSettings),
                )

                if (user.isLoggedIn) {
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text("退出登录") },
                        leadingContent = {
                            Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null)
                        },
                        modifier = Modifier.clickableRow(onLogout),
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            Spacer(Modifier.height(48.dp))
        }
    }
}

private fun Modifier.clickableRow(onClick: () -> Unit): Modifier =
    this.fillMaxWidth().clickable(onClick = onClick)
