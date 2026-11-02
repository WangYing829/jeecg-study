#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""jeecg-study · 基座同步工具（导出 diff / 从上游还原完整基座）

## 为什么需要它

`02-代码开发/05-源码/01-JeecgBoot基座/` 是 **JeecgBoot v3.9.2 独立基座**，自带 `.git`。
本仓库的入库策略（见 `02-代码开发/05-源码/README.md` §4）：

> **本仓库只跟踪本项目相对上游的改动（修改 / 新增 / 删除），上游历史留在基座自己的 `.git` 里。**

于是同一目录在两个地方有两种形态：

| 位置 | 形态 | 用途 |
|---|---|---|
| **本地工作区** | **完整基座**（上游全部文件 + 本项目改动） | 能编译、能跑、能改 |
| **GitHub 仓库** | **overlay**（只含 diff 文件） | 体积小、审阅清晰、不重复上游历史 |

本脚本负责在两种形态之间转换。

## 子命令

| 命令 | 作用 | 在哪跑 |
|---|---|---|
| `list` | 列出相对上游的改动清单（不改文件） | 本地工作区 |
| `export` | 生成 overlay 清单文件（供 `stage` 精确入库） | 本地工作区 |
| `stage` | **把 overlay 文件精确写入本仓库的 git 索引** | 本地工作区 |
| `restore` | **从上游 clone + overlay 覆盖 → 还原出完整可编译基座** | clone 本仓库后 |
| `verify` | 校验 overlay 与清单是否一致（overlay 里不该有多余文件） | 两侧都行 |

## 用法

```powershell
# 1) 本地：算出改动清单并生成 overlay 清单
python 05-公共资源\\2026103019-基座同步.py export

# 2) 入库：把 overlay 精确写进本仓库索引（**不要用 git add -f**，见下）
python 05-公共资源\\2026103019-基座同步.py stage
git commit -m "..."

# 3) 他人 clone 本仓库后：还原出完整基座
python 05-公共资源\\2026103019-基座同步.py restore --target ..\\JeecgBoot基座
```

## 为什么不能用 `git add -f`

基座目录自带 `.git`（上游仓库），在 git 眼里就是一个**嵌套仓库**。实测：

| 命令 | 结果 |
|---|---|
| `git add -f -- <基座内文件>` | **exit 0 但索引没变**（静默跳过） |
| `git add -f -- <整个基座目录>` | `fatal: 'sub/' does not have a commit checked out` |

所以 `stage` 绕开工作区扫描，**直接把 blob 写进索引**：

```
git hash-object -w --no-filters -- <文件>            → 得到 blob hash
git update-index --add --cacheinfo <mode>,<hash>,<路径>  → 写入索引
```

两个坑（都实测过）：

1. 必须用 **`--cacheinfo`（单条形式）**。`--index-info`（批量形式）会被
   `.gitignore` 命中而**静默跳过**——exit 0 但条目根本没落地。
2. 必须**回读校验**：`git ls-files --cached` 确认条目真的在索引里，
   否则「报成功但没入库」会被当成成功。

上游历史仍留在基座自己的 `.git` 里，本仓库「只跟踪相对上游的改动」这条约定**不变**。

## 退出码

0 = 成功；2 = 基座目录缺失 / 不是 git 仓库 / 上游 tag 不存在；3 = overlay 与清单不一致（verify 模式）
"""

import argparse
import os
import shutil
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# 基座目录（本地 = 完整基座；GitHub = overlay）
BASE_DIR = os.path.join(ROOT, "02-代码开发", "05-源码", "01-JeecgBoot基座")

# 上游
UPSTREAM_URL = "https://github.com/jeecgboot/JeecgBoot.git"
UPSTREAM_TAG = "v3.9.2"

# overlay 清单文件（放在基座目录里，跟着 overlay 一起走）
LIST_MD = "基座改动清单.md"
LIST_TXT = "基座overlay-files.txt"

# 基座里**永远不算 diff**的目录（产物 / 依赖 / 运行时释放物）
EXCLUDE_PREFIXES = (
    "node_modules/",
    "target/",
    "dist/",
    ".git/",
    "jeecg-boot/jeecg-module-system/jeecg-system-start/config/",
    "jeecg-boot/jeecg-module-system/jeecg-system-start/logs/",
)


def die(msg, code=2):
    print("[FAIL] " + msg)
    sys.exit(code)


def git(*args, cwd=BASE_DIR):
    """跑 git 并返回 stdout 文本。

    固定加 `-c core.quotepath=false`：默认开启时 git 会把非 ASCII 路径转义成
    八进制（"\\346\\240\\273..."），写进 overlay 清单就是坏路径。
    """
    p = subprocess.run(
        ["git", "-c", "core.quotepath=false"] + list(args),
        cwd=cwd,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
        encoding="utf-8",
        errors="replace",
    )
    if p.returncode != 0:
        die("git %s 失败：%s" % (" ".join(args), (p.stderr or "").strip()))
    return p.stdout


def check_base():
    if not os.path.isdir(BASE_DIR):
        die("找不到基座目录：%s" % BASE_DIR)
    if not os.path.isdir(os.path.join(BASE_DIR, ".git")):
        die("基座目录不是 git 仓库（缺 .git）：%s" % BASE_DIR)
    if not git("rev-parse", "--verify", UPSTREAM_TAG).strip():
        die("基座里没有上游 tag：%s（先 git fetch --tags）" % UPSTREAM_TAG)


def excluded(rel):
    r = rel.replace("\\", "/")
    return any(r.startswith(p) for p in EXCLUDE_PREFIXES)


def collect_diff():
    """算出相对上游的改动。

    返回 (modified, added, deleted)：
      modified/added —— 相对路径列表
      deleted        —— 相对路径列表（上游有、本项目删了）
    """
    modified, deleted = [], []
    for line in git("diff", "--name-status", UPSTREAM_TAG).splitlines():
        if not line.strip():
            continue
        parts = line.split("\t")
        status = parts[0].strip()
        path = parts[-1].strip()
        if excluded(path):
            continue
        if status.startswith("D"):
            deleted.append(path)
        else:
            modified.append(path)

    added = []
    for path in git("ls-files", "--others", "--exclude-standard").splitlines():
        path = path.strip()
        if not path or excluded(path):
            continue
        # 清单产物自己不能进清单（否则下一轮 export 会自我引用）
        if path in (LIST_MD, LIST_TXT):
            continue
        added.append(path)

    return sorted(set(modified)), sorted(set(added)), sorted(set(deleted))


def cmd_list(args):
    check_base()
    modified, added, deleted = collect_diff()
    print("上游：%s  tag=%s" % (UPSTREAM_URL, UPSTREAM_TAG))
    print("基座：%s" % BASE_DIR)
    print("")
    print("修改 (%d)：" % len(modified))
    for p in modified:
        print("  M %s" % p)
    print("新增 (%d)：" % len(added))
    for p in added:
        print("  A %s" % p)
    print("删除 (%d)：" % len(deleted))
    for p in deleted:
        print("  D %s" % p)
    print("")
    print("合计需入库 overlay 的文件：%d 个" % (len(modified) + len(added)))
    return 0


def cmd_export(args):
    check_base()
    modified, added, deleted = collect_diff()
    overlay_files = modified + added

    # 机器读：每行一个相对路径，直接喂给 git add --pathspec-from-file
    txt_path = os.path.join(BASE_DIR, LIST_TXT)
    with open(txt_path, "w", encoding="utf-8", newline="\n") as f:
        for p in overlay_files:
            f.write(p + "\n")

    # 人读：改了什么、为什么
    md_path = os.path.join(BASE_DIR, LIST_MD)
    with open(md_path, "w", encoding="utf-8", newline="\n") as f:
        f.write("# 基座改动清单（相对上游 `%s`）\n\n" % UPSTREAM_TAG)
        f.write("> 本文件由 `05-公共资源/2026103019-基座同步.py export` 生成，**不要手改**。\n")
        f.write("> 上游：`%s`；上游历史留在基座自己的 `.git` 里，本仓库只跟踪下列改动。\n\n" % UPSTREAM_URL)
        f.write("## 修改（%d）\n\n" % len(modified))
        for p in modified:
            f.write("- `M` `%s`\n" % p)
        f.write("\n## 新增（%d）\n\n" % len(added))
        for p in added:
            f.write("- `A` `%s`\n" % p)
        f.write("\n## 删除（%d）\n\n" % len(deleted))
        if deleted:
            for p in deleted:
                f.write("- `D` `%s`\n" % p)
        else:
            f.write("（无）\n")
        f.write("\n## 怎么用\n\n")
        f.write("```powershell\n")
        f.write("# 1) 还原出完整基座（clone 本仓库后）\n")
        f.write("python 05-公共资源\\2026103019-基座同步.py restore --target <目录>\n")
        f.write("\n# 2) 入库：只加 overlay 里的这些文件\n")
        f.write("git add -f --pathspec-from-file=02-代码开发/05-源码/01-JeecgBoot基座/%s\n" % LIST_TXT)
        f.write("```\n")

    print("OK 改动清单：%d 改 / %d 增 / %d 删" % (len(modified), len(added), len(deleted)))
    print("    %s" % txt_path)
    print("    %s" % md_path)
    print("    overlay 文件数 = %d" % len(overlay_files))
    return 0


def cmd_verify(args):
    """校验：overlay 目录里只应存在清单内的文件（本地完整基座会不满足，需指定 --overlay-root）。"""
    overlay_root = args.overlay_root or BASE_DIR
    if not os.path.isdir(overlay_root):
        die("overlay 目录不存在：%s" % overlay_root)

    expected = set()
    for line in open(os.path.join(overlay_root, LIST_TXT), encoding="utf-8"):
        line = line.strip()
        if line:
            expected.add(line.replace("\\", "/"))

    actual = set()
    for dirpath, dirnames, filenames in os.walk(overlay_root):
        rel_dir = os.path.relpath(dirpath, overlay_root).replace("\\", "/")
        if rel_dir == ".":
            rel_dir = ""
        for name in filenames:
            rel = (rel_dir + "/" + name) if rel_dir else name
            if excluded(rel) or rel in (LIST_MD, LIST_TXT):
                continue
            actual.add(rel)

    missing = sorted(expected - actual)
    extra = sorted(actual - expected)
    if missing:
        print("[FAIL] 清单里有、overlay 里没有：%d 个" % len(missing))
        for p in missing[:20]:
            print("  - %s" % p)
    if extra:
        print("[FAIL] overlay 里有、清单里没有（不该入库）：%d 个" % len(extra))
        for p in extra[:20]:
            print("  + %s" % p)
    if missing or extra:
        sys.exit(3)
    print("OK overlay 与清单一致（%d 个文件）" % len(actual))
    return 0


def cmd_stage(args):
    """把 overlay 文件精确写入**本仓库**的 git 索引。

    为什么不能直接 `git add -f`：基座目录自带 `.git`（上游仓库），
    在 git 眼里就是一个**嵌套仓库**——父仓库 `git add -f <基座内文件>`
    会**静默跳过**（exit 0 但索引没变），`git add <整个基座目录>` 则报
    `does not have a commit checked out`。所以这里绕开工作区扫描，
    直接把 blob 写进索引。上游历史仍留在基座自己的 `.git` 里。
    """
    repo = ROOT
    if not os.path.isdir(os.path.join(repo, ".git")):
        die("本仓库还不是 git 仓库（缺 %s/.git），先 git init" % repo)

    txt_path = os.path.join(BASE_DIR, LIST_TXT)
    if not os.path.exists(txt_path):
        die("找不到 overlay 清单：%s（先跑 export）" % txt_path)
    with open(txt_path, encoding="utf-8") as f:
        files = [l.strip().replace("\\", "/") for l in f if l.strip()]
    if not files:
        die("overlay 清单是空的（先跑 export）")

    prefix = os.path.relpath(BASE_DIR, repo).replace("\\", "/") + "/"

    # 基座仓库里的文件模式（保留可执行位）；新增文件不在其中，用默认 100644
    modes = {}
    for line in git("ls-files", "-s").splitlines():
        parts = line.split("\t", 1)
        if len(parts) != 2:
            continue
        head = parts[0].split()
        if len(head) >= 3:
            modes[parts[1].replace("\\", "/")] = head[0]

    def blob_hash(repo_rel):
        p = subprocess.run(
            ["git", "hash-object", "-w", "--no-filters", "--", repo_rel],
            cwd=repo, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
            text=True, encoding="utf-8", errors="replace",
        )
        if p.returncode != 0:
            die("hash-object 失败：%s（%s）" % (repo_rel, (p.stderr or "").strip()))
        return p.stdout.strip()

    entries = []  # (mode, blob_hash, repo_relative_path)
    missing = []
    for rel_base in files:
        abs_path = os.path.join(BASE_DIR, rel_base.replace("/", os.sep))
        if not os.path.isfile(abs_path):
            missing.append(rel_base)
            continue
        repo_rel = prefix + rel_base
        mode = modes.get(rel_base, "100644")
        entries.append((mode, blob_hash(repo_rel), repo_rel))

    # 清单产物本身也要入库（restore 靠它们）
    for name in (LIST_TXT, LIST_MD):
        repo_rel = prefix + name
        if not os.path.isfile(os.path.join(BASE_DIR, name)):
            continue
        entries.append(("100644", blob_hash(repo_rel), repo_rel))

    if not entries:
        die("没有可入库的文件（overlay 清单里的文件都不存在）")

    # 写入索引：必须用 `--cacheinfo`（单条形式）。
    #   - `--index-info` 会被 .gitignore 命中而**静默跳过**（exit 0 但条目没落地）；
    #   - `--cacheinfo` 不看 ignore 规则，能把被忽略目录里的文件写进索引。
    # Windows 命令行有 32KB 上限，所以分批调用。
    BATCH = 80
    for i in range(0, len(entries), BATCH):
        args = ["update-index", "--add"]
        for mode, h, rp in entries[i:i + BATCH]:
            args += ["--cacheinfo", "%s,%s,%s" % (mode, h, rp)]
        p = subprocess.run(
            ["git", "-c", "core.quotepath=false"] + args,
            cwd=repo, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
            text=True, encoding="utf-8", errors="replace",
        )
        if p.returncode != 0:
            die("update-index 失败：%s" % (p.stderr or "").strip())

    # 回读校验：确认条目真的进了索引（--index-info 那次就是「报成功但没落地」）
    staged = set(git("ls-files", "--cached", cwd=repo).splitlines())
    want = {rp for _, _, rp in entries}
    lost = sorted(want - staged)
    if lost:
        die("写入索引后校验失败：%d 个文件没进索引（例如 %s）" % (len(lost), lost[0]))

    print("OK 已写入本仓库索引：%d 个文件（%d 改 / %d 增 + 2 份清单）"
          % (len(entries), len(files) - len(missing), len(missing)))
    if missing:
        print("[WARN] 清单里有、基座工作区找不到（已跳过）%d 个：" % len(missing))
        for p_ in missing[:10]:
            print("   - " + p_)
    print("   下一步：git status --short   然后 git commit")
    return 0


def cmd_restore(args):
    """从上游 clone + overlay 覆盖 → 还原出完整可编译基座。"""
    target = os.path.abspath(args.target)
    overlay_root = os.path.abspath(args.overlay_root or BASE_DIR)

    if os.path.exists(target) and os.listdir(target):
        die("目标目录非空：%s（换个空目录，避免覆盖已有基座）" % target)

    print("1) clone 上游 %s @ %s" % (UPSTREAM_URL, UPSTREAM_TAG))
    os.makedirs(os.path.dirname(target) or ".", exist_ok=True)
    p = subprocess.run(
        ["git", "clone", "--branch", UPSTREAM_TAG, "--depth", "1", UPSTREAM_URL, target],
        stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, encoding="utf-8", errors="replace",
    )
    if p.returncode != 0:
        die("clone 失败：%s" % (p.stderr or "").strip())

    # overlay 清单
    txt_path = os.path.join(overlay_root, LIST_TXT)
    if not os.path.exists(txt_path):
        die("找不到 overlay 清单：%s（先在完整基座上跑 export）" % txt_path)
    files = []
    for line in open(txt_path, encoding="utf-8"):
        line = line.strip()
        if line:
            files.append(line.replace("\\", "/"))

    print("2) 覆盖 %d 个改动文件" % len(files))
    copied = 0
    for rel in files:
        src = os.path.join(overlay_root, rel.replace("/", os.sep))
        dst = os.path.join(target, rel.replace("/", os.sep))
        if not os.path.exists(src):
            print("   [跳过] overlay 缺文件：%s" % rel)
            continue
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        shutil.copy2(src, dst)
        copied += 1

    # 删除清单
    deleted = []
    md_path = os.path.join(overlay_root, LIST_MD)
    if os.path.exists(md_path):
        in_del = False
        for line in open(md_path, encoding="utf-8"):
            s = line.strip()
            if s.startswith("## 删除"):
                in_del = True
                continue
            if in_del and s.startswith("## "):
                break
            if in_del and s.startswith("- `D` `"):
                deleted.append(s.split("`")[3])
    if deleted:
        print("3) 删除 %d 个上游文件" % len(deleted))
        for rel in deleted:
            dst = os.path.join(target, rel.replace("/", os.sep))
            if os.path.exists(dst):
                os.remove(dst)
    else:
        print("3) 无删除项")

    print("")
    print("OK 完整基座已还原：%s" % target)
    print("   覆盖 %d 个文件 / 删除 %d 个文件" % (copied, len(deleted)))
    print("   下一步：")
    print("     cd \"%s\"" % target)
    print("     git checkout -b feature/jeecg-study   # 上游 tag 上开本项目分支")
    print("     pnpm install && mvn -pl jeecg-boot-module/jeecg-module-study -am package")
    return 0


def main():
    ap = argparse.ArgumentParser(description="jeecg-study · 基座同步工具")
    sub = ap.add_subparsers(dest="cmd")

    sub.add_parser("list", help="列出相对上游的改动清单")
    sub.add_parser("export", help="生成 overlay 清单文件（供 stage 入库）")
    sub.add_parser("stage", help="把 overlay 文件精确写入本仓库 git 索引")

    p_verify = sub.add_parser("verify", help="校验 overlay 与清单是否一致")
    p_verify.add_argument("--overlay-root", default=None,
                           help="overlay 目录（默认 02-代码开发/05-源码/01-JeecgBoot基座）")

    p_restore = sub.add_parser("restore", help="从上游 + overlay 还原完整基座")
    p_restore.add_argument("--target", required=True, help="还原目标目录（必须为空或不存在）")
    p_restore.add_argument("--overlay-root", default=None,
                            help="overlay 目录（默认 02-代码开发/05-源码/01-JeecgBoot基座）")

    args = ap.parse_args()
    cmd = args.cmd or "list"
    fn = {"list": cmd_list, "export": cmd_export, "stage": cmd_stage,
          "verify": cmd_verify, "restore": cmd_restore}[cmd]
    return fn(args)


if __name__ == "__main__":
    sys.exit(main() or 0)
