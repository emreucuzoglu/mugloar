package com.mugloar.gateway;

import com.mugloar.domain.GameState;
import com.mugloar.domain.PurchaseResult;
import com.mugloar.domain.Quest;
import com.mugloar.domain.Reputation;
import com.mugloar.domain.ShopItem;
import com.mugloar.domain.SolveResult;
import com.mugloar.gateway.client.MugloarClient;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.mapstruct.factory.Mappers;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MugloarGateway {

  private final MugloarClient client;
  private final MugloarMapper mapper = Mappers.getMapper(MugloarMapper.class);
  private final QuestDecoder decoder = new QuestDecoder(mapper);

  public GameState startGame() {
    GameState started = mapper.toGameState(client.startGame());
    if (StringUtils.isBlank(started.gameId())) {
      throw new MugloarApiException(
          MugloarApiException.NO_RESPONSE, "Game server started a game without an id");
    }
    return started;
  }

  public List<Quest> getBoard(String gameId) {
    return decoder.decodeAll(client.getMessages(gameId));
  }

  public SolveResult solve(String gameId, String questId) {
    return mapper.toSolveResult(client.solve(gameId, questId));
  }

  public List<ShopItem> getShop(String gameId) {
    return mapper.toShopItems(client.getShop(gameId));
  }

  public PurchaseResult buy(String gameId, String itemId) {
    return mapper.toPurchaseResult(client.buy(gameId, itemId));
  }

  public Reputation investigateReputation(String gameId) {
    return mapper.toReputation(client.investigateReputation(gameId));
  }
}
