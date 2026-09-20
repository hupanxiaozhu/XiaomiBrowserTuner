#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""HookBrowser 工程静态走查（无需 JDK / 不跑构建）。

检查项：
  1. 代码里 R.xxx 引用的资源是否都定义了
  2. prefs.xml 的 app:key 与 Config.kt 常量值是否一一对应
  3. 默认值表（Config.kt DEFAULTS）与 prefs.xml 的 app:defaultValue 是否一致
  4. prefs.xml / AndroidManifest.xml 引用的 @string @array @style ... 是否存在
  5. Manifest 引用是否都能解析；manifest 里不得残留 xposed* legacy 声明
  6. META-INF/xposed/{module.prop,java_init.list,scope.list} 与入口类、宿主包名对齐，
     且全工程不再出现 de.robv 旧 API 的可执行引用
  6. Features.ALL 注册表与 features/ 下的实际文件是否一致
  7. Kotlin 符号交叉检查：自有 object 的成员引用与本项目 import 是否都能解析
     （专门拦「改了调用点却漏写实现」这类只会在编译期暴露的错）
  8. try/catch 两个分支给同一个 val 赋值（`Val cannot be reassigned`）
  9. Manifest 里声明的 Activity 是否有对应源码文件
 10. 规则库 SP 文件名三处一致（模块进程写、宿主进程读；写错不报错，只会「导入了但不生效」）
 11. 粗粒度语法体检：剥掉注释/字符串后 kt 的 () [] {} 是否平衡、每个 xml 能否被 ElementTree 解析
  12. 自绘开关行（ui/SwitchRowPreference）与 ui/FeatureDetails.kt 的详情表一一对应
      （漏一条 = 那一行点了没反应，多一条 = 死代码；两者都不报错，只能静态钉死）

用法：python tools/verify_static.py   （退出码 0 = 全部通过）
"""
import os
import re
import sys
import xml.etree.ElementTree as ET

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'app', 'src', 'main')
JAVA = os.path.join(ROOT, 'java')
RES = os.path.join(ROOT, 'res')
MANIFEST = os.path.join(ROOT, 'AndroidManifest.xml')
PKG_DIR = os.path.join(JAVA, 'com', 'hupan', 'hookbrowser')

problems = []


def where_rel(p):
    """相对 app/src/main 的短路径，出错时更好定位"""
    return os.path.relpath(p, ROOT).replace('\\', '/')

# ---------- 1. 收集资源定义 ----------
defs = {'string': set(), 'array': set(), 'color': set(), 'xml': set(),
        'layout': set(), 'drawable': set(), 'style': set(), 'id': set(), 'menu': set()}

for dirpath, _, files in os.walk(RES):
    for f in files:
        p = os.path.join(dirpath, f)
        name, ext = os.path.splitext(f)
        kind = os.path.basename(dirpath)
        if ext == '.xml' and kind == 'values':
            txt = open(p, encoding='utf-8').read()
            for m in re.finditer(r'<(string|string-array|color|style)\s+name="([^"]+)"', txt):
                t = m.group(1)
                defs['array' if t == 'string-array' else t].add(m.group(2))
        elif ext == '.xml':
            if kind.startswith('xml'):
                defs['xml'].add(name)
            elif kind == 'layout':
                defs['layout'].add(name)
                defs['id'] |= set(re.findall(r'android:id="@\+id/(\w+)"',
                                             open(p, encoding='utf-8').read()))
            elif kind == 'menu':
                defs['menu'].add(name)
                defs['id'] |= set(re.findall(r'android:id="@\+id/(\w+)"',
                                             open(p, encoding='utf-8').read()))
            elif kind.startswith('drawable') or kind.startswith('mipmap'):
                defs['drawable'].add(name)

# ---------- 2. 检查代码里的 R.xxx 引用 ----------
for dirpath, _, files in os.walk(JAVA):
    for f in files:
        if not f.endswith(('.kt', '.java')):
            continue
        p = os.path.join(dirpath, f)
        txt = open(p, encoding='utf-8').read()
        for m in re.finditer(r'\bR\.(\w+)\.(\w+)', txt):
            kind, name = m.group(1), m.group(2)
            if kind not in defs:
                problems.append('未知资源类型 R.%s（%s 引用 %s）' % (kind, where_rel(p), name))
            elif name not in defs[kind]:
                problems.append('资源缺失：R.%s.%s（被 %s 引用）' % (kind, name, where_rel(p)))

# ---------- 3. prefs.xml 的 key 与 Config.kt 常量值对齐 ----------
prefs_txt = open(os.path.join(RES, 'xml', 'prefs.xml'), encoding='utf-8').read()
# 只取「开关类」条目（SwitchPreferenceCompat / ListPreference）的 key。
# 普通 <Preference> 是跳转入口（如「自定义拦截规则」管理页），没有对应布尔开关，
# 让它的 key 参与对齐检查只会逼着人去 Config.kt 里补一个没意义的常量。
xml_keys = set()
# 开关类条目：库自带的 SwitchPreferenceCompat / ListPreference，以及自绘的
# com.hupan.hookbrowser.ui.SwitchRowPreference（1.9.4 起的功能行）
for _m in re.finditer(
        r'<(SwitchPreferenceCompat|ListPreference|[\w.]+SwitchRowPreference)\b(.*?)/>',
        prefs_txt, re.S):
    _k = re.search(r'app:key="([^"]+)"', _m.group(2))
    if _k:
        xml_keys.add(_k.group(1))

cfg = open(os.path.join(PKG_DIR, 'Config.kt'), encoding='utf-8').read()
cfg_map = dict(re.findall(r'const val (\w+) = "([^"]+)"', cfg))

# 开关 key = 全大写常量，排除包名/SP 文件名/默认值这类非开关常量
NON_SWITCH = ('MODULE_PKG', 'PREFS_NAME')
switch_keys = {v for k, v in cfg_map.items()
               if k.isupper() and k not in NON_SWITCH and not k.endswith('_DEFAULT')}
# ua_mode 是 ListPreference，不在布尔开关表里，需要单独排除
for k in ('UA_MODE',):
    switch_keys.discard(cfg_map.get(k, ''))
    xml_keys.discard(cfg_map.get(k, ''))

if switch_keys != xml_keys:
    problems.append('开关 key 不一致：仅 Config.kt 有 %s；仅 prefs.xml 有 %s'
                    % (sorted(switch_keys - xml_keys), sorted(xml_keys - switch_keys)))

# 默认值表 vs prefs.xml 的 defaultValue
xml_defaults = {}
for blk in re.finditer(
        r'<(?:SwitchPreferenceCompat|[\w.]+SwitchRowPreference)\b(.*?)/>', prefs_txt, re.S):
    b = blk.group(1)
    k = re.search(r'app:key="([^"]+)"', b)
    v = re.search(r'app:defaultValue="(\w+)"', b)
    if k and v:
        xml_defaults[k.group(1)] = (v.group(1) == 'true')

cfg_defaults = {}
ksec = re.search(r'DEFAULTS = linkedMapOf\((.*?)\)', cfg, re.S)
for m in re.finditer(r'(\w+) to (true|false)', ksec.group(1)):
    cfg_defaults[cfg_map.get(m.group(1), m.group(1))] = (m.group(2) == 'true')

for k in xml_defaults:
    if k in cfg_defaults and cfg_defaults[k] != xml_defaults[k]:
        problems.append('默认值不一致 %s：prefs.xml=%s Config.kt=%s'
                        % (k, xml_defaults[k], cfg_defaults[k]))
for k in cfg_defaults:
    if k not in xml_defaults:
        problems.append('Config.kt 默认值表里的 %s 在 prefs.xml 中没有对应开关' % k)

# ---------- 4. prefs.xml 引用的 @string / @array ----------
for m in re.finditer(r'@(string|array)/([\w.]+)', prefs_txt):
    kind = 'array' if m.group(1) == 'array' else 'string'
    if m.group(2) not in defs[kind]:
        problems.append('prefs.xml 引用了不存在的 @%s/%s' % (m.group(1), m.group(2)))

# ---------- 5. Manifest 引用 ----------
man = open(MANIFEST, encoding='utf-8').read()
for m in re.finditer(r'@(string|array|style|drawable|color|mipmap|xml)/([\w.]+)', man):
    kind = m.group(1)
    if kind in defs and m.group(2) not in defs[kind]:
        problems.append('Manifest 引用了不存在的 @%s/%s' % (m.group(1), m.group(2)))

# 1.10.0：模块身份改由 META-INF/xposed/ 声明，manifest 里不能再留 xposed* meta-data。
# 只在 XML 注释之外检查 —— 注释里为了说清「为什么删掉它们」必然要提到这些名字。
_man_stripped = re.sub(r'<!--.*?-->', '', man, flags=re.S)
for legacy in ('xposedminversion', 'xposedsharedprefs', 'xposedmodule', 'xposedscope'):
    if legacy in _man_stripped:
        problems.append('Manifest 仍残留 legacy 声明 %s（API 102 下应由 META-INF/xposed/ 承担）' % legacy)

# 1.10.1：开关的写入端。宿主读的是框架数据库，模块 App 必须经 XposedProvider 拿到
# IXposedService 才能写进去。少声明它 = 「界面上开关是开的、宿主读到空表」且不报任何错。
if 'io.github.libxposed.service.XposedProvider' not in _man_stripped:
    problems.append('Manifest 缺少 XposedProvider（模块进程拿不到框架服务，开关写不进远端）')
elif '${applicationId}.XposedService' not in _man_stripped:
    problems.append('XposedProvider 的 authorities 必须是 ${applicationId}.XposedService（写错同样静默失效）')

# ---------- 6. META-INF/xposed 注册文件与入口类对齐 ----------
META = os.path.join(ROOT, 'resources', 'META-INF', 'xposed')
mkt = open(os.path.join(PKG_DIR, 'MainHook.kt'), encoding='utf-8').read()
pkg = re.search(r'^package ([\w.]+)', mkt, re.M).group(1)
expect = pkg + '.MainHook'

init_p = os.path.join(META, 'java_init.list')
if not os.path.exists(init_p):
    problems.append('缺少 META-INF/xposed/java_init.list（API 102 模块入口声明）')
else:
    entry = open(init_p, encoding='utf-8').read().strip()
    if entry != expect:
        problems.append('java_init.list 内容 %r 与入口类 %r 不一致' % (entry, expect))

prop_p = os.path.join(META, 'module.prop')
if not os.path.exists(prop_p):
    problems.append('缺少 META-INF/xposed/module.prop（API 102 模块描述）')
else:
    prop = open(prop_p, encoding='utf-8').read()
    for need in ('minApiVersion', 'targetApiVersion'):
        if need not in prop:
            problems.append('module.prop 缺少 %s' % need)

scope_p = os.path.join(META, 'scope.list')
if not os.path.exists(scope_p):
    problems.append('缺少 META-INF/xposed/scope.list（作用域）')
else:
    scope = open(scope_p, encoding='utf-8').read().strip()
    if scope != 'com.android.browser':
        problems.append('scope.list 内容 %r 与目标宿主 com.android.browser 不一致' % scope)

if 'XposedModule' not in mkt:
    problems.append('MainHook 未继承 XposedModule（API 102 入口）')
if 'de.robv' in mkt:
    problems.append('MainHook 仍引用 de.robv 旧 API（API 102 下被禁止）')

# 全工程不得再出现旧 API 的**可执行引用**（注释里的历史说明不算）
LEGACY_SYMS = ('de.robv.android.xposed', 'XposedHelpers', 'XposedBridge', 'XC_MethodHook',
               'MethodHookParam', 'IXposedHookLoadPackage', 'XSharedPreferences')
for dirpath, _, files in os.walk(JAVA):
    for f in files:
        if not f.endswith(('.kt', '.java')):
            continue
        p = os.path.join(dirpath, f)
        in_block = False
        for i, line in enumerate(open(p, encoding='utf-8'), 1):
            s = line.strip()
            if in_block:
                if '*/' in s:
                    in_block = False
                continue
            if s.startswith('/*'):
                if '*/' not in s:
                    in_block = True
                continue
            code = line.split('//')[0]
            for sym in LEGACY_SYMS:
                if sym in code:
                    problems.append('%s:%d 仍引用旧 API 符号 %s' % (where_rel(p), i, sym))

# ---------- 7. Feature 注册表 vs 实际文件 ----------
feat_dir = os.path.join(PKG_DIR, 'features')
files = {os.path.splitext(f)[0] for f in os.listdir(feat_dir) if f.endswith('.kt')}
reg = open(os.path.join(feat_dir, 'Feature.kt'), encoding='utf-8').read()
registered = set(re.findall(r'^\s{8}(\w+),?\s*$', reg, re.M))
files -= {'Feature', 'UaBuilder'}
if registered != files:
    problems.append('Features.ALL 注册与实际文件不一致：仅注册 %s；仅存在文件 %s'
                    % (sorted(registered - files), sorted(files - registered)))

# ---------- 8. Kotlin 符号交叉检查（防「改了调用点却漏写实现」） ----------
kt_files = []
for dirpath, _, files in os.walk(PKG_DIR):
    for f in files:
        if f.endswith('.kt'):
            kt_files.append(os.path.join(dirpath, f))


def _strip_comments(s):
    s = re.sub(r'/\*.*?\*/', '', s, flags=re.S)
    return re.sub(r'//[^\n]*', '', s)


def _block(src, i):
    """i 指向 '{'，返回配对到的块内容"""
    depth = 0
    for j in range(i, len(src)):
        if src[j] == '{':
            depth += 1
        elif src[j] == '}':
            depth -= 1
            if depth == 0:
                return src[i + 1:j]
    return src[i + 1:]


def _top_members(body):
    """块内第一层成员名（fun / val / var / const val / 内嵌 object）"""
    names, depth, i = set(), 0, 0
    while i < len(body):
        c = body[i]
        if c == '{':
            depth += 1
        elif c == '}':
            depth -= 1
        elif depth == 0:
            m = re.match(r'(?:(?:internal|private|public|protected|override|const)\s+)*'
                         r'(?:fun|val|var|object|class)\s+(?:<[^>]+>\s*)?([A-Za-z_]\w*)', body[i:])
            if m:
                names.add(m.group(1))
                i += m.end()
                continue
        i += 1
    return names


symbols = set()   # 本项目所有顶层 object / class 名
members = {}      # object 名 -> 其成员集合
for p in kt_files:
    src = _strip_comments(open(p, encoding='utf-8').read())
    for m in re.finditer(r'^(?:internal\s+|private\s+)?(?:abstract\s+|sealed\s+|open\s+)?'
                         r'(?:object|class|interface|enum class)\s+(\w+)', src, re.M):
        symbols.add(m.group(1))
    for m in re.finditer(r'^(?:internal\s+)?object\s+(\w+)', src, re.M):
        brace = src.find('{', m.end())
        if brace >= 0:
            members.setdefault(m.group(1), set()).update(_top_members(_block(src, brace)))

# 与 android 框架类同名的自有 object：XLog 转发层叫 Log（android.util.Log），
# 宿主/框架的 `Log.d(...)`、`Log.getStackTraceString(...)` 会被误判成「成员缺失」。
# 这类名字按外部类处理，不做成员核对。
EXTERNAL_NAMES = {'Log'}

for p in kt_files:
    src = _strip_comments(open(p, encoding='utf-8').read())
    for name in sorted(members):
        if name in EXTERNAL_NAMES:
            continue
        for m in re.finditer(r'\b' + name + r'\.(\w+)', src):
            if m.group(1) not in members[name]:
                problems.append('符号缺失：%s.%s（%s 引用）——检查是否漏写定义'
                                % (name, m.group(1), where_rel(p)))
    for m in re.finditer(r'^import\s+com\.hupan\.hookbrowser[\w.]*\.(\w+)\s*$', src, re.M):
        if m.group(1) not in symbols and m.group(1) != 'R':
            problems.append('import 无法解析：%s（%s）' % (m.group(1), where_rel(p)))

# ---------- 9. try / catch 两个分支都给同一个 val 赋值（Val cannot be reassigned） ----------
# 反例（1.6.7 真实踩过）：
#     val read: String
#     try { read = "ok" } catch (t: Throwable) { read = "失败" }
# Kotlin 的确定赋值分析**不覆盖 try/catch**，两个分支各赋一次会被判成「重复赋值」，
# 报 `Val cannot be reassigned` —— 只有编译期才暴露，静态走查必须拦下来。
# 正解：让它成为表达式 —— `val parsed: Pair<String, String> = try { … } catch { … }`
def _assigned_names(block):
    return {m.group(1) for m in re.finditer(r'(?m)^\s*([A-Za-z_]\w*)\s*=(?!=)', block)}


def _last_decl_kind(src, name, upto):
    """src[:upto] 范围内 name 最近一次声明是 val 还是 var（没有声明返回 None）"""
    kind = None
    for mm in re.finditer(r'(?m)^\s*(val|var)\s+%s\s*:' % re.escape(name), src[:upto]):
        kind = mm.group(1)
    return kind


for p in kt_files:
    src = _strip_comments(open(p, encoding='utf-8').read())
    for m in re.finditer(r'\btry\s*\{', src):
        brace = src.index('{', m.start())
        try_body = _block(src, brace)
        after = brace + 1 + len(try_body) + 1                 # try 块 '}' 之后
        cm = re.match(r'\s*catch\s*(?:\([^)]*\))?\s*\{', src[after:])
        if not cm:
            continue
        cbrace = src.index('{', after + cm.start())
        common = _assigned_names(try_body) & _assigned_names(_block(src, cbrace))
        for name in sorted(common):
            if _last_decl_kind(src, name, m.start()) == 'val':
                problems.append(
                    'try/catch 两个分支都给 `val %s` 赋值 → Val cannot be reassigned（%s）：'
                    '改成表达式 val x: T = try { … } catch { … }' % (name, where_rel(p)))

# ---------- 10. Manifest 里声明的 Activity 必须有对应源码 ----------
for m in re.finditer(r'android:name="\.([\w.]+)"', man):
    rel = m.group(1).replace('.', os.sep) + '.kt'
    if not os.path.exists(os.path.join(PKG_DIR, rel)):
        problems.append('Manifest 声明了 .%s，但 %s 不存在' % (m.group(1), rel.replace(os.sep, '/')))

# ---------- 11. 规则库 SP 文件名三处一致 ----------
# 模块进程写 adrules.xml、宿主进程读它，中间隔着两个进程。
# 任何一处文件名写错都不会报错，只会「规则导入了但永远不生效」——必须静态钉死。
def _const_str(path, name):
    if not os.path.exists(path):
        return None
    m = re.search(r'const val %s = "([^"]+)"' % name, open(path, encoding='utf-8').read())
    return m.group(1) if m else None


_adb_dir = os.path.join(PKG_DIR, 'adblock')
store_name = _const_str(os.path.join(_adb_dir, 'AdRuleStore.kt'), 'PREFS')
channel_name = _const_str(os.path.join(_adb_dir, 'AdRuleChannel.kt'), 'PREFS')
# 1.10.0：读了 PrefsFileAccess.kt 的第三处拷贝；该文件已随 API 102 迁移删除，
# 规则库组的定义只剩「模块侧写 / 宿主侧读」两处，核对这两处即可。
if store_name is None or channel_name is None:
    problems.append('规则库组名没解析出来：AdRuleStore=%r AdRuleChannel=%r'
                    % (store_name, channel_name))
elif store_name != channel_name:
    problems.append('规则库组名两处不一致：AdRuleStore=%r AdRuleChannel=%r'
                    % (store_name, channel_name))

# ---------- 12. 开关行与「详情表」一一对应 ----------
# 1.9.4 起功能行是自绘的 SwitchRowPreference：「点行看详情」靠 FeatureDetails 的键挂上去
# （PrefsFragment#bindDetails 用 findPreference 找，找不到就静默跳过）。
# 少一条 = 那一行点了没反应；多一条 = 死代码。两者都不会报错，只能静态钉死。
_row_keys = set()
for _m in re.finditer(r'SwitchRowPreference\b(.*?)/>', prefs_txt, re.S):
    _k = re.search(r'app:key="([^"]+)"', _m.group(1))
    if _k:
        _row_keys.add(_k.group(1))
_fd_path = os.path.join(PKG_DIR, 'ui', 'FeatureDetails.kt')
if _row_keys and os.path.exists(_fd_path):
    _fd = open(_fd_path, encoding='utf-8').read()
    _detail_keys = {cfg_map.get(_n, _n) for _n in re.findall(r'Config\.(\w+) to Detail', _fd)}
    if _row_keys != _detail_keys:
        problems.append('功能详情表与开关行不一致：缺详情 %s；多余详情 %s'
                        % (sorted(_row_keys - _detail_keys), sorted(_detail_keys - _row_keys)))

# ---------- 13. 粗粒度语法体检 ----------
# 本机没有 JDK、不跑构建，漏一个括号以前只有到 Android Studio 里才暴露。
# 这里剥掉注释 / 字符串 / 字符字面量后检查 () [] {} 是否平衡（字符串模板里的花括号
# 也在字符串内被一起剥掉，不影响判断），再用 ElementTree 把每个 xml 解一遍。
_PAIRS = {')': '(', ']': '[', '}': '{'}


def _scan_block_comments(src):
    """Kotlin 块注释**可嵌套**：注释正文里出现 `/*` 会再开一层。

    KDoc 表格里写 `` `/path/*` `` 就中招 —— 开了两层只闭一层，编译器报的却是
    文件末尾的 `Unclosed comment`，回不到真凶行。这里把深度算出来并指出起始行。

    注释内部**不再识别** `//` 与引号：KDoc 里写 `https://x/y` 时那个 `//`
    要是被当成行注释，会把同一行后面的 `*/` 一起吃掉，误报未闭合。
    """
    errs, i, n, depth, starts = [], 0, len(src), 0, []
    while i < n:
        c = src[i]
        nxt = src[i + 1] if i + 1 < n else ''
        if depth > 0:
            if c == '/' and nxt == '*':
                depth += 1
                starts.append(src.count('\n', 0, i) + 1)
                i += 2
            elif c == '*' and nxt == '/':
                depth -= 1
                if starts:
                    starts.pop()
                i += 2
            else:
                i += 1
            continue
        if c == '/' and nxt == '/':
            j = src.find('\n', i)
            i = n if j < 0 else j
        elif src.startswith('"""', i):
            j = src.find('"""', i + 3)
            i = n if j < 0 else j + 3
        elif c in '"\'':
            j = i + 1
            while j < n:
                if src[j] == '\\':
                    j += 2
                    continue
                if src[j] == c or src[j] == '\n':
                    j += 1
                    break
                j += 1
            i = j
        elif c == '/' and nxt == '*':
            starts.append(src.count('\n', 0, i) + 1)
            depth += 1
            i += 2
        elif c == '*' and nxt == '/':
            errs.append('第 %d 行有多余的 */' % (src.count('\n', 0, i) + 1))
            i += 2
        else:
            i += 1
    if depth > 0:
        errs.append('第 %d 行起的块注释没闭合（注释文本里可能混进了 /*，嵌套深度剩 %d）'
                    % (starts[0] if starts else -1, depth))
    return errs


for p in kt_files:
    for _e in _scan_block_comments(open(p, encoding='utf-8').read()):
        problems.append('块注释异常：%s %s' % (where_rel(p), _e))


def _strip_code(src):
    out, i, n = [], 0, len(src)
    while i < n:
        c = src[i]
        nxt = src[i + 1] if i + 1 < n else ''
        if c == '/' and nxt == '/':
            j = src.find('\n', i)
            j = n if j < 0 else j
            out.append(' ' * (j - i))
            i = j
        elif c == '/' and nxt == '*':
            j, d = i, 0
            while j < n:
                if src.startswith('/*', j):
                    d += 1
                    j += 2
                elif src.startswith('*/', j):
                    d -= 1
                    j += 2
                    if d == 0:
                        break
                else:
                    j += 1
            out.append(''.join('\n' if ch == '\n' else ' ' for ch in src[i:j]))
            i = j
        elif src.startswith('"""', i):
            j = src.find('"""', i + 3)
            j = n if j < 0 else j + 3
            out.append(''.join('\n' if ch == '\n' else ' ' for ch in src[i:j]))
            i = j
        elif c in '"\'':
            j = i + 1
            while j < n:
                if src[j] == '\\':
                    j += 2
                    continue
                if src[j] == c or src[j] == '\n':
                    j += 1
                    break
                j += 1
            out.append(''.join('\n' if ch == '\n' else ' ' for ch in src[i:j]))
            i = j
        else:
            out.append(c)
            i += 1
    return ''.join(out)


for p in kt_files:
    code = _strip_code(open(p, encoding='utf-8').read())
    stack, bad = [], None
    for ln, line in enumerate(code.split('\n'), 1):
        for ch in line:
            if ch in '([{':
                stack.append((ch, ln))
            elif ch in _PAIRS:
                if not stack or stack[-1][0] != _PAIRS[ch]:
                    bad = '第 %d 行的 %s 不匹配' % (ln, ch)
                    break
                stack.pop()
        if bad:
            break
    if not bad and stack:
        bad = '第 %d 行的 %s 没有闭合' % (stack[-1][1], stack[-1][0])
    if bad:
        problems.append('括号不平衡：%s %s' % (where_rel(p), bad))

for _dp, _, _fs in os.walk(RES):
    for _f in _fs:
        if not _f.endswith('.xml'):
            continue
        _p = os.path.join(_dp, _f)
        try:
            ET.parse(_p)
        except Exception as _e:
            problems.append('XML 解析失败：%s（%s）' % (where_rel(_p), _e))

# ---------- 输出 ----------
print('=== 开关 key 对齐 ===')
print('  Config.kt : %s' % sorted(switch_keys))
print('  prefs.xml : %s' % sorted(xml_keys))
print('\n=== 检查结果 ===')
if problems:
    for p in problems:
        print('  [!] ' + p)
    sys.exit(1)
print('  全部通过')
