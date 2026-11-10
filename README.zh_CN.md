<div align="center">
<a href="./README.md">English</a> | <a href="./README.de_DE.md">Deutsch</a> | <a href="./README.pt_BR.md">Português (Brasil)</a>
</div>

# Termora

**Termora** 是一款跨平台终端模拟器和 SSH 客户端，支持 **Windows、macOS、Linux**。

<div align="center">
  <img src="docs/readme-zh_CN.png" alt="Readme" />
</div>

Termora 使用 [**Kotlin/JVM**](https://kotlinlang.org/) 开发，支持（正在实现中） [**XTerm 控制序列协议**](https://invisible-island.net/xterm/ctlseqs/ctlseqs.html)。未来目标是借助 [**Kotlin Multiplatform**](https://kotlinlang.org/docs/multiplatform.html) 实现 **全平台支持**，包括 Android、iOS、iPadOS 等。



## ✨ 功能特性

- 🧬 跨平台运行
- 🔐 内建密钥管理器
- 🖼️ 支持 X11 转发
- 🧑‍💻 SSH-Agent 集成
- 💻 系统信息展示
- 📁 图形化 SFTP 文件管理
- 📊 Nvidia 显卡使用率查看
- ⚡ 快捷指令支持


## 🚀 文件传输

- 支持 A ↔ B 服务器间直接传输
- 文件夹递归复制支持
- 最多可同时运行 **6 个传输任务**

<div align="center">
  <img src="docs/transfer-zh_CN.png" alt="Transfer" />
</div>


## 📝 文件编辑功能

- 保存后自动上传修改内容
- 文件 / 文件夹 重命名
- 快速删除大文件夹：`rm -rf` 支持
- 可视化更改权限
- 支持新建文件 / 文件夹

<div align="center">
  <img src="docs/transfer-edit-zh_CN.png" alt="Transfer Edit" />
</div>



## 💻 主机

- 类似文件夹树形结构
- 给主机添加标签
- 从其它软件导入
- 使用传输工具打开

<div align="center">
  <img src="docs/host-zh_CN.png" alt="Transfer Edit" />
</div>


## 🧩 插件

- 🌍 Geo：显示主机位置信息
- 🔄 Sync：将配置同步至 Gist 或 WebDAV
- 🗂️ WebDAV：连接 WebDAV 对象存储
- 📝 Editor：内置 SFTP 文件编辑器
- 📡 SMB:  连接 [SMB](https://baike.baidu.com/item/smb/4750512) 文件共享协议
- ☁️ S3：连接 S3 对象存储
- ☁️ Huawei OBS：连接华为云对象存储
- ☁️ Tencent COS：连接腾讯云 COS
- ☁️ Alibaba OSS：连接阿里云 OSS
- 👉 [查看所有插件...](https://www.termora.cn/plugins)




## 📦 下载

- 🧾 [Latest release](https://github.com/TermoraDev/termora/releases/latest)
- 🍺 **Homebrew**：`brew install --cask termora`
- 🪟 **WinGet**：`winget install termora`
- <img src="https://apps.microsoft.com/assets/icons/logo-16x16.png" alt="microsoft logo"/> <b>Microsoft Store</b>: <a href="https://apps.microsoft.com/store/detail/9NRZBHG43SB9?cid=DevShareMCLPCS">Termora</a>



## 🪟 终端分屏

右键终端标签，打开 **终端分屏** 菜单：

- **向右分屏 / 向下分屏（新建会话）**：为当前主机新建一个终端会话，支持继续拆分成多个窗格。
- **向右分屏 / 向下分屏（已有终端）**：将已经打开的终端移入分屏，无需重新连接。
- 点击窗格可切换当前操作的终端，拖动分隔线可调整大小；窗格右上角可以打开操作菜单或单独关闭该终端。
- **恢复独立标签页**：将这一组终端恢复为独立标签页，保留各自会话。重连单个终端时保留分屏位置。

分屏布局在当前窗口会话内有效。

## 🛠️ 开发指南

需要 **JDK 25**，建议使用带有 `bin/javac` 的 [JetBrainsRuntime](https://github.com/JetBrains/JetBrainsRuntime) JDK。将 `JAVA_HOME` 指向 JDK 根目录，并将其 `bin` 目录加入 `PATH`。

- Windows PowerShell 本地运行：`.\gradlew.bat :run`
- macOS / Linux 本地运行：`./gradlew :run`
- 检查环境：`./gradlew --version`（Windows 使用 `.\gradlew.bat --version`）。
- IntelliJ IDEA：Gradle distribution 选择 **Wrapper**，Gradle JVM 选择 **JDK 25**，重新加载 Gradle 项目后选择 **Termora** 运行配置或运行 `run` 任务。自带完整 JBR 25 的 IDEA 可以直接选择其运行时 JDK。

Gradle Wrapper 从华为云镜像下载，并使用官方 SHA-256 校验。Gradle 插件仓库在 `settings.gradle.kts` 中配置，所有模块的依赖仓库在 `build.gradle.kts` 的 `allprojects.repositories` 中统一配置：优先使用阿里云、华为云镜像，未收录的依赖回退至官方仓库。JetBrains、JitPack 等专用仓库仍用于提供特定依赖。

镜像地址是仓库地址，不能配置为 `systemProp.http.proxyHost` 或 `systemProp.https.proxyHost`。如果曾在用户目录下的 `.gradle/gradle.properties` 中将 `mirrors.aliyun.com` 配成代理，应删除这些错误配置，再重新同步项目。


## 📄 授权协议

Termora 采用双重许可方式，您可以选择：

- **AGPL-3.0**：自由使用、修改、分发（遵循 [AGPL 条款](https://opensource.org/license/agpl-v3)）
- **专有许可**：如需闭源或商业用途，请联系作者获取授权  
