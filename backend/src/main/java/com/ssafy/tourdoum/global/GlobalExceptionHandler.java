package com.ssafy.tourdoum.global;

import com.ssafy.tourdoum.accommodation.AccommodationNotFoundException;
import com.ssafy.tourdoum.attraction.AttractionNotFoundException;
import com.ssafy.tourdoum.auth.InvalidPasswordException;
import com.ssafy.tourdoum.auth.InvalidResetTokenException;
import com.ssafy.tourdoum.auth.LoginLockedException;
import com.ssafy.tourdoum.auth.RefreshTokenException;
import com.ssafy.tourdoum.auth.TooManyLoginAttemptsException;
import com.ssafy.tourdoum.chat.ChatChannelNotFoundException;
import com.ssafy.tourdoum.chat.ChatForbiddenException;
import com.ssafy.tourdoum.member.DuplicateEmailException;
import com.ssafy.tourdoum.member.DuplicateNicknameException;
import com.ssafy.tourdoum.notification.NotificationForbiddenException;
import com.ssafy.tourdoum.notification.NotificationNotFoundException;
import com.ssafy.tourdoum.plan.PlanForbiddenException;
import com.ssafy.tourdoum.plan.PlanNotFoundException;
import com.ssafy.tourdoum.reservation.ReservationForbiddenException;
import com.ssafy.tourdoum.reservation.ReservationNotFoundException;
import com.ssafy.tourdoum.review.ReviewForbiddenException;
import com.ssafy.tourdoum.review.ReviewNotFoundException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 전역 예외 핸들러. 검증 오류 → 400 + [{field, message}] 배열. */
@RestControllerAdvice
public class GlobalExceptionHandler {

  /** Bean Validation 오류 → 400. */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public List<ErrorResponse> handleValidationException(MethodArgumentNotValidException ex) {
    return ex.getBindingResult().getFieldErrors().stream()
        .map(FieldError::getField)
        .distinct()
        .map(
            field -> {
              String message =
                  ex.getBindingResult().getFieldErrors(field).stream()
                      .map(FieldError::getDefaultMessage)
                      .findFirst()
                      .orElse("잘못된 값입니다.");
              return new ErrorResponse(field, message);
            })
        .toList();
  }

  /** 이메일 중복 → 409 Conflict. */
  @ExceptionHandler(DuplicateEmailException.class)
  @ResponseStatus(HttpStatus.CONFLICT)
  public ErrorResponse handleDuplicateEmail(DuplicateEmailException ex) {
    return new ErrorResponse("email", ex.getMessage());
  }

  /** 닉네임 중복 → 409 Conflict. */
  @ExceptionHandler(DuplicateNicknameException.class)
  @ResponseStatus(HttpStatus.CONFLICT)
  public ErrorResponse handleDuplicateNickname(DuplicateNicknameException ex) {
    return new ErrorResponse("nickname", ex.getMessage());
  }

  /** 여행지 미존재 → 404 Not Found. */
  @ExceptionHandler(AttractionNotFoundException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public ErrorResponse handleAttractionNotFound(AttractionNotFoundException ex) {
    return new ErrorResponse("id", ex.getMessage());
  }

  /** 숙박 미존재 → 404 Not Found. */
  @ExceptionHandler(AccommodationNotFoundException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public ErrorResponse handleAccommodationNotFound(AccommodationNotFoundException ex) {
    return new ErrorResponse("id", ex.getMessage());
  }

  /** 후기 미존재 → 404 Not Found. */
  @ExceptionHandler(ReviewNotFoundException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public ErrorResponse handleReviewNotFound(ReviewNotFoundException ex) {
    return new ErrorResponse("id", ex.getMessage());
  }

  /** 후기 삭제 권한 없음 → 403 Forbidden. */
  @ExceptionHandler(ReviewForbiddenException.class)
  @ResponseStatus(HttpStatus.FORBIDDEN)
  public ErrorResponse handleReviewForbidden(ReviewForbiddenException ex) {
    return new ErrorResponse("id", ex.getMessage());
  }

  /** 예약 미존재 → 404 Not Found. */
  @ExceptionHandler(ReservationNotFoundException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public ErrorResponse handleReservationNotFound(ReservationNotFoundException ex) {
    return new ErrorResponse("id", ex.getMessage());
  }

  /** 예약 취소 권한 없음 → 403 Forbidden. */
  @ExceptionHandler(ReservationForbiddenException.class)
  @ResponseStatus(HttpStatus.FORBIDDEN)
  public ErrorResponse handleReservationForbidden(ReservationForbiddenException ex) {
    return new ErrorResponse("id", ex.getMessage());
  }

  /** 여행 계획 미존재 → 404 Not Found. */
  @ExceptionHandler(PlanNotFoundException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public ErrorResponse handlePlanNotFound(PlanNotFoundException ex) {
    return new ErrorResponse("id", ex.getMessage());
  }

  /** 여행 계획 접근 권한 없음 → 403 Forbidden. */
  @ExceptionHandler(PlanForbiddenException.class)
  @ResponseStatus(HttpStatus.FORBIDDEN)
  public ErrorResponse handlePlanForbidden(PlanForbiddenException ex) {
    return new ErrorResponse("id", ex.getMessage());
  }

  /** 알림 미존재 → 404 Not Found. */
  @ExceptionHandler(NotificationNotFoundException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public ErrorResponse handleNotificationNotFound(NotificationNotFoundException ex) {
    return new ErrorResponse("id", ex.getMessage());
  }

  /** 알림 읽음 처리 권한 없음 → 403 Forbidden. */
  @ExceptionHandler(NotificationForbiddenException.class)
  @ResponseStatus(HttpStatus.FORBIDDEN)
  public ErrorResponse handleNotificationForbidden(NotificationForbiddenException ex) {
    return new ErrorResponse("id", ex.getMessage());
  }

  /** 채팅 채널 미존재 → 404 Not Found. */
  @ExceptionHandler(ChatChannelNotFoundException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public ErrorResponse handleChatChannelNotFound(ChatChannelNotFoundException ex) {
    return new ErrorResponse("id", ex.getMessage());
  }

  /** 채팅 채널 접근 권한 없음 → 403 Forbidden. */
  @ExceptionHandler(ChatForbiddenException.class)
  @ResponseStatus(HttpStatus.FORBIDDEN)
  public ErrorResponse handleChatForbidden(ChatForbiddenException ex) {
    return new ErrorResponse("id", ex.getMessage());
  }

  /** 날짜/파라미터 오류 → 400 Bad Request. */
  @ExceptionHandler(IllegalArgumentException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ErrorResponse handleIllegalArgument(IllegalArgumentException ex) {
    return new ErrorResponse("request", ex.getMessage());
  }

  /** 로그인 실패 → 401 Unauthorized (ADR-0011 BE-1). */
  @ExceptionHandler(BadCredentialsException.class)
  @ResponseStatus(HttpStatus.UNAUTHORIZED)
  public ErrorResponse handleBadCredentials(BadCredentialsException ex) {
    return new ErrorResponse("credentials", ex.getMessage());
  }

  /** Refresh token 검증 실패 / replay 감지 → 401 (ADR-0011 BE-2, #63). */
  @ExceptionHandler(RefreshTokenException.class)
  @ResponseStatus(HttpStatus.UNAUTHORIZED)
  public ErrorResponse handleRefreshTokenException(RefreshTokenException ex) {
    return new ErrorResponse("refreshToken", ex.getMessage());
  }

  /** 비밀번호 정책 위반 → 400 (ADR-0011 BE-4.2). */
  @ExceptionHandler(InvalidPasswordException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ErrorResponse handleInvalidPassword(InvalidPasswordException ex) {
    return new ErrorResponse("password", ex.getMessage());
  }

  /** Reset 토큰 무효 → 400 (ADR-0011 BE-4.5). */
  @ExceptionHandler(InvalidResetTokenException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ErrorResponse handleInvalidResetToken(InvalidResetTokenException ex) {
    return new ErrorResponse("token", ex.getMessage());
  }

  /** 계정 잠금 → 423 Locked (ADR-0011 BE-4.4). */
  @ExceptionHandler(LoginLockedException.class)
  @ResponseStatus(HttpStatus.LOCKED)
  public ErrorResponse handleLoginLocked(LoginLockedException ex) {
    return new ErrorResponse("account", ex.getMessage());
  }

  /** per-IP throttle 초과 → 429 Too Many Requests (ADR-0011 BE-4.4). */
  @ExceptionHandler(TooManyLoginAttemptsException.class)
  @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
  public ErrorResponse handleTooMany(TooManyLoginAttemptsException ex) {
    return new ErrorResponse("ip", ex.getMessage());
  }
}
