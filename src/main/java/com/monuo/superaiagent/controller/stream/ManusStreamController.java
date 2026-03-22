package com.monuo.superaiagent.controller.stream;

import com.monuo.superaiagent.agent.MonuoManus;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Manus 超级智能体流式接口控制器
 */
@RestController
@RequestMapping("/ai/manus")
public class ManusStreamController {

    private static final Logger log = LoggerFactory.getLogger(ManusStreamController.class);

    @Resource
    private MonuoManus monuoManus;

    /**
     * 流式调用 Manus 超级智能体
     * 使用 SseEmitter 进行流式传输
     *
     * @param message 用户消息
     * @param chatId  对话 ID（用于数据库持久化）
     * @return SseEmitter 流式数据
     */
    @GetMapping("/chat")
    public SseEmitter doChatWithManus(String message, String chatId) {
        try {
            return monuoManus.runStream(message, chatId);
        } catch (Exception e) {
            // 检查是否是内容安全检测失败
            if (e.getMessage() != null && e.getMessage().contains("DataInspectionFailed")) {
                log.warn("内容安全检测失败，用户输入可能包含敏感内容：{}", message);
                SseEmitter emitter = new SseEmitter(30000L);
                try {
                    emitter.send(SseEmitter.event()
                        .name("error")
                        .data("抱歉，您的输入可能包含不当内容，请修改后重试。"));
                    emitter.complete();
                } catch (Exception sendEx) {
                    log.error("发送错误消息失败", sendEx);
                }
                return emitter;
            }
            // 其他异常
            log.error("Manus 智能体异常：{}", e.getMessage(), e);
            SseEmitter emitter = new SseEmitter(30000L);
            try {
                emitter.send(SseEmitter.event()
                    .name("error")
                    .data("抱歉，发生了错误：" + e.getMessage()));
                emitter.complete();
            } catch (Exception sendEx) {
                log.error("发送错误消息失败", sendEx);
            }
            return emitter;
        }
    }
}
