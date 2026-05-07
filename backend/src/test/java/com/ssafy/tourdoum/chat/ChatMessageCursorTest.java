package com.ssafy.tourdoum.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** {@link ChatMessageCursor} 단위 — encode/decode round-trip + 변조 방지. */
class ChatMessageCursorTest {

  @Test
  @DisplayName("encode → decode round-trip은 동일 값을 복원한다")
  void encode_decode_round_trip() {
    ChatMessageCursor original =
        new ChatMessageCursor(Instant.parse("2026-05-08T07:55:56.713Z"), 12345L);

    String token = original.encode();
    ChatMessageCursor decoded = ChatMessageCursor.decodeOrNull(token);

    assertThat(decoded).isEqualTo(original);
    assertThat(token).doesNotContain("=", "+", "/"); // URL-safe + no padding.
  }

  @Test
  @DisplayName("decodeOrNull(null) / decodeOrNull(\"\") 는 null을 반환한다 (cursor 미지정)")
  void decode_null_or_blank_returns_null() {
    assertThat(ChatMessageCursor.decodeOrNull(null)).isNull();
    assertThat(ChatMessageCursor.decodeOrNull("")).isNull();
    assertThat(ChatMessageCursor.decodeOrNull("   ")).isNull();
  }

  @Test
  @DisplayName("base64 디코드 자체가 깨지면 IllegalArgumentException")
  void decode_invalid_base64_throws() {
    assertThatThrownBy(() -> ChatMessageCursor.decodeOrNull("!@#not-base64$$$"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("JSON shape이 무효(timestamp/id 없음) 시 IllegalArgumentException")
  void decode_invalid_payload_shape_throws() {
    String bad = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"x\":1}".getBytes());
    assertThatThrownBy(() -> ChatMessageCursor.decodeOrNull(bad))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("음수 id payload는 무효 — IllegalArgumentException")
  void decode_negative_id_throws() {
    String bad =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(("{\"t\":\"2026-05-08T07:55:56Z\",\"id\":-1}").getBytes());
    assertThatThrownBy(() -> ChatMessageCursor.decodeOrNull(bad))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
