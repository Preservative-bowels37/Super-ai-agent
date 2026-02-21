package com.monuo.superaiagent.tools;

import cn.hutool.extra.mail.MailAccount;
import cn.hutool.extra.mail.MailUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 邮件发送工具类（基于 Hutool MailUtil）
 */
@Slf4j
@Component
public class MailSendTool {

    @Value("${qq-email.from}")
    private String fromEmail;

    @Value("${qq-email.auth-code}")
    private String authCode;

    @Value("${qq-email.smtp-host}")
    private String smtpHost;

    @Value("${qq-email.smtp-port}")
    private String smtpPort;

    /**
     * 发送邮件
     *
     * @param to      收件人邮箱
     * @param subject 邮件标题
     * @param content 邮件正文，可为 HTML
     * @param html    是否为html格式
     */
    @Tool(description = "Send email to a user with subject and content.")
    public String sendMail(
            @ToolParam(description = "Email address to send to.") String to,
            @ToolParam(description = "Email subject") String subject,
            @ToolParam(description = "Email content") String content,
            @ToolParam(description = "If the content is .html style") boolean html) {
        try {
            MailAccount account = new MailAccount();
            account.setHost(smtpHost);
            account.setPort(Integer.parseInt(smtpPort));
            account.setAuth(true);
            account.setFrom(fromEmail);
            account.setPass(authCode);
            account.setSslEnable(true);  // QQ 邮箱必须开启 SSL

            MailUtil.send(account, to, subject, content, html);
            log.info("✅ 邮件发送成功 -> {}", to);
            return "邮件发送成功 -> " + to;
        } catch (Exception e) {
            log.error("❌ 邮件发送失败：{}", e.getMessage());
            return "邮件发送失败：" + e.getMessage();
        }
    }
}
