# -*- coding: utf-8 -*-
"""
报告生成器：把 Markdown 源稿 + JSON 元数据渲染成带侧边导航的自包含 HTML 报告。

用法:
    python 2026103001-报告生成器.py <源稿.md> <输出.html>

源稿格式:
    首个 <!--META ... --> 块内是 JSON 元数据，其后是 Markdown 正文。

设计约定:
    - 壳子样式固定为「侧边导航 + 章节正文」，移动端顶栏 + 悬浮按钮 + 遮罩面板。
    - 导航自动由 meta.sections 生成，每节必须带 id。
    - 表格在 <=820px 视口下转卡片，由壳子的 JS 自动补 data-label。
    - 输出为 UTF-8 无 BOM，HTML 源稿内联，无需外部依赖。
"""
import json
import os
import re
import sys

import markdown

# ---------------------------------------------------------------- 样式（壳子）

CSS = r"""
  :root{
    --bg:#fafaf9; --surface:#ffffff; --chrome:#f7f7f5; --line:#e6e6e1; --line-soft:#eeeeea;
    --ink:#15171c; --ink-2:#454b58; --muted:#8b8f98;
    --accent:#10b981; --accent-ink:#047857; --accent-soft:rgba(16,185,129,.10); --accent-line:rgba(16,185,129,.28);
    --up:#d93a2b; --up-soft:rgba(217,58,43,.10); --up-line:rgba(217,58,43,.28);
    --down:#0f8a6d; --down-soft:rgba(15,138,109,.10); --down-line:rgba(15,138,109,.28);
    --amber:#b07d12; --amber-soft:rgba(176,125,18,.10); --amber-line:rgba(176,125,18,.28);
    --sans:'Outfit','PingFang SC','Microsoft YaHei','Noto Sans SC',sans-serif;
    --mono:'JetBrains Mono','SFMono-Regular',Consolas,monospace;
    --nav-w:272px;
  }
  *{box-sizing:border-box;}
  html{scroll-behavior:smooth;}
  body{
    margin:0;background:var(--bg);color:var(--ink);
    font-family:var(--sans);font-size:15px;line-height:1.65;
    -webkit-font-smoothing:antialiased;
    background-image:
      radial-gradient(720px 300px at 10% -8%, rgba(16,185,129,.06), transparent 62%),
      radial-gradient(620px 280px at 94% -6%, rgba(21,94,117,.05), transparent 60%);
    background-repeat:no-repeat;
  }
  a{color:inherit;}
  code{font-family:var(--mono);font-size:.88em;background:#f4f4f1;border:1px solid var(--line-soft);border-radius:4px;padding:1px 5px;overflow-wrap:anywhere;}

  /* ============ 布局：侧边导航 + 章节正文 ============ */
  .shell{display:flex;align-items:flex-start;max-width:1280px;margin:0 auto;gap:0;}
  .sidebar{
    width:var(--nav-w);flex:none;position:sticky;top:0;align-self:flex-start;
    height:100vh;overflow-y:auto;padding:26px 20px 40px 24px;border-right:1px solid var(--line);
  }
  .sidebar::-webkit-scrollbar{width:8px;}
  .sidebar::-webkit-scrollbar-thumb{background:#dcdcd6;border-radius:6px;}
  .brand{display:flex;align-items:center;gap:9px;margin-bottom:6px;}
  .brand-mark{width:26px;height:26px;flex:none;border-radius:8px;display:flex;align-items:center;justify-content:center;background:var(--accent-soft);color:var(--accent-ink);border:1px solid var(--accent-line);}
  .brand-mark svg{width:14px;height:14px;}
  .brand h1{font-size:15px;font-weight:600;margin:0;letter-spacing:-.01em;}
  .meta-line{font-family:var(--mono);font-size:10.5px;color:var(--muted);margin-bottom:18px;}
  .nav-title{font-size:10.5px;letter-spacing:.1em;text-transform:uppercase;color:var(--muted);margin:20px 0 8px;padding-left:11px;}
  .nav-link{
    display:flex;align-items:center;gap:8px;padding:7px 11px;border-radius:9px;
    font-size:13.5px;color:var(--ink-2);text-decoration:none;border-left:2px solid transparent;
    transition:background .15s,color .15s,border-color .15s;
  }
  .nav-link:hover{background:#f0f0ec;color:var(--ink);}
  .nav-link.active{background:var(--accent-soft);color:var(--accent-ink);border-left-color:var(--accent);font-weight:500;}
  .nav-link .lv{
    margin-left:auto;font-family:var(--mono);font-size:9.5px;padding:1px 6px;border-radius:999px;
    border:1px solid var(--line);background:var(--surface);color:var(--muted);white-space:nowrap;
  }
  .nav-link.active .lv{border-color:var(--accent-line);background:var(--surface);}
  .mini-rate{margin-top:14px;border-top:1px solid var(--line);padding-top:14px;}
  .mini-row{display:flex;align-items:center;gap:8px;margin-bottom:9px;font-size:12px;color:var(--ink-2);}
  .mini-row .bar{flex:1;height:6px;background:#eeeeea;border-radius:4px;overflow:hidden;}
  .mini-row .bar i{display:block;height:100%;border-radius:4px;}
  .mini-row .n{font-family:var(--mono);font-size:10.5px;color:var(--muted);width:58px;text-align:right;}

  .main{flex:1;min-width:0;padding:34px 40px 80px;}

  /* ============ 标题区 ============ */
  .kicker{display:inline-flex;align-items:center;gap:7px;font-family:var(--mono);font-size:11px;letter-spacing:.14em;text-transform:uppercase;color:var(--accent-ink);background:var(--accent-soft);border:1px solid var(--accent-line);padding:5px 11px;border-radius:999px;}
  .kicker i{width:6px;height:6px;border-radius:50%;background:var(--accent);}
  h2.title{font-size:32px;line-height:1.25;font-weight:600;letter-spacing:-.018em;margin:16px 0 0;}
  .sub{color:var(--ink-2);font-size:15px;margin:10px 0 0;max-width:74ch;}
  .facts{display:flex;flex-wrap:wrap;gap:9px;margin-top:20px;}
  .fact{background:var(--surface);border:1px solid var(--line);border-radius:11px;padding:9px 13px;box-shadow:0 2px 8px rgba(0,0,0,.03);}
  .fact b{display:block;font-family:var(--mono);font-size:15px;font-weight:600;letter-spacing:-.01em;}
  .fact span{font-size:11.5px;color:var(--muted);}

  /* 语义色工具类 */
  .up{color:var(--up);} .down{color:var(--down);} .flat{color:var(--muted);} .amber{color:var(--amber);}

  /* ============ 章节 ============ */
  section{margin-top:52px;scroll-margin-top:24px;}
  .sec-head{display:flex;align-items:baseline;gap:11px;padding-bottom:11px;border-bottom:1px solid var(--line);margin-bottom:20px;}
  .sec-num{font-family:var(--mono);font-size:12px;color:var(--accent-ink);font-weight:600;}
  .sec-head h3{font-size:21px;font-weight:600;letter-spacing:-.012em;margin:0;}
  .sec-head .tag{margin-left:auto;font-size:11.5px;color:var(--muted);font-family:var(--mono);}
  p{margin:0 0 11px;}
  .lead-p{font-size:15.5px;color:var(--ink-2);max-width:78ch;margin-bottom:18px;}
  h4.sub-h{font-size:15px;font-weight:600;margin:26px 0 10px;letter-spacing:-.008em;}
  h5.sub-h5{font-size:13.5px;font-weight:600;margin:18px 0 7px;color:var(--ink-2);}
  ul.plain{margin:6px 0 12px;padding-left:20px;}
  ul.plain li{margin:5px 0;font-size:13.5px;color:var(--ink-2);}
  ol.plain{margin:6px 0 12px;padding-left:20px;}
  ol.plain li{margin:5px 0;font-size:13.5px;color:var(--ink-2);}
  .cite-src{display:inline-block;margin-top:5px;font-family:var(--mono);font-size:10.5px;color:var(--muted);
    border:1px solid var(--line);border-radius:5px;padding:1px 6px;background:var(--surface);}
  .cite-src a{color:var(--accent-ink);text-decoration:none;}

  /* ============ 核心信息 ============ */
  .core-list{list-style:none;padding:0;margin:0;}
  .core-item{display:flex;gap:13px;padding:13px 0;border-bottom:1px solid var(--line-soft);}
  .core-item:last-child{border-bottom:0;}
  .core-dot{width:9px;height:9px;border-radius:50%;flex:none;margin-top:7px;background:var(--accent);box-shadow:0 0 0 3px var(--accent-soft);}
  .core-dot.r{background:var(--up);box-shadow:0 0 0 3px var(--up-soft);}
  .core-dot.a{background:var(--amber);box-shadow:0 0 0 3px var(--amber-soft);}
  .core-body{min-width:0;}
  .core-body h4{margin:0 0 3px;font-size:14.5px;font-weight:600;}
  .core-body p{margin:0;font-size:13.5px;color:var(--ink-2);}

  /* ============ 数据卡 ============ */
  .grid4{display:grid;gap:12px;grid-template-columns:repeat(auto-fit,minmax(168px,1fr));margin:18px 0 4px;}
  .dcard{background:var(--surface);border:1px solid var(--line);border-radius:13px;padding:14px 15px;box-shadow:0 2px 8px rgba(0,0,0,.03);}
  .dcard .k{font-size:11.5px;color:var(--muted);margin-bottom:5px;}
  .dcard .v{font-family:var(--mono);font-size:19px;font-weight:600;letter-spacing:-.02em;line-height:1.2;}
  .dcard .d{font-family:var(--mono);font-size:11.5px;margin-top:3px;}

  /* ============ 表格 ============ */
  table{width:100%;border-collapse:collapse;margin-top:12px;font-size:13.5px;}
  th,td{text-align:left;padding:10px 12px;border-bottom:1px solid var(--line-soft);vertical-align:top;}
  th{font-size:11.5px;color:var(--muted);font-weight:500;letter-spacing:.04em;background:#f7f7f4;border-bottom:1px solid var(--line);}
  td:first-child{font-weight:500;white-space:normal;min-width:72px;}
  td.mono{font-family:var(--mono);font-size:12px;white-space:nowrap;}
  .pill{display:inline-block;padding:1px 8px;border-radius:999px;font-size:11.5px;font-weight:600;white-space:nowrap;}
  .p-ok{background:var(--down-soft);color:var(--down);border:1px solid var(--down-line);}
  .p-warn{background:var(--amber-soft);color:var(--amber);border:1px solid var(--amber-line);}
  .p-bad{background:var(--up-soft);color:var(--up);border:1px solid var(--up-line);}
  .p-info{background:var(--accent-soft);color:var(--accent-ink);border:1px solid var(--accent-line);}
  .p-gray{background:#f2f2ef;color:var(--muted);border:1px solid var(--line);}

  /* ============ 来源表：长名称不撑破表格 + 可点链接 ============ */
  .tbl-src td:first-child{white-space:normal;min-width:150px;}
  .tbl-src .u-cell{font-family:var(--mono);font-size:11.5px;line-height:1.5;word-break:break-all;overflow-wrap:anywhere;}
  .src-link{color:var(--accent-ink);text-decoration:none;border-bottom:1px solid var(--accent-line);word-break:break-all;}
  .src-link:hover{color:var(--accent);border-bottom-color:var(--accent);}

  /* ============ 流程/命令块：宽内容内部滚动，不撑破页面 ============ */
  pre.cmd{background:#15171c;color:#e6e8ec;border-radius:12px;padding:14px 16px;overflow-x:auto;
    font-family:var(--mono);font-size:12px;line-height:1.7;margin:10px 0 14px;
    -webkit-overflow-scrolling:touch;}
  pre.cmd code{background:transparent;border:0;padding:0;color:inherit;font-size:inherit;overflow-wrap:normal;white-space:pre;}
  pre.cmd .c{color:#8b8f98;}
  pre.cmd .ok{color:#34d399;font-weight:600;}
  pre.cmd .bad{color:#f87171;font-weight:600;}
  pre.cmd .warn{color:#fbbf24;font-weight:600;}

  /* ============ 双栏 ============ */
  .cols{display:grid;gap:14px;grid-template-columns:1fr 1fr;margin-top:14px;}
  .panel{background:var(--surface);border:1px solid var(--line);border-radius:14px;padding:16px 17px;box-shadow:0 2px 8px rgba(0,0,0,.03);}
  .panel h4{margin:0 0 10px;font-size:13.5px;font-weight:600;display:flex;align-items:center;gap:7px;}
  .panel h4 svg{width:15px;height:15px;}
  .panel.bad h4{color:var(--up);}
  .panel.good h4{color:var(--down);}
  .panel.info h4{color:var(--accent-ink);}
  .panel.warn h4{color:var(--amber);}
  .panel ul{margin:0;padding-left:0;list-style:none;}
  .panel li{position:relative;padding-left:15px;margin-bottom:9px;font-size:13.5px;color:var(--ink-2);line-height:1.6;}
  .panel li:last-child{margin-bottom:0;}
  .panel li::before{content:"";position:absolute;left:0;top:9px;width:5px;height:5px;border-radius:50%;background:currentColor;opacity:.45;}
  .panel p{font-size:13.5px;color:var(--ink-2);}

  /* ============ 结论条 ============ */
  .callout{border:1px solid var(--accent-line);background:var(--accent-soft);border-radius:14px;padding:16px 18px;margin-top:18px;}
  .callout h4{margin:0 0 7px;font-size:14px;font-weight:600;color:var(--accent-ink);display:flex;align-items:center;gap:8px;}
  .callout h4 svg{width:15px;height:15px;}
  .callout p{margin:0;font-size:13.5px;color:#2c4a3e;}
  .callout p+p{margin-top:7px;}
  .callout.warn{border-color:var(--amber-line);background:var(--amber-soft);}
  .callout.warn h4{color:var(--amber);} .callout.warn p{color:#5c4413;}
  .callout.bad{border-color:var(--up-line);background:var(--up-soft);}
  .callout.bad h4{color:var(--up);} .callout.bad p{color:#6b2018;}
  .note{border:1px solid var(--line);background:var(--surface);border-radius:13px;padding:14px 16px;margin-top:14px;font-size:12.5px;color:var(--muted);}
  .note b{color:var(--ink-2);}

  /* ============ 风险条 ============ */
  .risk{display:flex;gap:12px;align-items:flex-start;padding:12px 0;border-bottom:1px dashed var(--line);}
  .risk:last-child{border-bottom:0;}
  .risk .lv-badge{flex:none;font-family:var(--mono);font-size:11px;font-weight:600;padding:3px 8px;border-radius:6px;}
  .risk .rb{min-width:0;}
  .risk .rb b{display:block;font-size:13.5px;margin-bottom:2px;}
  .risk .rb span{font-size:13px;color:var(--ink-2);}

  /* 正文 markdown 渲染补充 */
  .main h4{font-size:15px;font-weight:600;margin:26px 0 10px;letter-spacing:-.008em;}
  .main h5{font-size:13.5px;font-weight:600;margin:18px 0 7px;color:var(--ink-2);}
  .main ul,.main ol{margin:6px 0 12px;padding-left:20px;}
  .main li{margin:5px 0;font-size:13.5px;color:var(--ink-2);}
  .main strong{color:var(--ink);font-weight:600;}
  .main blockquote{border-left:3px solid var(--accent-line);background:var(--accent-soft);
    border-radius:0 12px 12px 0;padding:11px 15px;margin:12px 0;font-size:13.5px;color:#2c4a3e;}
  .main blockquote p{margin:0;}
  .main hr{border:0;border-top:1px solid var(--line);margin:26px 0;}
  .main img{max-width:100%;}

  .foot{margin-top:48px;padding-top:18px;border-top:1px solid var(--line);font-size:12px;color:var(--muted);display:flex;flex-wrap:wrap;gap:8px 20px;}
  .foot code{background:transparent;border:0;padding:0;color:var(--muted);}

  /* ============ 移动端：悬浮导航面板 ============ */
  .m-topbar,.m-fab,.scrim,.m-close{display:none;}

  @media (max-width:1000px){
    .shell{display:block;}
    body{padding-top:48px;}

    .m-topbar{
      display:flex;align-items:center;gap:9px;
      position:fixed;top:0;left:0;right:0;height:48px;z-index:45;
      padding:0 12px;background:rgba(250,250,249,.94);backdrop-filter:blur(10px);
      border-bottom:1px solid var(--line);
    }
    .m-topbar .t-btn{
      display:inline-flex;align-items:center;gap:5px;flex:none;
      font-family:var(--sans);font-size:12.5px;color:var(--ink-2);
      background:var(--surface);border:1px solid var(--line);border-radius:8px;
      padding:6px 10px;cursor:pointer;transition:all .16s;
    }
    .m-topbar .t-btn:hover{border-color:#d3d3cd;color:var(--ink);}
    .m-topbar .t-btn svg{width:14px;height:14px;color:var(--muted);}
    .m-topbar .t-title{
      flex:1;min-width:0;font-size:13px;color:var(--ink-2);font-weight:500;
      white-space:nowrap;overflow:hidden;text-overflow:ellipsis;
    }

    .sidebar{
      position:fixed;left:auto;right:14px;top:auto;bottom:78px;
      width:min(80vw,320px);height:auto;max-height:min(64vh,580px);
      z-index:70;border:1px solid var(--line);border-radius:18px;
      background:var(--chrome);padding:16px 16px 22px;
      box-shadow:0 20px 56px -14px rgba(0,0,0,.32);
      opacity:0;transform:translateY(18px) scale(.97);pointer-events:none;
      transition:opacity .26s ease,transform .26s cubic-bezier(.2,.8,.3,1);
    }
    .sidebar.open{opacity:1;transform:none;pointer-events:auto;}
    .m-close{
      display:inline-flex;align-items:center;justify-content:center;margin-left:auto;flex:none;
      width:30px;height:30px;border-radius:8px;border:1px solid var(--line);
      background:var(--surface);color:var(--muted);cursor:pointer;padding:0;
    }
    .m-close svg{width:14px;height:14px;}
    .m-close:hover{color:var(--ink);border-color:#d3d3cd;}

    .scrim{
      display:block;position:fixed;inset:0;z-index:65;
      background:rgba(21,23,28,.34);opacity:0;pointer-events:none;transition:opacity .26s;
    }
    .scrim.show{opacity:1;pointer-events:auto;}

    .m-fab{
      display:inline-flex;align-items:center;gap:7px;
      position:fixed;right:14px;bottom:16px;z-index:60;
      font-family:var(--sans);font-size:13px;color:#fff;
      background:var(--ink);border:1px solid var(--ink);border-radius:999px;
      padding:11px 16px;cursor:pointer;
      box-shadow:0 10px 26px -6px rgba(0,0,0,.45);
      opacity:0;transform:translateY(14px) scale(.9);pointer-events:none;
      transition:opacity .24s,transform .24s,background .16s;
    }
    .m-fab.show{opacity:1;transform:none;pointer-events:auto;}
    .m-fab:hover{background:#2b2f38;}
    .m-fab svg{width:15px;height:15px;}

    .main{padding:26px 22px 96px;}
    .cols{grid-template-columns:1fr;}
  }

  /* 手机小屏：表格转卡片 */
  @media (max-width:820px){
    table,tbody,tr,td{display:block;width:100%;}
    thead{display:none;}
    table{font-size:12.5px;}
    tr{
      background:var(--surface);border:1px solid var(--line);border-radius:12px;
      padding:11px 14px;margin-bottom:10px;box-shadow:0 2px 8px rgba(0,0,0,.03);
    }
    td{border:0;padding:3px 0;}
    td:first-child{font-weight:600;font-size:13.5px;padding-bottom:6px;white-space:normal;}
    td:not(:first-child){
      position:relative;padding:3px 0 3px 76px;font-size:12.5px;color:var(--ink-2);
    }
    td:not(:first-child)::before{
      content:attr(data-label);position:absolute;left:0;top:3px;width:68px;
      font-size:11px;color:var(--muted);
    }
  }

  @media (max-width:680px){
    body{font-size:14px;line-height:1.72;overflow-wrap:anywhere;}
    h2.title{font-size:23px;letter-spacing:-.015em;}
    .sub{font-size:14px;}
    .kicker{font-size:10px;padding:4px 9px;}
    .facts{gap:7px;}
    .fact{flex:1 1 calc(50% - 4px);min-width:0;padding:9px 11px;}
    .fact b{font-size:14px;}
    .fact span{font-size:10.5px;}
    .grid4{grid-template-columns:1fr 1fr;gap:9px;}
    .dcard{padding:12px 13px;}
    .dcard .v{font-size:16px;}
    .main{padding:22px 15px 92px;}
    section{margin-top:38px;}
    .sec-head{flex-wrap:wrap;gap:8px;}
    .sec-head h3{font-size:18px;}
    .sec-head .tag{margin-left:0;width:100%;}
    .core-body h4{font-size:14px;}
    .core-body p{font-size:13px;}
    .panel{padding:14px 15px;}
    .panel li{font-size:13px;}
    .panel h4{font-size:13px;}
    .callout{padding:14px 15px;}
    .callout p{font-size:13px;}
    .foot{gap:6px 14px;font-size:11.5px;}
    .note{font-size:12px;}
  }
"""

# ---------------------------------------------------------------- 脚本（壳子）

JS = r"""
<script>
(function(){
  var BRAND = (document.getElementById('mTitle')||{}).textContent || '文档';
  var links   = Array.prototype.slice.call(document.querySelectorAll('.nav-link'));
  var pairs   = links.map(function(l){
    var id = l.getAttribute('href').replace('#','');
    var isJump = (id === 'top' || id === 'bottom');
    return { link:l, sec: isJump ? null : document.getElementById(id) };
  });
  var sidebar = document.querySelector('.sidebar');
  var scrim   = document.getElementById('scrim');
  var fab     = document.getElementById('fab');
  var mTitle  = document.getElementById('mTitle');
  var panelOpen = false;

  function isMobile(){ return window.matchMedia('(max-width:1000px)').matches; }
  function openPanel(){
    panelOpen = true;
    if (sidebar) sidebar.classList.add('open');
    if (scrim)   scrim.classList.add('show');
  }
  function closePanel(){
    panelOpen = false;
    if (sidebar) sidebar.classList.remove('open');
    if (scrim)   scrim.classList.remove('show');
  }
  function togglePanel(){ panelOpen ? closePanel() : openPanel(); }

  function onScroll(){
    var y = window.scrollY + 140, idx = -1;
    for (var i=0;i<pairs.length;i++){
      if (pairs[i].sec && pairs[i].sec.offsetTop <= y) { idx = i; }
    }
    if (idx < 0) { idx = 0; for (var j=0;j<pairs.length;j++){ if (pairs[j].sec){ idx = j; break; } } }

    pairs.forEach(function(p,i){ if (p.sec) p.link.classList.toggle('active', i === idx); });

    if (mTitle) {
      mTitle.textContent = window.scrollY < 40
        ? BRAND
        : pairs[idx].link.textContent.replace(/[↑↓]\s*/g,'').trim();
    }
    // 下滑后导航不可见 → 收起来为悬浮按钮
    if (fab) fab.classList.toggle('show', window.scrollY > 80);
  }
  window.addEventListener('scroll', onScroll, {passive:true});
  window.addEventListener('resize', onScroll);
  onScroll();

  // 导航点击：顶/底跳转 或 锚点定位
  pairs.forEach(function(p){
    p.link.addEventListener('click', function(e){
      e.preventDefault();
      var href = p.link.getAttribute('href');
      if (href === '#top') {
        window.scrollTo({top:0, behavior:'smooth'});
      } else if (href === '#bottom') {
        window.scrollTo({top:document.documentElement.scrollHeight, behavior:'smooth'});
      } else if (p.sec) {
        window.scrollTo({top: p.sec.getBoundingClientRect().top + window.scrollY - 18, behavior:'smooth'});
        history.replaceState(null,'',href);
      }
      closePanel();
    });
  });

  var menuBtn  = document.getElementById('menuBtn');
  var closeBtn = document.getElementById('closeBtn');
  var topBtn   = document.getElementById('topBtn');
  if (menuBtn)  menuBtn.addEventListener('click', togglePanel);
  if (closeBtn) closeBtn.addEventListener('click', closePanel);
  if (fab)      fab.addEventListener('click', togglePanel);
  if (scrim)    scrim.addEventListener('click', closePanel);
  if (topBtn)   topBtn.addEventListener('click', function(){ window.scrollTo({top:0, behavior:'smooth'}); });
  document.addEventListener('keydown', function(e){ if (e.key === 'Escape') closePanel(); });

  // 移动端表格卡片：为每个单元格补列名标签
  Array.prototype.forEach.call(document.querySelectorAll('table'), function(t){
    var heads = Array.prototype.map.call(t.querySelectorAll('thead th'), function(th){
      return th.textContent.trim();
    });
    Array.prototype.forEach.call(t.querySelectorAll('tbody tr'), function(tr){
      Array.prototype.forEach.call(tr.children, function(td,i){
        if (heads[i]) td.setAttribute('data-label', heads[i]);
      });
    });
  });
})();
</script>
"""

SVG_FOLDER = (
    '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" '
    'stroke-linecap="round" stroke-linejoin="round">'
    '<path d="M4 20h16a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2h-7.9a2 2 0 0 1-1.69-.9L9.6 3.9A2 2 0 0 0 7.93 3H4a2 2 0 0 0-2 2v13c0 1.1.9 2 2 2Z"/></svg>'
)


# ---------------------------------------------------------------- 工具函数

def esc(s):
    return (str(s).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace('"', "&quot;"))


def parse_meta(raw):
    m = re.search(r"<!--META\s*(\{.*?\})\s*-->", raw, re.S)
    if not m:
        raise SystemExit("源稿缺少 <!--META {json} --> 块")
    meta = json.loads(m.group(1))
    body = raw[:m.start()] + raw[m.end():]
    return meta, body


def accent_css(meta):
    """主色覆盖：同时派生 accent-ink / accent-soft / accent-line。"""
    a = meta.get("accent")
    if not a:
        return ""
    a = a.strip()
    if not a.startswith("#") or len(a) != 7:
        return ""
    r, g, b = int(a[1:3], 16), int(a[3:5], 16), int(a[5:7], 16)
    ink = "#%02x%02x%02x" % (max(0, r - 90), max(0, g - 60), max(0, b - 50))
    soft = "rgba(%d,%d,%d,.10)" % (r, g, b)
    line = "rgba(%d,%d,%d,.28)" % (r, g, b)
    return ("\n  :root{--accent:%s;--accent-ink:%s;--accent-soft:%s;--accent-line:%s;}" % (a, ink, soft, line))


def render_body(body):
    md = markdown.Markdown(extensions=["tables", "fenced_code", "sane_lists", "attr_list"])
    html = md.convert(body)
    # 表格加横向滚动包装，避免长表格撑破容器
    html = re.sub(r"<table>", '<div class="tbl-wrap"><table>', html)
    html = re.sub(r"</table>", "</table></div>", html)
    # 围栏代码块套用 pre.cmd 样式（内部滚动，不撑破页面）
    html = re.sub(r"<pre>", '<pre class="cmd">', html)
    return html


def build(meta, body_html):
    brand = meta.get("brand", "jeecg-study")
    title = meta.get("title", "文档")
    kicker = meta.get("kicker", "")
    subtitle = meta.get("subtitle", "")
    meta_line = meta.get("meta_line", "")
    sections = meta.get("sections", [])
    facts = meta.get("facts", [])
    side = meta.get("side", [])
    footer = meta.get("footer", [])

    # 章节正文
    body_parts = []
    for i, sec in enumerate(sections):
        sid = sec.get("id") or ("sec%d" % i)
        num = sec.get("num", "%02d" % (i + 1))
        nav = sec.get("nav", sid)
        tag = sec.get("tag", "")
        h = sec.get("html", "")
        body_parts.append(
            '<section id="%s">\n'
            '  <div class="sec-head"><span class="sec-num">%s</span><h3>%s</h3>%s</div>\n'
            '  %s\n'
            '</section>' % (esc(sid), esc(num), esc(nav),
                            ('<span class="tag">%s</span>' % esc(tag)) if tag else "",
                            h)
        )

    # 侧边导航：快速跳转（顶/底）与章节分开渲染，每个锚点只出现一次
    jump_links = [
        '<a class="nav-link" href="#top"><span>↑ 页面顶部</span></a>',
        '<a class="nav-link" href="#bottom"><span>↓ 页面底部</span></a>',
    ]
    chapter_links = []
    for sec in sections:
        sid = sec.get("id")
        nav = sec.get("nav", sid)
        badge = sec.get("badge", "")
        chapter_links.append(
            '<a class="nav-link" href="#%s">%s%s</a>' % (
                esc(sid), esc(nav),
                ('<span class="lv">%s</span>' % esc(badge)) if badge else "")
        )

    # 侧边小部件
    side_html = ""
    if side:
        rows = []
        for s in side:
            rows.append(
                '<div class="mini-row"><span>%s</span><span class="bar">'
                '<i style="width:%s%%;background:%s"></i></span><span class="n">%s</span></div>'
                % (esc(s.get("label", "")), s.get("pct", 0), esc(s.get("color", "#10b981")), esc(s.get("note", "")))
            )
        side_html = ('<div class="nav-title">%s</div><div class="mini-rate">%s</div>'
                     % (esc(meta.get("side_title", "概览")), "".join(rows)))

    # 事实卡
    fact_html = ""
    if facts:
        cells = []
        for f in facts:
            cells.append('<div class="fact"><b>%s</b><span>%s</span></div>' % (esc(f.get("v", "")), esc(f.get("k", ""))))
        fact_html = '<div class="facts">%s</div>' % "".join(cells)

    foot_html = ""
    if footer:
        foot_html = '<footer class="foot">%s</footer>' % "".join(
            "<span>%s</span>" % f if isinstance(f, str) else "<span>%s</span>" % esc(f.get("t", "")) for f in footer)

    doc = u"""<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>%(doc_title)s</title>
<style>
%(css)s
  .tbl-wrap{width:100%%;overflow-x:auto;-webkit-overflow-scrolling:touch;}
  .tbl-wrap table{margin-top:0;}
  @media (max-width:820px){ .tbl-wrap{overflow-x:visible;} }
</style>
</head>
<body id="top">
<div class="scrim" id="scrim"></div>

<div class="m-topbar">
  <button class="t-btn" id="menuBtn" type="button">
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M8 6h13M8 12h13M8 18h13M3 6h.01M3 12h.01M3 18h.01"/></svg>
    目录
  </button>
  <span class="m-title" id="mTitle">%(brand)s</span>
  <button class="t-btn" id="topBtn" type="button">
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="m18 15-6-6-6 6"/><path d="M12 9v12"/></svg>
    顶部
  </button>
</div>

<div class="shell">

  <aside class="sidebar">
    <div class="brand">
      <span class="brand-mark">%(svg_folder)s</span>
      <h1>%(brand)s</h1>
      <button class="m-close" id="closeBtn" type="button" aria-label="关闭导航">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round"><path d="M18 6 6 18M6 6l12 12"/></svg>
      </button>
    </div>
    <div class="meta-line">%(meta_line)s</div>

    <div class="nav-title">快速跳转</div>
    %(jump_links)s

    <div class="nav-title">章节</div>
    %(chapter_links)s

    %(side_html)s
  </aside>

  <main class="main">
    <div class="kicker"><i></i>%(kicker)s</div>
    <h2 class="title">%(title)s</h2>
    <p class="sub">%(subtitle)s</p>
    %(fact_html)s

%(body)s

    <div id="bottom"></div>
    %(foot)s
  </main>

</div>

<button class="m-fab" id="fab" type="button">
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="m18 15-6-6-6 6"/><path d="M12 9v12"/></svg>
  目录
</button>

%(js)s
</body>
</html>
""" % {
        "doc_title": esc("%s · %s" % (brand, title)),
        "css": CSS + accent_css(meta),
        "brand": esc(brand),
        "svg_folder": SVG_FOLDER,
        "meta_line": esc(meta_line),
        "jump_links": "\n    ".join(jump_links),
        "chapter_links": "\n    ".join(chapter_links),
        "side_html": side_html,
        "kicker": esc(kicker),
        "title": esc(title),
        "subtitle": esc(subtitle),
        "fact_html": fact_html,
        "body": "\n".join(body_parts),
        "foot": foot_html,
        "js": JS,
    }
    return doc


def main():
    if len(sys.argv) != 3:
        print("用法: python 2026103001-报告生成器.py <源稿.md> <输出.html>")
        return 1
    src, out = sys.argv[1], sys.argv[2]
    with open(src, "r", encoding="utf-8") as f:
        raw = f.read()
    meta, body = parse_meta(raw)

    # 章节 html 若未在 META 里给出，则从正文的二级标题自动切分
    if not meta.get("sections"):
        sections = []
        chunks = re.split(r"\n##\s+", body)
        body = chunks[0]
        for idx, ch in enumerate(chunks[1:]):
            lines = ch.split("\n")
            head = lines[0].strip()
            m = re.match(r"^(.*?)\s*\{(.*)\}\s*$", head)
            if m:
                head, extra = m.group(1).strip(), m.group(2).strip()
            else:
                extra = ""
            sid = "s%d" % (idx + 1)
            nav = head
            badge = ""
            mm = re.match(r"^(.*?)\s*\[(.*)\]\s*$", nav)
            if mm:
                nav, badge = mm.group(1).strip(), mm.group(2).strip()
            sections.append({"id": sid, "nav": nav, "badge": badge, "num": "%02d" % (idx + 1),
                             "tag": extra, "html": render_body(ch)})
            meta.setdefault("sections", sections)
            meta["sections"] = sections
    else:
        # META 给了 sections：正文按二级标题切块填回
        chunks = re.split(r"\n##\s+", body)
        rest = chunks[0]
        blocks = chunks[1:]
        secs = meta["sections"]
        for i, sec in enumerate(secs):
            sec.setdefault("html", "")
            if i < len(blocks):
                sec["html"] = render_body(blocks[i])
            else:
                sec["html"] = render_body(rest)
        # 正文中未被切走的头部内容拼进第一节
        if secs and rest.strip():
            secs[0]["html"] = render_body(rest) + secs[0].get("html", "")

    doc = build(meta, body)
    outdir = os.path.dirname(os.path.abspath(out))
    if outdir and not os.path.isdir(outdir):
        os.makedirs(outdir)
    with open(out, "w", encoding="utf-8", newline="\n") as f:
        f.write(doc)
    print("OK %s (%d bytes, %d sections)" % (out, len(doc.encode("utf-8")), len(meta.get("sections", []))))
    return 0


if __name__ == "__main__":
    sys.exit(main())
