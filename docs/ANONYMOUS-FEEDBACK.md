# 启用轻账匿名反馈

1. 用 `a1070503737@gmail.com` 新建一个 Google Apps Script 项目。把 [安装脚本](../tools/setup-feedback.gs) 全部粘贴到编辑器，保存后选择 `setupQingZhangFeedback`，点「运行」并完成 Google 授权。项目授权用于创建表单、设置提交触发器和转发反馈邮件。[Google Apps Script](https://script.google.com/)
2. 从执行日志打开「表单编辑地址」，点击「发布」，把填写者的常规访问权限设为「知道链接的任何人」。发布后复制**填写链接**。[Google 表单发布说明](https://support.google.com/docs/answer/2839588?hl=zh-Hans)
3. 用未登录 Google 的浏览器打开填写链接，提交一条测试反馈，确认 Gmail 收到了正文。表单的「回复」页也会保留提交记录。

发布 APK 时，在项目的 `gradle.properties` 设置公开填写链接，供本次和后续构建使用。

```properties
qingzhangFeedbackFormUrl=https://docs.google.com/forms/d/e/表单的公开编号/viewform
```

配置后，「设置 → 意见反馈」会打开匿名表单，「邮件反馈」也可继续使用。应用只打开公开填写链接。联系信息由填写者自愿提供。
