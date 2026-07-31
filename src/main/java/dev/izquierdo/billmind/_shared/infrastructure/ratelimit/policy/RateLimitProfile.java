package dev.izquierdo.billmind._shared.infrastructure.ratelimit.policy;

import java.util.List;

/**
 * The rate-limit treatment of a class of endpoints. A profile fixes the design-level decisions —
 * which identities it is counted against ({@link #keyTypes()}) and how it behaves on store failure
 * ({@link #failMode()}) — while the tunable numbers (capacity, refill, cost) come from configuration.
 *
 * <p>{@link #ADMIN} carries two key types on purpose: a pre-auth IP layer that caps brute force
 * before the token introspection call, and a post-auth token layer that caps a single valid
 * credential. See {@code docs/RATELIMIT.md}.
 *
 * <p>{@link #UPLOAD} and {@link #CHAT} carry two for a different reason. {@code SESSION} is derived
 * from {@code X-Session-Id}, a header the client writes itself: an attacker who sends a fresh UUID
 * per request gets a fresh bucket per request, so on its own it bounds honest users and nobody else.
 * {@code IP} is the only pre-auth identity that cannot be rotated for free, so it goes underneath as
 * a ceiling — with its own, far wider bucket (see {@code overrides} in {@code RateLimitProperties}),
 * sized so it never touches a real visitor, NAT-shared or not, and only bites at volumes no human
 * produces. Layers are evaluated in order and the first breach wins, so a visitor who exhausts their
 * own session budget is thrown out before spending any of the IP ceiling that their NAT neighbours share.
 */
public enum RateLimitProfile {

    /** {@code POST /invoices} — LLM extraction, paid. */
    UPLOAD(FailMode.FAIL_CLOSED, List.of(KeyType.SESSION, KeyType.IP)),

    /** {@code POST /assistant/chat} — RAG + streaming LLM, paid. */
    CHAT(FailMode.FAIL_CLOSED, List.of(KeyType.SESSION, KeyType.IP)),

    /** Admin routes that change state — brute-force sensitive, two layers. */
    ADMIN(FailMode.FAIL_CLOSED, List.of(KeyType.IP, KeyType.TOKEN)),

    /**
     * Admin reads. Same two identities and the same fail-closed stance as {@link #ADMIN}, wider
     * bucket: a listing is refreshed by hand and changes nothing, so the budget sized for guarding
     * destructive routes only got in the honest operator's way.
     *
     * <p>The widening is not free — the {@code IP} layer is the pre-auth cap, so an attacker spraying
     * tokens gets that same headroom on admin {@code GET}s. It buys them attempts against a
     * high-entropy credential and reaches nothing that mutates, which is the trade this profile makes
     * deliberately and the mutating routes refuse.
     */
    ADMIN_READ(FailMode.FAIL_CLOSED, List.of(KeyType.IP, KeyType.TOKEN)),

    /** Cheap public reads — no LLM, no cost exposure. */
    PUBLIC_READ(FailMode.FAIL_OPEN, List.of(KeyType.IP)),

    /**
     * Safe default for any unmapped {@code /api/v1/} route: fail-closed, and — like {@link #UPLOAD}
     * and {@link #CHAT} — never session-keyed alone. A profile whose only identity is one the caller
     * writes themselves is not a limit, and this one guards the routes nobody has thought about yet.
     */
    DEFAULT(FailMode.FAIL_CLOSED, List.of(KeyType.SESSION, KeyType.IP)),

    /** No limit — actuator, static assets, internal routes. */
    NONE(FailMode.FAIL_OPEN, List.of());

    private final FailMode failMode;
    private final List<KeyType> keyTypes;

    RateLimitProfile(FailMode failMode, List<KeyType> keyTypes) {
        this.failMode = failMode;
        this.keyTypes = keyTypes;
    }

    public FailMode failMode() {
        return failMode;
    }

    public List<KeyType> keyTypes() {
        return keyTypes;
    }

    public boolean limited() {
        return !keyTypes.isEmpty();
    }
}