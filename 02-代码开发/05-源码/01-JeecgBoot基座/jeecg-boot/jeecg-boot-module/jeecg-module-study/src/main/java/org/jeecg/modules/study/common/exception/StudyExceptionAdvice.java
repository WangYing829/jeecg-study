package org.jeecg.modules.study.common.exception;

import org.jeecg.common.api.vo.Result;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.study.common.constant.StudyErrorCode;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import lombok.extern.slf4j.Slf4j;

/**
 * study 域业务异常的统一出口。
 *
 * <p><b>为什么必须单独写这一个类</b>：底座自带的 {@code JeecgBootExceptionHandler}
 * 能把 {@link JeecgBootException} 的 {@code errCode} 正确写进响应，但它是
 * {@code @RestControllerAdvice} 里<b>没有声明优先级</b>的普通一员；
 * 而 JimuReport（{@code jimureport-spring-boot3-starter}）自带的
 * {@code JimuGlobalExceptionHandler} 也声明了 {@code @ExceptionHandler(Exception.class)}，
 * 并且同样没有优先级。两个 advice 的 order 都是默认值（{@code LOWEST_PRECEDENCE}），
 * 最终由 <b>bean 注册先后</b> 决定谁先命中 —— 运行时实测命中的是 Jimu 那一个，
 * 于是本域所有业务错误码（4001 / 4004 / 4009 / <b>4010</b> …）全部被压成
 * {@code code=500}，错误码契约整条失效。</p>
 *
 * <p>修法不是去改底座（上游零改动原则），而是在<b>本模块内</b>声明一个
 * 优先级明确更高的 advice：</p>
 * <ul>
 *   <li>{@code @Order(HIGHEST_PRECEDENCE)} —— 排序由 order 决定，
 *       不再依赖 bean 注册顺序，结果确定；</li>
 *   <li>{@code basePackages = "org.jeecg.modules.study"} —— 只接管 study 控制器
 *       抛出的异常，<b>不改变上游 sys 模块等任何既有接口的响应形态</b>。</li>
 * </ul>
 *
 * <p>返回体沿用底座 {@code Result}：{@code HTTP 200 + success=false + code=业务码}，
 * 与《接口设计说明书》§2.2「业务错误码一律返回 HTTP 200 + code != 0」一致。</p>
 *
 * @author jeecg-study
 */
@Slf4j
@RestControllerAdvice(basePackages = "org.jeecg.modules.study")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class StudyExceptionAdvice {

    /**
     * 处理本域抛出的业务异常，按其携带的错误码原样返回。
     *
     * <p>本域所有业务错误（越权 4010、缺错因 4001、家庭码错误 4009、
     * 上下文未配置 4004 …）都是 {@link JeecgBootException} 的子类或实例，
     * 因此这一处即可覆盖全部业务错误码。</p>
     *
     * @param e 业务异常
     * @return 带业务错误码的失败响应
     */
    @ExceptionHandler(JeecgBootException.class)
    public Result<?> handleJeecgBootException(JeecgBootException e) {
        int code = e.getErrCode();
        if (code == StudyErrorCode.FORBIDDEN_SCOPE) {
            // 越权是安全事件，单独留痕：谁访问了不属于他的孩子
            log.warn("[study] 越权访问被拒（{}）：{}", code, e.getMessage());
        } else {
            log.warn("[study] 业务异常（{}）：{}", code, e.getMessage());
        }
        return Result.error(code, e.getMessage());
    }
}
