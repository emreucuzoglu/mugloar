package com.mugloar.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ServerErrorPageTest {

  @LocalServerPort private int port;

  @Test
  void rejectedRequestDoesNotRevealTheServer() throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/games/a%00b")).build();

    HttpResponse<String> response;
    try (HttpClient client = HttpClient.newHttpClient()) {
      response = client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    assertThat(response.statusCode()).isEqualTo(400);
    assertThat(response.body()).doesNotContainIgnoringCase("tomcat");
  }
}
