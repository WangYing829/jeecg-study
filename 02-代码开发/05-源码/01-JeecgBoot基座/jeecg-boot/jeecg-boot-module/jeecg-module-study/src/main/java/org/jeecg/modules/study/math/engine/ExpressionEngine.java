package org.jeecg.modules.study.math.engine;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 受限表达式引擎：只支持 {@code + - * / ( )} 与标识符、字面量。
 *
 * <p><b>为什么要有它</b>：母题第 4 段的「解题步骤链」里写的就是算式，
 * 变式答案必须由<b>这条算式 + 替换后的参数</b>算出来，
 * 才谈得上「答案由规则产出而非模型」（R-02 / TC-04-04）。
 * 有了仿射分析还能做第 3 层「逆向提问」（已知结果求条件）。</p>
 *
 * <p>本类<b>不依赖任何 AI 客户端</b>，全部是确定性算术。</p>
 *
 * @author jeecg-study
 */
public final class ExpressionEngine {

    /** 除法保留位数 */
    private static final int DIVIDE_SCALE = 12;

    private ExpressionEngine() {
    }

    /**
     * 求值。
     *
     * @param expression 表达式
     * @param params     参数表（标识符 → 数值）
     * @return 表达式取值
     */
    public static BigDecimal evaluate(String expression, Map<String, BigDecimal> params) {
        return parse(expression).eval(params);
    }

    /**
     * 仿射分析：表达式能否写成 {@code a·x + b}。
     *
     * @param expression 表达式
     * @param variable   变量名
     * @param params     其它参数的取值
     * @return 仿射形式；不是仿射时返回 null
     */
    public static AffineForm affine(String expression, String variable, Map<String, BigDecimal> params) {
        return parse(expression).affine(variable, params);
    }

    /**
     * 表达式里出现的全部标识符（按出现顺序）。
     *
     * @param expression 表达式
     * @return 标识符集合
     */
    public static Set<String> identifiers(String expression) {
        Set<String> out = new LinkedHashSet<>();
        parse(expression).collectIdentifiers(out);
        return out;
    }

    // ------------------------------------------------------------------ 内部：解析

    private static Node parse(String expression) {
        if (expression == null || expression.trim().isEmpty()) {
            throw new IllegalArgumentException("公式不能为空");
        }
        Parser parser = new Parser(expression);
        Node node = parser.parseExpression();
        parser.skipSpaces();
        if (!parser.eof()) {
            throw new IllegalArgumentException("公式无法解析：" + expression);
        }
        return node;
    }

    /** 表达式节点 */
    private interface Node {

        BigDecimal eval(Map<String, BigDecimal> params);

        AffineForm affine(String variable, Map<String, BigDecimal> params);

        void collectIdentifiers(Set<String> out);
    }

    /** 字面量 */
    private static final class NumberNode implements Node {

        private final BigDecimal value;

        NumberNode(BigDecimal value) {
            this.value = value;
        }

        @Override
        public BigDecimal eval(Map<String, BigDecimal> params) {
            return value;
        }

        @Override
        public AffineForm affine(String variable, Map<String, BigDecimal> params) {
            return AffineForm.constant(value);
        }

        @Override
        public void collectIdentifiers(Set<String> out) {
            // 无标识符
        }
    }

    /** 变量 */
    private static final class VariableNode implements Node {

        private final String name;

        VariableNode(String name) {
            this.name = name;
        }

        @Override
        public BigDecimal eval(Map<String, BigDecimal> params) {
            BigDecimal value = params.get(name);
            if (value == null) {
                throw new IllegalArgumentException("公式缺少参数：" + name);
            }
            return value;
        }

        @Override
        public AffineForm affine(String variable, Map<String, BigDecimal> params) {
            if (name.equals(variable)) {
                return new AffineForm(BigDecimal.ONE, BigDecimal.ZERO);
            }
            BigDecimal value = params.get(name);
            if (value == null) {
                throw new IllegalArgumentException("公式缺少参数：" + name);
            }
            return AffineForm.constant(value);
        }

        @Override
        public void collectIdentifiers(Set<String> out) {
            out.add(name);
        }
    }

    /** 取负 */
    private static final class NegNode implements Node {

        private final Node operand;

        NegNode(Node operand) {
            this.operand = operand;
        }

        @Override
        public BigDecimal eval(Map<String, BigDecimal> params) {
            return operand.eval(params).negate();
        }

        @Override
        public AffineForm affine(String variable, Map<String, BigDecimal> params) {
            AffineForm form = operand.affine(variable, params);
            if (form == null) {
                return null;
            }
            return new AffineForm(form.getA().negate(), form.getB().negate());
        }

        @Override
        public void collectIdentifiers(Set<String> out) {
            operand.collectIdentifiers(out);
        }
    }

    /** 二元运算 */
    private static final class BinaryNode implements Node {

        private final char operator;

        private final Node left;

        private final Node right;

        BinaryNode(char operator, Node left, Node right) {
            this.operator = operator;
            this.left = left;
            this.right = right;
        }

        @Override
        public BigDecimal eval(Map<String, BigDecimal> params) {
            BigDecimal l = left.eval(params);
            BigDecimal r = right.eval(params);
            switch (operator) {
                case '+':
                    return l.add(r);
                case '-':
                    return l.subtract(r);
                case '*':
                    return l.multiply(r);
                case '/':
                    if (r.compareTo(BigDecimal.ZERO) == 0) {
                        throw new IllegalArgumentException("公式里出现除以零");
                    }
                    return l.divide(r, DIVIDE_SCALE, RoundingMode.HALF_UP);
                default:
                    throw new IllegalArgumentException("无法识别的运算符：" + operator);
            }
        }

        @Override
        public AffineForm affine(String variable, Map<String, BigDecimal> params) {
            AffineForm l = left.affine(variable, params);
            AffineForm r = right.affine(variable, params);
            if (l == null || r == null) {
                return null;
            }
            switch (operator) {
                case '+':
                    return new AffineForm(l.getA().add(r.getA()), l.getB().add(r.getB()));
                case '-':
                    return new AffineForm(l.getA().subtract(r.getA()), l.getB().subtract(r.getB()));
                case '*':
                    // 乘积是仿射的充要条件：至少一边与变量无关
                    if (l.isConstant()) {
                        return new AffineForm(l.getB().multiply(r.getA()), l.getB().multiply(r.getB()));
                    }
                    if (r.isConstant()) {
                        return new AffineForm(l.getA().multiply(r.getB()), l.getB().multiply(r.getB()));
                    }
                    return null;
                case '/':
                    // 分母与变量无关时商仍是仿射的
                    if (r.isConstant() && r.getB().compareTo(BigDecimal.ZERO) != 0) {
                        return new AffineForm(l.getA().divide(r.getB(), DIVIDE_SCALE, RoundingMode.HALF_UP),
                                l.getB().divide(r.getB(), DIVIDE_SCALE, RoundingMode.HALF_UP));
                    }
                    return null;
                default:
                    return null;
            }
        }

        @Override
        public void collectIdentifiers(Set<String> out) {
            left.collectIdentifiers(out);
            right.collectIdentifiers(out);
        }
    }

    /** 递归下降解析器 */
    private static final class Parser {

        private final String text;

        private int pos;

        Parser(String text) {
            this.text = text;
        }

        boolean eof() {
            return pos >= text.length();
        }

        void skipSpaces() {
            while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
                pos++;
            }
        }

        Node parseExpression() {
            Node node = parseTerm();
            while (true) {
                skipSpaces();
                if (eof()) {
                    return node;
                }
                char c = text.charAt(pos);
                if (c != '+' && c != '-') {
                    return node;
                }
                pos++;
                node = new BinaryNode(c, node, parseTerm());
            }
        }

        Node parseTerm() {
            Node node = parseFactor();
            while (true) {
                skipSpaces();
                if (eof()) {
                    return node;
                }
                char c = text.charAt(pos);
                if (c != '*' && c != '/') {
                    return node;
                }
                pos++;
                node = new BinaryNode(c, node, parseFactor());
            }
        }

        Node parseFactor() {
            skipSpaces();
            if (eof()) {
                throw new IllegalArgumentException("公式不完整");
            }
            char c = text.charAt(pos);
            if (c == '-') {
                pos++;
                return new NegNode(parseFactor());
            }
            if (c == '+') {
                pos++;
                return parseFactor();
            }
            return parsePrimary();
        }

        Node parsePrimary() {
            skipSpaces();
            if (eof()) {
                throw new IllegalArgumentException("公式不完整");
            }
            char c = text.charAt(pos);
            if (c == '(') {
                pos++;
                Node node = parseExpression();
                skipSpaces();
                if (eof() || text.charAt(pos) != ')') {
                    throw new IllegalArgumentException("公式里括号不匹配");
                }
                pos++;
                return node;
            }
            if (Character.isDigit(c) || c == '.') {
                int start = pos;
                while (pos < text.length()
                        && (Character.isDigit(text.charAt(pos)) || text.charAt(pos) == '.')) {
                    pos++;
                }
                return new NumberNode(new BigDecimal(text.substring(start, pos)));
            }
            if (Character.isLetter(c) || c == '_') {
                int start = pos;
                while (pos < text.length()
                        && (Character.isLetterOrDigit(text.charAt(pos)) || text.charAt(pos) == '_')) {
                    pos++;
                }
                return new VariableNode(text.substring(start, pos));
            }
            throw new IllegalArgumentException("公式里有无法识别的字符：" + c);
        }
    }
}
