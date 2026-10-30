package com.mugloar.game;

import static com.mugloar.testsupport.Fixtures.aGameState;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GameSessionRepositoryTest {

  private final GameSessionRepository repository = new GameSessionRepository(2);

  private static GameSession session(String id) {
    return new GameSession(aGameState().gameId(id).build());
  }

  @Test
  void findsWhatWasSaved() {
    GameSession one = session("one");

    repository.save("one", one);

    assertThat(repository.find("one")).containsSame(one);
    assertThat(repository.find("other")).isEmpty();
  }

  @Test
  void evictsTheOldestGameBeyondTheLimit() {
    repository.save("one", session("one"));
    repository.save("two", session("two"));
    repository.save("three", session("three"));

    assertThat(repository.size()).isEqualTo(2);
    assertThat(repository.find("one")).isEmpty();
    assertThat(repository.find("two")).isPresent();
    assertThat(repository.find("three")).isPresent();
  }

  @Test
  void readingAGameKeepsItFromBeingEvicted() {
    repository.save("one", session("one"));
    repository.save("two", session("two"));
    repository.find("one");

    repository.save("three", session("three"));

    assertThat(repository.find("one")).isPresent();
    assertThat(repository.find("two")).isEmpty();
  }
}
