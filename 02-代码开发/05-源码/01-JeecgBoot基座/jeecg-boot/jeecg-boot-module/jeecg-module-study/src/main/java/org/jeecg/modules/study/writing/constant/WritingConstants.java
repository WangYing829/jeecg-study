package org.jeecg.modules.study.writing.constant;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 语文写作域常量。
 *
 * <p>写作域是<b>无 AI 客户端</b>的域（R-02）：批改差异全部由确定性规则算出，
 * 因此 {@link #SOURCE_RULE} 是唯一可能的判定来源。</p>
 *
 * @author jeecg-study
 */
public final class WritingConstants {

    private WritingConstants() {
    }

    /** 对照式批改的差异条数上限（服务端硬上限，只给最该改的三条） */
    public static final int MAX_DIFF_COUNT = 3;

    /** 家长一句话反馈的字数上限 */
    public static final int MAX_FEEDBACK_LENGTH = 100;

    /** 判定来源：写作域恒为 rule（没有 AI 客户端参与判定） */
    public static final String SOURCE_RULE = "rule";

    /** 来源模块：写作 */
    public static final String SOURCE_MODULE = "WRITING";

    /** 内容类型：段落 */
    public static final String CONTENT_TYPE = "PARAGRAPH";

    /** 学科编码：语文 */
    public static final String SUBJECT_CODE = "CN";

    /** 版权类型：自编 */
    public static final String COPYRIGHT_SELF = "SELF";

    /** 反馈对象类型：写作 */
    public static final String TARGET_TYPE_WRITING = "WRITING";

    /** 提纲最少正文段数 */
    public static final int MIN_OUTLINE_BODY = 2;

    /** 作文篇幅下限（有效字数） */
    public static final int MIN_DRAFT_LENGTH = 200;

    /** 作文篇幅上限（有效字数） */
    public static final int MAX_DRAFT_LENGTH = 800;

    /** 单句长度上限（有效字数），超过视为长句过多 */
    public static final int MAX_SENTENCE_LENGTH = 45;

    /** 连接词最少出现次数 */
    public static final int MIN_CONNECTOR_COUNT = 3;

    /** 细节词最少出现次数 */
    public static final int MIN_DETAIL_WORD_COUNT = 2;

    /** 提纲通过后的下一步 */
    public static final String NEXT_STEP_DRAFT = "DRAFT";

    /** 提纲未通过时的下一步 */
    public static final String NEXT_STEP_OUTLINE = "OUTLINE";

    /** 拆解库分组的编码前缀（拆解集编码，如 DS-0001） */
    public static final String DISSECTION_CODE_PREFIX = "DS";

    /**
     * 确定性摘要（12 位十六进制）。
     *
     * <p>用于生成<b>稳定可复现</b>的业务编码：同一孩子 + 同一业务键
     * 永远得到同一个编码，靠数据库唯一索引兜住「重复提交不产生重复行」。</p>
     *
     * @param input 业务键
     * @return 12 位十六进制摘要
     */
    public static String digest12(String input) {
        return hex(input, 6);
    }

    /**
     * 确定性摘要（6 位十六进制）。
     *
     * @param input 业务键
     * @return 6 位十六进制摘要
     */
    public static String digest6(String input) {
        return hex(input, 3);
    }

    private static String hex(String input, int byteCount) {
        if (input == null) {
            return "000000000000".substring(0, byteCount * 2);
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < byteCount && i < bytes.length; i++) {
                sb.append(String.format("%02x", bytes[i]));
            }
            return sb.toString();
        } catch (Exception e) {
            return String.format("%0" + (byteCount * 2) + "x", input.hashCode() & 0x7fffffff);
        }
    }
}
