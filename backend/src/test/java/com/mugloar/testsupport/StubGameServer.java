package com.mugloar.testsupport;

import com.mugloar.gateway.MugloarApiException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tools.jackson.databind.json.JsonMapper;

/**
 * A real HTTP server on a free local port, standing in for the game. It answers either from a
 * script of faults or from a {@link SimulatedMugloarClient}, so tests cross real sockets and JSON.
 */
public final class StubGameServer implements AutoCloseable {

  public static final int ANY_PORT = 0;
  public static final int NO_BACKLOG_LIMIT = 0;
  public static final int OK = 200;
  public static final int NOT_FOUND = 404;
  public static final int GONE = 410;
  public static final int STOP_AT_ONCE = 0;
  public static final long NO_BODY = -1;

  private static final Pattern MOVE = Pattern.compile("^/([^/]+)/solve/([^/]+)$");
  private static final Pattern BUY = Pattern.compile("^/([^/]+)/shop/buy/([^/]+)$");
  private static final Pattern BOARD = Pattern.compile("^/([^/]+)/messages$");
  private static final Pattern SHOP = Pattern.compile("^/([^/]+)/shop$");
  private static final Pattern REPUTATION = Pattern.compile("^/([^/]+)/investigate/reputation$");

  @FunctionalInterface
  public interface Script {
    void answer(HttpExchange exchange) throws IOException;
  }

  private final HttpServer server;
  private final ExecutorService threads = Executors.newVirtualThreadPerTaskExecutor();
  private final List<String> rawPaths = new CopyOnWriteArrayList<>();
  private final List<String> requests = new CopyOnWriteArrayList<>();
  private volatile Script script = StubGameServer::notFound;

  public StubGameServer() {
    try {
      server =
          HttpServer.create(
              new InetSocketAddress(InetAddress.getLoopbackAddress(), ANY_PORT), NO_BACKLOG_LIMIT);
    } catch (IOException e) {
      throw new IllegalStateException("Could not open a local port", e);
    }
    server.setExecutor(threads);
    server.createContext(
        "/",
        exchange -> {
          rawPaths.add(exchange.getRequestURI().getRawPath());
          requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath());
          try (exchange) {
            script.answer(exchange);
          }
        });
    server.start();
  }

  public URI baseUrl() {
    return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
  }

  public List<String> requests() {
    return requests;
  }

  public List<String> rawPaths() {
    return rawPaths;
  }

  public void answerWith(Script newScript) {
    this.script = newScript;
  }

  public void answerFrom(SimulatedMugloarClient sim) {
    this.script = exchange -> simulate(sim, exchange);
  }

  public static Script status(int status, String contentType, String body) {
    return exchange -> send(exchange, status, contentType, body);
  }

  public static Script json(int status, String body) {
    return status(status, "application/json", body);
  }

  public static Script hangUp() {
    return exchange -> exchange.getRequestBody().readAllBytes();
  }

  public static Script stall(long millis) {
    return exchange -> {
      try {
        Thread.sleep(millis);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    };
  }

  private static void simulate(SimulatedMugloarClient sim, HttpExchange exchange)
      throws IOException {
    String path = exchange.getRequestURI().getPath();
    Object answer;
    try {
      answer = route(sim, path);
    } catch (MugloarApiException e) {
      send(exchange, e.status(), "text/plain", String.valueOf(e.getMessage()));
      return;
    }
    if (answer == null) {
      notFound(exchange);
      return;
    }
    send(exchange, OK, "application/json", JsonMapper.shared().writeValueAsString(answer));
  }

  private static Object route(SimulatedMugloarClient sim, String path) {
    if (path.equals("/game/start")) {
      return sim.startGame();
    }
    Matcher move = MOVE.matcher(path);
    if (move.matches()) {
      return sim.solve(move.group(1), move.group(2));
    }
    Matcher buy = BUY.matcher(path);
    if (buy.matches()) {
      return sim.buy(buy.group(1), buy.group(2));
    }
    Matcher board = BOARD.matcher(path);
    if (board.matches()) {
      return sim.getMessages(board.group(1));
    }
    Matcher shop = SHOP.matcher(path);
    if (shop.matches()) {
      return sim.getShop(shop.group(1));
    }
    Matcher reputation = REPUTATION.matcher(path);
    if (reputation.matches()) {
      return sim.investigateReputation(reputation.group(1));
    }
    return null;
  }

  private static void notFound(HttpExchange exchange) throws IOException {
    exchange.sendResponseHeaders(NOT_FOUND, NO_BODY);
  }

  private static void send(HttpExchange exchange, int status, String contentType, String body)
      throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", contentType);
    exchange.sendResponseHeaders(status, bytes.length == 0 ? NO_BODY : bytes.length);
    if (bytes.length > 0) {
      exchange.getResponseBody().write(bytes);
    }
  }

  @Override
  public void close() {
    server.stop(STOP_AT_ONCE);
    threads.shutdownNow();
  }
}
