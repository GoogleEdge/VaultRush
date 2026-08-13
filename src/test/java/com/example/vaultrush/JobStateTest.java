package com.example.vaultrush;

import com.example.vaultrush.arena.ArenaDefinition;
import com.example.vaultrush.arena.ArenaMatch;
import com.example.vaultrush.arena.Team;
import com.example.vaultrush.model.JobType;
import com.example.vaultrush.model.PlayerSession;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JobStateTest {
    @Test
    void queueAndJobSelectionStaySynchronized() {
        ArenaMatch match = new ArenaMatch(new ArenaDefinition("test"));
        UUID uuid = UUID.randomUUID();
        assertTrue(match.enqueue(uuid, JobType.SCOUT));
        assertEquals(JobType.SCOUT, match.queuedJob(uuid));
        assertFalse(match.enqueue(uuid, JobType.GUARDIAN));
        assertTrue(match.removeQueued(uuid));
        assertFalse(match.queue().contains(uuid));
        assertNull(match.queuedJob(uuid));
    }

    @Test
    void resetClearsQueuedAndActiveJobState() {
        ArenaMatch match = new ArenaMatch(new ArenaDefinition("test"));
        UUID uuid = UUID.randomUUID();
        match.enqueue(uuid, JobType.ENGINEER);
        match.sessions().put(uuid, new PlayerSession(uuid, Team.RED, null,
                JobType.ENGINEER));
        match.reset();
        assertTrue(match.queue().isEmpty());
        assertTrue(match.queuedJobs().isEmpty());
        assertTrue(match.sessions().isEmpty());
    }

    @Test
    void activeAndPassiveCooldownsAreMatchLocal() {
        PlayerSession session = new PlayerSession(UUID.randomUUID(), Team.BLUE,
                null, JobType.ILLUSIONIST);
        long now = 20_000L;
        session.startJobCooldown(now + 25_000L);
        session.startJobPassiveCooldown(now + 15_000L);
        assertEquals(JobType.ILLUSIONIST, session.job());
        assertEquals(25_000L, session.jobCooldownRemaining(now));
        assertFalse(session.jobPassiveReady(now));
        assertTrue(session.jobPassiveReady(now + 15_000L));
    }
}
