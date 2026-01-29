package com.monuo.superaiagent.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;
import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@TableName(value = "`chat_message`", autoResultMap = true)
public class ChatMessage implements Serializable {

    @Serial
    @TableField(exist = false)
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    @TableField("conversation_id")
    private String conversationId;

    @TableField("message_type")
    private MessageType messageType;

    @TableField("content")
    private String content;

    @TableField(value = "metadata", typeHandler = com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler.class)
    private Map<String, Object> metadata;

    @TableField(value = "`create_time`", fill = FieldFill.INSERT)
    private Date createTime;

    @Version
    @TableField(value = "`update_time`", fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;

    @TableField("`is_delete`")
    @TableLogic
    private boolean isDelete;

    public enum MessageType {
        USER,
        ASSISTANT,
        SYSTEM,
        TOOL
    }
}
