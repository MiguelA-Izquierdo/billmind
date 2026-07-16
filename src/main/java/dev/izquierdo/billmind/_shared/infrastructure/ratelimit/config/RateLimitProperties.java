package dev.izquierdo.billmind._shared.infrastructure.ratelimit.config;

import dev.izquierdo.billmind._shared.infrastructure.ratelimit.policy.KeyType;
import dev.izquierdo.billmind._shared.infrastructure.ratelimit.policy.RateLimitPolicy;
import dev.izquierdo.billmind._shared.infrastructure.ratelimit.policy.RateLimitProfile;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Binds the tunable per-profile token-bucket numbers from {@code billmind.ratelimit.profiles.*} and
 * turns a {@link RateLimitProfile} into a numeric {@link RateLimitPolicy}. Fails fast at startup if
 * any limited profile is left unconfigured, so a misconfiguration surfaces on boot rather than as a
 * 500 on the first request.
 *
 * <p>A profile's layers do not have to share a bucket. {@code overrides.<key-type>} gives one layer
 * its own numbers, which is what lets {@code UPLOAD}/{@code CHAT} carry a per-session budget sized for
 * a real visitor <em>and</em> a much wider per-IP ceiling that only bites at volumes no human reaches:
 *
 * <pre>{@code
 * billmind.ratelimit.profiles.upload.capacity=5              # the SESSION layer
 * billmind.ratelimit.profiles.upload.overrides.ip.capacity=10  # the IP ceiling
 * }</pre>
 *
 * <p>An override is a complete bucket, not a patch: it must declare every number, so what a layer
 * enforces is readable in one place instead of being half-inherited.
 */
@Component
@ConfigurationProperties(prefix = "billmind.ratelimit")
public class RateLimitProperties {

    private Map<RateLimitProfile, Limit> profiles = new EnumMap<>(RateLimitProfile.class);

    public Map<RateLimitProfile, Limit> getProfiles() {
        return profiles;
    }

    public void setProfiles(Map<RateLimitProfile, Limit> profiles) {
        this.profiles = profiles;
    }

    /** The profile's base bucket — what every layer without an override enforces. */
    public RateLimitPolicy policyFor(RateLimitProfile profile) {
        return toPolicy(profile, limitOf(profile));
    }

    /** The bucket the given layer enforces: its override when it has one, the base bucket otherwise. */
    public RateLimitPolicy policyFor(RateLimitProfile profile, KeyType keyType) {
        Limit base = limitOf(profile);
        return toPolicy(profile, base.getOverrides().getOrDefault(keyType, base));
    }

    private Limit limitOf(RateLimitProfile profile) {
        Limit limit = profiles.get(profile);
        if (limit == null) {
            throw new IllegalStateException("No rate-limit configuration for profile " + profile);
        }
        return limit;
    }

    private RateLimitPolicy toPolicy(RateLimitProfile profile, Limit limit) {
        return new RateLimitPolicy(profile, limit.capacity, limit.refillTokens, limit.refillPeriod, limit.cost);
    }

    @PostConstruct
    void validate() {
        for (RateLimitProfile profile : RateLimitProfile.values()) {
            if (!profile.limited()) {
                continue;
            }
            if (!profiles.containsKey(profile)) {
                throw new IllegalStateException("Missing rate-limit configuration for profile " + profile);
            }
            validateBuckets(profile);
        }
    }

    /** Builds every bucket the profile can enforce, so bad numbers fail the boot, not the request. */
    private void validateBuckets(RateLimitProfile profile) {
        buildOrFail(profile, limitOf(profile), "base bucket");
        limitOf(profile).getOverrides().forEach((keyType, override) -> {
            if (!profile.keyTypes().contains(keyType)) {
                throw new IllegalStateException("Profile " + profile + " has no " + keyType
                        + " layer, so its override would never be enforced");
            }
            buildOrFail(profile, override, keyType + " override");
        });
    }

    private void buildOrFail(RateLimitProfile profile, Limit limit, String what) {
        try {
            toPolicy(profile, limit);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "Invalid rate-limit " + what + " for profile " + profile + ": " + e.getMessage(), e);
        }
    }

    /** Mutable holder for JavaBean binding of one bucket. */
    public static class Limit {
        private long capacity;
        private long refillTokens;
        private Duration refillPeriod;
        private long cost = 1;
        private Map<KeyType, Limit> overrides = new LinkedHashMap<>();

        public long getCapacity() {
            return capacity;
        }

        public void setCapacity(long capacity) {
            this.capacity = capacity;
        }

        public long getRefillTokens() {
            return refillTokens;
        }

        public void setRefillTokens(long refillTokens) {
            this.refillTokens = refillTokens;
        }

        public Duration getRefillPeriod() {
            return refillPeriod;
        }

        public void setRefillPeriod(Duration refillPeriod) {
            this.refillPeriod = refillPeriod;
        }

        public long getCost() {
            return cost;
        }

        public void setCost(long cost) {
            this.cost = cost;
        }

        /** Per-layer buckets. Only meaningful on a profile's base limit — an override has none. */
        public Map<KeyType, Limit> getOverrides() {
            return overrides;
        }

        public void setOverrides(Map<KeyType, Limit> overrides) {
            this.overrides = overrides;
        }
    }
}