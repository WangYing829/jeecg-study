package org.jeecg.modules.study.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.jeecg.common.api.vo.Result;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.study.common.constant.StudyErrorCode;
import org.jeecg.modules.study.common.exception.StudyExceptionAdvice;
import org.jeecg.modules.study.common.exception.StudyForbiddenException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * study 域业务异常出口的自动化测试。
 *
 * <p><b>为什么这条断言必须存在</b>：本域的业务错误码（4001 / 4004 / 4009 / 4010 …）
 * 全部靠异常携带的 {@code errCode} 传到响应体里。而运行环境里同时存在底座的
 * {@code JeecgBootExceptionHandler} 与 JimuReport 的 {@code JimuGlobalExceptionHandler}
 * 两个 advice，都声明了 {@code @ExceptionHandler(Exception.class)} 家族且都<b>没有优先级</b>，
 * 命中谁取决于 bean 注册顺序 —— 实测命中 Jimu 那一个，于是错误码被整条压成 500。</p>
 *
 * <p>本类断言两件事：</p>
 * <ol>
 *   <li><b>行为</b>：业务错误码必须原样出现在响应体的 {@code code} 里（4010 尤其关键，
 *       越权不能退化成 404 或 500，否则就给了资源探测的口子）；</li>
 *   <li><b>结构</b>：本 advice 必须声明 {@code @Order(HIGHEST_PRECEDENCE)} 且
 *       {@code basePackages} 限定在 study 包下 —— 前者保证稳定抢到处理权，
 *       后者保证不改变上游接口的响应形态。</li>
 * </ol>
 *
 * @author jeecg-study
 */
class StudyExceptionAdviceTest {

    private final StudyExceptionAdvice advice = new StudyExceptionAdvice();

    @Test
    @DisplayName("越权 4010 必须原样返回，不能退化成 500 或 404")
    void forbiddenScopeKeeps4010() {
        Result<?> result = advice.handleJeecgBootException(new StudyForbiddenException());

        assertThat(result.getCode()).isEqualTo(StudyErrorCode.FORBIDDEN_SCOPE);
        assertThat(result.getCode()).isEqualTo(4010);
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).isEqualTo("无权访问该孩子的数据");
    }

    @Test
    @DisplayName("带自定义文案的越权同样返回 4010")
    void forbiddenScopeKeeps4010WithCustomMessage() {
        Result<?> result = advice.handleJeecgBootException(
                new StudyForbiddenException("当前登录身份不是孩子，无权访问学习数据"));

        assertThat(result.getCode()).isEqualTo(StudyErrorCode.FORBIDDEN_SCOPE);
        assertThat(result.getMessage()).isEqualTo("当前登录身份不是孩子，无权访问学习数据");
    }

    @Test
    @DisplayName("其余业务错误码（4001/4004/4009）必须逐个原样返回")
    void businessCodesArePreserved() {
        int[] codes = {
                StudyErrorCode.MISSING_ERROR_CAUSE,
                StudyErrorCode.CONTEXT_NOT_CONFIGURED,
                StudyErrorCode.FAMILY_CODE_INVALID,
                StudyErrorCode.PRACTICE_ORDER_VIOLATION,
                StudyErrorCode.FORBIDDEN_SCOPE
        };

        for (int code : codes) {
            Result<?> result = advice.handleJeecgBootException(new JeecgBootException("业务异常", code));
            assertThat(result.getCode()).isEqualTo(code);
            assertThat(result.getMessage()).isEqualTo("业务异常");
            assertThat(result.isSuccess()).isFalse();
        }
    }

    @Test
    @DisplayName("advice 必须声明最高优先级，否则会被第三方 advice 抢走")
    void adviceMustDeclareHighestPrecedence() {
        Order order = StudyExceptionAdvice.class.getAnnotation(Order.class);

        assertThat(order).isNotNull();
        assertThat(order.value()).isEqualTo(Ordered.HIGHEST_PRECEDENCE);
    }

    @Test
    @DisplayName("advice 必须限定在 study 包下，不得改变上游接口的响应形态")
    void adviceMustBeScopedToStudyPackage() {
        RestControllerAdvice annotation = StudyExceptionAdvice.class.getAnnotation(RestControllerAdvice.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.basePackages()).contains("org.jeecg.modules.study");
    }
}
