import { ref } from 'vue';
import type { Ref } from 'vue';
import { confirmEyeRest, sendEyeHeartbeat, startEyeSession, type StudyEyeSessionVO } from '/@/views/study/api';

/**
 * 护眼休息状态（设计规范 §10.5 / 产品红线 R-03）。
 *
 * <p>核心口径：</p>
 * <ul>
 *   <li>「是否需要休息」由<b>服务端</b>下发 {@code restRequired}，前端只负责展示；</li>
 *   <li>清除遮罩的<b>唯一</b>通道是用户显式确认（{@link StudyEyeState.confirmRest}），
 *       页面里没有任何「到点自动消失」的定时器（R-03 / 自检清单 #38）；</li>
 *   <li>用眼时长由服务端按心跳基线累计，客户端<b>只上报「还在 / 已离开」</b>，
 *       不上报时长数字（R-03 / R03-3）。</li>
 * </ul>
 */
export interface StudyEyeState {
  /** 服务端下发的休息阻断标志 */
  restRequired: Ref<boolean>;
  /** 服务端下发的休息提醒文案（含可执行动作） */
  restPrompt: Ref<string>;
  /** 蓝光过滤开关状态（服务端已落库） */
  blueLightOn: Ref<boolean>;
  /** 护眼提醒间隔（分钟，家长端配置，服务端随会话下发） */
  eyeIntervalMin: Ref<number | undefined>;
  /** 用眼会话 ID */
  sessionId: Ref<string>;
  /** 开始用眼会话并开启心跳 */
  start: () => Promise<void>;
  /** 上报一次心跳 */
  heartbeat: (active?: boolean) => Promise<void>;
  /** 用户显式确认休息——唯一的清除通道 */
  confirmRest: () => Promise<void>;
  /** 切换蓝光过滤 */
  setBlueLight: (value: boolean) => Promise<void>;
  /** 停止心跳 */
  stop: () => void;
}

/** 心跳间隔（毫秒）：只说明「还在」，不上报时长 */
const HEARTBEAT_INTERVAL = 60000;

/**
 * 创建一份护眼休息状态。
 *
 * <p>⚠ 这里<b>不</b>注册 {@code onMounted} / {@code onBeforeUnmount}：
 * 生命周期钩子会绑定到「第一个调用本工厂的组件」身上。一旦护眼设置页先创建了这份状态，
 * 路由切到别的学习页时，布局挂载就不会再触发 {@code start}()，
 * 护眼会话静默停摆（提醒间隔读不到、时长不累计）。
 * 会话的生命周期必须由<b>学习端布局</b>显式持有。</p>
 *
 * @return 护眼状态（restRequired / restPrompt / blueLightOn / eyeIntervalMin / sessionId + 会话方法）
 */
export function useStudyEyeState(): StudyEyeState {
  const restRequired = ref(false);
  const restPrompt = ref('');
  const blueLightOn = ref(false);
  const eyeIntervalMin = ref<number | undefined>(undefined);
  const sessionId = ref('');
  let timer: number | undefined;

  const applySession = (session?: StudyEyeSessionVO) => {
    if (!session) {
      return;
    }
    if (session.sessionId) {
      sessionId.value = session.sessionId;
    }
    if (typeof session.restRequired === 'boolean') {
      restRequired.value = session.restRequired;
    }
    if (session.restPrompt) {
      restPrompt.value = session.restPrompt;
    }
    if (typeof session.blueLightOn === 'boolean') {
      blueLightOn.value = session.blueLightOn;
    }
    if (typeof session.eyeIntervalMin === 'number') {
      eyeIntervalMin.value = session.eyeIntervalMin;
    }
  };

  const heartbeat = async (active = true) => {
    if (!sessionId.value) {
      return;
    }
    try {
      applySession(await sendEyeHeartbeat(sessionId.value, { active, blueLight: blueLightOn.value }));
    } catch (e) {
      // 心跳失败不打断学习，服务端会按基线自行累计
    }
  };

  const start = async () => {
    try {
      applySession(await startEyeSession());
    } catch (e) {
      // 会话开不起来时不影响做题，只是没有护眼统计
    }
    stopTimer();
    timer = window.setInterval(() => {
      // 遮罩出现时说明孩子在休息，此时不再累计心跳
      if (!restRequired.value) {
        heartbeat(true);
      }
    }, HEARTBEAT_INTERVAL);
  };

  const confirmRest = async () => {
    if (sessionId.value) {
      try {
        applySession(await confirmEyeRest(sessionId.value));
        return;
      } catch (e) {
        // 请求失败时也要放行，避免把孩子卡在遮罩后面
      }
    }
    restRequired.value = false;
  };

  const setBlueLight = async (value: boolean) => {
    blueLightOn.value = value;
    await heartbeat(true);
  };

  const stopTimer = () => {
    if (timer !== undefined) {
      window.clearInterval(timer);
      timer = undefined;
    }
  };

  const stop = () => {
    stopTimer();
  };

  return { restRequired, restPrompt, blueLightOn, eyeIntervalMin, sessionId, start, heartbeat, confirmRest, setBlueLight, stop };
}

/**
 * 学习端<b>共享</b>的那一份护眼状态。
 *
 * <p>为什么用模块级单例，而不是 provide / inject 或 defineExpose：</p>
 * <ul>
 *   <li><b>provide / inject 方向反了</b>：页面模板里写的是 {@code <StudyLayout>}，
 *       也就是布局是页面的<b>子</b>组件；而 provide / inject 的方向是「父 provide → 子 inject」，
 *       布局 provide 的东西页面（父级）永远 inject 不到，只会拿到 null；</li>
 *   <li><b>defineExpose + 模板 ref 也不可靠</b>：模板 ref 拿到的是 Vue 的深层 reactive 代理，
 *       代理会把嵌套对象里的 ref <b>自动解包</b>（{@code eye.eyeIntervalMin} 拿到的是数字而不是 Ref），
 *       写成 {@code eye.eyeIntervalMin.value} 反而永远是 undefined；</li>
 *   <li>单例最直白：布局与页面拿到<b>同一个对象、同一批 ref</b>，不依赖任何跨组件传值技巧。</li>
 * </ul>
 */
let sharedEyeState: StudyEyeState | null = null;

/**
 * 取学习端共享的那份护眼状态（首次调用时创建）。
 *
 * @return 全局唯一的护眼状态实例
 */
export function useSharedEyeState(): StudyEyeState {
  if (!sharedEyeState) {
    sharedEyeState = useStudyEyeState();
  }
  return sharedEyeState;
}
