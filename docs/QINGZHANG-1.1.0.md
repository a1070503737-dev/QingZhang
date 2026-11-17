# 轻账 1.1.0 开发交付

## 开发前核验

基线：GitHub `codex/ledger-ui-refresh` 提交 `80f99f902ae3c2da546432928ea1c8c8b15e7000`（1.0.9，versionCode 10）。开发分支为 `codex/qingzhang-1.1.0`，不覆盖原发布分支，也不创建 Release 或标签。

项目是原生 Android 单 Activity 应用，Kotlin 2.4.21、Jetpack Compose / Material 3、Hilt 2.60.1、Room 2.8.5、DataStore、WorkManager；保留原有依赖及 MVVM / Repository 结构。构建使用 AGP 9.5.1、Gradle 9.9.1、JDK 21、compile/target SDK 37、min SDK 26。

applicationId 继续为 `com.zhuangxiu.ledger`；目标 versionName `1.1.0`、versionCode `11`。原有 1.0.9 是已成功构建并交付的稳定基线，开发时再次核对了远端提交。

原数据库 `renovation.db` 的 v1 五张业务表：`categories`（阶段/费用类型）、`expenses`（装修订单）、`payments`（分次实际付款）、`attachments`（凭证）、`budgets`（总/分类预算）。装修合同金额、实际付款、剩余待付各自计算。旧导航包括首页、账单、统计、我的；备份是 SQLite 数据库与凭证图片 ZIP，恢复前检查 SQLite 完整性、Room 身份和外键。

## 本次功能

- 品牌显示改为「轻账」，蓝色 Material 3 主题、浅灰背景、圆角卡片、系统深色模式及新账本图标。
- 首页 / 统计 / 账本三项底部导航；首页顶部切换账本和进入设置，DataStore 记住最后使用的账本。
- 默认生活账本、装修账本；可创建多个生活类型账本、重命名账本，数据按 bookId 隔离。
- 生活账目新增、查询、编辑、确认删除；金额、收入/支出、分类、日期时间、支付方式、备注全部写入 Room。
- 快速记账默认支出及当天时间，金额自动聚焦、应用内数字键盘、分类图标网格、支付方式图标、日历与时间选择、底部固定保存按钮。
- 正数整数分存储，最多两位小数，单笔不超过 999,999,999.99 元。状态锁及稳定 UUID 防止重复新增，表单通过 SavedStateHandle 保存。
- 首页按月显示真实收入/支出/结余和账目；可搜索当前月分类、备注、支付方式，点记录编辑。
- 统计提供按月收支、分类环形图及排行比例条、截至所选月的近六个月收支柱状图（含零记录月份）。
- 退款只作为收入计一次；不自动抵扣旧支出，不把装修合同、待付款或装修付款写入生活统计。
- 装修首页、账单筛选、详情、分次付款、预算、分类、凭证、提醒以及原有统计继续沿用。
- 保留原有静态 GitHub 更新机制；测试版不修改正式更新清单。

## 数据迁移与恢复

Room v1 → v2 只有新增表：

| 表 | 内容 |
| --- | --- |
| `books` | 账本 ID、名称、DAILY/RENOVATION 类型；默认 ID 1 生活、ID 2 装修 |
| `daily_entries` | UUID 主键、bookId 外键、整数分金额、收支/分类/支付方式、epochDay 日期、当天分钟、备注 |

新记录按 `bookId, day, minute` 建索引。原有五张表的导出 schema 完全一致，没有删表、改字段、搬移旧账目或 destructive migration。原装修数据通过统一入口访问。

- Room 事务执行迁移，失败不会把原表清空。在首次打开 v1 数据库前，另存主库及 WAL 到应用私有目录 `database_safety/before-upgrade-v1`。
- ZIP 格式仍是 `database/renovation.db` 和 `receipts/`，兼容旧版文件。
- 导出先获取写事务锁，复制数据库和 WAL，再在副本合并 WAL，避免直接 checkpoint 后复制期间出现新写入。
- 恢复先在临时目录解压、校验版本/完整性/Room 身份/外键，并使用 Room 打开和迁移临时数据库；失败不触碰当前账本。
- 恢复 v1 备份：替换装修数据，同时把当前生活账本及生活记录保留到待恢复数据库中。
- 恢复 v2 备份：恢复备份中的全部账本。设置页有明确覆盖确认，替换前保存 `before-restore.zip`，可通过「导出恢复前安全副本」取出。
- 恢复日志在替换前落盘；若替换中断，下次建立 Room 单例前回滚旧数据库及凭证。恢复后必须重启进程。
- v2 备份不能导回旧版 1.0.x；旧版已按数据库版本拒绝不兼容恢复。升级前仍建议用户手动导出一份 v1 备份，避免卸载或设备丢失时私有备份不可取回。

日期按用户选择的本地日历日及分钟保存，不会因跨时区旅行而改变账目所属月份。现阶段无时区换算、币种换算，也不自动导入外部账单。

## 主要文件

- `data/db/AppDatabase.kt`、`DailyEntities.kt`、`app/schemas/.../2.json`：数据结构、DAO 与显式迁移。
- `data/repository/DailyRepository.kt`：校验、CRUD、重复提交控制和多账本。
- `domain/DailyStatistics.kt`：统一精确收支统计。
- `ui/daily/`：首页、快速记账、统计、账本及 ViewModel。
- `ui/navigation/`、`ui/theme/Theme.kt`、`ui/home/`、`ui/stats/`、`ui/mine/`：导航、视觉与原装修功能接入。
- `data/repository/BackupManager.kt`、`DatabaseSafety.kt`：一致快照、旧备份兼容、恢复回滚。
- `DailyDatabaseTest.kt`、`DailyStatisticsTest.kt`、`DailyFlowTest.kt`、`DailyScreenTest.kt`：迁移、备份、金额、真实 Activity 操作及显示测试；原装修回归测试保留。

## 验证结果

实际执行 `testDebugUnitTest`：**106 项测试通过，0 失败、0 错误、0 跳过**。包含原有 87 项及新增 19 项，清单见 [TEST-RESULTS-1.1.0.json](TEST-RESULTS-1.1.0.json)。

最终执行 `./gradlew testDebugUnitTest assembleDebug assembleRelease lintDebug --offline --console=plain`，**BUILD SUCCESSFUL（6 分 50 秒）**。Kotlin、Room/KSP、Compose、资源打包、R8 压缩及 Debug/Release APK 构建通过。Lint **0 错误、6 条建议性警告**（备份规则配置、资源目录、KTX 与测试依赖版本目录），没有新增错误。

交付的是沿用现有长期签名的 Release 构建测试 APK，`1.1.0 (11)`，约 3.1 MB；与 1.0.9 的包名/签名一致、versionCode 递增，16 KiB ZIP 对齐及 APK 签名验证通过。没有生成新密钥，没有把密钥或 GitHub 令牌放入 APK、源码或提交。完整核验见 [SIGNING-VERIFICATION-1.1.0.txt](SIGNING-VERIFICATION-1.1.0.txt)。

[下载测试 APK](../dist/qingzhang-1.1.0-signed-test.apk)。**请先在旧版导出备份，再覆盖安装；此测试版还未经过实体手机覆盖安装验收。数据库升级为 v2 后，不应尝试强行降级为 1.0.x。**Robolectric 使用 Android SDK 35 原生图形模式，不代表实体手机已完成覆盖安装。

界面截图（测试数据不会进入安装包）：
- [首页](screenshots/qingzhang-1.1.0/qingzhang-home.png)
- [快速记账](screenshots/qingzhang-1.1.0/qingzhang-quick-entry.png)
- [真实统计](screenshots/qingzhang-1.1.0/qingzhang-stats.png)
- [深色首页](screenshots/qingzhang-1.1.0/qingzhang-home-dark.png)
- [320dp / 1.3 倍字体](screenshots/qingzhang-1.1.0/qingzhang-quick-small-large-text.png)

## 交付范围与后续建议

1.1.0 不增加云账户、同步、自动读取微信/支付宝、AI、多人协作、收费或广告。暂不支持删除整本账本、自定义生活分类、月预算；已支持新建及重命名生活类型账本，未来业务类型可在 BookKind 扩展。

下一版建议先补月预算、分类自定义、CSV 导出及更多无障碍测试，再考虑可选的加密备份。云同步应有独立冲突与恢复设计，不能直接覆盖本地数据库。

需要真实手机验收系统文件选择器、分享、时间选择、键盘、低内存进程恢复、Android 不同版本的安装覆盖和厂商系统行为。自动测试与同证书验证不能替代这些设备测试。

## 验收场景与验证方式

| 验收场景 | 自动化证据 / 待真机部分 |
| --- | --- |
| 首次打开 | DailyFlowTest 启动真实 MainActivity、Hilt、Room，显示默认生活账本与零余额 |
| 新增支出/收入 | DailyFlowTest 通过界面保存 25.50 支出与 100 收入，检查 74.50 结余 |
| 编辑/删除 | DailyFlowTest 将支出改为 20，检查 80 结余；取消删除后再确认删除，检查 100 结余 |
| 首页即时刷新 | 同一流程等待 Room Flow 更新后的真实金额，未重启页面刷新 |
| 分类/月趋势 | DailyStatisticsTest 验证分类排行、退款、分精度、跨年、闰日和六个月零数据补齐 |
| 重启后记录 | DailyDatabaseTest 关闭/重开磁盘 Room 数据库，核对修改后的记录；DailyFlowTest 重建 Activity |
| 切换账本 | DailyFlowTest 切换生活/装修；DailyDatabaseTest 验证其他生活账本数据隔离 |
| 保留装修功能 | 原 RepositoryTest、BudgetCalculatorTest、StatsCalculatorTest、LedgerFlowTest 回归 |
| v1 升级保留数据 | 直接使用版本库中 v1 schema SQL 创建旧数据库，插入订单、付款、凭证关联和预算，再由 Room 执行真实迁移 |
| 备份恢复 | v2 ZIP 往返、v1 ZIP 恢复且保留生活账、损坏文件拒绝覆盖、中断恢复回滚 |
| 深色/屏幕尺寸 | Robolectric 原生图形截图，393dp 普通手机与 320dp / 1.3 倍字体；小屏固定键盘和保存入口可操作 |
| 实际手机安装 | 尚未连接实体设备；同包名、签名和 versionCode 校验不能代替实体设备覆盖安装测试 |

界面截图来自测试数据，不会打包为应用默认账目。测试 APK 首次安装没有任何演示金额。

## 后续发布约束

本分支只交付手动安装的测试 APK，不创建正式 Release，也不修改 `dist/update.json`。后续若获准正式发布，APK 附件需沿用更新协议文件名 `renovation-ledger-1.1.0-signed.apk` 并通过 `tools/create_update_manifest.py` 生成对应清单；若测试后继续修复，建议递增为 1.1.1（versionCode 至少 12），保证已安装测试版也能发现新版本。始终沿用现有长期签名。
