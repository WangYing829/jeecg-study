package org.jeecg.modules.study.registry.vo;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 坐标树全树响应。
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "坐标树全树响应")
public class KnowledgePointTreeVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 当前考纲版本 */
    @Schema(description = "当前考纲版本")
    private String version;

    /** 最后更新时间 */
    @Schema(description = "最后更新时间")
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updatedAt;

    /** 根节点列表 */
    @Schema(description = "根节点列表")
    private java.util.List<KnowledgePointVO> nodes = new ArrayList<>();

}
