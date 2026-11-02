package org.jeecg.modules.study;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * jeecg-study 模块的<b>测试用</b>启动类。
 *
 * <p>刻意只扫描 {@code org.jeecg.modules.study}：把底座（Shiro / Quartz / 平台服务 /
 * 动态数据源等）排除在测试上下文之外，让测试只依赖本模块的 Bean，
 * 避免上游基座的自动装配把测试拖成一整套应用启动。</p>
 *
 * <p>排除的上游自动配置：</p>
 * <ul>
 *   <li>Shiro 三件套 —— 测试不启动 Web 过滤器链，也不需要 Realm；
 *       登录身份用 {@code ThreadContext} 绑 Mock Subject 模拟（见 {@code StudyTestSupport}）。</li>
 *   <li>LiteFlow —— 流程引擎与本模块无关。</li>
 * </ul>
 *
 * <p>底座能力（用户、角色、Redis）在测试里用 Mock 顶替：
 * 见 {@code support/StudyTestSupport}。</p>
 *
 * @author jeecg-study
 */
@SpringBootApplication(
        scanBasePackages = "org.jeecg.modules.study",
        excludeName = {
                "org.apache.shiro.spring.boot.autoconfigure.ShiroAutoConfiguration",
                "org.apache.shiro.spring.boot.autoconfigure.ShiroBeanAutoConfiguration",
                "org.apache.shiro.spring.boot.autoconfigure.ShiroAnnotationProcessorAutoConfiguration",
                "org.apache.shiro.spring.config.web.autoconfigure.ShiroWebAutoConfiguration",
                "org.apache.shiro.spring.config.web.autoconfigure.ShiroWebFilterConfiguration",
                "org.apache.shiro.spring.config.web.autoconfigure.ShiroWebMvcAutoConfiguration",
                "com.yomahub.liteflow.springboot.config.LiteflowMainAutoConfiguration"
        })
/**
 * 只注册带 {@code @Mapper} 注解的接口。
 *
 * <p>不写 {@code basePackages} 通配是故意的：{@code @MapperScan} 默认会把包下<b>所有接口</b>
 * 都当成 Mapper 注册，Service 接口会被误注册成 MapperFactoryBean，
 * 导致「found 2: xxxServiceImpl, xxxService」这类重复 Bean 错误。</p>
 */
@MapperScan(basePackages = "org.jeecg.modules.study", annotationClass = Mapper.class)
public class StudyTestApplication {
}
