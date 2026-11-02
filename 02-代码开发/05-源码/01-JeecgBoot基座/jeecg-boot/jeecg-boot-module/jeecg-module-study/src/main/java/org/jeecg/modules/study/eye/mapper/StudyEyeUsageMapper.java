package org.jeecg.modules.study.eye.mapper;

import java.util.Date;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import org.jeecg.modules.study.eye.entity.StudyEyeUsage;

/**
 * 用眼记录 Mapper。
 *
 * <p>心跳写入用 CAS：{@code heartbeat_count} 充当乐观版本号，
 * 两个并发心跳不会把同一次心跳算两遍（TC-08-08 的同款口径）。</p>
 *
 * @author jeecg-study
 */
@Mapper
public interface StudyEyeUsageMapper extends BaseMapper<StudyEyeUsage> {

    /**
     * 新建用眼会话。
     *
     * <p>归属日用数据库的 {@code CURDATE()} 落库，避免应用与数据库时区不一致时日期漂移。</p>
     *
     * @param usage 用眼记录（id / child_id / session_id 等由调用方填好）
     * @return 影响行数
     */
    @Insert("INSERT INTO study_eye_usage (id, child_id, context_id, session_id, start_at, active_minutes,"
            + " heartbeat_count, rest_count, rest_confirmed, blue_light_on, usage_date, del_flag, create_by,"
            + " create_time, update_by, update_time)"
            + " VALUES (#{id}, #{childId}, #{contextId}, #{sessionId}, #{startAt}, #{activeMinutes},"
            + " #{heartbeatCount}, #{restCount}, #{restConfirmed}, #{blueLightOn}, CURDATE(), #{delFlag},"
            + " #{createBy}, #{createTime}, #{updateBy}, #{updateTime})")
    int insertSession(StudyEyeUsage usage);

    /**
     * 数据库当前日期。
     *
     * @return 当前日期
     */
    @Select("SELECT CURDATE()")
    java.sql.Date currentDate();

    /**
     * 按会话 ID 查用眼记录。
     *
     * @param sessionId 会话 ID
     * @return 用眼记录；不存在返回 null
     */
    @Select("SELECT * FROM study_eye_usage WHERE session_id = #{sessionId} AND del_flag = 0 LIMIT 1")
    StudyEyeUsage selectBySessionId(@Param("sessionId") String sessionId);

    /**
     * 查该孩子最近一次会话的蓝光过滤状态（刷新页面后据此恢复开关）。
     *
     * @param childId 孩子 ID
     * @return 0关 1开；没有记录返回 null
     */
    @Select("SELECT blue_light_on FROM study_eye_usage WHERE child_id = #{childId} AND del_flag = 0"
            + " ORDER BY create_time DESC, id DESC LIMIT 1")
    Integer selectLatestBlueLightOn(@Param("childId") String childId);

    /**
     * 查孩子的当前上下文 ID。
     *
     * @param childId 孩子 ID
     * @return 上下文 ID；未配置返回 null
     */
    @Select("SELECT id FROM study_context WHERE child_id = #{childId} AND is_current = 1 AND del_flag = 0"
            + " ORDER BY effective_from DESC, id DESC LIMIT 1")
    String selectCurrentContextId(@Param("childId") String childId);

    /**
     * 查孩子当前上下文里配置的护眼提醒间隔（分钟）。
     *
     * @param childId 孩子 ID
     * @return 间隔分钟数；未配置返回 null
     */
    @Select("SELECT eye_interval_min FROM study_context WHERE child_id = #{childId}"
            + " AND is_current = 1 AND del_flag = 0 ORDER BY effective_from DESC, id DESC LIMIT 1")
    Integer selectCurrentEyeIntervalMin(@Param("childId") String childId);

    /**
     * 心跳写入（CAS）。
     *
     * <p>只推进 {@code update_time}（服务端 lastHeartbeat 基线），
     * 客户端上报的时长一律不参与。</p>
     *
     * @param id                     用眼记录主键
     * @param expectedHeartbeatCount 期望的旧心跳次数（乐观版本号）
     * @param heartbeatCount         新的心跳次数
     * @param activeMinutes          新的累计有效用眼分钟
     * @param restCount              新的休息提醒触发次数
     * @param restConfirmed          新的休息确认次数
     * @param blueLightOn            蓝光过滤状态 0关 1开
     * @param updateTime             服务端最近一次心跳时间
     * @return 影响行数；0 表示并发冲突
     */
    @Update("UPDATE study_eye_usage SET heartbeat_count = #{heartbeatCount}, active_minutes = #{activeMinutes},"
            + " rest_count = #{restCount}, rest_confirmed = #{restConfirmed}, blue_light_on = #{blueLightOn},"
            + " update_time = #{updateTime}"
            + " WHERE id = #{id} AND heartbeat_count = #{expectedHeartbeatCount} AND del_flag = 0")
    int updateHeartbeat(@Param("id") String id,
                        @Param("expectedHeartbeatCount") int expectedHeartbeatCount,
                        @Param("heartbeatCount") int heartbeatCount,
                        @Param("activeMinutes") int activeMinutes,
                        @Param("restCount") int restCount,
                        @Param("restConfirmed") int restConfirmed,
                        @Param("blueLightOn") int blueLightOn,
                        @Param("updateTime") Date updateTime);

    /**
     * 记录一次休息确认（清除阻断遮罩的唯一入口）。
     *
     * <p><b>不推进 {@code update_time}</b>：该列是 lastHeartbeat 基线，
     * 休息确认不是心跳，不应该打断基线。</p>
     *
     * @param id            用眼记录主键
     * @param restConfirmed 新的休息确认次数
     * @return 影响行数
     */
    @Update("UPDATE study_eye_usage SET rest_confirmed = #{restConfirmed}"
            + " WHERE id = #{id} AND del_flag = 0")
    int updateRestConfirmed(@Param("id") String id, @Param("restConfirmed") int restConfirmed);

    /**
     * 结束会话。
     *
     * @param id        用眼记录主键
     * @param endAt     会话结束时间
     * @param updateTime 服务端最近一次心跳时间
     * @return 影响行数
     */
    @Update("UPDATE study_eye_usage SET end_at = #{endAt}, update_time = #{updateTime}"
            + " WHERE id = #{id} AND del_flag = 0")
    int closeSession(@Param("id") String id, @Param("endAt") Date endAt, @Param("updateTime") Date updateTime);
}
