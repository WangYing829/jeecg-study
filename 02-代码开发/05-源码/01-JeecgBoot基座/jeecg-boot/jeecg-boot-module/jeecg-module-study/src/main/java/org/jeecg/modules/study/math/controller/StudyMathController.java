package org.jeecg.modules.study.math.controller;

import java.util.List;

import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;

import org.jeecg.common.api.vo.Result;
import org.jeecg.modules.study.math.constant.MathConstants;
import org.jeecg.modules.study.math.service.StudyMathService;
import org.jeecg.modules.study.math.vo.MathProgressVO;
import org.jeecg.modules.study.math.vo.MotherDetailVO;
import org.jeecg.modules.study.math.vo.MotherListItemVO;
import org.jeecg.modules.study.math.vo.MotherPassCommand;
import org.jeecg.modules.study.math.vo.MotherPassResultVO;
import org.jeecg.modules.study.math.vo.MotherSaveCommand;
import org.jeecg.modules.study.math.vo.NarrativeBriefVO;
import org.jeecg.modules.study.math.vo.NarrativeCommand;
import org.jeecg.modules.study.math.vo.NarrativeResultVO;
import org.jeecg.modules.study.math.vo.VariantAnswerCommand;
import org.jeecg.modules.study.math.vo.VariantAnswerResultVO;
import org.jeecg.modules.study.math.vo.VariantGenerateCommand;
import org.jeecg.modules.study.math.vo.VariantGenerateResultVO;
import org.jeecg.modules.study.math.vo.VariantVO;

/**
 * 数学母题模块（F-MTH-01…F-MTH-06）。
 *
 * <p><b>两个硬口径</b>：</p>
 * <ul>
 *   <li><b>不下发答案</b>：变式与母题的响应里都没有答案字段，
 *       答案只在服务端参与判定（R-01）；口述不过关只给追问，不给答案。</li>
 *   <li><b>childId 不作数</b>：孩子身份一律取会话里的自己；
 *       家长身份必须与关系表求交集，越权一律 4010。</li>
 * </ul>
 *
 * @author jeecg-study
 */
@Tag(name = "jeecg-study · 数学母题")
@RestController
@RequestMapping("/api/v1/study/math")
@RequiredArgsConstructor
public class StudyMathController {

    private final StudyMathService mathService;

    @Operation(summary = "闯关顺序列表（考纲权重降序 + 掌握度升序）")
    @RequiresPermissions(MathConstants.PERMISSION_VIEW)
    @GetMapping("/mother/list")
    public Result<List<MotherListItemVO>> list(
            @RequestParam(value = "childId", required = false) String childId) {
        return Result.OK(mathService.motherList(childId));
    }

    @Operation(summary = "母题详情（五段结构）")
    @RequiresPermissions(MathConstants.PERMISSION_VIEW)
    @GetMapping("/mother/{code}")
    public Result<MotherDetailVO> detail(@PathVariable("code") String code) {
        return Result.OK(mathService.motherDetail(code));
    }

    @Operation(summary = "新建母题（五段齐全校验，缺任一段返回 4007）")
    @RequiresPermissions(MathConstants.PERMISSION_EDIT)
    @PostMapping("/mother")
    public Result<MotherDetailVO> create(@RequestBody MotherSaveCommand command) {
        return Result.OK(mathService.saveMother(command));
    }

    @Operation(summary = "修改母题（五段齐全校验）")
    @RequiresPermissions(MathConstants.PERMISSION_EDIT)
    @PutMapping("/mother/{code}")
    public Result<MotherDetailVO> update(@PathVariable("code") String code,
                                          @RequestBody MotherSaveCommand command) {
        return Result.OK(mathService.updateMother(code, command));
    }

    @Operation(summary = "生成三层变式（换数字 / 换情境 / 逆向）")
    @RequiresPermissions(MathConstants.PERMISSION_VIEW)
    @PostMapping("/mother/{code}/variants")
    public Result<VariantGenerateResultVO> generateVariants(@PathVariable("code") String code,
                                                             @RequestBody(required = false)
                                                             VariantGenerateCommand command) {
        String childId = command == null ? null : command.getChildId();
        return Result.OK(mathService.generateVariants(code, childId));
    }

    @Operation(summary = "变式列表（响应不含答案）")
    @RequiresPermissions(MathConstants.PERMISSION_VIEW)
    @GetMapping("/mother/{code}/variants")
    public Result<List<VariantVO>> listVariants(@PathVariable("code") String code,
                                                 @RequestParam(value = "childId", required = false) String childId) {
        return Result.OK(mathService.listVariants(code, childId));
    }

    @Operation(summary = "提交过关判定（变式全对且口述讲出关键关系才过关）")
    @RequiresPermissions(MathConstants.PERMISSION_PASS)
    @PostMapping("/mother/{code}/pass")
    public Result<MotherPassResultVO> pass(@PathVariable("code") String code,
                                            @RequestBody MotherPassCommand command) {
        return Result.OK(mathService.submitPass(code, command));
    }

    @Operation(summary = "取关卡说明（口述任务）")
    @RequiresPermissions(MathConstants.PERMISSION_VIEW)
    @GetMapping("/mother/{code}/narrative")
    public Result<NarrativeBriefVO> narrative(@PathVariable("code") String code) {
        return Result.OK(mathService.narrativeBrief(code));
    }

    @Operation(summary = "提交口述（返回命中 / 缺失关键词与苏格拉底式追问，不给答案）")
    @RequiresPermissions(MathConstants.PERMISSION_PASS)
    @PostMapping("/mother/{code}/narrative")
    public Result<NarrativeResultVO> submitNarrative(@PathVariable("code") String code,
                                                      @RequestBody NarrativeCommand command) {
        return Result.OK(mathService.submitNarrative(code, command));
    }

    @Operation(summary = "变式作答（判定 + 步骤级错因打标 + 错题入池）")
    @RequiresPermissions(MathConstants.PERMISSION_PASS)
    @PostMapping("/mother/{code}/variants/{variantCode}/answer")
    public Result<VariantAnswerResultVO> answer(@PathVariable("code") String code,
                                                 @PathVariable("variantCode") String variantCode,
                                                 @RequestBody VariantAnswerCommand command) {
        return Result.OK(mathService.answerVariant(code, variantCode, command));
    }

    @Operation(summary = "关卡进度（以母题为单位：这类题我会了 N 类）")
    @RequiresPermissions(MathConstants.PERMISSION_VIEW)
    @GetMapping("/progress")
    public Result<MathProgressVO> progress(
            @RequestParam(value = "childId", required = false) String childId) {
        return Result.OK(mathService.progress(childId));
    }
}
