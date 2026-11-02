import { onBeforeUnmount, onMounted } from 'vue';

/**
 * 学习端主题锁定（设计规范 §4.2③ / §9.7）。
 *
 * <p>学习端与孩子端<b>固定浅色</b>：根节点显式 {@code data-theme="light"}，
 * 不随系统暗色或全局暗色开关切换——护眼场景下深色底反而增加疲劳。</p>
 *
 * <p>锁定只在学习端布局挂载期间生效，卸载时还原，
 * 因此不影响管理端与家长端（家长端跟随全局暗色开关，见设计规范 §9.6）。</p>
 *
 * <p><b>为什么不能只在 onMounted 里锁一次</b>（2026-10-31 实测修掉的真缺陷）：
 * 全局主题初始化是<b>异步</b>的——{@code src/logics/initAppConfig.ts:53} 会调用
 * {@code updateDarkTheme} 把暗色状态写进根节点，它可能在学习端布局挂载<b>之后</b>才跑完，
 * 把 {@code html} 的 {@code data-theme} 改回 {@code dark} 并加上 {@code dark} class。
 * 一次性锁定会被它整个冲掉，表现为<b>学习端偶发变暗</b>（同一个孩子端里，
 * 7 个页面有 1 个是暗的——纯看谁跑得慢，属于典型竞态），
 * 违反 TC-08-22「系统切 dark 后进入学习端，学习端仍为浅色，无半深半浅」。</p>
 *
 * <p>所以这里用 <b>MutationObserver 兜底</b>：根节点的 {@code data-theme} / {@code class}
 * 一旦被改成暗色，立刻压回浅色。观察器回调里的条件判断保证不会自激循环。</p>
 *
 * <p>本模块只做「锁定」，<b>不提供</b>任何切换浅色 / 深色的入口（规范 §4.2③）。</p>
 *
 * @author jeecg-study
 */

/** 全局暗色开关写入的裸 localStorage 键（{@code src/store/modules/app.ts:118}） */
const GLOBAL_DARK_KEY = '__APP__DARK__MODE__';

export function useStudyThemeLock() {
  /** 属性观察器：拦住全局暗色初始化对根节点的改写 */
  let observer: MutationObserver | null = null;

  /** 强制根节点回到浅色（幂等，可反复调用） */
  const forceLight = () => {
    const root = document.documentElement;
    root.setAttribute('data-theme', 'light');
    root.classList.remove('dark');
  };

  onMounted(() => {
    forceLight();

    observer = new MutationObserver(() => {
      const root = document.documentElement;
      if (root.getAttribute('data-theme') !== 'light' || root.classList.contains('dark')) {
        forceLight();
      }
    });
    observer.observe(document.documentElement, {
      attributes: true,
      attributeFilter: ['data-theme', 'class'],
    });
  });

  onBeforeUnmount(() => {
    observer?.disconnect();
    observer = null;

    // 还原成**当前全局暗色状态**，而不是挂载时的快照：
    // 学习端挂载期间全局开关可能被改成 dark，还原旧快照会把家长端 / 管理端留在浅色。
    const root = document.documentElement;
    if (window.localStorage.getItem(GLOBAL_DARK_KEY) === 'dark') {
      root.setAttribute('data-theme', 'dark');
      root.classList.add('dark');
    } else {
      root.setAttribute('data-theme', 'light');
      root.classList.remove('dark');
    }
  });
}
