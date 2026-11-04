package com.mugloar.gateway.dto;

public record PurchaseResultDTO(
    boolean shoppingSuccess, long gold, int lives, int level, int turn) {}
