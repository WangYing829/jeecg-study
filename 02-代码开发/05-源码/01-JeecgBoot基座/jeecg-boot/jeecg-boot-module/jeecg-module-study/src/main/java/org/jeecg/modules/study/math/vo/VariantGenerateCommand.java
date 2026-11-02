package org.jeecg.modules.study.math.vo;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 生成变式入参。
 *
 * <p>{@code childId} 只在家长 / 教师身份下有意义：服务端会拿它和
 * 「家长↔孩子关系表」求交集，越权一律 4010；
 * 孩子身份下前端传什么都不算数，一律按会话里的自己算。</p>
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "生成变式入参")
public class VariantGenerateCommand implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 目标孩子 ID（可空；孩子身份下忽略） */
    @Schema(description = "目标孩子 ID")
    private String childId;
}
