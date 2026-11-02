package org.jeecg.modules.study.kid.vo;

import java.io.Serializable;
import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * 孩子端候选列表项（**只暴露这三个字段**）
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "孩子端候选列表项（**只暴露这三个字段**）")
public class KidCandidateVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 孩子 ID */
    @Schema(description = "孩子 ID")
    private String id;

    /** 昵称 */
    @Schema(description = "昵称")
    private String nickname;

    /** 头像 */
    @Schema(description = "头像")
    private String avatar;

}
