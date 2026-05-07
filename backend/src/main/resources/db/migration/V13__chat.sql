-- V13: 채팅 채널 / 채널 구성원 / 메시지

CREATE TABLE chat_channels (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    name       VARCHAR(100)           NOT NULL,
    type       ENUM ('PUBLIC', 'DM')  NOT NULL DEFAULT 'PUBLIC',
    created_at DATETIME(6)            NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);

CREATE TABLE chat_members (
    channel_id           BIGINT      NOT NULL,
    member_id            BIGINT      NOT NULL,
    joined_at            DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    last_read_message_id BIGINT,
    PRIMARY KEY (channel_id, member_id),
    CONSTRAINT fk_chat_members_channel FOREIGN KEY (channel_id) REFERENCES chat_channels (id) ON DELETE CASCADE,
    CONSTRAINT fk_chat_members_member  FOREIGN KEY (member_id)  REFERENCES members (id)       ON DELETE CASCADE
);

CREATE TABLE chat_messages (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    channel_id BIGINT      NOT NULL,
    sender_id  BIGINT      NOT NULL,
    content    TEXT        NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_chat_messages_channel FOREIGN KEY (channel_id) REFERENCES chat_channels (id) ON DELETE CASCADE,
    CONSTRAINT fk_chat_messages_sender  FOREIGN KEY (sender_id)  REFERENCES members (id)       ON DELETE CASCADE,
    INDEX idx_chat_messages_channel_id (channel_id, id)
);
