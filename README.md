# Message-Check

Message-Check 是一个针对 Minecraft 服务器的多端聊天与指令审核插件，同一套 Jar 同时兼容以下服务端：

- Velocity 代理 (Minecraft 1.8-1.21.x)
- Spigot/Paper 等 Bukkit 系列 (1.8-1.21.x)
- Folia (1.20.x 及以上)

插件支持从 Java 8 到 Java 21+ 的运行环境，并使用高性能的异步 Netty 线程池处理审核逻辑，以尽可能减少主线程阻塞。

## 功能特性

- **异步消息审核**：在 Netty 事件循环中执行本地过滤、重复消息检测和外部 API 审核，降低主线程压力。
- **多端兼容**：同一个 Jar 同时包含 `plugin.yml` 和 `velocity-plugin.json`，可直接部署到 Bukkit/Folia 或 Velocity。
- **可配置关键字/正则过滤**：支持自定义违禁词和正则表达式，自动屏蔽涉政、色情、外挂宣传等内容。
- **刷屏检测**：记录玩家三分钟内的消息，基于自定义相似度和数量阈值判断是否刷屏。
- **OpenAI 审核集成**：可选接入 ChatGPT API，支持配置模型、请求超时、缓存时长与自定义提示词。
- **中国地区免费审核 API**：当 `settings.region` 设置为 `china`/`cn` 时，将自动切换到 https://v2.xxapi.cn/api/detect 提供的免费违禁词检测。
- **结果缓存**：使用 Caffeine 内存缓存，并可选落地到 MySQL/MariaDB 或 Redis，避免重复请求外部 API。
- **Folia 支持**：通过调度适配层兼容 Folia 的新调度模型，同步/异步回调均可安全执行。
- **指令审核**：拦截 `/me`、`/msg`、`/tell`、`/hh` 等可配置指令，对内容进行审核后再决定是否放行。
- **管理命令**：`/messagecheck reload` 可在游戏内或控制台热重载配置。

## 配置

插件首次启动会在数据目录生成 `config.yml`，主要配置项包含：

- `settings`：开关、异步线程数、需要拦截的指令、通知权限以及 `region`（在中国大陆可设为 `china`/`cn` 自动使用免费审核 API）。
- `spam-control`：刷屏阈值、检测时长、相似度阈值。
- `filters`：违禁词、正则、颜色/URL 清洗策略。
- `openai`：API 地址、模型、超时和缓存时长。
- `cache`：内存缓存大小和过期时间。
- `storage`：MySQL/MariaDB 与 Redis 的连接信息。
- `logging`：日志级别与调试开关。

详细注释可参考 [`src/main/resources/config.yml`](src/main/resources/config.yml)。

## 构建

项目使用 Gradle 构建，默认会生成单个可部署的 Shadow Jar。仓库未包含 Gradle Wrapper 的二进制文件，请确保本地已安装 Gradle 8+，然后执行：

```bash
gradle build
```

构建完成后，在 `build/libs/Message-Check-<version>.jar` 可找到最终产物。

## 许可证

本项目基于 [MIT License](LICENSE)。
