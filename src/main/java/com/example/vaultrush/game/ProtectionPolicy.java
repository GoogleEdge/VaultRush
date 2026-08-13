package com.example.vaultrush.game;

import com.example.vaultrush.arena.GameState;

public final class ProtectionPolicy {
    public enum Decision { UNPROTECTED, ALLOWED, MATCH_NOT_RUNNING, NOT_PARTICIPANT }

    private ProtectionPolicy() {
    }

    public static Decision decide(boolean protectionEnabled,
                                  boolean protectedWorld,
                                  GameState state,
                                  boolean participant) {
        if (!protectionEnabled || !protectedWorld) return Decision.UNPROTECTED;
        if (state != GameState.RUNNING) return Decision.MATCH_NOT_RUNNING;
        return participant ? Decision.ALLOWED : Decision.NOT_PARTICIPANT;
    }
}
