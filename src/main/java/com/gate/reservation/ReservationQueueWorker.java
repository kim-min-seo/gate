package com.gate.reservation;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ReservationQueueWorker {
    private final StringRedisTemplate redis;
    public ReservationQueueWorker(StringRedisTemplate redis) { this.redis = redis; }

    @Scheduled(fixedDelay = 5000)
    public void cleanQueues() {
        for (long slotId = 1; slotId <= 3; slotId++) {
            String key = "gate:queue:slot:" + slotId;
            var users = redis.opsForList().range(key, 0, -1);
            if (users == null || users.isEmpty()) continue;
            java.util.HashSet<String> seen = new java.util.HashSet<>();
            for (String user : users) if (!seen.add(user)) redis.opsForList().remove(key, 1, user);
        }
    }
}
