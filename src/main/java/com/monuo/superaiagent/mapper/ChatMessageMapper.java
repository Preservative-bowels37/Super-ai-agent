package com.monuo.superaiagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.monuo.superaiagent.entity.ChatMessage;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {
}
