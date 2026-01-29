-- 创建数据库（使用与参考一致的字符集和排序规则）
DROP DATABASE IF EXISTS `super_ai_agent`;
CREATE DATABASE IF NOT EXISTS `super_ai_agent` 
    DEFAULT CHARACTER SET utf8mb4 
    COLLATE utf8mb4_general_ci;  -- 与参考表保持一致

USE `super_ai_agent`;

-- 导出 表 super_ai_agent.chat_message 结构
DROP TABLE IF EXISTS `chat_message`;
CREATE TABLE IF NOT EXISTS `chat_message` (
                                              `id`              bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                                              `conversation_id` varchar(64)     NOT NULL DEFAULT '' COMMENT '会话ID',
    `message_type`    varchar(20)     NOT NULL DEFAULT '' COMMENT '消息类型：user-用户, assistant-助手, system-系统',
    `content`         text            NOT NULL COMMENT '消息内容',
    `metadata`        text            NOT NULL COMMENT '元数据（JSON格式）',
    `create_time`     datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_delete`       tinyint(1)      NOT NULL DEFAULT 0 COMMENT '是否删除：0-未删除 1-已删除',
    PRIMARY KEY (`id`),
    KEY `idx_conversation_id` (`conversation_id`)  -- 使用 KEY 而非 INDEX，与参考表一致
    ) ENGINE=InnoDB
    AUTO_INCREMENT=1
    DEFAULT CHARSET=utf8mb4
    COLLATE=utf8mb4_general_ci
    COMMENT='聊天消息表';