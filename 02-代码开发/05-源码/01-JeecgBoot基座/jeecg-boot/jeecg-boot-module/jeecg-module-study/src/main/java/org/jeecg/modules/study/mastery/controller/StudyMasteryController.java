package org.jeecg.modules.study.mastery.controller;

import java.util.List;

import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;

import org.jeecg.common.api.vo.Result;
import org.jeecg.modules.study.mastery.service.StudyMasteryService;
import org.jeecg.modules.study.mastery.vo.MasteryPointVO;
import org.jeecg.modules.study.mastery.vo.MasteryWeakPointVO;

/**
 * 考点掌握度接口（F-PAR-01 / F-FAM-07）。
 *
 * <p><b>数据范围</b>：家长侧传 {@code childCode}，服务端与关系表求交集；
 * 孩子侧不传就是自己。越权一律 4010。</p>
 *
 * <p><b>只给客观状态</b>（R-04）：响应里没有名次类字段，
 * 也没有答案、解析与讲解字段。</p>
 *
 * @author jeecg-study
 */
@Tag(name = "jeecg-study · 考点掌握度")
@RestController
@RequestMapping("/api/v1/study/mastery")
@RequiredArgsConstructor
public class StudyMasteryController {

    private final StudyMasteryService masteryService;

    @Operation(summary = "薄弱考点清单（按错题条数从多到少，只给客观状态）")
    @RequiresPermissions({"study:parent:report:view", "study:kid:error:query"})
    @GetMapping("/weak-points")
    public Result<List<MasteryWeakPointVO>> weakPoints(
            @RequestParam(value = "childCode", required = false) String childCode,
            @RequestParam(value = "limit", required = false) Integer limit) {
        return Result.OK(masteryService.weakPoints(childCode, limit == null ? 0 : limit));
    }

    @Operation(summary = "某个考点的掌握度（含错因分布）")
    @RequiresPermissions({"study:parent:report:view", "study:kid:error:query"})
    @GetMapping("/{pointCode}")
    public Result<MasteryPointVO> point(@PathVariable("pointCode") String pointCode,
                                         @RequestParam(value = "childCode", required = false) String childCode) {
        return Result.OK(masteryService.point(childCode, pointCode));
    }
}
