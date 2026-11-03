package org.jeecg.modules.study.common.record.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import org.jeecg.modules.study.common.record.StudyRecordTableGuard;

/**
 * 作答记录<b>按月分表</b>的建表 Mapper —— <b>运行期建表 DDL 的唯一来源</b>。
 *
 * <p><b>为什么要运行期建表</b>：作答记录按月分表（{@code study_practice_record_yyyyMM}），
 * 运维的月度任务（{@code 02-代码开发/02-数据库/03-脚本/05-partition.sql}）负责建表，
 * 但<b>任务没跑</b>、<b>任务跑失败</b>、<b>跨月首日 00:00 那一瞬间</b>都会让当月表不存在。
 * 表不存在时，<b>读也挂</b>——练习域「取一道孩子没做过的题」这条 SQL 里有
 * {@code NOT EXISTS (SELECT 1 FROM ${tableName} ...)} 子查询，
 * 表不存在 ⇒ 取题直接失败 ⇒ 孩子连题都拿不到，不是「只是记不上分」。</p>
 *
 * <p><b>所以这里做幂等的「不存在才建」</b>：表结构与《数据库设计说明书》、
 * {@code 05-partition.sql} 完全一致（三处必须同步，见 ADR-045）。</p>
 *
 * <p><b>DDL 单一真源</b>：这段 DDL 曾经只存在于 {@code StudyMathRecordMapper} 里，
 * 于是只有数学域有兜底、练习域与背单词域没有 —— 这是 2026-11-03 排查出的
 * 跨月必炸的隐患（ADR-045）。现在 DDL 只此一份，其余域一律经
 * {@link StudyRecordTableGuard} 拿表名。</p>
 *
 * <p><b>包路径不是随手定的</b>：基座生产环境的 MyBatis 扫描范围是
 * {@code @MapperScan(value={"org.jeecg.**.mapper*"})} —— 只扫<b>包名以 mapper 开头</b>的目录。
 * 本接口若放在 {@code common.record} 下，<b>生产环境不会被注册</b>，
 * {@link StudyRecordTableGuard} 注入不到它，应用启动即失败。
 * 所以它必须待在 {@code ...record.mapper} 里（ADR-045）。</p>
 *
 * @author jeecg-study
 */
@Mapper
public interface StudyRecordTableMapper {

    /**
     * 幂等建当月分表（不存在才建）。
     *
     * <p>表名来自服务端时钟，不接受任何外部输入，因此 {@code ${tableName}} 拼接是安全的。</p>
     *
     * @param tableName 月表名（如 {@code study_practice_record_202611}）
     * @return 影响行数
     */
    @Update("CREATE TABLE IF NOT EXISTS `${tableName}` ("
            + "`id` varchar(32) NOT NULL COMMENT '主键',"
            + "`child_id` varchar(32) DEFAULT NULL COMMENT '所属孩子 study_child.id(多用户隔离)',"
            + "`context_id` varchar(32) NOT NULL COMMENT '全局上下文',"
            + "`source_module` varchar(16) NOT NULL COMMENT '来源模块',"
            + "`content_type` varchar(24) NOT NULL COMMENT '内容类型',"
            + "`content_id` varchar(32) NOT NULL COMMENT '内容业务ID',"
            + "`knowledge_point_code` varchar(32) DEFAULT NULL COMMENT '考点业务ID',"
            + "`answer` varchar(1024) DEFAULT NULL COMMENT '孩子作答',"
            + "`correct` tinyint NOT NULL COMMENT '是否正确',"
            + "`judge_source` varchar(16) NOT NULL DEFAULT 'rule' COMMENT '判定来源',"
            + "`error_cause` varchar(16) DEFAULT NULL COMMENT '错因',"
            + "`error_code` varchar(32) DEFAULT NULL COMMENT '错题业务ID',"
            + "`duration_ms` int DEFAULT NULL COMMENT '耗时毫秒',"
            + "`step_tags` json DEFAULT NULL COMMENT '步骤错因打标',"
            + "`practice_date` date NOT NULL COMMENT '作答日期',"
            + "`create_by` varchar(50) DEFAULT NULL COMMENT '创建人',"
            + "`create_time` datetime DEFAULT NULL COMMENT '创建时间',"
            + "`update_by` varchar(50) DEFAULT NULL COMMENT '更新人',"
            + "`update_time` datetime DEFAULT NULL COMMENT '更新时间',"
            + "`del_flag` tinyint NOT NULL DEFAULT 0 COMMENT '删除标记 0正常 1删除',"
            + "PRIMARY KEY (`id`),"
            + "KEY `idx_record_child` (`child_id`, `del_flag`),"
            + "KEY `idx_pr_date` (`practice_date`, `source_module`),"
            + "KEY `idx_pr_content` (`content_type`, `content_id`),"
            + "KEY `idx_pr_kp` (`knowledge_point_code`),"
            + "KEY `idx_pr_error` (`error_code`)"
            + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='作答记录(按月分表)'")
    int createTableIfAbsent(@Param("tableName") String tableName);
}
