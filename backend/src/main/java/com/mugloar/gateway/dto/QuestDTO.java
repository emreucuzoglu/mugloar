package com.mugloar.gateway.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record QuestDTO(
    @JsonProperty("adId") String questId,
    String message,
    int reward,
    int expiresIn,
    Integer encrypted,
    String probability) {}
