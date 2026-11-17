# 1.0.5 候选版与签名发布说明

> 状态：源码准备中；只有通过同签名验证、真机覆盖更新验证后，才能发布为正式 v1.0.5。不要把 Debug APK 当作正式安装包。

## 本次改动

- `versionName = "1.0.5"`、`versionCode = 6`（1.0.4 为 5）。
- 公开 GitHub 仓库无需 Token 即可检查、下载正式 Release，仍保留可选的只读令牌支持。
- 数据库结构、应用 ID `com.zhuangxiu.ledger` 保持不变。

## GitHub Actions 候选版构建

工作流：`.github/workflows/prepare-release-1.0.5.yml`，在目标分支 `codex/ledger-ui-refresh` 推送相关源码时自动运行，也可手动运行。

运行单元测试、构建 release APK；**只在原始 1.0.4 签名密钥与密码可用时签名**。签名后强制执行 `tools/verify_update.py` 与 1.0.4 对比包名、签名证书、versionCode，额外核对 versionName 和 ZIP alignment。产物保存在 Actions Artifacts，**不会自动创建 GitHub Release**。

如果没有配置签名密钥，流程只会完成单元测试和未签名构建，明确报告“没有产生可安装的正式候选版”。

## 签名密钥配置（请在 GitHub 设置中操作）

前往 GitHub 仓库 **Settings → Secrets and variables → Actions → New repository secret**，分别添加：

1. `RENOVATION_LEDGER_KEYSTORE_BASE64`：用 **1.0.4 原始** `renovation-ledger-release.keystore` 文件转成 Base64 后的文本。
2. `RENOVATION_LEDGER_STORE_PASSWORD`：原密钥库密码。
3. `RENOVATION_LEDGER_KEY_PASSWORD`：别名 `renovation-ledger` 的原密钥密码。

注意：**不要生成新的签名密钥，不要将密钥文件、密码或 Base64 文本上传到公开仓库，也不要粘贴到公开 Issue 或聊天中。** 密钥由 GitHub Actions Secrets 注入临时构建环境，不写入 APK 或仓库。找不到原签名密钥，则无法制作能直接覆盖已安装 1.0.4 的新安装包。

## 正式发布前的检查

1. 确认 CI 单元测试和 release APK 编译通过。
2. 确认 CI 使用同一密钥签名，证书 SHA-256 应为 `90d5df45a2ea445564d9b400bc19b4224ae8ec43fa3c81f1bf8a5dbb971a4fd5`，安装包 metadata 为 `com.zhuangxiu.ledger` / `6` / `1.0.5`。
3. 在真实手机用现有 1.0.4 数据直接覆盖安装候选版，检查账目、照片附件、备份恢复及“我的 → 检查更新”功能。
4. 通过后创建 Tag `v1.0.5` 的正式 GitHub Release，上传 **已经验证过的** `renovation-ledger-1.0.5-signed.apk` 和 `SHA256SUMS-1.0.5.txt`，确保附件的 SHA-256 digest 可被应用读取，再设为 Latest。
5. 未完成上述步骤前不要覆盖/删除 v1.0.4 Release，也不要卸载设备上已有的 App。
