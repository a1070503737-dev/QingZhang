# 语音记账和 Android AppFunctions

## 应用内使用

1. 打开一个未归档的日常账本，点「记一笔」和右上角的麦克风。
2. 点「开始中文语音识别」，授权麦克风后说「昨天早餐 8 元，午饭 25 元」。也可以直接输入文字，再点「解析文字」。
3. 核对每笔草稿的金额、收支、分类、日期和备注。可编辑字段或移除草稿。点「确认保存」后，整批账目写入当前日常账本。发现同日同类同金额时，界面会再次询问。
4. 识别失败、未授权或没有中文离线模型时，使用同页文字框。Android 12 起可尝试系统设备端识别，具体取决于设备和已安装语言模型。语音识别使用系统设备端接口，解析和保存都在本机。没有启用任何云端解析服务。

现有装修项目付款继续走装修记账流程。日期支持今天、昨天、前天、中文年月日的阿拉伯数字写法及 ISO 日期，缺省日期会显示为本地今天。同一句里由标点或「然后」「还有」分隔的多笔账目可批量预览。没有把握的金额或分类需要补齐后才能保存。

## AppFunctions 权限和行为

使用 AndroidX AppFunctions 1.0.0、KSP 生成的注册文件，以及 Android 系统的服务绑定权限。三个函数都默认关闭。在「设置 → 系统助手」开启之后，带系统执行权限的助手才能调用。关掉开关后，应用会拒绝新的调用。

- **createLedgerEntry** 打开待确认的草稿，可传入用户刚口述的内容。用户确认后，复用语音记账服务保存。调用重试应沿用同一个 `requestId`（8–96 个字母、数字、下划线、冒号或连字符）。同一请求只保存一批。
- **queryLedgerEntries** 在应用内打开指定账本、月份或日期的账单。
- **openMonthlySummary** 在应用内打开指定账本和月份的实际收支及分类汇总，统计口径与主应用一致。

省略账本编号时使用默认日常账本（编号 1）。函数响应为系统启动动作，账单和金额只在轻账界面中展示。Gemini 在识别用户主动说出的内容时适用 Gemini 自身的数据处理设置。正式版最低系统要求仍为 Android 8.0。对 AppFunctions 的支持由设备系统和 AndroidX 库决定。

## ADB 测试

使用启用了 AppFunctions 的 Android 16 或更新设备或模拟器。安装调试 APK 并启动轻账。调试包独立存储，适合生成测试账目。

```bash
adb devices
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell cmd app_function help
adb shell cmd app_function list-app-functions --package com.zhuangxiu.ledger.test
```

三个函数的 ID 前缀为 `com.zhuangxiu.ledger.assistant.BaseLedgerAppFunctionService#`。在应用设置里启用系统助手，等待系统索引，然后执行以下命令。系统命令能验证授权、参数及返回的启动动作。要实际打开返回的动作，可使用官方 AppFunctions 测试助手或 Gemini 开发者预览。

```bash
adb shell "cmd app_function execute-app-function \
  --package com.zhuangxiu.ledger.test \
  --function 'com.zhuangxiu.ledger.assistant.BaseLedgerAppFunctionService#createLedgerEntry' \
  --parameters '{\"requestId\":\"adb-voice-test-001\",\"transcript\":\"早餐 8 元，午饭 25 元\"}'"

adb shell "cmd app_function execute-app-function \
  --package com.zhuangxiu.ledger.test \
  --function 'com.zhuangxiu.ledger.assistant.BaseLedgerAppFunctionService#queryLedgerEntries' \
  --parameters '{\"month\":\"2026-11\"}'"

adb shell "cmd app_function execute-app-function \
  --package com.zhuangxiu.ledger.test \
  --function 'com.zhuangxiu.ledger.assistant.BaseLedgerAppFunctionService#openMonthlySummary' \
  --parameters '{}'"
```

检查关闭授权时调用被拒绝，非法月份和日期在打开页面前被拒绝。同一新增请求确认两次后，账目只增加一批。在兼容设备上通过真实麦克风测试简体中文识别、拒绝权限和离线模型缺失三种情况。

## Gemini 开发者预览

1. 申请 Google Connected Apps Platform 开发者预览。
2. 在 Play Console 为正式包名 `com.zhuangxiu.ledger` 上传对应 App Bundle 到测试轨道。使用项目原有签名配置。
3. 在 Google Connected Console 创建 Android MCP 类型的 Connected App，关联同一个 Play 应用和测试版本。把同一批测试账号加入 Play 测试轨道及 Connected App 的测试名单。
4. 在受支持的设备上用测试账号安装对应版本，在轻账内开启「系统助手」，确认 Console 检测到三个函数，再在 Gemini 里测试新增、查询、月度统计和授权撤回。

系统注册、通过 ADB 手动调用和通过 Gemini 找到应用属于不同环节。GitHub APK 分发可以用来做普通 App 或 ADB 试用。Gemini 中的可见范围取决于官方开发者预览、Play 测试版本和地区、设备支持情况。当前更改没有提交到 Google Connected Console。

[AppFunctions 集成与测试](https://developer.android.com/ai/appfunctions/add-appfunctions) · [Gemini Connected Apps 接入](https://developer.android.com/ai/appfunctions/create-manage-connected-apps) · [AppFunctions 数据共享指南](https://developer.android.com/ai/appfunctions/quality-guidelines)
