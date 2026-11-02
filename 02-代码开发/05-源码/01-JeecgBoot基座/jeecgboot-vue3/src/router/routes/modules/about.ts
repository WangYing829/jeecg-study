import type { AppRouteModule } from '/@/router/types';

import { LAYOUT } from '/@/router/constant';
import { t } from '/@/hooks/web/useI18n';

const dashboard: AppRouteModule = {
  path: '/about',
  name: 'About',
  component: LAYOUT,
  redirect: '/about/index',
  meta: {
    hideChildrenInMenu: true,
    // update-begin--author:mavis---date:20261102---for:【jeecg-study】图标名失效：simple-icons 集合里叫 aboutdotme（simple-icons v10 起去掉了连字符），旧名 about-dot-me 在本地/在线两种模式下都取不到，图标退化成空心圆点-----------
    icon: 'simple-icons:aboutdotme',
    // update-end--author:mavis---date:20261102---for:【jeecg-study】图标名失效：simple-icons 集合里叫 aboutdotme（simple-icons v10 起去掉了连字符），旧名 about-dot-me 在本地/在线两种模式下都取不到，图标退化成空心圆点-----------
    title: t('routes.dashboard.about'),
    orderNo: 100000,
  },
  children: [
    {
      path: 'index',
      name: 'AboutPage',
      component: () => import('/@/views/sys/about/index.vue'),
      meta: {
        title: t('routes.dashboard.about'),
        // update-begin--author:mavis---date:20261102---for:【jeecg-study】同上：改成集合里真实存在的图标名，且与 sys_permission 里「关于」菜单的 icon 保持一致-----------
        icon: 'simple-icons:aboutdotme',
        // update-end--author:mavis---date:20261102---for:【jeecg-study】同上：改成集合里真实存在的图标名，且与 sys_permission 里「关于」菜单的 icon 保持一致-----------
        hideMenu: true,
      },
    },
  ],
};

export default dashboard;
