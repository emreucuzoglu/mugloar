package com.mugloar.gateway;

import com.mugloar.domain.GameState;
import com.mugloar.domain.Probability;
import com.mugloar.domain.PurchaseResult;
import com.mugloar.domain.Quest;
import com.mugloar.domain.Reputation;
import com.mugloar.domain.ShopItem;
import com.mugloar.domain.SolveResult;
import com.mugloar.gateway.dto.GameStartDTO;
import com.mugloar.gateway.dto.PurchaseResultDTO;
import com.mugloar.gateway.dto.QuestDTO;
import com.mugloar.gateway.dto.ReputationDTO;
import com.mugloar.gateway.dto.ShopItemDTO;
import com.mugloar.gateway.dto.SolveResultDTO;
import java.util.List;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(unmappedTargetPolicy = ReportingPolicy.ERROR)
interface MugloarMapper {

  @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
  @Mapping(target = "stateReputation", constant = "0")
  GameState toGameState(GameStartDTO start);

  ShopItem toShopItem(ShopItemDTO item);

  List<ShopItem> toShopItems(List<ShopItemDTO> items);

  SolveResult toSolveResult(SolveResultDTO result);

  PurchaseResult toPurchaseResult(PurchaseResultDTO result);

  Reputation toReputation(ReputationDTO reputation);

  @Mapping(target = "questId", source = "decodedId")
  @Mapping(target = "rawQuestId", source = "quest.questId")
  @Mapping(target = "message", source = "decodedMessage")
  @Mapping(target = "reward", source = "quest.reward")
  @Mapping(target = "expiresIn", source = "quest.expiresIn")
  @Mapping(target = "probability", source = "decodedProbability")
  @Mapping(target = "encrypted", source = "wasEncrypted")
  Quest toQuest(
      QuestDTO quest,
      String decodedId,
      String decodedMessage,
      String decodedProbability,
      boolean wasEncrypted);

  default Probability toProbability(String label) {
    return Probability.fromLabel(label);
  }
}
