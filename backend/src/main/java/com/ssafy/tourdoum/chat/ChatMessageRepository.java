package com.ssafy.tourdoum.chat;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** 채팅 메시지 JPA 레포지토리. */
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

  /**
   * 채널의 메시지 목록 (sinceId 이후, 최대 50건).
   *
   * <p>FE 폴링 시 마지막으로 받은 메시지 ID 이후 신규 메시지만 가져온다.
   */
  List<ChatMessage> findTop50ByChannelIdAndIdGreaterThanOrderByIdAsc(Long channelId, Long sinceId);

  /**
   * 채널의 최근 메시지 목록 (sinceId 없이 초기 로드).
   *
   * <p>최근 50건을 역순으로 가져온다.
   */
  List<ChatMessage> findTop50ByChannelIdOrderByIdDesc(Long channelId);
}
