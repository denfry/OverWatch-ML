package net.denfry.owml.punishments;

import net.kyori.adventure.text.Component;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PunishmentData {
    public int level = 0;
    public final Map<String, Long> expiries = new ConcurrentHashMap<>();
    public final Map<String, Integer> counters = new ConcurrentHashMap<>();
    public final Set<String> flags = ConcurrentHashMap.newKeySet();
    public Component miningReputation = Component.text("Trusted Miner");
    public long reputationExpiry = 0;

    public boolean hasExpired(String key) {
        Long expiry = expiries.get(key);
        return expiry == null || expiry < System.currentTimeMillis();
    }

    public void setExpiry(String key, long durationMs) {
        expiries.put(key, System.currentTimeMillis() + durationMs);
    }
}
