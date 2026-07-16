package dev.izquierdo.billmind._shared.infrastructure.ratelimit.config;

import dev.izquierdo.billmind._shared.infrastructure.ratelimit.policy.KeyType;
import dev.izquierdo.billmind._shared.infrastructure.ratelimit.policy.RateLimitPolicy;
import dev.izquierdo.billmind._shared.infrastructure.ratelimit.policy.RateLimitProfile;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(EnableProperties.class);

    @Test
    void shouldBindEveryProfileIncludingRelaxedEnumKeys() {
        runner.withPropertyValues(allProfiles()).run(context -> {
            assertThat(context).hasNotFailed();
            RateLimitProperties properties = context.getBean(RateLimitProperties.class);

            RateLimitPolicy publicRead = properties.policyFor(RateLimitProfile.PUBLIC_READ);
            assertThat(publicRead.capacity()).isEqualTo(60);

            RateLimitPolicy fallback = properties.policyFor(RateLimitProfile.DEFAULT);
            assertThat(fallback.capacity()).isEqualTo(30);
        });
    }

    /** UPLOAD's IP ceiling is a separate bucket from its session budget — that is the whole point. */
    @Test
    void shouldBindAPerLayerOverrideAsItsOwnBucket() {
        runner.withPropertyValues(allProfiles()).run(context -> {
            assertThat(context).hasNotFailed();
            RateLimitProperties properties = context.getBean(RateLimitProperties.class);

            RateLimitPolicy session = properties.policyFor(RateLimitProfile.UPLOAD, KeyType.SESSION);
            assertThat(session.capacity()).isEqualTo(5);
            assertThat(session.cost()).isEqualTo(5);

            RateLimitPolicy ipCeiling = properties.policyFor(RateLimitProfile.UPLOAD, KeyType.IP);
            assertThat(ipCeiling.capacity()).isEqualTo(10);
            assertThat(ipCeiling.cost()).isEqualTo(1);
        });
    }

    /** A layer without an override falls back to the profile's base bucket. */
    @Test
    void shouldFallBackToTheBaseBucketForALayerWithoutAnOverride() {
        runner.withPropertyValues(allProfiles()).run(context -> {
            RateLimitProperties properties = context.getBean(RateLimitProperties.class);

            assertThat(properties.policyFor(RateLimitProfile.CHAT, KeyType.IP).capacity())
                    .isEqualTo(properties.policyFor(RateLimitProfile.CHAT).capacity());
        });
    }

    /** An override on a layer the profile does not carry would silently never run — fail the boot. */
    @Test
    void shouldFailStartupWhenAnOverrideTargetsALayerTheProfileDoesNotHave() {
        String[] withStraySessionOverride = concat(allProfiles(),
                "billmind.ratelimit.profiles.admin.overrides.session.capacity=99",
                "billmind.ratelimit.profiles.admin.overrides.session.refill-tokens=99",
                "billmind.ratelimit.profiles.admin.overrides.session.refill-period=PT1M");

        runner.withPropertyValues(withStraySessionOverride).run(context ->
                assertThat(context).hasFailed());
    }

    /** An override is a complete bucket, not a patch: half-declared numbers must not boot. */
    @Test
    void shouldFailStartupWhenAnOverrideIsIncomplete() {
        String[] withIncompleteOverride = concat(allProfiles(),
                "billmind.ratelimit.profiles.chat.overrides.ip.capacity=60");

        runner.withPropertyValues(withIncompleteOverride).run(context ->
                assertThat(context).hasFailed());
    }

    @Test
    void shouldFailStartupWhenALimitedProfileIsMissing() {
        String[] withoutAdmin = new String[]{
                "billmind.ratelimit.profiles.upload.capacity=5",
                "billmind.ratelimit.profiles.upload.refill-tokens=5",
                "billmind.ratelimit.profiles.upload.refill-period=PT1H",
                "billmind.ratelimit.profiles.upload.cost=5",
                "billmind.ratelimit.profiles.chat.capacity=20",
                "billmind.ratelimit.profiles.chat.refill-tokens=20",
                "billmind.ratelimit.profiles.chat.refill-period=PT1M",
                "billmind.ratelimit.profiles.public-read.capacity=60",
                "billmind.ratelimit.profiles.public-read.refill-tokens=60",
                "billmind.ratelimit.profiles.public-read.refill-period=PT1M",
                "billmind.ratelimit.profiles.default.capacity=30",
                "billmind.ratelimit.profiles.default.refill-tokens=30",
                "billmind.ratelimit.profiles.default.refill-period=PT1M"
        };
        runner.withPropertyValues(withoutAdmin).run(context ->
                assertThat(context).hasFailed());
    }

    /** CHAT deliberately carries no IP override here, so the fallback path is exercised too. */
    private String[] allProfiles() {
        return new String[]{
                "billmind.ratelimit.profiles.upload.capacity=5",
                "billmind.ratelimit.profiles.upload.refill-tokens=5",
                "billmind.ratelimit.profiles.upload.refill-period=PT1H",
                "billmind.ratelimit.profiles.upload.cost=5",
                "billmind.ratelimit.profiles.upload.overrides.ip.capacity=10",
                "billmind.ratelimit.profiles.upload.overrides.ip.refill-tokens=10",
                "billmind.ratelimit.profiles.upload.overrides.ip.refill-period=PT1H",
                "billmind.ratelimit.profiles.upload.overrides.ip.cost=1",
                "billmind.ratelimit.profiles.chat.capacity=20",
                "billmind.ratelimit.profiles.chat.refill-tokens=20",
                "billmind.ratelimit.profiles.chat.refill-period=PT1M",
                "billmind.ratelimit.profiles.admin.capacity=5",
                "billmind.ratelimit.profiles.admin.refill-tokens=5",
                "billmind.ratelimit.profiles.admin.refill-period=PT1M",
                "billmind.ratelimit.profiles.public-read.capacity=60",
                "billmind.ratelimit.profiles.public-read.refill-tokens=60",
                "billmind.ratelimit.profiles.public-read.refill-period=PT1M",
                "billmind.ratelimit.profiles.default.capacity=30",
                "billmind.ratelimit.profiles.default.refill-tokens=30",
                "billmind.ratelimit.profiles.default.refill-period=PT1M"
        };
    }

    private String[] concat(String[] base, String... extra) {
        return Stream.concat(Stream.of(base), Stream.of(extra)).toArray(String[]::new);
    }

    @EnableConfigurationProperties(RateLimitProperties.class)
    static class EnableProperties {
    }
}