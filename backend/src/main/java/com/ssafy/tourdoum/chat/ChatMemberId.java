package com.ssafy.tourdoum.chat;

import java.io.Serializable;
import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** ChatMember 복합 PK. */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ChatMemberId implements Serializable {

  private Long channelId;
  private Long memberId;

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ChatMemberId that)) return false;
    return Objects.equals(channelId, that.channelId) && Objects.equals(memberId, that.memberId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(channelId, memberId);
  }
}
