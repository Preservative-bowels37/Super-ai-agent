package com.monuo.superaiagent.chatmemory;

import com.monuo.superaiagent.converter.MessageConverter;
import com.monuo.superaiagent.entity.ChatMessage;
import com.monuo.superaiagent.repository.ChatMessageRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor// 构造函数注入
public class DatabaseBasedChatMemory implements ChatMemory {

    private static final Logger log = LoggerFactory.getLogger(DatabaseBasedChatMemory.class);

    private final ChatMessageRepository chatMessageRepository;

    @Override
    public void add(String conversationId, List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            log.debug("No messages to save for conversation {}", conversationId);
            return;
        }

        // 直接转换并保存所有消息，不进行内容去重
        // 用户可能真的想发送相同的内容，这是正常的对话行为
        List<ChatMessage> newChatMessages = messages.stream()
                .map(message -> MessageConverter.toChatMessage(message, conversationId))
                .collect(Collectors.toList());

        if (newChatMessages.isEmpty()) {
            log.debug("No messages to convert for conversation {}", conversationId);
            return;
        }

        // 批量保存新消息
        chatMessageRepository.saveBatch(newChatMessages, newChatMessages.size());
        log.debug("Saved {} messages for conversation {}", newChatMessages.size(), conversationId);
    }

    @Override
    public List<Message> get(String conversationId) {
        List<ChatMessage> chatMessages = chatMessageRepository.listByConversationId(conversationId);
        List<Message> messages = chatMessages.stream()
                .map(MessageConverter::toMessage)
                .collect(Collectors.toList());
        
        log.debug("Retrieved {} messages for conversation {}", messages.size(), conversationId);
        return messages;
    }

    @Override
    public void clear(String conversationId) {
        chatMessageRepository.deleteByConversationId(conversationId);
        log.debug("Cleared all messages for conversation {}", conversationId);
    }
}
