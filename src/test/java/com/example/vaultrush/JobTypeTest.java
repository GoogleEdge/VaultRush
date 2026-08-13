package com.example.vaultrush;

import com.example.vaultrush.model.JobType;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class JobTypeTest {
    @Test
    void orderAndIdsRemainStable() {
        assertEquals(List.of("assault", "scout", "guardian", "engineer", "illusionist"),
                Arrays.stream(JobType.values()).map(JobType::id).toList());
        assertEquals(JobType.ASSAULT, JobType.fromIndex(0));
        assertEquals(JobType.ILLUSIONIST, JobType.fromIndex(4));
        assertEquals(JobType.GUARDIAN, JobType.fromId("GUARDIAN"));
    }

    @Test
    void invalidIdsAndIndexesAreRejected() {
        assertNull(JobType.fromId(null));
        assertNull(JobType.fromId("mage"));
        assertNull(JobType.fromIndex(-1));
        assertNull(JobType.fromIndex(5));
    }
}
