# 轻账 · QingZhang

> 一款面向 Android 的轻量记账应用。日常收支与装修账本放在一起管理，优先本地存储，支持按需备份和导入账单。

**当前正式版：v1.3.47** ｜ **系统要求：Android 8.0（API 26）及以上** ｜ **包名：`com.zhuangxiu.ledger`**

[🌐 轻账下载官网](https://a1070503737-dev.github.io/QingZhang/) · [⬇️ 下载最新 APK](https://github.com/a1070503737-dev/QingZhang/releases/latest/download/renovation-ledger-latest.apk) · [查看最新发行版](https://github.com/a1070503737-dev/QingZhang/releases/latest) · [版本列表](https://github.com/a1070503737-dev/QingZhang/releases)

## ✨ 主要功能

| 功能 | 说明 |
| --- | --- |
| 日常记账 | 记录收入与支出，按月查看明细，支持编辑、删除、分类与统计 |
| 装修账本 | 记录装修项目、预算、付款进度、待付款与凭证照片 |
| 多账本管理 | 生活账本与装修账本分开整理，也可查看汇总 |
| 数据统计 | 按分类、月份查看收支趋势和预算情况 |
| 数据管理 | 导入并预览 CSV / XLSX 账单，支持本地 ZIP 备份与恢复及 Google Drive 备份 |
| 应用内更新 | 从 GitHub Releases 检查更新，匹配时优先下载增量包，安装前由 Android 确认 |
| 意见反馈 | 在应用内匿名提交使用体验和建议，联系方式选填 |

应用支持在首页「本月明细」长按账目编辑或删除（删除后可通过底部提示即时撤销），首页右下角按钮点按手动记账、长按语音记账（滑离按钮可取消识别），记账页内支持图片识图记账。

## 📲 安装与更新

1. **首次安装**：从上方的「查看最新发行版」或轻账官网获取正式安装包，在 Android 手机上打开并按系统提示安装。
2. **已有正式版**：进入应用的「设置 → 检查更新」，按提示下载并安装。1.3.23 如因仓库更名遇到检查失败，可手动覆盖安装新版。
3. **换机、重装或跨签名版本升级前**：先在应用中导出并验证备份，避免丢失账本和照片。

你也可以通过 [轻账下载官网](https://a1070503737-dev.github.io/QingZhang/) 获取最新 APK。正式版本统一通过 [GitHub Releases](https://github.com/a1070503737-dev/QingZhang/releases) 分发。仓库 `main` 分支不再存放旧版 APK；**不要使用历史文档中的旧 `dist/*.apk` 链接下载安装**。部分历史构建使用不同的签名，无法保证跨版本直接覆盖安装。

> 当前公开 Release 的下载与更新**无需个人 GitHub Token**。如下载或升级失败，请优先检查网络、版本及签名是否匹配。

## 🔐 数据、隐私与备份

- **本地优先**：账单、数据库和凭证照片保存在应用私有存储中；记账本身不要求登录。
- **备份可选**：可以手动导出 ZIP；Google Drive 备份需要用户主动授权，自动备份需另行启用。
- **删除应用会删除本地数据**：应用关闭 Android 系统自动备份（`allowBackup=false`）。请在卸载或换机前确认备份可读取。
- **恢复会替换当前账本数据**：恢复之前应另外留存一份当前数据的备份。

Google Drive 接入与发行配置参见 [备份配置说明](docs/GOOGLE-DRIVE-BACKUP.md)。

## 🛠️ 开发与构建

**技术栈：** Kotlin、Jetpack Compose、Material 3、Hilt、Room、DataStore、WorkManager、CameraX、Coil。项目使用 Gradle，`compileSdk / targetSdk = 37`，最低支持 API 26。

使用 Android Studio 打开仓库根目录并完成 Gradle 同步，然后选择真机或模拟器运行。Windows 可在项目根目录执行：

```powershell
# 运行单元测试
.\gradlew.bat testDebugUnitTest

# 构建调试安装包
.\gradlew.bat assembleDebug

# 构建未签名的 Release 包
.\gradlew.bat assembleRelease
```

macOS / Linux 将 `gradlew.bat` 换成 `./gradlew`。

- 调试 APK 通常生成在 `app/build/outputs/apk/debug/`。
- 正式版需要使用**与现有正式版一致的签名凭据**完成签名，不能把未签名的构建产物直接当作可安装的正式版。
- 私钥、签名文件、签名密码以及访问令牌均应在仓库外妥善保存，**不要提交到 Git，也不要嵌入公开发布的 APK**。即使有 `.gitignore`，它也不能清除已经提交的历史。

### 项目结构

```text
app/src/main/java/com/zhuangxiu/ledger/
├── data/           # Room 数据库与数据仓库
├── domain/         # 金额、预算、付款及统计逻辑
├── drive/          # Google Drive 备份
├── importing/      # CSV / XLSX 账单导入
├── notification/   # 本地提醒
├── ui/             # Compose 页面、导航和主题
└── update/         # 正式版检查与下载
app/src/test/       # 单元与界面相关测试
docs/               # 历史设计、验收与发行说明
tools/              # 更新清单生成与校验脚本
```

## 使用声明与法律责任

轻账（QingZhang）是一款用于个人及家庭财务记录的开源软件。

本项目遵循 MIT License 开源协议，允许用户按照协议规定使用、修改和分发软件。

任何使用者均应遵守其所在地适用的法律法规，不得利用本软件实施诈骗、洗钱、非法集资、侵犯他人隐私或其他违法犯罪活动。

第三方基于本项目开展的独立业务活动，由相应实施者依法承担责任。项目维护者不参与、不认可任何未经授权的第三方商业活动。

本项目按现状提供，不提供超出适用法律允许范围的任何担保。本声明不排除依法不能免除的责任。

## 📄 开源许可

轻账原创源代码采用 [MIT License](LICENSE) 发布。你可以在保留版权及许可证声明的前提下使用、修改和再分发代码（包括商业用途）。第三方依赖、素材及其授权仍遵循各自许可证；本许可证不授予应用名称、商标或其他第三方内容的额外权利。

## 📚 文档与维护说明

- [Google Drive 备份配置](docs/GOOGLE-DRIVE-BACKUP.md)
- [匿名反馈配置](docs/ANONYMOUS-FEEDBACK.md)
- [ai 记账与内置语音模型](docs/VOICE-ENTRY.md)
- [鲨鱼记账导入与迁移说明（历史版本资料）](docs/QINGZHANG-1.2.0.md)
- [公开发行版更新方案（历史技术说明）](docs/UPDATE-1.0.8.md)

`docs/` 中的旧版说明、签名验证记录及下载地址仅供追溯，**不代表当前版本的安装指引**。请始终以本页和最新 GitHub Release 为准。

---

**轻账**：把每一笔生活支出和装修投入记清楚。
