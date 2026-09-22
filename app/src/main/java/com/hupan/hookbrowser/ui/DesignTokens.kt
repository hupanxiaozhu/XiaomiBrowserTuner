package com.hupan.hookbrowser.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 三工程统一设计令牌 v2（Compose 侧落地）。
 *
 * 单一真源：`三工程统一设计规范 v2.md`（本地设计规范库）。
 * 等价副本（**值必须逐项相同**）：
 * - 番茄 `lsp/HookFanqie/app/src/main/java/cc/hookfanqie/compat/ui/DesignTokens.kt`
 * - 多看 `DuokanTuner/app/src/main/res/values/design_tokens.xml`
 * - 浏览器（本工程，XML 时代的副本已随 1.11.0 的界面重建删除，现以本文件为准）
 *
 * 行尾的 `// ds_xxx = 值` 注释是给 `sync_check.py` 用的对账锚点，
 * 不要改格式；改值 = 三边一起改 + 跑脚本 + 三边版本号各 +1。
 */

/**
 * 颜色令牌：与番茄的 [DsColor] 逐值相同（深色强调色按澎湃真值取 #277AF7 那次修正后
 * 三边统一为 #4C8DFF，这里跟随）。
 *
 * 深浅两套都是**显式值**，不依赖 miuix 默认色板 —— miuix 的默认值有三处与另两个工程不同
 * （surface 偏白、次要文本偏蓝灰、深色块底与卡片同色），不覆盖就会出现三边观感不一致。
 */
internal object DsColor {
    // ---- 亮色 ----
    val bgLight = Color(0xFFF2F2F7)           // 页面底
    val cardLight = Color(0xFFFFFFFF)         // 卡片
    val fieldLight = Color(0xFFF2F2F7)        // 输入区 / 内容块
    val textPrimaryLight = Color(0xFF191919)
    val textSecondaryLight = Color(0xFF858585)
    val textHintLight = Color(0x4D000000)
    val accentLight = Color(0xFF3482FF)
    val accentSoftLight = Color(0x1F3482FF)   // 强调色 12%
    val onAccentLight = Color(0xFFFFFFFF)
    val dividerLight = Color(0x15000000)
    val trackOffLight = Color(0xFFE5E5E5)
    val outlineLight = Color(0xFFCCCCCC)
    val rippleLight = Color(0x1F000000)
    val rippleOnAccentLight = Color(0x33FFFFFF)
    val arrowLight = Color(0xFFC4C4C6)
    val dangerLight = Color(0xFFFF3B30)

    // ---- 深色 ----
    val bgDark = Color(0xFF000000)
    val cardDark = Color(0xFF1C1C1E)
    val fieldDark = Color(0xFF2C2C2E)
    val textPrimaryDark = Color(0xFFFFFFFF)
    val textSecondaryDark = Color(0xFF999999)
    val textHintDark = Color(0x4DFFFFFF)
    val accentDark = Color(0xFF4C8DFF)
    val accentSoftDark = Color(0x334C8DFF)    // 强调色 20%
    val onAccentDark = Color(0xFFFFFFFF)
    val dividerDark = Color(0x1AFFFFFF)
    val trackOffDark = Color(0xFF3A3A3C)
    val outlineDark = Color(0xFF636366)
    val rippleDark = Color(0x33FFFFFF)
    val rippleOnAccentDark = Color(0x33FFFFFF)
    val arrowDark = Color(0xFF6C6C70)
    val dangerDark = Color(0xFFFF453A)

    /** 详情浮层背后的遮罩（深浅同值，50% 黑） */
    val scrim = Color(0x80000000)
}

/** 字号层级：与番茄 / 多看的 `@dimen/ds_text_*` 同值（sp）。 */
internal object DsType {
    /** 页头大标题 */
    val display: TextUnit = 30.sp           // ds_text_display = 30sp
    /** 关于页 hero 模块名 */
    val hero: TextUnit = 22.sp              // ds_text_hero = 22sp
    /** 详情弹窗标题 */
    val dialogTitle: TextUnit = 17.sp       // ds_text_dialog_title = 17sp
    /** 卡片标题 */
    val title: TextUnit = 16.sp             // ds_text_title = 16sp
    /** 信息取值 */
    val value: TextUnit = 15.sp             // ds_text_value = 15sp
    /** 分区标题 */
    val section: TextUnit = 14.sp           // ds_text_section = 14sp
    /** 正文 / 副文案 */
    val body: TextUnit = 13.sp              // ds_text_body = 13sp
    /** 小标签 / 等宽类名 */
    val label: TextUnit = 12.sp             // ds_text_label = 12sp
    /** 胶囊文字 / 脚注 / 底栏标签 */
    val caption: TextUnit = 11.sp           // ds_text_caption = 11sp

    /** 行高：正文 13sp → 19sp */
    val lineBody: TextUnit = 19.sp          // ds_line_body = 6dp（行距补偿）
    /** 行高：标签 12sp → 17sp */
    val lineLabel: TextUnit = 17.sp         // ds_line_label = 5dp
    /** 行高：说明文字 11sp → 16sp */
    val lineCaption: TextUnit = 16.sp       // ds_line_caption = 5dp
    /** 行高：等宽 12sp → 18sp */
    val lineMono: TextUnit = 18.sp          // ds_line_mono = 6dp
}

/** 间距与尺寸（dp）。 */
internal object DsSpace {
    val xs = 4.dp                            // ds_space_xs = 4dp
    val sm = 8.dp                            // ds_space_sm = 8dp
    val md = 12.dp                           // ds_space_md = 12dp
    val lg = 16.dp                           // ds_space_lg = 16dp
    val xl = 20.dp                           // ds_space_xl = 20dp
    val xxl = 24.dp                          // ds_space_xxl = 24dp

    val pagePadH = 16.dp                     // ds_page_pad_h = 16dp
    val cardPadH = 18.dp                     // ds_card_pad_h = 18dp
    val cardPadV = 16.dp                     // ds_card_pad_v = 16dp
    val cardGap = 10.dp                      // ds_card_gap = 10dp
    val headerPadH = 22.dp                   // ds_header_pad_h = 22dp
    val headerPadTop = 20.dp                 // ds_header_pad_top = 20dp
    val headerPadBottom = 14.dp              // ds_header_pad_bottom = 14dp
    val headerSubGap = 6.dp                  // ds_header_sub_gap = 6dp
    val sectionPadStart = 8.dp               // ds_section_pad_start = 8dp
    val sectionPadTop = 22.dp                // ds_section_pad_top = 22dp
    val sectionPadBottom = 2.dp              // ds_section_pad_bottom = 2dp
    val blockPad = 12.dp                     // ds_block_pad = 12dp
    val dialogPadH = 22.dp                   // ds_dialog_pad_h = 22dp
    val heroPadH = 20.dp                     // ds_hero_pad_h = 20dp
    val heroPadV = 22.dp                     // ds_hero_pad_v = 22dp
    val heroIconSize = 52.dp                 // ds_hero_icon_size = 52dp

    val rowMinHeight = 60.dp                 // ds_row_min_height = 60dp
    val buttonHeight = 48.dp                 // ds_button_height = 48dp
    val navHeight = 56.dp                    // ds_nav_height = 56dp
    val topBarHeight = 56.dp                 // ds_top_bar_height = 56dp
    val dividerHeight = 1.dp                 // ds_divider_height = 1dp
    val iconNav = 22.dp                      // ds_icon_nav = 22dp
    val iconChevron = 18.dp                  // ds_icon_chevron = 18dp
    val contentMaxWidth = 640.dp             // ds_content_max_width = 640dp
}

/** 圆角：卡片 20、内容块 / 输入区 / 按钮 12、胶囊 = 高度一半（stadium）。 */
internal object DsRadius {
    val card = 20.dp                         // ds_radius_card = 20dp
    val block = 12.dp                        // ds_radius_block = 12dp
    val capsuleTag = 11.dp                   // ds_radius_capsule_tag = 11dp
    val capsuleChip = 16.dp                  // ds_radius_capsule_chip = 16dp
}

/** 胶囊规格：形状一律 stadium（圆角 = 高度 / 2）。 */
internal object DsCapsule {
    val tagHeight = 22.dp                    // ds_capsule_tag_h = 22dp
    val chipHeight = 32.dp                   // ds_capsule_chip_h = 32dp
    val tagPadH = 10.dp                      // ds_capsule_tag_pad_h = 10dp
    val chipPadH = 16.dp                     // ds_capsule_chip_pad_h = 16dp
    val navPillPadH = 12.dp                  // ds_nav_pill_pad_h = 12dp
    val navPillPadV = 6.dp                   // ds_nav_pill_pad_v = 6dp
    val navLabelGap = 3.dp                   // ds_nav_label_gap = 3dp
}

/** 动效时长（ms）；插值统一用减速（LinearOutSlowInEasing ≈ DecelerateInterpolator）。 */
internal object DsMotion {
    /** 状态胶囊底色 + 文字色过渡 */
    const val state = 220                    // ds_dur_state = 220
    /** 底栏指示器：淡入 + 缩放 0.88 → 1 */
    const val nav = 260                      // ds_dur_nav = 260
    /** 页面 / 内容切换 */
    const val page = 200                     // ds_dur_page = 200
    /** 按压反馈 */
    const val press = 120                    // ds_dur_press = 120
}

/** 阴影（elevation）：卡片不投影，靠底色分层。 */
internal object DsElevation {
    val card = 0.dp                          // ds_elevation_card = 0dp
    val nav = 8.dp                           // ds_elevation_nav = 8dp
    val dialog = 16.dp                       // ds_elevation_dialog = 16dp
}
