# 项目协作提示

## 编码与文件

- 文本文件通常使用 UTF-8（no BOM）。
- Windows 专用脚本或注册表文件按系统默认编码处理，例如 `.bat`、`.cmd`、`.reg`。
- 读取已有文件时尽量保留原编码。

## 开发习惯

- 除非明确需要，一般不主动执行完整构建。
- 代码以可读性优先，避免过度使用难懂的语法糖。
- 实现命令时优先参考 Paper Brigadier API。

## 服务端源码参考

本项目使用 [`paperweight.foliaDevBundle`](https://docs.papermc.io/paper/dev/userdev/) 获取 Folia 开发依赖。需要查服务端源码或补丁时，可以参考这些缓存位置。

| 位置 | 内容 |
|------|------|
| Gradle 模块缓存 `dev.folia/dev-bundle/*.zip` | Folia dev bundle，主要包含补丁和相关元数据 |
| `paperweight-userdev` 缓存的 `work/setupMacheSources_*/output.zip` | 反编译后的纯原版 Minecraft 源码 |
| `paperweight-userdev` 缓存的 `work/applyDevBundlePatches_*/output.jar` | 打完 Folia 全部补丁的完整源码 + 编译产物 |

## 参考文档

- [Paper Brigadier 命令 API](https://docs.papermc.io/paper/dev/command-api/basics/arguments-and-literals/)
- [Paper Plugins 入门](https://docs.papermc.io/paper/dev/getting-started/paper-plugins/)
- 其他 dev 文档索引：<https://docs.papermc.io/paper/dev/>
