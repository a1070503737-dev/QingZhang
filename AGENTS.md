# 轻账发行流程

本项目的 Android 新版本完成测试后，直接发布到本仓库的 GitHub Releases，无需逐次向项目所有者确认。发布前递增版本号和 versionCode，并使用现有正式版签名。使用 `tools/verify_update.py` 核对覆盖安装所需的包名、版本号和签名。

使用 `tools/create_update_manifest.py` 从签名后的 APK 生成 `update.json`。在发行草稿上传带版本号的 APK、官网直链使用的同一 APK 副本 `renovation-ledger-latest.apk`、更新清单及校验文件。核对附件齐全后设为 Latest，再验证公开的更新清单和 APK 直链。
