package com.mugloar.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class QuestTypeTest {

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "Help Abdul-hamid Rickard to clean their dog | CLEAN",
        "Help Fyodor Witherspoon to fix their bucket | FIX",
        "Help Inès Siddall to reach an agreement with Voula Everett on the matters of disputed land | NEGOTIATE",
        "Help Kory Thrussell to transport a magic cat to peninsula in Towerfirth | TRANSPORT",
        "Help Qiu Ecclestone to sell an unordinary squirrel on the local market | SELL",
        "Help Theodoar Turnbull to write their biographical novel about their difficulties with a cat | WRITE",
        "Create an advertisement campaign for Desdemona Hlöðversson to promote their water based business | ADVERTISE",
        "Escort Chiumbo Pickering to pasture in Ferncaster where they can meet with their long lost sheep | ESCORT",
        "Rescue Boaz Rogerson from village in Evercawl where they are attacked by angry wagon | RESCUE",
        "Investigate Cybele Lindsay and find out their relation to the magic house. | INVESTIGATE",
        "Infiltrate The Eagle Soldiers and recover their secrets. | INFILTRATE",
        "Kill Nestan-darejan Sampson with pot and make Terell Attwater from mountains in Darkshire watch | KILL",
        "Help defending peninsula in Lightdeen from the intruders | DEFEND",
        "Steal water delivery to Dierk Hightower and share some of the profits with the people. | STEAL_DELIVERY",
        "Steal chicken from Dierk Hightower | STEAL_DELIVERY",
        "Steal super awesome diamond beer mug from Ketzi'ah Huddleson | STEAL_DIAMOND",
        "Something entirely new | UNKNOWN"
      })
  void recognisesRealMessages(String message, QuestType expected) {
    assertThat(QuestType.classify(message)).isEqualTo(expected);
  }

  @Test
  void noMessageIsUnknown() {
    assertThat(QuestType.classify(null)).isEqualTo(QuestType.UNKNOWN);
  }

  @Test
  void onlyTheDiamondTheftIsATrap() {
    assertThat(QuestType.values())
        .filteredOn(QuestType::isTrap)
        .containsExactly(QuestType.STEAL_DIAMOND);
  }

  @Test
  void catalogueValuesAreConsistent() {
    assertThat(QuestType.values())
        .allSatisfy(type -> {
          assertThat(type.firstTurn()).isLessThanOrEqualTo(type.lastTurn());
          assertThat(type.rewardMin()).isLessThanOrEqualTo(type.rewardMedian());
          assertThat(type.rewardMedian()).isLessThanOrEqualTo(type.rewardMax());
        });
  }

  @Test
  void stealingCostsStateReputation() {
    assertThat(QuestType.STEAL_DELIVERY.reputationChange().state()).isEqualTo(-2);
    assertThat(QuestType.STEAL_DELIVERY.reputationChange().people()).isEqualTo(1);
  }

  @Test
  void killingRaisesUnderworldReputation() {
    assertThat(QuestType.KILL.reputationChange().underworld()).isEqualTo(2);
    assertThat(QuestType.KILL.reputationChange().state()).isEqualTo(-1);
  }
}
