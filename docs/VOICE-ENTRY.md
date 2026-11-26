# ai 记账

1. 在首页长按「记一笔」。首次使用时授予麦克风权限。如果当前在全部账本或装修账本，而且有多个日常账本可选，先选择要记入的账本。
2. 按住说完再松手，例如「昨晚八点半吃饭花了 25 元，用微信付的」。开始录音时手机会震动，按钮会显示「松手识别」；按住时将手指滑离按钮区域会显示「松手取消」，此时松手即可取消语音识别。单次录音最长 45 秒。
3. 在首页弹出的草稿里核对金额、分类、支付方式、日期、时间和备注。可以手动选择支付方式，时间可填 24 小时制的具体值（如 18:30）。时间留空时，保存会使用手机当前时间。可以编辑识别文字，再点「解析文字」。说「大概三十元」之类的估算金额时，需要补填确切金额。确认后才保存，可能重复的账目会再次提示。
4. 轻点「记一笔」打开记账表单。识别文字为空时，可以在语音草稿里手动输入。

## 图片记账

在首页轻点「记一笔」，再点日常记账表单右上角的「图片记账」。可以从相册或文件选择一张单笔交易详情截图，或拍一张小票。轻账在手机上识别文字，再提取能确认的实付金额、日期、时间、支付方式和商品或商户信息。每张图片生成一份可编辑的草稿。金额不明确时留空，需核对后保存。图片账单保存后会回到表单，已输入的内容仍在。

中文 OCR 随应用安装，可离线使用。ML Kit 无法运行或没有读到文字时，轻账会自动用内置的 Tesseract 中英文识别。图片和识别结果在手机上处理。可以修改提取到的文字再点「解析文字」。

备用识别使用 Tesseract4Android 4.9.0 与固定版本的 tessdata_fast 4.1.0，许可证随安装包保存在 `assets/ocr/licenses` 下。[Tesseract4Android](https://github.com/adaptech-cz/Tesseract4Android/releases/tag/4.9.0) · [文字模型](https://github.com/tesseract-ocr/tessdata_fast/tree/65727574dfcd264acbb0c3e07860e4e9e9b22185)

## 可选云端整理

在「设置 → 云端语音整理」选择接口类型，填入服务商提供的 HTTPS 地址、模型 ID 和 API Key。支持 DeepSeek、OpenAI 兼容 Chat Completions、OpenAI Responses、Google Gemini 和 Anthropic Messages。「测试连接」会用所选模型处理一条固定消息，并显示服务和模型。点「测试并启用」可在测试成功后保存配置。服务未报告模型时，界面显示配置的模型 ID。

轻账把当次文字、当天日期、分类和支付方式选项发给选定服务，按账号用量计费。密钥在本机加密保存，可以暂停或移除。记录时间由本机按用户说出的时间处理。云端调用失败时会提示并使用本地规则生成草稿。

## 离线模型

轻账内置 Zipformer 中文 CTC int8 模型，通过 sherpa-onnx 2.0.3 在本机识别。安装后可在飞行模式下使用。录音在内存中以 16 kHz 单声道 PCM 处理，转写后由本地规则生成草稿。内置模型的许可证与上游来源位于安装包的 `assets/voice` 目录。

在「设置 → 离线语音模型」点「下载并使用」可启用约 237 MB 的 SenseVoice Small INT8。下载支持暂停和继续，轻账校验模型和词表后才会启用。回到首页长按「记一笔」即可用新模型离线转写普通话或粤语。设置中可以切回内置模型，也可以删除下载文件。当前设备内存不足或模型无法加载时，会使用内置模型完成本次转写。

SenseVoice 文件来自固定的上游版本，单独保存于应用的免备份目录。模型和词表的校验值记录于 `SenseVoiceDownload.kt`。[SenseVoice INT8 上游模型](https://huggingface.co/csukuangfj/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2025-09-09/tree/355f4d4884d8afd08aef04b9007a8556d7b463b2)

| 构建文件 | SHA-256 |
| --- | --- |
| `app/src/main/assets/voice/model.int8.onnx` | `32e5f17cc9a77d480f8d94bda97b7cc7a40965b6651b35385813f561a74129c8` |
| `app/src/main/assets/voice/tokens.txt` | `6fed8c6c248516f38e7faa19404b57413e8ce259f1cbc1fa4aebc86eac32fdfd` |
| `app/libs/sherpa-onnx-static-2.0.3.aar` | `7744a3a8f08f017d59c2d8ed8d9b700345561f5abe9f4e06dc6eaef3a0b977b4` |

[Zipformer 中文模型](https://huggingface.co/csukuangfj/sherpa-onnx-zipformer-ctc-small-zh-int8-2025-07-16) · [sherpa-onnx Android 运行库](https://github.com/k2-fsa/sherpa-onnx/releases/tag/v2.0.3)

## 真机测试

调试 APK 使用独立应用数据。安装后开启飞行模式，从首页试一次长按录音、多笔确认和拒绝权限后切换到常规记账。试录「拿铁二十，地铁三块五」并核对分类和金额。试录「外卖三十块左右」时，金额应留空等待确认。
