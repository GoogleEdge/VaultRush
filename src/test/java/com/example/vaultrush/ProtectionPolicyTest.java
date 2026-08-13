package com.example.vaultrush;

import com.example.vaultrush.arena.GameState;
import com.example.vaultrush.game.ProtectionPolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProtectionPolicyTest {
    @Test
    void unrelatedWorldsRemainUnprotected() {
        assertEquals(ProtectionPolicy.Decision.UNPROTECTED,
                ProtectionPolicy.decide(true, false,
                        GameState.WAITING, false));
        assertEquals(ProtectionPolicy.Decision.UNPROTECTED,
                ProtectionPolicy.decide(false, true,
                        GameState.RUNNING, true));
    }

    @Test
    void mapIsLockedOutsideRunningState() {
        for (GameState state : new GameState[]{GameState.WAITING,
                GameState.COUNTDOWN, GameState.ENDING}) {
            assertEquals(ProtectionPolicy.Decision.MATCH_NOT_RUNNING,
                    ProtectionPolicy.decide(true, true, state, true));
        }
    }

    @Test
    void onlyRunningParticipantsMayModifyBlocks() {
        assertEquals(ProtectionPolicy.Decision.ALLOWED,
                ProtectionPolicy.decide(true, true,
                        GameState.RUNNING, true));
        assertEquals(ProtectionPolicy.Decision.NOT_PARTICIPANT,
                ProtectionPolicy.decide(true, true,
                        GameState.RUNNING, false));
    }
}
