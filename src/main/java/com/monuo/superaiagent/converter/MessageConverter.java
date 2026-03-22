package com.monuo.superaiagent.converter;

import com.monuo.superaiagent.entity.ChatMessage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.Map;

public class MessageConverter {

    // Message -> ChatMessage
    public static ChatMessage toChatMessage(Message message, String conversationId) {
        String content = getMessageText(message);
        Map<String, Object> metadata = message.getMetadata();
        
        // 【关键修复】如果 metadata 包含 thinkingSteps 且 content 包含【思考】标记
        // 说明这是工具调用阶段的消息，需要清空 content 或提取【回复】之后的内容
        if (metadata != null && metadata.containsKey("thinkingSteps") && content != null && content.contains("【思考】")) {
            int replyStart = content.indexOf("【回复】");
            if (replyStart != -1) {
                // 有【回复】标记，只保留【回复】之后的内容
                content = content.substring(replyStart + 4).trim();
            } else {
                // 只有【思考】没有【回复】，清空 content
                content = "";
            }
        }
        
        return ChatMessage.builder()
                .conversationId(conversationId)
                .messageType(convertMessageType(message))
                .content(content)
                .metadata(metadata)
                .build();
    }

    // ChatMessage -> Message
    public static Message toMessage(ChatMessage chatMessage) {
        ChatMessage.MessageType messageType = chatMessage.getMessageType();
        String text = chatMessage.getContent();
        Map<String, Object> metadata = chatMessage.getMetadata();
        
        return switch (messageType) {
            case USER -> new UserMessage(text);
            case ASSISTANT -> new AssistantMessage(text, metadata != null ? metadata : new java.util.HashMap<>());
            case SYSTEM -> new SystemMessage(text);
            case TOOL -> new UserMessage(text);
        };
    }

    private static ChatMessage.MessageType convertMessageType(Message message) {
        if (message instanceof UserMessage) {
            return ChatMessage.MessageType.USER;
        } else if (message instanceof AssistantMessage) {
            return ChatMessage.MessageType.ASSISTANT;
        } else if (message instanceof SystemMessage) {
            return ChatMessage.MessageType.SYSTEM;
        } else {
            return ChatMessage.MessageType.USER;
        }
    }

    private static String getMessageText(Message message) {
        if (message instanceof UserMessage userMessage) {
            return userMessage.getText();
        } else if (message instanceof AssistantMessage assistantMessage) {
            return assistantMessage.getText();
        } else if (message instanceof SystemMessage systemMessage) {
            return systemMessage.getText();
        } else {
            return "";
        }
    }
}
