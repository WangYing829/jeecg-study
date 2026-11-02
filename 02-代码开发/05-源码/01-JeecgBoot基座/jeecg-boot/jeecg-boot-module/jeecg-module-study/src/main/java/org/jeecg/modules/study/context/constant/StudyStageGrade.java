package org.jeecg.modules.study.context.constant;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 学段 / 年级 / 省级行政区字典。
 *
 * <p>学段与年级的对应关系是硬规则（TC-01-03）：</p>
 * <ul>
 *   <li>{@code PRIMARY} 小学 → G1..G6</li>
 *   <li>{@code JUNIOR} 初中 → G7..G9</li>
 *   <li>{@code SENIOR} 高中 → G10..G12</li>
 * </ul>
 * <p>年级必须落在所属学段内，非法组合一律 4004（上下文未配置）。</p>
 *
 * @author jeecg-study
 */
public final class StudyStageGrade {

    /** 学段：小学 */
    public static final String STAGE_PRIMARY = "PRIMARY";

    /** 学段：初中 */
    public static final String STAGE_JUNIOR = "JUNIOR";

    /** 学段：高中 */
    public static final String STAGE_SENIOR = "SENIOR";

    /** 小学年级 */
    public static final List<String> GRADES_PRIMARY =
            Collections.unmodifiableList(Arrays.asList("G1", "G2", "G3", "G4", "G5", "G6"));

    /** 初中年级 */
    public static final List<String> GRADES_JUNIOR =
            Collections.unmodifiableList(Arrays.asList("G7", "G8", "G9"));

    /** 高中年级 */
    public static final List<String> GRADES_SENIOR =
            Collections.unmodifiableList(Arrays.asList("G10", "G11", "G12"));

    /** 学段名称 */
    private static final Map<String, String> STAGE_NAMES = new LinkedHashMap<>();

    /** 学段 → 允许的年级 */
    private static final Map<String, List<String>> STAGE_GRADES = new LinkedHashMap<>();

    /** 年级 → 年级名称 */
    private static final Map<String, String> GRADE_NAMES = new LinkedHashMap<>();

    /** 省级行政区编码 → 名称 */
    private static final Map<String, String> PROVINCE_NAMES = new LinkedHashMap<>();

    static {
        STAGE_NAMES.put(STAGE_PRIMARY, "小学");
        STAGE_NAMES.put(STAGE_JUNIOR, "初中");
        STAGE_NAMES.put(STAGE_SENIOR, "高中");

        STAGE_GRADES.put(STAGE_PRIMARY, GRADES_PRIMARY);
        STAGE_GRADES.put(STAGE_JUNIOR, GRADES_JUNIOR);
        STAGE_GRADES.put(STAGE_SENIOR, GRADES_SENIOR);

        for (int i = 1; i <= 12; i++) {
            GRADE_NAMES.put("G" + i, gradeName(i));
        }

        PROVINCE_NAMES.put("11", "北京市");
        PROVINCE_NAMES.put("12", "天津市");
        PROVINCE_NAMES.put("13", "河北省");
        PROVINCE_NAMES.put("14", "山西省");
        PROVINCE_NAMES.put("15", "内蒙古自治区");
        PROVINCE_NAMES.put("21", "辽宁省");
        PROVINCE_NAMES.put("22", "吉林省");
        PROVINCE_NAMES.put("23", "黑龙江省");
        PROVINCE_NAMES.put("31", "上海市");
        PROVINCE_NAMES.put("32", "江苏省");
        PROVINCE_NAMES.put("33", "浙江省");
        PROVINCE_NAMES.put("34", "安徽省");
        PROVINCE_NAMES.put("35", "福建省");
        PROVINCE_NAMES.put("36", "江西省");
        PROVINCE_NAMES.put("37", "山东省");
        PROVINCE_NAMES.put("41", "河南省");
        PROVINCE_NAMES.put("42", "湖北省");
        PROVINCE_NAMES.put("43", "湖南省");
        PROVINCE_NAMES.put("44", "广东省");
        PROVINCE_NAMES.put("45", "广西壮族自治区");
        PROVINCE_NAMES.put("46", "海南省");
        PROVINCE_NAMES.put("50", "重庆市");
        PROVINCE_NAMES.put("51", "四川省");
        PROVINCE_NAMES.put("52", "贵州省");
        PROVINCE_NAMES.put("53", "云南省");
        PROVINCE_NAMES.put("54", "西藏自治区");
        PROVINCE_NAMES.put("61", "陕西省");
        PROVINCE_NAMES.put("62", "甘肃省");
        PROVINCE_NAMES.put("63", "青海省");
        PROVINCE_NAMES.put("64", "宁夏回族自治区");
        PROVINCE_NAMES.put("65", "新疆维吾尔自治区");
        PROVINCE_NAMES.put("71", "台湾省");
        PROVINCE_NAMES.put("81", "香港特别行政区");
        PROVINCE_NAMES.put("82", "澳门特别行政区");
    }

    private StudyStageGrade() {
    }

    /**
     * 全部学段编码。
     */
    public static List<String> allStages() {
        return Collections.unmodifiableList(new ArrayList<>(STAGE_NAMES.keySet()));
    }

    /**
     * 学段编码是否合法。
     *
     * @param stageCode 学段编码
     * @return 合法返回 true
     */
    public static boolean isValidStage(String stageCode) {
        return stageCode != null && STAGE_NAMES.containsKey(stageCode.trim().toUpperCase());
    }

    /**
     * 取某学段允许的年级列表。
     *
     * @param stageCode 学段编码
     * @return 年级列表；学段非法返回空列表
     */
    public static List<String> gradesOf(String stageCode) {
        if (stageCode == null) {
            return Collections.emptyList();
        }
        List<String> grades = STAGE_GRADES.get(stageCode.trim().toUpperCase());
        return grades == null ? Collections.emptyList() : grades;
    }

    /**
     * 判断年级是否落在所属学段内。
     *
     * @param stageCode 学段编码
     * @param gradeCode 年级编码
     * @return 落在学段内返回 true
     */
    public static boolean gradeInStage(String stageCode, String gradeCode) {
        if (stageCode == null || gradeCode == null) {
            return false;
        }
        return gradesOf(stageCode).contains(gradeCode.trim().toUpperCase());
    }

    /**
     * 学段名称。
     *
     * @param stageCode 学段编码
     * @return 名称；未知返回编码本身
     */
    public static String stageName(String stageCode) {
        if (stageCode == null) {
            return null;
        }
        String key = stageCode.trim().toUpperCase();
        String name = STAGE_NAMES.get(key);
        return name == null ? key : name;
    }

    /**
     * 年级名称。
     *
     * @param gradeCode 年级编码
     * @return 名称；未知返回编码本身
     */
    public static String gradeName(String gradeCode) {
        if (gradeCode == null) {
            return null;
        }
        String key = gradeCode.trim().toUpperCase();
        String name = GRADE_NAMES.get(key);
        return name == null ? key : name;
    }

    /**
     * 省级行政区名称。
     *
     * @param provinceCode 省级行政区编码
     * @return 名称；未知返回编码本身
     */
    public static String provinceName(String provinceCode) {
        if (provinceCode == null) {
            return null;
        }
        String key = provinceCode.trim();
        String name = PROVINCE_NAMES.get(key);
        return name == null ? key : name;
    }

    private static String gradeName(int grade) {
        switch (grade) {
            case 1:
                return "一年级";
            case 2:
                return "二年级";
            case 3:
                return "三年级";
            case 4:
                return "四年级";
            case 5:
                return "五年级";
            case 6:
                return "六年级";
            case 7:
                return "七年级";
            case 8:
                return "八年级";
            case 9:
                return "九年级";
            case 10:
                return "十年级";
            case 11:
                return "十一年级";
            case 12:
                return "十二年级";
            default:
                return "G" + grade;
        }
    }
}
