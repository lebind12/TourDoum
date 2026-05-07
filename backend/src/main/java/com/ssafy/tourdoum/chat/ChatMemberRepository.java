package com.ssafy.tourdoum.chat;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** 채팅 채널 구성원 JPA 레포지토리. */
public interface ChatMemberRepository extends JpaRepository<ChatMember, ChatMemberId> {

  /** 채널 구성원 존재 여부. */
  boolean existsByChannelIdAndMemberId(Long channelId, Long memberId);

  /** 채널 구성원 조회. */
  Optional<ChatMember> findByChannelIdAndMemberId(Long channelId, Long memberId);
}
