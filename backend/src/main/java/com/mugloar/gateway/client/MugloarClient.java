package com.mugloar.gateway.client;

import com.mugloar.gateway.GameOverException;
import com.mugloar.gateway.MugloarApiException;
import com.mugloar.gateway.dto.GameStartDTO;
import com.mugloar.gateway.dto.PurchaseResultDTO;
import com.mugloar.gateway.dto.QuestDTO;
import com.mugloar.gateway.dto.ReputationDTO;
import com.mugloar.gateway.dto.ShopItemDTO;
import com.mugloar.gateway.dto.SolveResultDTO;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@RequiredArgsConstructor
public class MugloarClient {

  private static final int MAX_BODY_IN_MESSAGE = 300;

  private final RestClient rest;
  private final RetryTemplate readRetry;
  private final RequestPacer pacer;

  public GameStartDTO startGame() {
    return call(null, () -> rest.post().uri("/game/start").retrieve().body(GameStartDTO.class));
  }

  public List<QuestDTO> getMessages(String gameId) {
    return readRetry.invoke(() ->
        call(
            gameId,
            () ->
                rest.get()
                    .uri("/{gameId}/messages", gameId)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {})));
  }

  public SolveResultDTO solve(String gameId, String questId) {
    return call(
        gameId,
        () ->
            rest.post()
                .uri("/{gameId}/solve/{adId}", gameId, questId)
                .retrieve()
                .body(SolveResultDTO.class));
  }

  public List<ShopItemDTO> getShop(String gameId) {
    return readRetry.invoke(() ->
        call(
            gameId,
            () ->
                rest.get()
                    .uri("/{gameId}/shop", gameId)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {})));
  }

  public PurchaseResultDTO buy(String gameId, String itemId) {
    return call(
        gameId,
        () ->
            rest.post()
                .uri("/{gameId}/shop/buy/{itemId}", gameId, itemId)
                .retrieve()
                .body(PurchaseResultDTO.class));
  }

  public ReputationDTO investigateReputation(String gameId) {
    return call(
        gameId,
        () ->
            rest.post()
                .uri("/{gameId}/investigate/reputation", gameId)
                .retrieve()
                .body(ReputationDTO.class));
  }

  private <T> T call(String gameId, Supplier<T> request) {
    for (int waits = 0; ; waits++) {
      pacer.pace();
      try {
        return send(gameId, request);
      } catch (HttpClientErrorException.TooManyRequests e) {
        String retryAfter = e.getResponseHeaders().getFirst(HttpHeaders.RETRY_AFTER);
        if (!pacer.waitForRateLimit(waits, retryAfter)) {
          throw answered(e);
        }
      }
    }
  }

  private <T> T send(String gameId, Supplier<T> request) {
    T body;
    try {
      body = request.get();
    } catch (HttpClientErrorException.TooManyRequests e) {
      throw e;
    } catch (HttpStatusCodeException e) {
      if (e.getStatusCode().value() == GameOverException.GONE) {
        throw new GameOverException(gameId);
      }
      throw answered(e);
    } catch (ResourceAccessException e) {
      throw new MugloarApiException(
          MugloarApiException.NO_RESPONSE, "Game server unreachable: " + e.getMessage(), e);
    } catch (RestClientException e) {
      throw new MugloarApiException(
          MugloarApiException.NO_RESPONSE,
          "Unexpected response from game server: " + e.getMessage(),
          e);
    }
    if (body == null) {
      throw new MugloarApiException(
          MugloarApiException.NO_RESPONSE, "Game server returned an empty body");
    }
    return body;
  }

  private static MugloarApiException answered(HttpStatusCodeException e) {
    int status = e.getStatusCode().value();
    return new MugloarApiException(
        status,
        "Game server answered "
            + status
            + ": "
            + summarize(e.getResponseBodyAsString(StandardCharsets.UTF_8)),
        e);
  }

  private static String summarize(String body) {
    return StringUtils.abbreviate(
        StringUtils.defaultIfBlank(StringUtils.strip(body), "(empty body)"),
        "…",
        MAX_BODY_IN_MESSAGE);
  }
}
