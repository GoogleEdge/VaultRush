package com.example.vaultrush.util;

import com.example.vaultrush.arena.Team;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class TeamAllocator {
    private TeamAllocator() {
    }

    public static Map<Team, List<UUID>> alternate(List<UUID> players) {
        Map<Team, List<UUID>> result = new EnumMap<>(Team.class);
        result.put(Team.RED, new ArrayList<>());
        result.put(Team.BLUE, new ArrayList<>());
        for (int index = 0; index < players.size(); index++) {
            result.get(index % 2 == 0 ? Team.RED : Team.BLUE).add(players.get(index));
        }
        return result;
    }
}
