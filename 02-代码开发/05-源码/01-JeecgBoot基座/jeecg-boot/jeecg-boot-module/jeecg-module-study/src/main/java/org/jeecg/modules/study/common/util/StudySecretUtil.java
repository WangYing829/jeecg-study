package org.jeecg.modules.study.common.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;

import javax.crypto.Mac;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * jeecg-study 密钥工具：家庭码 / PIN 的生成、哈希与校验。
 *
 * <p><b>为什么不用底座的 {@code PasswordUtil.encrypt}</b>：那个方法用的是
 * PBEWithMD5AndDES —— 它是**可逆的对称加密**（底座还提供了对应的 {@code decrypt}），
 * 拿到 salt 就能还原出 PIN 原文。孩子 PIN 只有 4–6 位数字，
 * 存可逆密文等于把孩子的凭据放在明文旁边。本类改用**单向**算法。</p>
 *
 * <p>两类凭据用不同策略，因为它们的检索方式不同：</p>
 * <table border="1">
 *   <caption>凭据策略对照</caption>
 *   <tr><th>凭据</th><th>算法</th><th>检索方式</th><th>原因</th></tr>
 *   <tr><td>PIN</td><td>PBKDF2-HMAC-SHA256（每行独立盐，10 万次迭代）</td><td>按 child_id 取行后校验</td>
 *       <td>熵低（4–6 位数字），必须用慢 KDF + 每行盐</td></tr>
 *   <tr><td>家庭码</td><td>HMAC-SHA256（服务端 pepper）</td><td>按 code 直接定位行</td>
 *       <td>需要 O(1) 索引检索；HMAC 是确定性的，pepper 在配置里不在库里</td></tr>
 * </table>
 *
 * @author jeecg-study
 */
public final class StudySecretUtil {

    /** PIN 哈希迭代次数 */
    public static final int PBKDF2_ITERATIONS = 100_000;

    /** PBKDF2 派生密钥长度（bit） */
    private static final int PBKDF2_KEY_LENGTH = 256;

    /** 盐长度（byte） */
    private static final int SALT_BYTES = 16;

    /** 家庭码字母表：去掉易混的 0/O/1/I */
    private static final char[] FAMILY_CODE_ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ".toCharArray();

    private static final SecureRandom RANDOM = new SecureRandom();

    private StudySecretUtil() {
    }

    /**
     * 生成随机盐（hex，32 字符）。
     */
    public static String newSalt() {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        return toHex(salt);
    }

    /**
     * 生成家庭码明文：6 位大写字母数字。
     *
     * <p>明文**只在生成时返回一次**，库里只存 HMAC。</p>
     */
    public static String newFamilyCode() {
        StringBuilder sb = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            sb.append(FAMILY_CODE_ALPHABET[RANDOM.nextInt(FAMILY_CODE_ALPHABET.length)]);
        }
        return sb.toString();
    }

    /**
     * 生成 PIN 明文：指定位数的数字。
     */
    public static String newPin(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(RANDOM.nextInt(10));
        }
        return sb.toString();
    }

    /**
     * PIN 哈希：PBKDF2-HMAC-SHA256。
     *
     * @param pin      明文 PIN
     * @param pinSalt  该行独立盐（hex）
     * @return hex 密文（64 字符）
     */
    public static String hashPin(String pin, String pinSalt) {
        return pbkdf2(pin, pinSalt);
    }

    /**
     * 校验 PIN。使用常量时间比较，避免计时侧信道。
     */
    public static boolean verifyPin(String pin, String pinSalt, String expectedHash) {
        if (pin == null || pinSalt == null || expectedHash == null) {
            return false;
        }
        String actual = pbkdf2(pin, pinSalt);
        return MessageDigest.isEqual(
                actual.getBytes(StandardCharsets.UTF_8),
                expectedHash.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 家庭码哈希：HMAC-SHA256(pepper, code)。
     *
     * <p>确定性 → 可以直接按 {@code family_code} 唯一索引定位家庭；
     * pepper 不在库里 → 拿到数据库也反推不出码。</p>
     */
    public static String hashFamilyCode(String code, String pepper) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(pepper.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return toHex(mac.doFinal(code.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | java.security.InvalidKeyException e) {
            throw new IllegalStateException("家庭码哈希失败", e);
        }
    }

    /**
     * 校验家庭码。
     */
    public static boolean verifyFamilyCode(String code, String pepper, String expectedHash) {
        if (code == null || expectedHash == null) {
            return false;
        }
        String actual = hashFamilyCode(code, pepper);
        return MessageDigest.isEqual(
                actual.getBytes(StandardCharsets.UTF_8),
                expectedHash.getBytes(StandardCharsets.UTF_8));
    }

    private static String pbkdf2(String plaintext, String saltHex) {
        try {
            byte[] salt = fromHex(saltHex);
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            PBEKeySpec spec = new PBEKeySpec(plaintext.toCharArray(), salt, PBKDF2_ITERATIONS, PBKDF2_KEY_LENGTH);
            byte[] key = factory.generateSecret(spec).getEncoded();
            return toHex(key);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("PIN 哈希失败", e);
        }
    }

    public static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    public static byte[] fromHex(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return Arrays.copyOf(data, data.length);
    }
}
