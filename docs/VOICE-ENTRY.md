# ai 记账

1. 在首页长按「ai 记账」。首次使用时授予麦克风权限。如果当前在全部账本或装修账本，而且有多个日常账本可选，先选择要记入的账本。
2. 按住说完再松手，例如「昨天早餐八块五，午饭二十五元」。开始录音时手机会震动，按钮会显示「松手识别」。单次录音最长 45 秒。
3. 在首页弹出的草稿里核对金额、日期、分类和备注。可以编辑识别文字，再点「解析文字」。说「大概三十元」之类的估算金额时，需要补填确切金额。确认后才保存，可能重复的账目会再次提示。
4. 轻点「ai 记账」可直接输入文字。

## 离线模型

轻账内置 Zipformer 中文 CTC int8 模型，通过 sherpa-onnx 2.0.3 在本机识别。安装后可在飞行模式下使用。录音在内存中以 16 kHz 单声道 PCM 处理，转写后由本地规则生成草稿。许可证与上游来源位于安装包的 `assets/voice` 目录。

| 构建文件 | SHA-256 |
| --- | --- |
| `app/src/main/assets/voice/model.int8.onnx` | `32e5f17cc9a77d480f8d94bda97b7cc7a40965b6651b35385813f561a74129c8` |
| `app/src/main/assets/voice/tokens.txt` | `6fed8c6c248516f38e7faa19404b57413e8ce259f1cbc1fa4aebc86eac32fdfd` |
| `app/libs/sherpa-onnx-static-2.0.3.aar` | `7744a3a8f08f017d59c2d8ed8d9b700345561f5abe9f4e06dc6eaef3a0b977b4` |

[Zipformer 中文模型](https://huggingface.co/csukuangfj/sherpa-onnx-zipformer-ctc-small-zh-int8-2025-07-16) · [sherpa-onnx Android 运行库](https://github.com/k2-fsa/sherpa-onnx/releases/tag/v2.0.3)

## 真机测试

调试 APK 使用独立应用数据。安装后开启飞行模式，从首页试一次长按录音、多笔确认和拒绝权限后手动输入。试录「拿铁二十，地铁三块五」并核对分类和金额。试录「外卖三十块左右」时，金额应留空等待确认。
