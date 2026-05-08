package com.ssafy.tourdoum.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssafy.tourdoum.chat.ChatChannel;
import com.ssafy.tourdoum.chat.ChatChannelRepository;
import com.ssafy.tourdoum.chat.ChatChannelType;
import com.ssafy.tourdoum.chat.ChatMemberRepository;
import com.ssafy.tourdoum.member.Member;
import com.ssafy.tourdoum.member.MemberService;
import com.ssafy.tourdoum.member.SignupRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * BE-1.1 dev profile auto-join — 신규 signup 회원이 seed PUBLIC 채널 (`seed-public-1`)에 자동 join 되는지 검증.
 *
 * <p>실행: {@code ./mvnw verify -Dtourdoum.it=true -Dtest=ChatPublicAutoJoinIT}.
 * dev profile 활성: {@code @ActiveProfiles("dev")}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("dev")
@EnabledIfSystemProperty(named = "tourdoum.it", matches = "true")
class ChatPublicAutoJoinIT {

  @SuppressWarnings("resource")
  @Container
  static final MySQLContainer<?> mysql =
      new MySQLContainer<>("mysql:8.4")
          .withDatabaseName("tourdoum")
          .withUsername("tourdoum")
          .withPassword("tourdoum");

  @SuppressWarnings("resource")
  @Container
  static final GenericContainer<?> redis =
      new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);

  @DynamicPropertySource
  static void overrideProperties(DynamicPropertyRegistry registry) {
    registry.add(
        "spring.datasource.url",
        () ->
            "jdbc:mysql://"
                + mysql.getHost()
                + ":"
                + mysql.getMappedPort(3306)
                + "/tourdoum?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true");
    registry.add("spring.datasource.username", mysql::getUsername);
    registry.add("spring.datasource.password", mysql::getPassword);
    registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    registry.add("spring.data.redis.host", redis::getHost);
    registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    registry.add("spring.flyway.enabled", () -> "true");
    registry.add("spring.flyway.locations", () -> "classpath:db/migration");
    registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    registry.add(
        "spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.MySQLDialect");
  }

  @Autowired private MemberService memberService;
  @Autowired private ChatChannelRepository channelRepository;
  @Autowired private ChatMemberRepository chatMemberRepository;

  @Test
  @DisplayName("seed PUBLIC 채널 존재 + dev profile → signup 후 chat_members INSERT")
  void signup_auto_joins_seed_public_channel_in_dev() {
    // given — seed PUBLIC sentinel
    ChatChannel seed = channelRepository.save(
        new ChatChannel("seed-public-1", ChatChannelType.PUBLIC, null, null));

    // when — 신규 signup
    String email = "autojoin-" + System.nanoTime() + "@example.com";
    Member m = memberService.signup(new SignupRequest(email, "password123", "join" + System.nanoTime()));

    // then — chat_members 존재 (AFTER_COMMIT listener)
    boolean joined = chatMemberRepository.existsByChannelIdAndMemberId(seed.getId(), m.getId());
    assertThat(joined).as("dev profile에서 seed PUBLIC 채널에 자동 join").isTrue();
  }

  @Test
  @DisplayName("seed PUBLIC 부재 → silent no-op (signup 자체 성공, 아무 채널에도 join 안 됨)")
  void signup_no_seed_channel_silent_noop() {
    String email = "noseed-" + System.nanoTime() + "@example.com";
    Member m = memberService.signup(new SignupRequest(email, "password123", "noseed" + System.nanoTime()));
    assertThat(m.getId()).isNotNull();
    assertThat(chatMemberRepository.findAll().stream().anyMatch(cm -> cm.getMemberId().equals(m.getId())))
        .as("seed 채널 없으면 어디에도 join 안 됨")
        .isFalse();
  }
}
