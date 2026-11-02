# -*- coding: utf-8 -*-
"""类简单名唯一性检查（ADR-027）。

为什么需要这个脚本
------------------
MyBatis-Spring 2.0.7 用「类名去首字母小写」作为 Mapper 的 bean 名；
Spring 对 @Service / @Controller 也用同一套默认 bean 名规则。
一旦**不同包下出现同名类**，启动时会直接抛
`ConflictingBeanDefinitionException`，**整个 Spring 上下文起不来**。

本轮开发中真实踩过：`error` 域与 `practice` 域各有一个 `StudyContextMapper`，
`error` 域与 `math` 域各有一个 `StudyQuestionVariantMapper`。
所以把「简单名唯一」从口头约定变成**可执行的闸门**。

检查范围
--------
- study 模块（`jeecg-boot-module/jeecg-module-study`）的 main / test 全量类；
- 基座里会被 study 模块依赖到的模块（system / base-core）做交叉核对。

退出码
------
0 = 无冲突；2 = 有冲突（打印冲突清单）。
"""
import os
import sys
from collections import defaultdict

ROOT = r"D:\DevProject\2-JavaPrj\20261030-jeecg-study"
BASE = os.path.join(ROOT, "02-代码开发", "05-源码", "01-JeecgBoot基座", "jeecg-boot")

# 需要扫描的源码根（相对 BASE）
SCAN_ROOTS = [
    ("jeecg-boot-module/jeecg-module-study/src/main/java", "study/main"),
    ("jeecg-boot-module/jeecg-module-study/src/test/java", "study/test"),
    ("jeecg-module-system/jeecg-system-biz/src/main/java", "system-biz"),
    ("jeecg-module-system/jeecg-system-start/src/main/java", "system-start"),
    ("jeecg-boot-base-core/src/main/java", "base-core"),
]

# 基座自带的历史同名（分属不同 jar / 不同子模块，基座自己能跑，不在本项目管辖范围）
BASELINE_ALLOWED = {
    "TestJobHandler",            # demo 模块的两个子包
    "IAiragBaseApi",
    "IOnlineBaseExtApi",
    "ISysBaseAPI",
}


# 只有这些注解的类才会被 Spring 注册成 bean（bean 名 = 类简单名去首字母小写），
# 也只有 @Mapper 会被 MyBatis 扫描器注册。普通 POJO / 枚举同名不构成冲突。
BEAN_ANNOTATIONS = (
    "@Mapper",
    "@Service",
    "@Component",
    "@Controller",
    "@RestController",
    "@Repository",
    "@Configuration",
)


def is_bean(path):
    """判断这个源文件里是否有会被 Spring / MyBatis 注册成 bean 的类型。"""
    try:
        with open(path, "r", encoding="utf-8", errors="ignore") as f:
            content = f.read()
    except OSError:
        return False
    return any(anno in content for anno in BEAN_ANNOTATIONS)


def collect():
    by_name = defaultdict(list)
    for rel, label in SCAN_ROOTS:
        root = os.path.join(BASE, rel.replace("/", os.sep))
        if not os.path.isdir(root):
            continue
        for dirpath, _dirnames, filenames in os.walk(root):
            for name in filenames:
                if not name.endswith(".java"):
                    continue
                full = os.path.join(dirpath, name)
                if not is_bean(full):
                    continue
                simple = name[:-5]
                by_name[simple].append("%s/%s" % (label, os.path.relpath(
                    full, root).replace("\\", "/")))
    return by_name


def main():
    by_name = collect()
    conflicts = []
    for simple, paths in sorted(by_name.items()):
        if len(paths) < 2:
            continue
        if simple in BASELINE_ALLOWED:
            continue
        conflicts.append((simple, paths))

    total = sum(len(v) for v in by_name.values())
    if not conflicts:
        print("OK 无重复简单名（扫描 %d 个类）" % total)
        return 0

    print("FAIL 发现 %d 组同名类（会导致 Spring 启动失败）" % len(conflicts))
    for simple, paths in conflicts:
        print("!! %s" % simple)
        for p in paths:
            print("     %s" % p)
    print("")
    print("处理方式：按域前缀改名（ADR-027）。例如")
    print("  error/mapper/StudyContextMapper      -> error/mapper/StudyErrorContextMapper")
    print("  practice/mapper/StudyContextMapper   -> practice/mapper/StudyPracticeContextMapper")
    return 2


if __name__ == "__main__":
    sys.exit(main())
