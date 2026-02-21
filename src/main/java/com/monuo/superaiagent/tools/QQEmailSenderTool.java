package com.monuo.superaiagent.tools;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.util.Properties;

/**
 * QQ邮箱邮件发送工具类
 */
@Slf4j
@Component
public class QQEmailSenderTool {

    @Value("${qq-email.from}")
    private String fromEmail;

    @Value("${qq-email.auth-code}")
    private String authCode;

    @Value("${qq-email.smtp-host}")
    private String smtpHost;

    @Value("${qq-email.smtp-port}")
    private String smtpPort;

    /**
     * 发送纯文本邮件
     *
     * @param to      收件人邮箱地址
     * @param subject 邮件主题
     * @param content 邮件正文（纯文本）
     * @throws MessagingException 发送失败时抛出异常
     */
    @Tool(description = "Sends an email using QQ Mail's SMTP service. " +
            "Requires the recipient's email address, subject line, and content. " +
            "The sender's QQ email address and SMTP authorization code must be pre-configured in the system.")
    public String sendTextEmail(
            @ToolParam(description = "Recipient's email address") String to,
            @ToolParam(description = "Email subject line") String subject,
            @ToolParam(description = "Content of the email") String content) {

        try {
            Properties props = new Properties();
            props.put("mail.smtp.host", smtpHost);
            props.put("mail.smtp.port", smtpPort);
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.starttls.enable", "true"); // 启用STARTTLS加密（推荐）

            Session session = Session.getInstance(props, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(fromEmail, authCode);
                }
            });

            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(fromEmail));
            message.setRecipient(Message.RecipientType.TO, new InternetAddress(to));
            message.setSubject(subject);
            message.setText(content);

            Transport.send(message);
            log.info("✅ 文本邮件发送成功！收件人：{}", to);
            return "邮件发送成功！收件人：" + to;
        } catch (Exception e) {
            log.error("❌ 邮件发送失败：{}", e.getMessage());
            return "邮件发送失败：" + e.getMessage();
        }
    }

    /**
     * 发送HTML格式邮件
     *
     * @param to           收件人邮箱
     * @param subject      邮件主题
     * @param htmlContent  HTML内容（支持标签如 <b>, <p>, <a> 等）
     * @throws MessagingException 发送失败时抛出
     */
    @Tool(description = "Sends an HTML formatted email using QQ Mail's SMTP service. " +
            "Requires the recipient's email address, subject line, and HTML content.")
    public String sendHtmlEmail(
            @ToolParam(description = "Recipient's email address") String to,
            @ToolParam(description = "Email subject line") String subject,
            @ToolParam(description = "HTML content of the email") String htmlContent) {

        try {
            Properties props = new Properties();
            props.put("mail.smtp.host", smtpHost);
            props.put("mail.smtp.port", smtpPort);
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.starttls.enable", "true");

            Session session = Session.getInstance(props, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(fromEmail, authCode);
                }
            });

            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(fromEmail));
            message.setRecipient(Message.RecipientType.TO, new InternetAddress(to));
            message.setSubject(subject);
            message.setContent(htmlContent, "text/html;charset=UTF-8");

            Transport.send(message);
            log.info("✅ HTML邮件发送成功！收件人：{}", to);
            return "HTML邮件发送成功！收件人：" + to;
        } catch (Exception e) {
            log.error("❌ HTML邮件发送失败：{}", e.getMessage());
            return "HTML邮件发送失败：" + e.getMessage();
        }
    }
}
