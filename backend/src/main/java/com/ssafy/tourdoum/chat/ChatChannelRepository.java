package com.ssafy.tourdoum.chat;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 채팅 채널 JPA 레포지토리. */
public interface ChatChannelRepository extends JpaRepository<ChatChannel, Long> {

  /**
   * 회원이 속한 채널 목록.
   *
   * <p>chat_members를 경유해 채널 목록을 조회한다.
   */
  @Query(
      "SELECT c FROM ChatChannel c"
          + " WHERE c.id IN (SELECT cm.channelId FROM ChatMember cm WHERE cm.memberId = :memberId)"
          + " ORDER BY c.createdAt DESC")
  List<ChatChannel> findMyChannels(@Param("memberId") Long memberId);

  /**
   * 두 회원 간 DM 채널 조회.
   *
   * <p>두 회원이 모두 속한 DM 채널을 찾는다 (중복 생성 방지).
   */
  @Query(
      "SELECT c FROM ChatChannel c"
          + " WHERE c.type = 'DM'"
          + "   AND c.id IN (SELECT cm.channelId FROM ChatMember cm WHERE cm.memberId = :memberA)"
          + "   AND c.id IN (SELECT cm.channelId FROM ChatMember cm WHERE cm.memberId = :memberB)")
  Optional<ChatChannel> findDmChannel(
      @Param("memberA") Long memberA, @Param("memberB") Long memberB);
}
