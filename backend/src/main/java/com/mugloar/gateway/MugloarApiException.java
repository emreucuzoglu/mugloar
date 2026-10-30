package com.mugloar.gateway;

import com.mugloar.util.LogSanitizer;
import lombok.Getter;
import org.springframework.http.HttpStatus;

public class MugloarApiException extends RuntimeException {

  public static final int NO_RESPONSE = 0;

  @Getter private final int status;

  public MugloarApiException(int status, String message) {
    super(LogSanitizer.clean(message));
    this.status = status;
  }

  public MugloarApiException(int status, String message, Throwable cause) {
    super(LogSanitizer.clean(message), cause);
    this.status = status;
  }

  public boolean isRefusal() {
    return HttpStatus.Series.resolve(status) == HttpStatus.Series.CLIENT_ERROR
        && status != HttpStatus.GONE.value()
        && !isRateLimited();
  }

  public boolean isRateLimited() {
    return status == HttpStatus.TOO_MANY_REQUESTS.value();
  }

  public boolean isTransient() {
    return status == NO_RESPONSE
        || HttpStatus.Series.resolve(status) == HttpStatus.Series.SERVER_ERROR;
  }
}
