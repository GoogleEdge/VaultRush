package com.example.vaultrush;

import com.example.vaultrush.arena.Team;
import com.example.vaultrush.util.TeamAllocator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TeamAllocatorTest {
    @Test
    void alternatesTeamsAndKeepsTheDifferenceAtMostOne() {
        List<UUID> players = List.of(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()
        );
        var assignment = TeamAllocator.alternate(players);

        assertEquals(3, assignment.get(Team.RED).size());
        assertEquals(2, assignment.get(Team.BLUE).size());
        assertTrue(assignment.get(Team.RED).stream().noneMatch(assignment.get(Team.BLUE)::contains));
    }
}
