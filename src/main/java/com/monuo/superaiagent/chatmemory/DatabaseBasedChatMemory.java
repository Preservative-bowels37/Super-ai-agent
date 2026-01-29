package com.monuo.superaiagent.chatmemory;

import com.monuo.superaiagent.converter.MessageConverter;
import com.monuo.superaiagent.entity.ChatMessage;
import com.monuo.superaiagent.repository.ChatMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@Slf4j
@RequiredArgsConstructor// 构造函数注入
public class DatabaseBasedChatMemory implements ChatMemory {

    private final ChatMessageRepository chatMessageRepository;

    @Override
    public void add(String conversationId, List<Message> messages) {
        List<ChatMessage> chatMessages = messages.stream()
                .map(message -> MessageConverter.toChatMessage(message, conversationId))
                .collect(Collectors.toList());
        
        chatMessageRepository.saveBatch(chatMessages, chatMessages.size());
        log.debug("Saved {} messages for conversation {}", messages.size(), conversationId);
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
