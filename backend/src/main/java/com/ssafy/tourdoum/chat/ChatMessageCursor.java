package com.ssafy.tourdoum.chat;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

/**
 * 채팅 메시지 cursor — `(createdAt, id)` 복합키.
 *
 * <p>ADR-0012 v2 §3: cursor는 base64url-encode(JSON `{"t":"<ISO-8601>","id":<long>}`)이다. 학습 단계는 단순
 * base64 변조 방지만 본다(HMAC 등 추가 서명은 보류 — ADR 명시).
 *
 * <ul>
 *   <li>encode: 정상 record → URL-safe Base64 string (no padding).
 *   <li>decode: URL-safe Base64 string → record. 깨진 입력은 {@link IllegalArgumentException}.
 * </ul>
 */
public record ChatMessageCursor(Instant createdAt, long id) {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
  private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

  /** {@code (createdAt, id)} → base64url(JSON). */
  public String encode() {
    try {
      Payload payload = new Payload(createdAt.toString(), id);
      byte[] json = MAPPER.writeValueAsBytes(payload);
      return ENCODER.encodeToString(json);
    } catch (Exception e) {
      throw new IllegalStateException("cursor encode 실패", e);
    }
  }

  /**
   * base64url(JSON) → record.
   *
   * @param token null/blank이면 null 반환 (cursor 미지정 = 첫 페이지)
   * @throws IllegalArgumentException 디코드 실패 / 형식 불일치 / 음수 id 등
   */
  public static ChatMessageCursor decodeOrNull(String token) {
    if (token == null || token.isBlank()) {
      return null;
    }
    try {
      byte[] json = DECODER.decode(token);
      Payload payload = MAPPER.readValue(json, Payload.class);
      if (payload.t == null || payload.t.isBlank() || payload.id < 0) {
        throw new IllegalArgumentException("cursor payload 무효");
      }
      return new ChatMessageCursor(Instant.parse(payload.t), payload.id);
    } catch (IllegalArgumentException ex) {
      throw ex;
    } catch (Exception e) {
      throw new IllegalArgumentException(
          "cursor 디코드 실패: " + new String(token.getBytes(StandardCharsets.UTF_8)), e);
    }
  }

  /** 내부 JSON shape. 짧은 키(t/id)로 토큰 길이 절감. */
  private record Payload(@JsonProperty("t") String t, @JsonProperty("id") long id) {}
}
