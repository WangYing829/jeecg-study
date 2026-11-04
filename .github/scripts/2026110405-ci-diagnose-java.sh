#!/usr/bin/env bash
# ============================================================================
# jeecg-study · CI 步骤③：Java 测试失败时的**紧凑诊断**
#
# ## 为什么单独写一个脚本
#
# GitHub Actions 的 annotation 有**硬上限**（实测：每个 step 最多 10 条 error
# + 10 条 warning）。2026-11-04 第 6 次 run 就是被这个上限坑了：
# 诊断分支先打「日志末尾 40 行」，把仅有的 10 条额度用在了 Maven 的
# `MojoExecutionException` 栈帧上（那 9 帧毫无信息量），
# 真正要看的「哪个用例失败、报什么错」全被挤掉了。
#
# 所以这里的规矩是：
#   **error 只放「结论」**（失败摘要 + 失败用例名），**warning 放「细节」**。
#   两个额度分开用，先把额度花在刀刃上。
#
# ## 输入
#
#   $1 = Maven 日志路径（ci.yml 里 tee 出来的 mvn.log）
#   $2 = surefire-reports 目录
#
# ## 输出
#
# 只输出 `::error::` / `::warning::` 两种 workflow command，
# 供 GitHub 渲染成公开可读的 annotation。
# ============================================================================
set -uo pipefail

LOG="${1:-}"
REPORTS="${2:-}"

# GitHub 要求：annotation 里 % 要转义成 %25，回车要转义成 %0D，且单行别太长
esc() {
  sed -e 's/%/%25/g' -e 's/\r/%0D/g' | cut -c1-360
}

echo "::error::Java 测试失败 —— 紧凑诊断开始"

# ---------------------------------------------------------------------------
# ① 结论层：Maven 自己的失败摘要（最多 4 条 error）
# ---------------------------------------------------------------------------
if [ -n "$LOG" ] && [ -f "$LOG" ]; then
  grep -aE '^\[ERROR\] (Failed to execute goal|Tests run:)' "$LOG" \
    | head -4 | sed 's/^/::error::/' | esc || true

  # 失败用例名（Maven 在 [ERROR] 段落里列出 `TestClass.method:123`）
  grep -aE '^\[ERROR\]   [A-Za-z0-9_]+\.[A-Za-z0-9_]+' "$LOG" \
    | head -5 | sed 's/^/::error::/' | esc || true
else
  echo "::error::诊断脚本找不到 Maven 日志：$LOG"
fi

# ---------------------------------------------------------------------------
# ② 细节层：surefire 报告（最多 5 个失败用例，用 warning 额度）
# ---------------------------------------------------------------------------
if [ -n "$REPORTS" ] && [ -d "$REPORTS" ]; then
  shown=0
  for f in "$REPORTS"/*.txt; do
    [ -f "$f" ] || continue
    grep -aqE 'Failures: [1-9]|Errors: [1-9]' "$f" || continue
    shown=$((shown + 1))
    [ "$shown" -gt 5 ] && break

    echo "::warning::失败报告 $(basename "$f" .txt)"
    # 摘要行：Tests run: N, Failures: X, Errors: Y, Skipped: Z ... <<< FAILURE!
    grep -am1 -E 'Tests run:.*<<< (FAILURE|ERROR)!' "$f" \
      | sed 's/^/::warning::/' | esc || true
    # 明细：方法名 + 异常类型 + 断言差异（最多 6 行）
    grep -aA6 -E '<<< (FAILURE|ERROR)!' "$f" | tail -n +2 | head -6 \
      | sed 's/^/::warning::/' | esc || true
  done
  if [ "$shown" = "0" ]; then
    echo "::warning::surefire 报告里没有失败用例（失败可能发生在编译或插件阶段）"
  fi
else
  echo "::warning::诊断脚本找不到 surefire-reports 目录：$REPORTS"
fi

echo "::error::Java 测试失败 —— 紧凑诊断结束"
exit 0
