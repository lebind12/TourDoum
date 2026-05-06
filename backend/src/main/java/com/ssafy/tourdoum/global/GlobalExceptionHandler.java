package com.ssafy.tourdoum.global;

import com.ssafy.tourdoum.member.DuplicateEmailException;
import com.ssafy.tourdoum.member.DuplicateNicknameException;
import java.util.List;
import org.springframework.http.HttpStatus;
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
}
