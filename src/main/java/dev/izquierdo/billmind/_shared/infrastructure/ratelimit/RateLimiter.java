package dev.izquierdo.billmind._shared.infrastructure.ratelimit;

import dev.izquierdo.billmind._shared.infrastructure.ratelimit.config.RateLimitProperties;
import dev.izquierdo.billmind._shared.infrastructure.ratelimit.key.RateLimitKeyGenerator;
import dev.izquierdo.billmind._shared.infrastructure.ratelimit.model.RateLimitDecision;
import dev.izquierdo.billmind._shared.infrastructure.ratelimit.model.RateLimitVerdict;
import dev.izquierdo.billmind._shared.infrastructure.ratelimit.observability.RateLimitMetrics;
import dev.izquierdo.billmind._shared.infrastructure.ratelimit.policy.FailMode;
import dev.izquierdo.billmind._shared.infrastructure.ratelimit.policy.KeyType;
import dev.izquierdo.billmind._shared.infrastructure.ratelimit.policy.RateLimitPhase;
import dev.izquierdo.billmind._shared.infrastructure.ratelimit.policy.RateLimitPolicy;
import dev.izquierdo.billmind._shared.infrastructure.ratelimit.policy.RateLimitProfile;
import dev.izquierdo.billmind._shared.infrastructure.ratelimit.store.RateLimitStore;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Enforces a profile's applicable layers for a given {@link RateLimitPhase}. A profile may be counted
 * against several identities (e.g. ADMIN by IP and by token); this consults each layer whose key type
 * belongs to the phase, and the first breach wins. On store failure the layer's {@link FailMode}
 * decides: fail-open allows, fail-closed reports unavailable (HTTP 503). Stateless and thread-safe.
 */
@Component
public class RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RateLimiter.class);

    private static final int FINGERPRINT_LENGTH = 12;

    private final Map<KeyType, RateLimitKeyGenerator> generators;
    private final RateLimitStore store;
    private final RateLimitProperties properties;
    private final RateLimitMetrics metrics;

    public RateLimiter(List<RateLimitKeyGenerator> generators, RateLimitStore store,
                       RateLimitProperties properties, RateLimitMetrics metrics) {
        this.generators = generators.stream()
                .collect(Collectors.toMap(RateLimitKeyGenerator::type, Function.identity()));
        this.store = store;
        this.properties = properties;
        this.metrics = metrics;
    }

    public RateLimitVerdict check(HttpServletRequest request, RateLimitProfile profile, RateLimitPhase phase) {
        RateLimitVerdict verdict = evaluate(request, profile, phase);
        if (verdict.rejected() || verdict.hasHeaders()) {
            metrics.recordOutcome(profile, phase, verdict.status());
        }
        return verdict;
    }

    /**
     * Layers are consulted in the order the profile declares them and the first breach wins, so a
     * caller who has exhausted their own budget is rejected without drawing from the shared ceiling
     * underneath it (the IP bucket a NAT's other users depend on).
     */
    private RateLimitVerdict evaluate(HttpServletRequest request, RateLimitProfile profile, RateLimitPhase phase) {
        if (!profile.limited()) {
            return RateLimitVerdict.unlimited();
        }
        RateLimitVerdict tightest = RateLimitVerdict.unlimited();
        for (KeyType keyType : profile.keyTypes()) {
            if (keyType.phase() != phase) {
                continue;
            }
            Optional<String> identity = generators.get(keyType).identity(request);
            if (identity.isEmpty()) {
                continue;
            }
            RateLimitVerdict verdict =
                    enforce(bucketKey(profile, identity.get()), properties.policyFor(profile, keyType));
            if (verdict.rejected()) {
                return verdict;
            }
            tightest = tighter(tightest, verdict);
        }
        return tightest;
    }

    private RateLimitVerdict enforce(String key, RateLimitPolicy policy) {
        try {
            RateLimitDecision decision = store.tryConsume(key, policy);
            if (decision.allowed()) {
                return RateLimitVerdict.allowed(decision.limit(), decision.remainingTokens(),
                        resetAfter(policy, decision.remainingTokens()));
            }
            log.warn("Rate limit exceeded (profile={}, key={}, retryAfter={}s)",
                    policy.profile(), fingerprint(key), decision.retryAfter().toSeconds());
            return RateLimitVerdict.throttled(decision.limit(), decision.retryAfter(), resetAfter(policy, 0));
        } catch (RuntimeException e) {
            log.warn("Rate-limit store failure (profile={}, failMode={})", policy.profile(), policy.failMode(), e);
            metrics.recordStoreError(policy.profile());
            return policy.failMode() == FailMode.FAIL_CLOSED
                    ? RateLimitVerdict.unavailable()
                    : RateLimitVerdict.unlimited();
        }
    }

    /** Refill is greedy (continuous, linear), so time-to-full is exact from the policy — no store round-trip. */
    private Duration resetAfter(RateLimitPolicy policy, long remaining) {
        long missingTokens = Math.max(0, policy.capacity() - remaining);
        long nanosPerToken = policy.refillPeriod().toNanos() / policy.refillTokens();
        return Duration.ofNanos(missingTokens * nanosPerToken);
    }

    private String bucketKey(RateLimitProfile profile, String identity) {
        return profile.name().toLowerCase() + ":" + identity;
    }

    /** The key embeds an IP or session id, so logs get a digest instead (rule #6) — still correlatable. */
    private String fingerprint(String key) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, FINGERPRINT_LENGTH);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private RateLimitVerdict tighter(RateLimitVerdict current, RateLimitVerdict candidate) {
        if (!current.hasHeaders()) {
            return candidate;
        }
        if (!candidate.hasHeaders()) {
            return current;
        }
        return candidate.remaining() < current.remaining() ? candidate : current;
    }
}