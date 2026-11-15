# 装修记账

个人 / 家庭装修记账 App：只管理一套房子的装修，数据全部保存在手机本地，不需要登录，也没有服务器。

## 下载安装

不想自己编译的话，可以直接下载安装包：[`dist/renovation-ledger-1.0.0-debug.apk`](dist/renovation-ledger-1.0.0-debug.apk)（约 26 MB，点开后按 **Download raw file**）。

- 下载到手机后点击安装，系统会提示“允许安装未知来源的应用”，允许即可。
- 这是**调试版**安装包，用临时的调试签名。以后如果改用自己签名的正式包，需要先卸载这个版本——卸载会清空数据，请先在 App 里导出备份。

## 功能

- **账单列表**：按日期分组，可搜索、按阶段 / 类型 / 付款状态筛选；**左滑或长按一笔账单即可删除**（会先确认）
- **记账**：金额、日期、装修阶段、费用类型、商家/工人、备注、付款状态、多张凭证；首页悬浮按钮一键“记一笔”，数字键盘输入金额
- **付款状态**：已付 / 定金 / 待付尾款 / 未付；一笔订单登记总价、已付、剩余，可分多次付款；待付款可设到期日并提前通知
- **预算**：总预算 + 按阶段 / 类型的单独预算；已花、剩余、百分比、进度条；用到 80% 预警、超支标红并通知
- **凭证**：拍照（CameraX）或从系统相册选图，图片保存在 App 私有目录；全屏查看、双指缩放、删除；凭证库按时间浏览
- **统计**：花费占比饼图（按阶段 / 类型）、每月花费柱状图、预算与实际对比，可按时间范围筛选
- **备份恢复**：数据库 + 图片打包成 zip，保存到本地或用系统分享导出；可从备份文件恢复
- **分类管理**：装修阶段、费用类型都可以增删改、排序；首次启动自动写入默认分类
- 温暖家居风界面，支持深色模式；金额统一显示为 `¥1,234.56`

### 几个约定

- 金额一律用 `Long` 按“分”存储，没有浮点误差。
- **“已花”按账单总价统计**（包含还没付清的尾款），因为签了单这笔钱就已经占用预算；**“待付总额”**是所有账单的剩余未付金额之和。
- 付款状态由“总价 + 付款记录”自动推导：付清即变“已付”，付了一部分是“定金”或“待付尾款”，没付是“未付”。
- 已有账单在使用的分类不能删除（可以改名）。

## 技术栈

Kotlin · Jetpack Compose · Material 3 · MVVM + Repository · Hilt · Room · DataStore · WorkManager · CameraX · Coil 3  
图表用 Compose Canvas 自绘（没有引入第三方图表库）。minSdk 26，targetSdk / compileSdk 37。

## 目录结构

```
app/src/main/java/com/zhuangxiu/ledger/
├── LedgerApp.kt / MainActivity.kt   应用入口、唯一的 Activity
├── data/
│   ├── db/            Room：实体、DAO、数据库（含默认分类）
│   └── repository/    账单、分类、预算、设置仓库；图片存储；备份恢复
├── domain/            纯计算逻辑：预算、付款状态、统计
├── di/                Hilt 模块
├── notification/      本地通知、每日提醒任务（WorkManager）
├── util/              金额、日期工具
└── ui/
    ├── theme/         配色、形状
    ├── components/    通用组件、图表
    ├── navigation/    路由、底部导航
    ├── home/ bills/ stats/ mine/      底部导航的 4 个页面
    └── edit/ detail/ viewer/ gallery/ 记账编辑、详情、图片查看、凭证库
app/src/test/          单元测试（预算计算、付款状态、统计、金额、备份、Repository）
app/schemas/           Room 导出的数据库结构，以后升级表结构写迁移时用
```

## 在 Android Studio 里运行

1. 安装最新稳定版 Android Studio（项目使用 Android Gradle Plugin 9.5，需要较新的版本；JDK 用 Studio 自带的即可）。
2. `File > Open`，选择本项目根目录，等待 Gradle 同步完成。首次同步会下载依赖，时间较长。
   如果提示缺少 SDK，按提示安装 **Android SDK Platform 37** 和 **Build-Tools 37**（`Settings > Languages & Frameworks > Android SDK`）。
3. 准备设备：
   - 真机：在手机“开发者选项”里打开 **USB 调试**，用数据线连接电脑并允许调试；
   - 或模拟器：`Tools > Device Manager > Create Virtual Device`，系统镜像选 Android 8.0（API 26）及以上。
4. 顶部工具栏选中 `app` 和你的设备，点击绿色的 **Run ▶**（Shift+F10）。

## 运行单元测试

- Android Studio：右键 `app/src/test` 目录 > `Run 'Tests in ...'`
- 命令行：`./gradlew testDebugUnitTest`（Windows 用 `gradlew.bat`）

测试在电脑上直接运行，不需要手机或模拟器。Repository 测试使用 Robolectric，首次运行会下载一个约 150 MB 的 Android 运行环境。

## 打包 APK

### 调试包（自己装着用最省事）

`Build > Generate App Bundles or APKs > Generate APKs`，或命令行：

```
./gradlew assembleDebug
```

产物在 `app/build/outputs/apk/debug/app-debug.apk`，可以直接拷到手机安装。调试包体积较大（约 26 MB）、运行稍慢。

### 正式包（体积小、速度快，需要签名）

1. `Build > Generate Signed App Bundle or APK…`，选择 **APK**。
2. 首次打包点 **Create new…** 创建签名文件（`.jks`），设置密码并填写信息。
   **务必保管好这个文件和密码**：以后升级 App 必须用同一个签名，否则只能卸载重装（卸载会清空数据，请先在 App 里导出备份）。
3. 选择 `release`，点击 **Create**。产物在 `app/release/app-release.apk`（约 3 MB）。

命令行 `./gradlew assembleRelease` 生成的是**未签名**的包（`app-release-unsigned.apk`），不能直接安装，需要按上面的方式签名。
签名文件和密码不要提交到 Git（`.gitignore` 已忽略 `*.jks`）。

## 数据与备份说明

- 数据库和图片都在 App 私有目录，卸载 App 会一并删除；系统的自动云备份已关闭（`allowBackup=false`），数据不会离开手机。
- 换手机、重装或升级前，请在 **我的 > 备份与恢复** 里导出备份；在新手机上用“从备份恢复”选择该 zip 文件即可。
- 恢复会覆盖当前全部数据，完成后 App 会自动重启。
- 提醒由后台任务每天早上 9 点左右检查一次；部分手机的省电策略会推迟或拦截后台任务，收不到提醒时请在系统设置里允许本应用自启动 / 后台运行。
