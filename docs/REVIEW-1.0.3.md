# 装修记账 1.0.3 优化记录

审查基线：`claude/android-renovation-accounting-app-48o21j`，提交 `ab70d4fb29e3328071947a763e34e9283ba7d9ac`。

## 已落实

- 界面：奶油白与松绿的浅色/深色主题；统一字号、圆角、描边、间距、阶段图标和四个主页面的标题。
- 首页：深色预算总览，明确区分订单总价、实际已付、剩余待付；增加首笔记账入口和加载提示。
- 适配：小屏或大字体时预算明细与付款卡纵向排列，避免金额被拆行；最近账单的日期独立显示。
- 后台提醒：使用 WorkManager 默认配置按需初始化，启动时仍注册同一个每日提醒任务。
- 待付：首页待付款卡直接进入“仅看待付”筛选，包含所有尚有余额的付款状态。
- 账单：日分组卡片与图标，支持大金额换行；付款和删除在数据库操作完成后才提示结果，失败显示错误。
- 金额输入：超长整数保留原文本并拒绝保存，修复原实现截掉高位后变成另一笔金额的问题。
- 结清：按数据库事务中的最新余额补记付款，保留已修改的账单内容；重复结清不会重复入账。
- 性能：首页汇总与账单筛选分组移到 Default 调度器，减少主线程计算。
- 恢复：在覆盖前验证 SQLite 完整性、版本、Room 身份与外键一致性，拒绝只含 SQLite 文件头的损坏备份。
- 更新核验：增加 `tools/verify_update.py`，检查 APK 有效签名、证书指纹、包名和递增版本号。

## 签名与覆盖安装

原版 APK SHA-256：
`ef6df0908516af0251d2fde0534deaf2a70b5c437e4c84439ef9403551320feb`

原签名证书 SHA-256：
`08e36317818aca74e02f9f02b3c14b6d73c27a70499b306fe82826dd67556833`

- 原包名 `com.zhuangxiu.ledger`，原 versionCode 3，新版 4 / 1.0.3。
- 包名、数据库名称 `renovation.db`、Room schema version 1、签名配置均未更换。
- 原版是临时 Android Debug 签名，密钥未在源码中保存。用户目前无法确认原构建环境是否保留密钥。
- `assembleRelease` 产物未签名，不能直接安装。没有原密钥时，无法交付保证可覆盖安装的新版 APK；证书或 APK 不能还原私钥。
- 新环境的 `assembleDebug` 会使用该环境自己的调试密钥，不代表能覆盖原版。不要将其作为更新交付。

找回原密钥后，在本地使用 Android build-tools 的 `apksigner sign --ks /path/to/original.keystore --out renovation-ledger-1.0.3.apk app/build/outputs/apk/release/app-release-unsigned.apk`，按提示输入密码，再运行：

```sh
python3 tools/verify_update.py dist/renovation-ledger-1.0.2-debug.apk renovation-ledger-1.0.3.apk
```

校验通过后，仍应在测试设备安装原版、写入账单和凭证，再执行 `adb install -r` 验证数据保留。此次未执行真实设备覆盖安装。

## 后续可优化

- 大账本：目前 Room 仍加载全部账单和关联付款/凭证。账单增多时，可以改成 SQL 汇总、数据库筛选与 Paging 分页；需要结合真实数据量验证收益。
- 凭证体积：目前保留原图片，可增加可选压缩与独立缩略图，同时保留合同/票据的可读性。
- 备份恢复：本次加强了覆盖前校验；后续可增加多文件替换中断时的回滚，以及导出期间并发写入的一致性测试。
- 长期发布：找回并安全备份原签名密钥，建立自动签名校验和版本号递增流程。

## 验证结果

- `./gradlew testDebugUnitTest assembleRelease lintDebug --console=plain`：2026-11-16 最终构建成功。
- 54 项自动化测试全部通过，0 失败、0 错误、0 跳过；包含金额、预算、付款、SQLite 备份校验以及 Compose 记账导航流程。
- Android lint：0 错误，4 条建议（两条测试依赖改用版本目录建议、自动备份规则建议、已有 SDK 条件简化建议）。
- 浅色、深色、空账本、小屏大字体、记账、待付筛选、统计、设置截图已检查，见 [界面预览](UI-PREVIEW.md)。
- APK 元数据：`com.zhuangxiu.ledger`，versionCode 4，versionName 1.0.3；minSdk 26 / targetSdk 37。
- 生成的正式包约 2.9 MiB，未签名；ZIP 对齐校验通过（包括 16 KiB 原生库页对齐）。
- 更新核验脚本已验证：拒绝未递增版本号的原包，也拒绝未签名的新包。
- 没有原私钥，无法完成相同签名的覆盖安装测试；未执行真机测试。
