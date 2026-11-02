package org.jeecg.modules.study.word.vo;

import java.io.Serializable;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 熟词测试入参。
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "熟词测试入参")
public class WordKnownTestRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 线别 IN / OUT。
     *
     * <p>熟词标记落在内容表 {@code study_word.known_flag} 上（内容级、全局共享），
     * 因此本入参不带孩子标识。</p>
     */
    @Schema(description = "线别")
    private String lineType;

    /** 判定为「已掌握」的单词业务ID 集合 */
    @Schema(description = "已掌握的单词业务ID")
    private List<String> wordCodes;
}
