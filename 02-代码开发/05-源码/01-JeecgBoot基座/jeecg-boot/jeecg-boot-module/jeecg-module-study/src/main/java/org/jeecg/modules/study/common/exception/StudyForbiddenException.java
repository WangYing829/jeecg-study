package org.jeecg.modules.study.common.exception;

import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.study.common.constant.StudyErrorCode;

/**
 * 越权异常（4010）。
 *
 * <p>统一用它表达「越权」，不用 404 —— 见 {@link StudyErrorCode#FORBIDDEN_SCOPE} 的说明。</p>
 *
 * @author jeecg-study
 */
public class StudyForbiddenException extends JeecgBootException {

    private static final long serialVersionUID = 1L;

    public StudyForbiddenException(String message) {
        super(message, StudyErrorCode.FORBIDDEN_SCOPE);
    }

    public StudyForbiddenException() {
        this("无权访问该孩子的数据");
    }
}
