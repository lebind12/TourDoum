package com.ssafy.tourdoum.attraction;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * 페이지네이션 공통 응답 래퍼.
 *
 * @param <T> 컨텐츠 타입
 */
public record PageResponse<T>(
    List<T> content, int page, int size, long totalElements, int totalPages, boolean last) {

  /** Spring Data Page 객체에서 변환. */
  public static <T> PageResponse<T> from(Page<T> page) {
    return new PageResponse<>(
        page.getContent(),
        page.getNumber(),
        page.getSize(),
        page.getTotalElements(),
        page.getTotalPages(),
        page.isLast());
  }
}
