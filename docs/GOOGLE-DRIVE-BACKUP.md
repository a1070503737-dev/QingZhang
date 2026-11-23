# Google Drive 自动备份的发行配置

应用使用 Google Identity Services 和 Google Drive API。Google Cloud 项目中的 Android OAuth 客户端绑定包名和签名证书，APK 无需包含客户端密钥。

1. 在发行维护者所有的 Google Cloud 项目中启用 Google Drive API。
2. 设置 Google Auth Platform 的品牌、受众和数据访问。应用使用 `https://www.googleapis.com/auth/drive.file`，只访问它创建的备份文件。测试阶段将验收账号加入测试用户。面向正式用户发布前，设置适当的受众和发布状态。
3. 在同一个 Google Cloud 项目里为 1.3.18 及以后版本新增 Android OAuth 客户端。包名为 `com.zhuangxiu.ledger`，签名 SHA-1 为 `2C:A7:FB:FC:E3:6E:47:DB:AB:04:70:27:02:FA:1C:A9:0A:28:CF:FD`。需要继续支持 1.3.17 及更早版本时，保留对应 SHA-1 为 `97:66:33:4E:C8:EF:6D:3D:85:04:16:0D:B6:64:F8:A0:FC:06:6E:F7` 的客户端。
4. 在有 Google Play 服务的 Android 设备上安装正式签名的 APK。连接测试账号，点击「立即备份」，确认 ZIP 出现在 Drive 中，并确认界面显示成功时间。
5. 在测试设备上重新安装，用同一 Google 账号查看云端备份。确认列表和文件一致，选择一份云端备份，验证账本和凭证可恢复。再验证断开后自动备份停止，旧 ZIP 仍可访问。

备份频率关闭时只执行用户主动安排的上传。每天及每周的计划由 WorkManager 在联网并获得系统运行机会时执行。备份以单独的 ZIP 文件存入用户的 Drive。应用内列出最近 30 份，其他备份仍可从 Drive 选择并手动恢复。恢复会替换本机账本。若替换中断，应用会在重启时回滚到原数据。

启用「只保留最新一份」后，下一次自动或手动备份上传和校验成功时，应用会将此账号先前由轻账上传的旧备份移到 Google Drive 回收站。

[Android Google 授权文档](https://developer.android.com/identity/authorization) · [Google Drive 权限范围](https://developers.google.com/workspace/drive/api/guides/api-specific-auth)
