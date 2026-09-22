/*
 * 项目信息（二级页）—— 完整的信息表。
 *
 * 为什么从关于页迁出来：七行带说明的信息表铺在一级页里会把页面撑到两屏多，
 * 而关于页要恒定在一屏左右（信息架构铁律：一级页只放开关 + 入口）。
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.hupan.hookbrowser.ui.DsSpace
import com.hupan.hookbrowser.ui.ProjectInfoCard
import com.hupan.hookbrowser.ui.moduleVersion
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
internal fun ProjectInfoPage() {
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val scrollBehavior = MiuixScrollBehavior()
    val listState = rememberLazyListState()
    val version = remember(context) { moduleVersion(context) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = "项目信息",
                subtitle = "版本 · 包名 · 作用域 · 运行环境",
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
                item { ProjectInfoCard(version) }
            }
        }
    }
}
