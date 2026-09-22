/*
 * 完整更新日志（二级页）—— 关于页只留当前版本一条，历史版本全部在这里。
 *
 * 折叠规则：当前版本（index 0）恒定展开；其余历史版本**默认全部收起**，
 * 点标题行才展开正文 —— 十几个历史版本的正文全铺开会有几千像素，
 * 用户在页面里根本找不到想看的那一版。
 */
package com.hupan.hookbrowser.ui.page

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hupan.hookbrowser.ui.CHANGELOGS
import com.hupan.hookbrowser.ui.ChangelogCard
import com.hupan.hookbrowser.ui.DsSpace
import com.hupan.hookbrowser.ui.navigation.LocalNavigator
import com.hupan.hookbrowser.ui.utils.pageContentPadding
import com.hupan.hookbrowser.ui.utils.pageScroll
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back

@Composable
internal fun ChangelogPage() {
    val navigator = LocalNavigator.current
    val scrollBehavior = MiuixScrollBehavior()
    val listState = rememberLazyListState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = "更新日志",
                subtitle = "共 ${CHANGELOGS.size} 个版本",
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = { navigator.pop() }) {
                        Icon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = "返回",
                            modifier = Modifier.size(26.dp),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxWidth()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.pageScroll(scrollBehavior),
                contentPadding = pageContentPadding(innerPadding, PaddingValues(0.dp)),
                verticalArrangement = Arrangement.spacedBy(DsSpace.cardGap),
            ) {
                items(count = CHANGELOGS.size) { index ->
                    ChangelogCard(
                        entry = CHANGELOGS[index],
                        current = index == 0,
                        collapsible = index > 0,
                    )
                }
            }
        }
    }
}
