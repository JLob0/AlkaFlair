package com.alkacode.flair.service;

import com.alkacode.economy.EconomyManager;

import java.util.UUID;

/** Fina camada sobre o EconomyManager da AlkaEconomy (R4, hard depend) - compra de tag e config-driven por currencyId, nunca uma moeda fixa. */
public final class FlairEconomyService {

    private final EconomyManager economyManager;

    public FlairEconomyService(EconomyManager economyManager) {
        this.economyManager = economyManager;
    }

    public boolean isValidCurrency(String currencyId) {
        return currencyId != null && economyManager.isValidCurrency(currencyId);
    }

    public boolean has(UUID uuid, String currencyId, double amount) {
        return economyManager.has(uuid, currencyId, amount);
    }

    public void withdraw(UUID uuid, String currencyId, double amount) {
        economyManager.removeBalance(uuid, currencyId, amount);
    }

    public String formatAmount(double amount) {
        return EconomyManager.formatValue(amount);
    }
}
