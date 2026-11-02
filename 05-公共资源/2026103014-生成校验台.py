# -*- coding: utf-8 -*-
"""
生成浏览器校验台：把全部报告分别放进 1280px 与 390px 的 iframe 里，
在真实浏览器中读取每份文档的 scrollWidth / clientWidth / 溢出元素数，
输出到 <pre id="RESULT">，供 --dump-dom 或 query 读取。

用法：python 2026103014-生成校验台.py
输出：_校验台.html（项目根目录）
"""
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BASE = "http://127.0.0.1:8899/"

FILES = [
    "01-\u9700\u6c42\u8fed\u4ee3/02-\u9700\u6c42\u6587\u6863/01-\u4ea7\u54c1\u9700\u6c42\u8bf4\u660e\u4e66.html",
    "01-\u9700\u6c42\u8fed\u4ee3/03-\u8bbe\u8ba1\u89c4\u8303/01-\u4ea7\u54c1\u8bbe\u8ba1\u89c4\u8303.html",
    "02-\u4ee3\u7801\u5f00\u53d1/01-\u8bbe\u8ba1\u6587\u6863/01-\u7cfb\u7edf\u8bbe\u8ba1\u8bf4\u660e\u4e66.html",
    "02-\u4ee3\u7801\u5f00\u53d1/02-\u6570\u636e\u5e93/01-\u6570\u636e\u5e93\u8bbe\u8ba1\u8bf4\u660e\u4e66.html",
    "02-\u4ee3\u7801\u5f00\u53d1/03-\u63a5\u53e3\u6587\u6863/01-\u63a5\u53e3\u8bbe\u8ba1\u8bf4\u660e\u4e66.html",
    "02-\u4ee3\u7801\u5f00\u53d1/04-\u524d\u7aef\u9875\u9762/01-\u524d\u7aef\u9875\u9762\u6e05\u5355.html",
    "03-\u6d4b\u8bd5\u9a8c\u8bc1/01-\u6d4b\u8bd5\u6587\u6863/01-\u6d4b\u8bd5\u5927\u7eb2.html",
    "03-\u6d4b\u8bd5\u9a8c\u8bc1/02-\u6d4b\u8bd5\u7528\u4f8b/01-\u6d4b\u8bd5\u7528\u4f8b.html",
    "04-\u8fd0\u8425\u90e8\u7f72/01-\u90e8\u7f72\u6587\u6863/01-\u90e8\u7f72\u65b9\u6848.html",
    "04-\u8fd0\u8425\u90e8\u7f72/02-\u8fd0\u7ef4\u6587\u6863/01-\u8fd0\u7ef4\u624b\u518c.html",
]

TPL = """<!DOCTYPE html>
<html lang="zh-CN"><head><meta charset="utf-8">
<title>jeecg-study 交付物校验台</title>
<style>
 body{font:13px/1.6 "Microsoft YaHei",sans-serif;margin:12px;background:#fafafa;color:#15171c}
 pre{background:#15171c;color:#e6e8ec;padding:14px;border-radius:10px;font:12px/1.7 Consolas,monospace;white-space:pre-wrap}
 .row{display:flex;gap:16px;align-items:flex-start;margin-bottom:18px;flex-wrap:wrap}
 .framebox{border:1px solid #ccc;border-radius:8px;padding:6px;background:#fff}
 .cap{font:600 12px/1.6 Consolas,monospace;color:#555;margin-bottom:4px}
 iframe{border:0;display:block}
 .m{width:390px;height:700px}
 .d{width:1280px;height:700px}
</style></head>
<body>
<h3>jeecg-study 交付物浏览器校验台</h3>
<div id="frames"></div>
<pre id="RESULT">running...</pre>
<script>
var FILES = %(files)s;
var BASE = "%(base)s";
var host = document.getElementById('frames');
var pending = 0, out = [];

FILES.forEach(function(f){
  ['d','m'].forEach(function(kind){
    var box = document.createElement('div'); box.className='framebox';
    var cap = document.createElement('div'); cap.className='cap';
    cap.textContent = (kind==='d'?'1280 ':'390 ') + f.split('/').pop();
    var ifr = document.createElement('iframe');
    ifr.className = kind;
    ifr.src = BASE + encodeURI(f);
    box.appendChild(cap); box.appendChild(ifr); host.appendChild(box);
  });
});

// 逐个 iframe 读取断言
var frames = Array.prototype.slice.call(document.querySelectorAll('iframe'));
var results = [];
frames.forEach(function(ifr){
  results.push(new Promise(function(resolve){
    var done = false;
    function probe(){
      if (done) return; done = true;
      var tag = (ifr.className==='d'?'D':'M') + ' ' + decodeURIComponent(ifr.src.split('/').pop());
      try {
        var w = ifr.contentWindow, d = ifr.contentDocument;
        var de = d.documentElement;
        var sw = de.scrollWidth, cw = de.clientWidth;
        // 找出真正溢出视口的元素
        var over = [];
        var all = d.body.querySelectorAll('*');
        for (var i=0;i<all.length;i++){
          var r = all[i].getBoundingClientRect();
          if (r.width > 0 && r.right > cw + 1.5) {
            over.push(all[i].tagName + (all[i].className ? '.'+String(all[i].className).split(' ')[0] : '')
                       + ' right=' + Math.round(r.right));
            if (over.length >= 5) break;
          }
        }
        // 表格卡片化是否生效（390 视口）
        var tblCard = 'n/a';
        if (ifr.className === 'm') {
          var t = d.querySelector('table');
          if (t) {
            var cs = w.getComputedStyle(t);
            tblCard = 'display=' + cs.display + ' thead=' + (d.querySelector('thead') ? w.getComputedStyle(d.querySelector('thead')).display : 'none');
          }
          // 移动端导航元素是否存在
          var fab = d.getElementById('fab'), sb = d.querySelector('.sidebar');
          tblCard += ' fab=' + (fab ? 'yes' : 'no') + ' sidebarPos=' + (sb ? w.getComputedStyle(sb).position : 'n/a')
                   + ' topbar=' + (d.querySelector('.m-topbar') ? w.getComputedStyle(d.querySelector('.m-topbar')).display : 'none');
        }
        resolve(tag + ' | scrollW=' + sw + ' clientW=' + cw + ' overflowX=' + (sw > cw + 1)
                + ' overCount=' + over.length + (over.length ? ' [' + over.join(' ; ') + ']' : '')
                + ' | ' + tblCard);
      } catch (e) {
        resolve(tag + ' | ERROR ' + e.message);
      }
    }
    ifr.onload = function(){
      setTimeout(function(){
        try { ifr.contentWindow.dispatchEvent(new Event('resize')); } catch(e){}
        setTimeout(probe, 120);
      }, 200);
    };
    setTimeout(probe, 4000); // 兜底
  }));
});

Promise.all(results).then(function(list){
  var bad = list.filter(function(x){ return /overflowX=true|ERROR/.test(x); });
  document.getElementById('RESULT').textContent =
    '总检查 ' + list.length + ' 项；异常 ' + bad.length + ' 项\\n\\n' + list.join('\\n');
  document.title = 'DONE bad=' + bad.length;
});
</script>
<script>
/* ============ 移动端交互状态迁移探针 ============ */
(function(){
  var PROBE = __PROBE_FILE__;
  var out = [];
  function p(k,v){ out.push(k+'='+v); }
  var ifr = document.createElement('iframe');
  ifr.className='m'; ifr.style.width='390px'; ifr.style.height='700px';
  // 必须固定在视口内：视口外的 iframe 不会被 Chrome 合成，scrollTo 会失效
  ifr.style.position='fixed'; ifr.style.top='0'; ifr.style.left='0';
  ifr.style.zIndex='99999'; ifr.style.border='1px solid #f0f';
  ifr.src = BASE + encodeURI(PROBE);
  document.getElementById('frames').appendChild(ifr);

  var ran = false;
  function run(){
    if (ran) return; ran = true;
    var sleep = function(ms){ return new Promise(function(r){ setTimeout(r, ms); }); };

    (async function(){
      try{
        var w = ifr.contentWindow, d = ifr.contentDocument;
        var de = d.documentElement;

        // 1) 初始：FAB 不应 show
        var fab = d.getElementById('fab');
        var sb  = d.querySelector('.sidebar');
        var scrim = d.getElementById('scrim');
        p('initial_fab_show', fab.classList.contains('show'));
        p('initial_sidebar_open', sb.classList.contains('open'));
        p('initial_scrim_show', scrim.classList.contains('show'));
        p('sidebar_position', w.getComputedStyle(sb).position);
        p('topbar_display', w.getComputedStyle(d.querySelector('.m-topbar')).display);
        p('matchMedia_mobile', w.matchMedia('(max-width:1000px)').matches);
        p('innerWidth', w.innerWidth);
        p('scrollBehavior', w.getComputedStyle(de).scrollBehavior);

        // 2) 覆盖 scrollTo 记录目标值（直接证明 handler 传了什么）
        var origScrollTo = w.scrollTo.bind(w);
        var calls = [];
        w.scrollTo = function(a, b){
          calls.push((typeof a === 'object' && a !== null) ? ('top=' + a.top) : ('args=' + a));
          return origScrollTo(a, b);
        };

        // 3) 滚动 400px：先关掉 CSS smooth，再设 scrollTop（避免动画读数）
        de.style.scrollBehavior = 'auto';
        de.scrollTop = 400;
        w.dispatchEvent(new Event('scroll'));
        await sleep(500);
        p('afterScroll_scrollY', w.scrollY);
        p('afterScroll_deScrollTop', de.scrollTop);
        p('afterScroll_clientH', de.clientHeight + '/' + de.scrollHeight);
        p('afterScroll_fab_show', fab.classList.contains('show'));
        p('afterScroll_fab_pe', w.getComputedStyle(fab).pointerEvents);

        // 4) 点击 FAB → 面板展开 + 遮罩
        fab.click();
        await sleep(300);
        p('afterClick_sidebar_open', sb.classList.contains('open'));
        p('afterClick_scrim_show', scrim.classList.contains('show'));
        p('afterClick_sidebar_pointerEvents', w.getComputedStyle(sb).pointerEvents);

        // 5) 点遮罩关闭
        scrim.click();
        await sleep(300);
        p('afterScrim_sidebar_open', sb.classList.contains('open'));
        p('afterScrim_scrim_show', scrim.classList.contains('show'));

        // 6) 点「↑ 页面顶部」→ 记录 scrollTo 目标
        var links = d.querySelectorAll('.nav-link');
        var topLink = null, secLink = null, bottomLink = null;
        for (var i=0;i<links.length;i++){
          var h = links[i].getAttribute('href');
          if (h === '#top' && !topLink) topLink = links[i];
          if (h === '#bottom' && !bottomLink) bottomLink = links[i];
          if (h === '#base' && !secLink) secLink = links[i];
        }
        calls = [];
        topLink.click();
        await sleep(900);
        p('topLink_exists', !!topLink);
        p('topScrollCalls', calls.join(' | ') || 'NONE');
        p('afterTopScroll_scrollY', w.scrollY);
        p('afterTop_hash', w.location.hash);

        // 7) 点「↓ 页面底部」
        calls = [];
        bottomLink.click();
        await sleep(900);
        p('bottomScrollCalls', calls.join(' | ') || 'NONE');
        p('bottomExpected', 'scrollHeight=' + de.scrollHeight);

        // 8) 点章节链接 → 定位到该节
        calls = [];
        secLink.click();
        await sleep(1200);
        p('secScrollCalls', calls.join(' | ') || 'NONE');
        p('afterSecLink_hash', w.location.hash);
        var sec = d.getElementById('base');
        p('afterSecLink_secTop', sec ? Math.round(sec.getBoundingClientRect().top) : 'n/a');
        p('activeNav', (d.querySelector('.nav-link.active')||{}).textContent);

        // 9) 表格卡片化：检查 data-label 是否补齐
        var td = d.querySelector('tbody td:not(:first-child)');
        p('td_data_label', td ? td.getAttribute('data-label') : 'none');
        w.scrollTo = origScrollTo;
      }catch(e){ p('ERROR', e.message); }

      var pre = document.createElement('pre');
      pre.id = 'PROBE';
      pre.textContent = out.join('\\n');
      document.body.appendChild(pre);
      document.title = 'DONE2';
    })();
  }

  ifr.onload = function(){ setTimeout(run, 600); };
  setTimeout(run, 5000);
})();
</script>
</body></html>
"""


def main():
    # 产物是「浏览器实测用的校验台」，属**产物**不是工具 ⇒ 落在测试阶段目录，
    # 不混在 05-公共资源/（那里只放生成器与校验脚本本身）。
    out = os.path.join(ROOT, "03-测试验证", "04-自动化测试", "2026103014-校验台.html")
    os.makedirs(os.path.dirname(out), exist_ok=True)
    html = TPL % {"files": json.dumps(FILES, ensure_ascii=False), "base": BASE}
    html = html.replace("__PROBE_FILE__", json.dumps(FILES[0], ensure_ascii=False))
    with open(out, "w", encoding="utf-8", newline="\n") as f:
        f.write(html)
    print("OK %s (%d bytes)" % (out, len(html.encode("utf-8"))))


if __name__ == "__main__":
    main()
