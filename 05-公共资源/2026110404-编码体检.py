#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
jeecg-study · 闸门：编码体检（UTF-8 / YAML / 改动痕迹）

为什么要有这道闸门
------------------
2026-11-04 本轮实测踩到过一次**静默损坏**：用 PowerShell 5.1 的默认 ANSI
（中文 Windows = GBK/CP936）读一个 UTF-8 的 `application-*.yml`，再以 UTF-8 写回，
文件里的中文**全部变成乱码**，而：

* `git diff` 只显示「整文件重写」，看不出是编码问题；
* YAML 注释乱码**不影响语法**，所以 Spring Boot 能起；
* 但**新增的 `update-begin/update-end` 痕迹行漏了 `#` 前缀**，
  于是「`update-begin---author:mavis ---date:...`」被 YAML 当成
  `key: value` 解析 → **整个 profile 语法非法**，应用起不来。

也就是说：**乱码 + 漏 `#` 这两件事叠在一起，第一次炸是在启动时，不是提交时。**
所以要把它们变成提交前的可执行断言。

四项检查
--------
C1 **GBK 反读检测**：把文件按 UTF-8 → GBK → UTF-8 走一圈，若往返后中文数量
    仍占原文的 35% 以上，说明原文就是「UTF-8 被当 GBK 读过」的乱码。
    阈值 0.35 是**实测标定**的：受损文件落在 0.479–0.504，
    正常文件最高只有 0.231，中间有 2 倍空档，零误报。
C2 **U+FFFD 检测**：任何受管文本文件里出现替换字符，说明解码丢过字节。
C3 **YAML 合法性**：所有带改动痕迹的 `*.yml` / `*.yaml` 必须能被 YAML 解析。
C4 **改动痕迹必须是注释**：`update-begin---author:` / `update-end---author:`
    所在的行必须以 `#` 开头 —— 否则 YAML 语法非法（上面那个坑）。

退出码：0 = 全部通过；2 = 有问题（CI 直接红）
"""

import os
import subprocess
import sys

try:
    import yaml
except ImportError:  # pragma: no cover
    yaml = None

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# 往返阈值（实测标定，见模块 docstring）
MOJIBAKE_RATIO_THRESHOLD = 0.35

# 二进制/大文件跳过
SKIP_SUFFIX = (
    ".png", ".jpg", ".jpeg", ".gif", ".ico", ".svg", ".webp", ".pdf",
    ".zip", ".gz", ".jar", ".war", ".class", ".woff", ".woff2", ".ttf",
    ".eot", ".mp4", ".mp3", ".exe", ".dll", ".so", ".dylib",
)
MAX_SIZE = 4 * 1024 * 1024

TEXT_SUFFIX = (
    ".md", ".html", ".csv", ".json", ".txt", ".yml", ".yaml",
    ".py", ".ps1", ".sh", ".sql", ".java", ".ts", ".tsx", ".vue", ".js", ".xml",
)

MARKER_BEGIN = "update-begin---author:"
MARKER_END = "update-end---author:"


def die(message):
    sys.stderr.write("[FAIL] %s\n" % message)
    sys.exit(2)


def git_ls_files():
    proc = subprocess.run(
        ["git", "-C", REPO_ROOT, "-c", "core.quotepath=false", "ls-files"],
        capture_output=True, text=True, encoding="utf-8", errors="replace",
    )
    if proc.returncode != 0:
        die("git ls-files 失败：%s" % proc.stderr.strip())
    return [line for line in proc.stdout.splitlines() if line]


def is_texty(path):
    lower = path.lower()
    if lower.endswith(SKIP_SUFFIX):
        return False
    return lower.endswith(TEXT_SUFFIX) or not os.path.splitext(lower)[1]


def read_bytes(rel_path):
    abs_path = os.path.join(REPO_ROOT, rel_path.replace("/", os.sep))
    if not os.path.isfile(abs_path):
        return None
    try:
        if os.path.getsize(abs_path) > MAX_SIZE:
            return None
    except OSError:
        return None
    with open(abs_path, "rb") as handle:
        return handle.read()


def cjk_count(text):
    return sum(1 for ch in text if "\u4e00" <= ch <= "\u9fff")


def mojibake_ratio(text):
    """UTF-8 → GBK → UTF-8 往返后，中文占比。
    正常中文文件的往返结果几乎全是 U+FFFD（GBK 字节不是合法 UTF-8），
    比值接近 0；被 ANSI 反读过的文件往返回来仍是通顺中文，比值高。"""
    try:
        raw = text.encode("gbk", errors="replace")
    except Exception:
        return 0.0
    try:
        back = raw.decode("utf-8", errors="replace")
    except Exception:
        return 0.0
    total = cjk_count(text)
    if total == 0:
        return 0.0
    return cjk_count(back) / float(total)


def main():
    tracked = git_ls_files()
    problems = []
    checked = 0
    yaml_checked = 0

    for rel_path in tracked:
        if not is_texty(rel_path):
            continue
        raw = read_bytes(rel_path)
        if raw is None:
            continue
        if b"\x00" in raw[:4096]:
            continue
        try:
            text = raw.decode("utf-8")
        except UnicodeDecodeError as exc:
            problems.append(("C2", rel_path, "文件不是合法 UTF-8：%s" % exc))
            continue

        checked += 1

        # C1 GBK 反读
        ratio = mojibake_ratio(text)
        if ratio > MOJIBAKE_RATIO_THRESHOLD:
            problems.append((
                "C1", rel_path,
                "疑似 UTF-8 被按 ANSI/GBK 反读（往返中文占比 %.3f > %.2f）"
                % (ratio, MOJIBAKE_RATIO_THRESHOLD),
            ))

        # C2 替换字符
        if "\ufffd" in text:
            problems.append((
                "C2", rel_path,
                "含 U+FFFD 替换字符 %d 处，说明解码丢过字节" % text.count("\ufffd"),
            ))

        lower = rel_path.lower()
        is_yaml = lower.endswith(".yml") or lower.endswith(".yaml")
        has_marker = (MARKER_BEGIN in text) or (MARKER_END in text)
        if not is_yaml:
            continue

        # C4 改动痕迹必须是注释
        for lineno, line in enumerate(text.splitlines(), start=1):
            stripped = line.strip()
            if (stripped.startswith(MARKER_BEGIN) or stripped.startswith(MARKER_END)):
                if not stripped.startswith("#"):
                    problems.append((
                        "C4", rel_path,
                        "第 %d 行改动痕迹不是注释（YAML 会当成 key:value → 语法非法）：%s"
                        % (lineno, stripped[:70]),
                    ))

        # C3 YAML 合法性（只对带改动痕迹的文件做，避免上游自定义标签误报）
        if has_marker:
            yaml_checked += 1
            if yaml is None:
                problems.append(("C3", rel_path, "缺少 PyYAML，无法校验 YAML 合法性"))
            else:
                try:
                    yaml.safe_load(text)
                except Exception as exc:
                    problems.append(("C3", rel_path, "YAML 解析失败：%s" % str(exc)[:160]))

    print("扫描受管文本文件 %d 个，其中带改动痕迹的 YAML %d 个" % (checked, yaml_checked))

    if problems:
        sys.stderr.write("\n=== 编码体检发现 %d 个问题 ===\n" % len(problems))
        for code, path, detail in problems:
            sys.stderr.write("  [%s] %s\n      %s\n" % (code, path, detail))
        sys.stderr.write("\n修法：把文件按 **UTF-8（无 BOM）** 重写；"
                         "PowerShell 下用 `[System.IO.File]::WriteAllText($p, $s, (New-Object System.Text.UTF8Encoding($false)))`"
                         "，不要用 Get-Content/Set-Content 裸转（默认 ANSI = GBK）。\n")
        sys.exit(2)

    print("ALL PASS 编码体检（无 GBK 反读、无替换字符、YAML 合法、痕迹均为注释）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
