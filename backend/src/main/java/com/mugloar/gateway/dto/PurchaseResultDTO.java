package com.mugloar.gateway.dto;

public record PurchaseResultDTO(
    boolean shoppingSuccess, int gold, int lives, int level, int turn) {}
