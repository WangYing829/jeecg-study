// =============================================================================
// 菜单图标白名单（离线图标集 safelist）
// =============================================================================
// 为什么需要这个文件：
//   菜单图标存在**数据库** sys_permission.icon 里，而前端图标组件的图标数据来自
//   vite-plugin-purge-icons 生成的离线图标集 —— 那个插件**只扫源码文本**
//   （content: **/*.html|*.pug|*.vue|*.js|*.ts），源码里没写过的图标名不会被收集，
//   于是离线模式下 renderSVG() 返回 null，图标退化成 <span class="iconify"> 空心圆点。
//   实测（2026-11-02）：数据库 92 个菜单图标里有 3 个**只存在于数据库**、
//   源码里从没出现过 → 离线模式下必然是空圆点。
//
//   ⚠ 本文件的注释里**不许写具体的图标名**：
//     自动化断言要判断「某个图标名是不是只出现在注释里」，
//     注释里写了具体图标名会让「白名单漏登记」这类缺陷被假绿掩盖。
//
// 维护口径：
//   1. 新增 / 修改 sys_permission.icon 后，把图标名补进下面这个清单；
//   2. 自动化断言 StudyMenuIconOfflineTest 会核对「数据库图标 ⊆ 本清单 ∪ 源码文本」，
//      漏补时测试直接失败并指出缺哪个，不会等到浏览器里才发现是空圆点；
//   3. 本文件只是**纯数据清单**，不参与任何运行时逻辑（purge 插件按文本提取），
//      所以不需要被任何模块 import。
// =============================================================================

export const MENU_ICON_SAFELIST: string[] = [
  'ant-design:account-book-outlined',
  'ant-design:aliwangwang-outlined',
  'ant-design:apartment-outlined',
  'ant-design:appstore-outlined',
  'ant-design:appstore-twotone',
  'ant-design:bar-chart-outlined',
  'ant-design:bell-outlined',
  'ant-design:book-outlined',
  'ant-design:book-twotone',
  'ant-design:border-right-outlined',
  'ant-design:box-plot-outlined',
  'ant-design:branches-outlined',
  'ant-design:bug-twotone',
  'ant-design:bulb-outlined',
  'ant-design:cloud-outlined',
  'ant-design:column-height-outlined',
  'ant-design:credit-card-twotone',
  'ant-design:customer-service-twotone',
  'ant-design:database-filled',
  'ant-design:deployment-unit-outlined',
  'ant-design:edit-filled',
  'ant-design:edit-outlined',
  'ant-design:exclamation-circle-outlined',
  'ant-design:experiment-outlined',
  'ant-design:eye-outlined',
  'ant-design:field-binary-outlined',
  'ant-design:field-number-outlined',
  'ant-design:file-add-outlined',
  'ant-design:file-excel-outlined',
  'ant-design:file-image-filled',
  'ant-design:file-word-outlined',
  'ant-design:form-outlined',
  'ant-design:funnel-plot-twotone',
  'ant-design:group-outlined',
  'ant-design:hdd-filled',
  'ant-design:hdd-twotone',
  'ant-design:history-outlined',
  'ant-design:home-outlined',
  'ant-design:idcard-twotone',
  'ant-design:ie-circle-filled',
  'ant-design:insert-row-above-outlined',
  'ant-design:insert-row-left-outlined',
  'ant-design:interaction-outlined',
  'ant-design:laptop-outlined',
  'ant-design:layout-filled',
  'ant-design:line-chart-outlined',
  'ant-design:line-height-outlined',
  'ant-design:menu-fold-outlined',
  'ant-design:menu-outlined',
  'ant-design:message-outlined',
  'ant-design:partition-outlined',
  'ant-design:pic-left-outlined',
  'ant-design:play-circle-outlined',
  'ant-design:profile-outlined',
  'ant-design:qrcode-outlined',
  'ant-design:read-filled',
  'ant-design:read-outlined',
  'ant-design:right-square-filled',
  'ant-design:rocket-filled',
  'ant-design:scan-outlined',
  'ant-design:setting',
  'ant-design:setting-outlined',
  'ant-design:setting-twotone',
  'ant-design:sketch-outlined',
  'ant-design:slack-outlined',
  'ant-design:sliders-outlined',
  'ant-design:small-dash-outlined',
  'ant-design:solution',
  'ant-design:star-outlined',
  'ant-design:swap-outlined',
  'ant-design:table-outlined',
  'ant-design:team',
  'ant-design:team-outlined',
  'ant-design:thunderbolt-filled',
  'ant-design:tool-outlined',
  'ant-design:tool-twotone',
  'ant-design:trademark-outlined',
  'ant-design:translation-outlined',
  'ant-design:user',
  'ant-design:user-outlined',
  'ant-design:usergroup-add-outlined',
  'ant-design:video-camera-filled',
  'ion:aperture-outline',
  'ion:bar-chart-outline',
  'ion:git-compare-outline',
  'ion:key-outline',
  'ion:layers-outline',
  'ion:menu-outline',
  'ion:settings-outline',
  'ion:tv-outline',
  'simple-icons:aboutdotme',
  'whh:paintroll',
];
