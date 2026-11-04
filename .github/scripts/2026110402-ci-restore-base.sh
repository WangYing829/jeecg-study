#!/usr/bin/env bash
# ============================================================================
# jeecg-study · CI 步骤①：把**完整基座**还原到仓库内的固定路径
#
# ## 为什么必须还原到「仓库内原位」
#
# `RedLineScanner.frontendRoot()`（jeecg-module-study/src/test/.../RedLineScanner.java:101）
# 是**按固定相对路径**拼前端源码根目录的：
#
#   <repoRoot>/02-代码开发/05-源码/01-JeecgBoot基座/jeecgboot-vue3/src
#
# 所以基座若还原到 /tmp 之类的临时目录，Java 侧所有前端断言都会**假红**
# （找不到 .vue 文件 ⇒ 判定为「没做红线检查」）。这不是断言的 bug，是路径约定。
#
# ## 本仓库里基座目录的两种形态
#
# | 位置 | 形态 |
# |---|---|
# | 本地工作区 | 完整基座（上游全部文件 + 本项目改动） |
# | GitHub 仓库 | overlay（只含相对上游的 diff 文件），由 `git update-index --cacheinfo` 强制入库 |
#
# CI 里 checkout 出来的是 **overlay 形态**（缺上游全量），必须先补齐。
#
# ## 做法
#
#   1) `2026103019-基座同步.py restore --target <临时空目录> --overlay-root <仓库内基座目录>`
#      —— 从上游 clone v3.9.2 + 用 overlay 覆盖，得到一份完整可编译基座；
#   2) 把这份完整基座**原位**覆盖回仓库内的基座目录。
#
# ## 为什么不直接 restore 到基座目录
#
# `restore` 要求目标目录**为空或不存在**（避免覆盖已有基座），
# 而仓库内的基座目录里正躺着 overlay 文件本身 —— 既是源又是目标，会冲突。
# 所以先落到临时目录，再原位覆盖回去。
#
# 退出码：0 = 还原成功；非 0 = 失败（step 直接红）
# ============================================================================
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
BASE="$ROOT/02-代码开发/05-源码/01-JeecgBoot基座"
SYNC="$ROOT/05-公共资源/2026103019-基座同步.py"

if [ ! -d "$BASE" ]; then
  echo "[FAIL] 仓库里没有基座 overlay 目录：$BASE"
  exit 2
fi
if [ ! -f "$SYNC" ]; then
  echo "[FAIL] 找不到基座同步脚本：$SYNC"
  exit 2
fi

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

echo "==> 1) 从上游 v3.9.2 + overlay 还原到临时目录"
python3 "$SYNC" restore --target "$TMP/jb" --overlay-root "$BASE"

echo "==> 2) 原位覆盖回 $BASE"
# cp -a 保留权限与符号链接；「$TMP/jb/.» 末尾的 /. 表示「连目录内容一起拷」。
# 不做删除：overlay 目录里只有 overlay 文件，它们已经包含在 $TMP/jb 里，
# 删了反而会丢掉 overlay 的清单文件（基座改动清单.md / 基座overlay-files.txt）。
cp -a "$TMP/jb/." "$BASE/"

echo "==> 3) 校验关键路径"
for p in \
  "jeecg-boot/pom.xml" \
  "jeecg-boot/jeecg-boot-module/jeecg-module-study/pom.xml" \
  "jeecgboot-vue3/src/views/study" \
  "jeecgboot-vue3/pnpm-lock.yaml"
do
  if [ ! -e "$BASE/$p" ]; then
    echo "[FAIL] 还原后缺少：$p"
    exit 3
  fi
done

# 断言必须能按固定相对路径找到前端源码 —— 路径对了，红线断言才不会假红
if [ ! -d "$BASE/jeecgboot-vue3/src/views/study" ]; then
  echo "[FAIL] 前端源码根不在固定路径上"
  exit 3
fi

echo "OK 完整基座已原位还原：$BASE"
