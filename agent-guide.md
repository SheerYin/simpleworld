# simpleworlds Agent 指南

> 本文件主要给 AI / Agent 阅读。

## 硬性规则

- **编码**：所有文件读写一律 UTF-8（no BOM）
- **构建**：不要主动 `gradlew build`，用户没要求就别构建
- **语法**：避免难懂的语法糖；可读性优先，好理解的糖可以用
- **命令实现**：使用 Paper Brigadier API（链接见下）

## 服务端源码定位

本项目通过 [`paperweight.foliaDevBundle`](https://docs.papermc.io/paper/dev/userdev/) 拉取 Folia 服务端源码。

| 位置 | 内容 |
|------|------|
| Gradle 模块缓存 `dev.folia/dev-bundle/*.zip` | **只含 patch**：`*.java` 为新增整文件，`*.java.patch` 为对原版的 diff |
| `paperweight-userdev` 缓存的 `work/setupMacheSources_*/output.zip` | 反编译后的纯原版 Minecraft 源码 |
| `paperweight-userdev` 缓存的 `work/applyDevBundlePatches_*/output.jar` | 打完 Folia 全部补丁的完整源码 + 编译产物 |

## 参考文档

- [Paper Brigadier 命令 API](https://docs.papermc.io/paper/dev/command-api/basics/arguments-and-literals/)
- [Paper Plugins 入门](https://docs.papermc.io/paper/dev/getting-started/paper-plugins/)
- 其他 dev 文档索引：<https://docs.papermc.io/paper/dev/>
