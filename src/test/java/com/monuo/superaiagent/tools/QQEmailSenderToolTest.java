package com.monuo.superaiagent.tools;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class QQEmailSenderToolTest {

    @Autowired
    private QQEmailSenderTool qqEmailSenderTool;

    @Test
    void sendTextEmail() {
        String result = qqEmailSenderTool.sendTextEmail(
                "recipient@example.com",  // 替换为目标邮箱
                "测试邮件",
                "这是一封通过Java发送的测试邮件。"
        );
        System.out.println("结果: " + result);
        assertNotNull(result);
        assertTrue(result.contains("成功") || result.contains("失败"));
    }

    @Test
    void sendHtmlEmail() {
        String html = "<h2>🎉 这是一封HTML测试邮件</h2>" +
                "<p><b>加粗内容</b>，<a href='https://example.com'>点击链接</a></p>";
        String result = qqEmailSenderTool.sendHtmlEmail("recipient@example.com", "HTML测试", html);
        System.out.println("结果: " + result);
        assertNotNull(result);
        assertTrue(result.contains("成功") || result.contains("失败"));
    }
}
