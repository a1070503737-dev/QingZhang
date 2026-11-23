const FEEDBACK_RECIPIENT = 'a1070503737@gmail.com';
const FEEDBACK_FORM_ID_KEY = 'qingzhangFeedbackFormId';
const FEEDBACK_QUESTION = '反馈内容';
const CONTACT_QUESTION = '联系方式（选填）';

function setupQingZhangFeedback() {
  const lock = LockService.getScriptLock();
  lock.waitLock(10000);
  try {
    const properties = PropertiesService.getScriptProperties();
    const existingId = properties.getProperty(FEEDBACK_FORM_ID_KEY);
    const form = existingId
      ? FormApp.openById(existingId)
      : FormApp.create('轻账意见反馈', false);

    if (!existingId) {
      form.setDescription('欢迎分享使用体验和建议。你可以匿名提交，填写的内容将发送给轻账开发者。');
      form.setCollectEmail(false);
      form.setLimitOneResponsePerUser(false);
      form.setPublishingSummary(false);
      form.setConfirmationMessage('已收到你的反馈，谢谢！');
      form.addParagraphTextItem().setTitle(FEEDBACK_QUESTION).setRequired(true);
      form.addTextItem().setTitle(CONTACT_QUESTION)
        .setHelpText('如果希望收到回复，可以留下方便联系的方式。');
      properties.setProperty(FEEDBACK_FORM_ID_KEY, form.getId());
    }

    const hasTrigger = ScriptApp.getProjectTriggers().some(trigger =>
      trigger.getHandlerFunction() === 'forwardQingZhangFeedback' &&
      trigger.getEventType() === ScriptApp.EventType.ON_FORM_SUBMIT &&
      trigger.getTriggerSourceId() === form.getId(),
    );
    if (!hasTrigger) {
      ScriptApp.newTrigger('forwardQingZhangFeedback').forForm(form).onFormSubmit().create();
    }

    console.log('表单编辑地址：' + form.getEditUrl());
  } finally {
    lock.releaseLock();
  }
}

function forwardQingZhangFeedback(event) {
  const formId = PropertiesService.getScriptProperties().getProperty(FEEDBACK_FORM_ID_KEY);
  if (!event || !event.response || event.source.getId() !== formId) {
    return;
  }

  const answers = new Map(event.response.getItemResponses().map(answer => [
    answer.getItem().getTitle(),
    String(answer.getResponse()).trim(),
  ]));
  const feedback = answers.get(FEEDBACK_QUESTION);
  if (!feedback) {
    return;
  }

  const contact = answers.get(CONTACT_QUESTION) || '未填写';
  MailApp.sendEmail({
    to: FEEDBACK_RECIPIENT,
    subject: '轻账 · 新反馈',
    body: '反馈内容\n' + feedback + '\n\n联系方式\n' + contact,
  });
}
