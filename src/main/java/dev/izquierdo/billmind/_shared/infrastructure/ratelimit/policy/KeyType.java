package dev.izquierdo.billmind._shared.infrastructure.ratelimit.policy;

/**
 * The identity a limit is counted against. Each key type is bound to the {@link RateLimitPhase} at
 * which it becomes trustworthy: {@link #TOKEN} is only usable once the bearer token has been
 * validated, whereas {@link #IP} and {@link #SESSION} are available before authentication.
 */
public enum KeyType {

    /** Client IP. The only identity an attacker cannot cheaply rotate before authentication. */
    IP(RateLimitPhase.PRE_AUTH),

    /** {@code X-Session-Id} header. The anonymous visitor's own budget. */
    SESSION(RateLimitPhase.PRE_AUTH),

    /** Hash of the validated bearer token. Caps abuse from a single valid credential. */
    TOKEN(RateLimitPhase.POST_AUTH);

    private final RateLimitPhase phase;

    KeyType(RateLimitPhase phase) {
        this.phase = phase;
    }

    public RateLimitPhase phase() {
        return phase;
    }
}