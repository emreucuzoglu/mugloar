package com.mugloar.web;

import com.mugloar.game.GameNotFoundException;
import com.mugloar.game.UnavailableException;
import com.mugloar.gateway.GameOverException;
import com.mugloar.gateway.MugloarApiException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
class ApiExceptionHandler {

  @ExceptionHandler(GameNotFoundException.class)
  ProblemDetail notFound(GameNotFoundException e) {
    return problem(HttpStatus.NOT_FOUND, "Game not found", e.getMessage());
  }

  @ExceptionHandler(UnavailableException.class)
  ProblemDetail unavailable(UnavailableException e) {
    return problem(HttpStatus.CONFLICT, "Not available", e.getMessage());
  }

  @ExceptionHandler(GameOverException.class)
  ProblemDetail gameOver(GameOverException e) {
    return problem(HttpStatus.GONE, "Game over", e.getMessage());
  }

  @ExceptionHandler(MugloarApiException.class)
  ProblemDetail upstream(MugloarApiException e) {
    log.warn("Game server call failed: {}", e.getMessage());
    if (e.isRateLimited()) {
      return problem(
          HttpStatus.SERVICE_UNAVAILABLE,
          "Game server busy",
          "The game server is busy. Wait a minute and try again.");
    }
    String detail =
        e.status() == MugloarApiException.NO_RESPONSE
            ? "The game server could not be reached. Try again in a moment."
            : "The game server rejected the request (HTTP " + e.status() + ").";
    return problem(HttpStatus.BAD_GATEWAY, "Game server error", detail);
  }

  @ExceptionHandler({HandlerMethodValidationException.class, ConstraintViolationException.class})
  ProblemDetail invalid(Exception e) {
    return problem(
        HttpStatus.BAD_REQUEST, "Invalid request", "One or more ids have an invalid format.");
  }

  private static ProblemDetail problem(HttpStatus status, String title, String detail) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setTitle(title);
    return problem;
  }
}
