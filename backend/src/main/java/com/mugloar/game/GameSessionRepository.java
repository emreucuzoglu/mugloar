package com.mugloar.game;

import com.mugloar.config.MugloarProperties;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

@Repository
class GameSessionRepository {

  private static final int INITIAL_CAPACITY = 16;
  private static final float LOAD_FACTOR = 0.75f;
  private static final boolean ACCESS_ORDER = true;

  private final Map<String, GameSession> sessions;

  @Autowired
  GameSessionRepository(MugloarProperties props) {
    this(props.maxSessions());
  }

  GameSessionRepository(int max) {
    this.sessions =
        new LinkedHashMap<>(INITIAL_CAPACITY, LOAD_FACTOR, ACCESS_ORDER) {
          @Override
          protected boolean removeEldestEntry(Map.Entry<String, GameSession> eldest) {
            return size() > max;
          }
        };
  }

  synchronized void save(String gameId, GameSession session) {
    sessions.put(gameId, session);
  }

  synchronized Optional<GameSession> find(String gameId) {
    return Optional.ofNullable(sessions.get(gameId));
  }

  synchronized int size() {
    return sessions.size();
  }
}
