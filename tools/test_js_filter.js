/**
 * JS_FILTER 回归测试（无需 JDK / 无需真机）
 *
 * 作用：从 SearchSugFeature.kt 里抠出 `JS_FILTER` 那段 JS，塞进一个最小 DOM mock 里跑。
 * DOM 结构照抄真机日志里 sug.browser.miui.com 的实际路径：
 *
 *   #app.flex > .sug-search > div > .topclick-wrap.card-list > .card.debug
 *            > ul.card__bd > li.top-click > a.top-click__sug.card
 *            > .top-click__info > .top-click__subtitle > .second-title > span.ads-tag
 *
 * 用法：node tools/test_js_filter.js      # 退出码 0 = 通过
 *
 * ⚠ 关于「级联」这条断言（1.6.2 加的）：mock 的 getBoundingClientRect 会随
 * `style.display==='none'` 归零，和浏览器一致。1.6.1 用「最近一个高度 ≥40px 的祖先」
 * 定位卡片，隐藏后高度归零 → 下一轮上爬一层 → 最终把 #app 整页藏掉（真机事故：
 * 广告和搜索联想一起消失）。断言 ④ 专门钉死这个回归。
 */
const fs = require('fs');
const path = require('path');

const KT = process.env.HB_KT || path.join(__dirname, '..', 'app', 'src', 'main', 'java', 'com', 'hupan',
  'hookbrowser', 'features', 'SearchSugFeature.kt');

// ---- 1. 从 Kotlin 源码里抠出 JS_FILTER 的原始字符串 ----
const src = fs.readFileSync(KT, 'utf8');
const marker = 'private const val JS_FILTER = """';
const i = src.indexOf(marker);
if (i < 0) fail('在 SearchSugFeature.kt 里找不到 JS_FILTER');
const start = i + marker.length;
const end = src.indexOf('"""', start);
if (end < 0) fail('JS_FILTER 的原始字符串没有闭合 """');
const js = src.slice(start, end);

// ---- 2. 最小 DOM mock ----
// opts.allAds：ul 里**只放广告卡**（场景 E 用）—— 复现真机「卡片全被藏、只剩空壳」
function buildDom(withAd, withBtn, opts) {
  const allAds = !!(opts && opts.allAds);
  const all = [];

  // 简易选择器：只支持 'tag', '.cls', 'tag.cls'（逗号在 closest 里拆）
  const parse = (s) => {
    const p = s.split('.');
    return { tag: p[0] ? p[0].toUpperCase() : '', cls: p.slice(1) };
  };
  const matches = (e, s) => {
    const p = parse(s);
    if (p.tag && e.tagName !== p.tag) return false;
    const ec = String(e.className || '').split(/\s+/);
    return p.cls.every((c) => ec.indexOf(c) >= 0);
  };
  const closestOf = (e, sel) => {
    const sels = sel.split(',').map((x) => x.trim());
    let n = e;
    while (n && n.nodeType === 1) {
      for (const s of sels) if (matches(n, s)) return n;
      n = n.parentNode;
    }
    return null;
  };

  const el = (tag, cls, h, id) => {
    const e = {
      nodeType: 1, tagName: tag.toUpperCase(), id: id || '', className: cls || '',
      childNodes: [], style: {}, attrs: {},
      // 高度模拟真实布局：① 自身或祖先 display:none → 不可见，高度 0；
      // ② 子元素全被隐藏 → 自身塌缩。级联 bug（隐藏祖先→继续上爬）靠这两条复现。
      getBoundingClientRect() {
        let p = this;
        while (p) {
          if (p.style && p.style.display === 'none') return { height: 0 };
          p = p.parentNode;
        }
        const kids = this.childNodes.filter((k) => k.nodeType === 1);
        if (kids.length && kids.every((k) => k.getBoundingClientRect().height === 0)) {
          return { height: 0 };
        }
        return { height: h || 0 };
      },
      setAttribute(k, v) { this.attrs[k] = v; },
      getAttribute(k) { return Object.prototype.hasOwnProperty.call(this.attrs, k) ? this.attrs[k] : null; },
      removeAttribute(k) { delete this.attrs[k]; },
      get textContent() { return this.childNodes.map((c) => c.nodeValue || '').join(''); },
      set textContent(v) { this.childNodes = [{ nodeType: 3, nodeValue: v }]; },
      closest(sel) { return closestOf(this, sel); },
      parentNode: null,
    };
    all.push(e);
    return e;
  };
  const text = (e, t) => e.childNodes.push({ nodeType: 3, nodeValue: t });
  const append = (p, c) => { p.childNodes.push(c); c.parentNode = p; };

  // 骨架（照日志，父先于子创建 → all 即文档序）
  const body = el('body', '', 800);
  const app = el('div', 'flex', 780, 'app');
  const sugSearch = el('div', 'sug-search', 780);
  const wrapOuter = el('div', '', 780);
  const cardList = el('div', 'topclick-wrap card-list card-list_line', 400);
  const cardBox = el('div', 'card debug', 400);
  const ul = el('ul', 'card__bd card__bd_top-common card__bd_topclick', 380);
  append(body, app); append(app, sugSearch); append(sugSearch, wrapOuter);
  append(wrapOuter, cardList); append(cardList, cardBox); append(cardBox, ul);

  // 广告卡（1.6.1 真机上就是它把 ul / #app 一起藏了）
  // allAds 场景再补一张：让 ul 内**一张正常卡都没有**，才谈得上空壳塌缩
  let adLi = null, adA = null, adsTag = null, ad2Li = null;
  if (withAd) {
    const mkAd = () => {
      const li = el('li', 'top-click debug top-click_square', 80);
      const a = el('a', 'top-click__sug card', 76);
      const info = el('div', 'top-click__info flex flex-v', 70);
      const subtitle = el('div', 'top-click__subtitle', 24);
      const secondTitle = el('div', 'second-title', 18);
      const tag = el('span', 'ads-tag', 14);
      text(tag, '广告');
      append(secondTitle, tag); append(subtitle, secondTitle); append(info, subtitle);
      append(a, info); append(li, a); append(ul, li);
      return { li, a, tag };
    };
    const first = mkAd();
    adLi = first.li; adA = first.a; adsTag = first.tag;
    if (allAds) ad2Li = mkAd().li;
  }

  // 正常联想项：**同结构、同 class，只少了 ads-tag** —— 用来验证不会被误杀
  // （allAds 场景不建它们：那里要的是「一张正常卡都没有」，才谈得上空壳塌缩）
  let okLi = null, okA = null, okWord = null, wordLi = null, wordSpan = null;
  if (!allAds) {
    okLi = el('li', 'top-click debug top-click_square', 60);
    okA = el('a', 'top-click__sug card', 56);
    const okInfo = el('div', 'top-click__info flex flex-v', 50);
    okWord = el('span', 'word', 24);
    text(okWord, '百度一下');
    append(okInfo, okWord); append(okA, okInfo); append(okLi, okA); append(ul, okLi);

    // 纯词联想项，词本身就是「安装」—— 判据 ③ 的负例：整卡文本 == 该词，**不许**当成按钮
    wordLi = el('li', 'top-click debug top-click_square', 60);
    const wordA = el('a', 'top-click__sug card', 56);
    const wordInfo = el('div', 'top-click__info flex flex-v', 50);
    wordSpan = el('span', 'word', 24);
    text(wordSpan, '安装');
    append(wordInfo, wordSpan); append(wordA, wordInfo); append(wordLi, wordA); append(ul, wordLi);
  }

  // 无 ads-tag、但有「安装」按钮的推荐卡（1.6.4 的目标：真机日志里漏掉的就是它）
  let btnLi = null, btnA = null, btnTag = null;
  if (withBtn) {
    btnLi = el('li', 'top-click debug top-click_square', 80);
    btnA = el('a', 'top-click__sug card', 76);
    const bInfo = el('div', 'top-click__info flex flex-v', 70);
    const bSub = el('div', 'top-click__subtitle', 24);
    const bName = el('span', 'name', 18);
    text(bName, 'Cellular-Z小工具应用商店版');
    const bBtn = el('span', 'top-click__btn install', 16);
    text(bBtn, '安装');
    append(bSub, bName); append(bSub, bBtn); append(bInfo, bSub);
    append(btnA, bInfo); append(btnLi, btnA); append(ul, btnLi);
    btnTag = bBtn;
  }

  // 卡片容器（div.card）底部那行「查看更多」小字入口 —— 真机残留的就是它，
  // 卡片被藏后它孤零零留着（或变成一条细长空白），塌缩时应当一起消失
  const moreWrap = el('div', 'card__more', 30);
  const moreSpan = el('span', 'more', 18);
  text(moreSpan, '查看更多');
  append(moreWrap, moreSpan); append(cardBox, moreWrap);

  const protectedNodes = { body, app, cardList, cardBox, ul };

  // 场景 D 用：运行中动态往列表里插一张新卡，模拟 Vue 异步渲染
  const addCard = (kind) => {
    const li = el('li', 'top-click debug top-click_square', 80);
    const a = el('a', 'top-click__sug card', 76);
    const info = el('div', 'top-click__info flex flex-v', 70);
    const sub = el('div', 'top-click__subtitle', 24);
    if (kind === 'ad') {
      const tag = el('span', 'ads-tag', 14);
      text(tag, '广告');
      append(sub, tag);
    } else if (kind === 'btn') {
      const nm = el('span', 'name', 18);
      text(nm, '某某应用商店版本号:1');
      const b = el('span', 'top-click__btn install', 16);
      text(b, '安装');
      append(sub, nm); append(sub, b);
    } else {
      const w = el('span', 'word', 24);
      text(w, '百度一下');
      append(sub, w);
    }
    append(info, sub); append(a, info); append(li, a); append(ul, li);
    return li;
  };

  return { all, body, app, ul, cardList, cardBox, addCard, adLi, adA, adsTag, ad2Li, moreWrap, moreSpan, okLi, okA, okWord, wordLi, wordSpan, btnLi, btnA, btnTag, protectedNodes, withAd, withBtn };
}

// 跑一次 JS_FILTER，返回 {obj, dom, win}
function run(withAd, withBtn, opts) {
  return runDom(buildDom(withAd, withBtn, opts));
}

// 在给定的 DOM 上跑一次（场景 E 用的是另一份「全是广告卡」的 DOM）
function runDom(dom) {
  const win = { innerHeight: 800 };          // 脚本会把 __hbApply 挂在它上面，留着给断言用
  const created = [];                        // 记录脚本创建的 <style>，用来断言 CSS 规则
  // 假 MutationObserver：只捕获回调，由测试手动喂 records —— 这样才能断言「同步、不 debounce」
  const observers = [];
  function FakeObserver(cb) { this.cb = cb; observers.push(this); }
  FakeObserver.prototype.observe = function () {};
  FakeObserver.prototype.disconnect = function () {};
  global.MutationObserver = FakeObserver;

  const head = { childNodes: [], appendChild(c) { this.childNodes.push(c); c.parentNode = this; } };
  global.document = {
    body: dom.body, documentElement: dom.body, head,
    getElementsByTagName: () => dom.all,
    getElementById: (id) => created.find((e) => e.id === id) || null,
    createElement: (tag) => {
      const e = {
        nodeType: 1, tagName: tag.toUpperCase(), id: '', className: '', type: '',
        childNodes: [], style: {},
        appendChild(c) { this.childNodes.push(c); c.parentNode = this; },
        setAttribute() {}, getAttribute() { return null; },
        set textContent(v) { this.childNodes.push({ nodeType: 3, nodeValue: v }); },
      };
      created.push(e);
      return e;
    },
    createTextNode: (t) => ({ nodeType: 3, nodeValue: t }),
  };
  global.window = win;
  win.CSS = { supports: (s) => /:has\(/.test(s) };   // 模拟 Chromium ≥105：支持 :has()
  win.MutationObserver = FakeObserver;               // 脚本读的是 window.MutationObserver
  global.location = { href: 'https://sug.browser.miui.com/' };
  // 注意：document/window 故意不还原 —— 后面还要手动调 window.__hbApply() 模拟 observer 多轮
  let result;
  try {
    result = eval(js);
  } catch (e) {
    fail('执行 JS_FILTER 抛异常：' + e);
  }
  let obj;
  try {
    obj = JSON.parse(result);
  } catch (e) {
    fail('JS_FILTER 没返回合法 JSON：' + result);
  }
  return { obj, dom, result, win, created, observers };
}

const problems = [];
const note = (m) => console.log('  ' + m);

// ---- 3. 场景 A：页面里有广告卡 ----
{
  const { obj, dom, win, result } = run(true);
  if (obj.err) problems.push('脚本内部报错：' + obj.err);

  // ① 广告卡被隐藏
  if (dom.adLi.style.display !== 'none') {
    problems.push('广告卡片没被隐藏（li display=' + dom.adLi.style.display + '）');
  }
  if (obj.hidden !== 1) problems.push('hidden 期望 1，实际 ' + obj.hidden);
  if (obj.marks !== 1) problems.push('marks 期望 1，实际 ' + obj.marks);

  // ② 正常联想项不受影响
  if (dom.okLi.style.display) problems.push('正常联想卡被误隐藏（li display=' + dom.okLi.style.display + '）');
  if (dom.okA.style.display) problems.push('正常联想 a 被误隐藏');
  if (dom.okWord.style.display) problems.push('正常联想词被误隐藏');

  // ②' 词本身就是「安装」的联想项 —— 不许被按钮判据误杀
  if (dom.wordLi.style.display) problems.push('「安装」这个纯词联想项被误杀（full===t 护栏失效）');
  if (dom.wordSpan.style.display) problems.push('「安装」联想词的 span 被误杀');
  if (obj.btn !== 0) problems.push('无按钮卡场景 btn 期望 0，实际 ' + obj.btn);

  // ③ 容器一个都不许动
  for (const [name, node] of Object.entries(dom.protectedNodes)) {
    if (node.style.display === 'none') problems.push('容器 ' + name + ' 被隐藏了（级联）');
  }

  // ④ 反复执行（模拟 MutationObserver 多轮）仍然不许级联
  const apply = win.__hbApply;
  if (typeof apply === 'function') {
    for (let n = 0; n < 5; n++) apply();
    for (const [name, node] of Object.entries(dom.protectedNodes)) {
      if (node.style.display === 'none') problems.push('重复执行后容器 ' + name + ' 被隐藏（级联回归）');
    }
    if (dom.okLi.style.display) problems.push('重复执行后正常联想卡被隐藏');
  } else {
    problems.push('脚本没有暴露 window.__hbApply，无法验证重复执行');
  }

  note('场景 A（有广告）：' + result);
}

// ---- 4. 场景 B：页面里没有广告标记 ----
{
  const { obj, dom, result } = run(false);
  if (obj.err) problems.push('无广告场景脚本报错：' + obj.err);
  if (obj.hidden !== 0) problems.push('无广告场景 hidden 期望 0，实际 ' + obj.hidden);
  if (dom.okLi.style.display) problems.push('无广告场景把正常项隐藏了');
  note('场景 B（无广告）：' + result);
}

// ---- 5. 场景 C：无 ads-tag 但有安装按钮的推荐卡（1.6.4 的修复目标）----
{
  const { obj, dom, win, result } = run(false, true);
  if (obj.err) problems.push('按钮卡场景脚本报错：' + obj.err);

  // ① 按钮卡被隐藏
  if (dom.btnLi.style.display !== 'none') {
    problems.push('带安装按钮的推荐卡没被隐藏（li display=' + dom.btnLi.style.display + '）');
  }
  if (obj.btn !== 1) problems.push('btn 期望 1，实际 ' + obj.btn);
  if (obj.hidden !== 0) problems.push('按钮卡场景 hidden 期望 0（没有 ads-tag），实际 ' + obj.hidden);
  if (!Array.isArray(obj.bhits) || obj.bhits.length !== 1) {
    problems.push('bhits 期望 1 条，实际 ' + JSON.stringify(obj.bhits));
  }

  // ② 同结构、无按钮的联想项一个都不许动
  if (dom.okLi.style.display) problems.push('按钮卡场景把正常联想卡误杀了');
  if (dom.wordLi.style.display) problems.push('按钮卡场景把「安装」纯词联想项误杀了');

  // ③ 容器不许动，且反复执行不许级联
  for (const [name, node] of Object.entries(dom.protectedNodes)) {
    if (node.style.display === 'none') problems.push('按钮卡场景容器 ' + name + ' 被隐藏（级联）');
  }
  const apply = win.__hbApply;
  if (typeof apply === 'function') {
    for (let n = 0; n < 5; n++) apply();
    for (const [name, node] of Object.entries(dom.protectedNodes)) {
      if (node.style.display === 'none') problems.push('按钮卡场景重复执行后容器 ' + name + ' 被隐藏');
    }
    if (dom.okLi.style.display) problems.push('按钮卡场景重复执行后联想卡被隐藏');
  } else {
    problems.push('按钮卡场景脚本没有暴露 window.__hbApply');
  }

  note('场景 C（按钮卡、无广告标）：' + result);
}

// ---- 6. 场景 D：1.6.5「不闪一下」——CSS 前置规则 + observer 同步隐藏 ----
{
  const { obj, dom, created, observers } = run(true, true);
  if (obj.err) problems.push('CSS/同步场景脚本报错：' + obj.err);

  // ① CSS 规则必须注入进 DOM，且用 !important 覆盖页面样式
  if (obj.css !== 1) problems.push('css 期望 1（已注入），实际 ' + obj.css);
  const style = created.find((e) => e.id === 'hb-css');
  if (!style) {
    problems.push('没有往 DOM 里插入 <style id="hb-css"> —— 卡片会先渲染再被隐藏（闪一下）');
  } else {
    const css = style.childNodes.map((c) => c.nodeValue || '').join('');
    if (!/ads-tag/.test(css)) problems.push('CSS 规则里没有 ads-tag 选择器：' + css);
    if (!/display:\s*none\s*!important/.test(css)) problems.push('CSS 规则没用 !important 覆盖：' + css);
    if (!/:has\(/.test(css)) problems.push('CSS 规则没有用 :has() 定位卡片：' + css);
  }

  // ② observer 必须**同步**隐藏新增卡片（回调返回时就已经 display:none）
  const ob = observers[0];
  if (!ob) {
    problems.push('没有注册 MutationObserver，异步渲染出来的卡片不会被处理');
  } else {
    const newAd = dom.addCard('ad');
    ob.cb([{ addedNodes: [newAd] }]);
    if (newAd.style.display !== 'none') {
      problems.push('observer 回调返回后新增广告卡仍可见 —— 回调里还有 debounce/延迟，真机就会「闪一下」');
    }
    // ③ 同步处理也不能误伤同一批插入的正常卡
    const newOk = dom.addCard('ok');
    ob.cb([{ addedNodes: [newOk] }]);
    if (newOk.style.display) problems.push('同步处理把同批插入的正常联想卡误杀了');
    // ④ 容器依然不许动
    for (const [name, node] of Object.entries(dom.protectedNodes)) {
      if (node.style.display === 'none') problems.push('同步路径把容器 ' + name + ' 隐藏了（级联）');
    }
  }

  // ⑤ 不支持 :has() 时不能崩，只是退化成 0
  {
    const noHas = buildDom(true, false);
    const win2 = { innerHeight: 800, CSS: { supports: () => false } };
    global.document = {
      body: noHas.body, documentElement: noHas.body, head: { appendChild() {} },
      getElementsByTagName: () => noHas.all,
      getElementById: () => null,
      createElement: () => ({ nodeType: 1, childNodes: [], style: {}, appendChild() {} }),
      createTextNode: (t) => ({ nodeType: 3, nodeValue: t }),
    };
    global.window = win2;
    global.MutationObserver = undefined;
    let r2;
    try {
      r2 = eval(js);
    } catch (e) {
      problems.push('不支持 :has() 的环境下脚本抛异常：' + e);
    }
    if (r2) {
      const o2 = JSON.parse(r2);
      if (o2.err) problems.push('不支持 :has() 时脚本内部报错：' + o2.err);
      if (o2.css !== 0) problems.push('不支持 :has() 时 css 期望 0，实际 ' + o2.css);
      if (o2.hidden !== 1) problems.push('不支持 :has() 时仍要靠 JS 隐藏（期望 hidden=1），实际 ' + o2.hidden);
      if (noHas.adLi.style.display !== 'none') problems.push('不支持 :has() 时广告卡没被 JS 兜底隐藏');
    }
  }

  note('场景 D（CSS + 同步 observer）：' + JSON.stringify({ css: obj.css, ob: obj.ob, hidden: obj.hidden, btn: obj.btn }));
}

// ---- 7. 场景 E：1.6.6「空壳塌缩」——卡片全被藏后，容器和「查看更多」一起消失 ----
{
  const dom = buildDom(true, true, { allAds: true });
  const { obj, win } = runDom(dom);
  if (obj.err) problems.push('塌缩场景脚本报错：' + obj.err);

  // ① 三张卡全被藏（2 张带 ads-tag + 1 张带安装按钮）
  if (obj.hidden !== 2) problems.push('塌缩场景 hidden 期望 2，实际 ' + obj.hidden);
  if (obj.btn !== 1) problems.push('塌缩场景 btn 期望 1，实际 ' + obj.btn);

  // ② 空壳塌缩：ul.card__bd / div.card / div.topclick-wrap 都该没了
  if (!(obj.fold >= 1)) {
    problems.push('空壳没被塌缩（fold=' + obj.fold + '）—— 真机上就是那条细长空白或「查看更多」');
  }
  if (dom.ul.style.display !== 'none') problems.push('ul.card__bd 没塌缩');
  if (dom.cardBox.style.display !== 'none') problems.push('div.card 没塌缩（「查看更多」就挂在这一层）');
  if (dom.cardList.style.display !== 'none') problems.push('div.topclick-wrap 没塌缩');

  // ③ 「查看更多」随父容器一起不可见（不是被单独 hide）
  if (dom.moreSpan.style.display === 'none') problems.push('直接改了「查看更多」元素本身，应该靠父容器隐藏');
  if (!/v=0/.test((obj.more || []).join('|'))) {
    problems.push('返回串里没有「查看更多已不可见」的读数：' + JSON.stringify(obj.more));
  }

  // ④ 塌缩边界：根容器与 sug-search 一个都不许动
  if (dom.body.style.display === 'none') problems.push('body 被塌缩了');
  if (dom.app.style.display === 'none') problems.push('#app 被塌缩了');
  const sugSearch = dom.all.find((e) => e.className === 'sug-search');
  if (sugSearch && sugSearch.style.display === 'none') problems.push('.sug-search 被塌缩了');

  // ⑤ 复活：容器里重新出现正常卡片 → 塌缩必须立刻撤销，否则联想词永远看不见
  const apply = win.__hbApply;
  if (typeof apply !== 'function') {
    problems.push('塌缩场景脚本没有暴露 window.__hbApply');
  } else {
    const okCard = dom.addCard('ok');
    const r = apply();
    if (r[2] !== 0) problems.push('有正常卡之后仍报告新塌缩 ' + r[2] + ' 个');
    if (dom.cardBox.style.display === 'none') problems.push('插入正常卡后 div.card 仍隐藏（联想词会看不见）');
    if (dom.cardList.style.display === 'none') problems.push('插入正常卡后 div.topclick-wrap 仍隐藏');
    if (dom.ul.style.display === 'none') problems.push('插入正常卡后 ul.card__bd 仍隐藏');
    if (okCard.style.display === 'none') problems.push('新增的正常联想卡被隐藏了');
    if (dom.cardBox.getAttribute('data-hb-empty')) problems.push('复活后 data-hb-empty 没被清掉');
  }

  // ⑥ 反复执行不许抖动、不许级联
  for (let n = 0; n < 5; n++) apply();
  if (dom.app.style.display === 'none' || dom.body.style.display === 'none') {
    problems.push('反复执行后根容器被隐藏（级联回归）');
  }
  if (dom.cardBox.style.display === 'none') problems.push('复活后再执行又把容器藏了（抖动）');

  note('场景 E（全是广告卡 → 空壳塌缩）：' + JSON.stringify({
    hidden: obj.hidden, btn: obj.btn, fold: obj.fold, more: obj.more,
    afterRevive: { fold: apply()[2], cardBox: dom.cardBox.style.display || '(显示)' },
  }));
}

if (problems.length) {
  console.error('JS_FILTER 测试失败：');
  problems.forEach((p) => console.error('  - ' + p));
  process.exit(1);
}
console.log('JS_FILTER 测试通过（广告卡被隐藏、正常联想与容器均未受影响、重复执行不级联）');
process.exit(0);

function fail(msg) {
  console.error('JS_FILTER 测试失败：' + msg);
  process.exit(1);
}
