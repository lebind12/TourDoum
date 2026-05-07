package com.ssafy.tourdoum.common.algorithm;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** KmpMatcher 단위 테스트. */
class KmpMatcherTest {

  @Test
  @DisplayName("text가 비어 있고 pattern이 있으면 매치되지 않는다")
  void emptyText() {
    assertThat(KmpMatcher.indexOf("", "a")).isEqualTo(-1);
    assertThat(KmpMatcher.contains("", "a")).isFalse();
  }

  @Test
  @DisplayName("pattern이 비어 있으면 0번 인덱스에서 매치된다")
  void emptyPattern() {
    assertThat(KmpMatcher.indexOf("abc", "")).isZero();
    assertThat(KmpMatcher.contains("abc", "")).isTrue();
  }

  @Test
  @DisplayName("text와 pattern이 모두 비어 있으면 0번 인덱스에서 매치된다")
  void emptyTextAndPattern() {
    assertThat(KmpMatcher.indexOf("", "")).isZero();
    assertThat(KmpMatcher.contains("", "")).isTrue();
  }

  @Test
  @DisplayName("pattern이 text보다 길면 매치되지 않는다")
  void patternLongerThanText() {
    assertThat(KmpMatcher.indexOf("ab", "abc")).isEqualTo(-1);
    assertThat(KmpMatcher.contains("ab", "abc")).isFalse();
  }

  @Test
  @DisplayName("단일 문자를 찾을 수 있다")
  void singleCharacterMatch() {
    assertThat(KmpMatcher.indexOf("abc", "b")).isEqualTo(1);
    assertThat(KmpMatcher.contains("abc", "b")).isTrue();
  }

  @Test
  @DisplayName("중복 문자 패턴을 LPS 테이블로 건너뛰며 찾는다")
  void repeatedPattern() {
    assertThat(KmpMatcher.buildLps("abab")).containsExactly(0, 0, 1, 2);
    assertThat(KmpMatcher.indexOf("ababab", "abab")).isZero();
    assertThat(KmpMatcher.contains("ababab", "abab")).isTrue();
  }

  @Test
  @DisplayName("한글 substring을 찾을 수 있다")
  void hangulMatch() {
    assertThat(KmpMatcher.indexOf("경복궁 야경", "경복")).isZero();
    assertThat(KmpMatcher.contains("경복궁 야경", "경복")).isTrue();
  }

  @Test
  @DisplayName("매치가 없으면 -1을 반환한다")
  void noMatch() {
    assertThat(KmpMatcher.indexOf("경복궁", "창덕")).isEqualTo(-1);
    assertThat(KmpMatcher.contains("경복궁", "창덕")).isFalse();
  }

  @Test
  @DisplayName("text 끝에서 매치되는 pattern을 찾는다")
  void matchAtEnd() {
    assertThat(KmpMatcher.indexOf("서울 경복궁", "궁")).isEqualTo(5);
    assertThat(KmpMatcher.contains("서울 경복궁", "궁")).isTrue();
  }

  @Test
  @DisplayName("text 시작에서 매치되는 pattern을 찾는다")
  void matchAtStart() {
    assertThat(KmpMatcher.indexOf("경복궁", "경")).isZero();
    assertThat(KmpMatcher.contains("경복궁", "경")).isTrue();
  }
}
