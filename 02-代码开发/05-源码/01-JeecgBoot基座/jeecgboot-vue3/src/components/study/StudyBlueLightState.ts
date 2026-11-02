/**
 * 蓝光过滤状态的本地持久化（设计规范 §10.5：状态持久化）。
 *
 * <p>学习端与家长端共享同一个开关状态；后端尚未提供护眼设置落库接口，
 * 现阶段先落本地，接口就绪后由服务端覆盖。</p>
 */

/** 本地存储键 */
export const STUDY_BLUE_LIGHT_KEY = 'study:eye:blue-light';

/** 根节点上的过滤类名 */
export const STUDY_BLUE_LIGHT_CLASS = 'study-blue-light-on';

/**
 * 读取本地保存的蓝光过滤状态。
 *
 * @return true = 已开启
 */
export function readBlueLight(): boolean {
  try {
    return window.localStorage.getItem(STUDY_BLUE_LIGHT_KEY) === '1';
  } catch (e) {
    return false;
  }
}

/**
 * 保存蓝光过滤状态并作用到根节点。
 *
 * @param value 是否开启
 */
export function writeBlueLight(value: boolean) {
  document.documentElement.classList.toggle(STUDY_BLUE_LIGHT_CLASS, value);
  try {
    window.localStorage.setItem(STUDY_BLUE_LIGHT_KEY, value ? '1' : '0');
  } catch (e) {
    // 隐私模式下 localStorage 不可用时忽略，不影响开关本身
  }
}
