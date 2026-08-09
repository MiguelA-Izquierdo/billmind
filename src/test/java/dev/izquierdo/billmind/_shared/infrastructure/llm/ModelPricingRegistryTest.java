package dev.izquierdo.billmind._shared.infrastructure.llm;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class ModelPricingRegistryTest {

    @Test
    void shouldReturnPricingForKnownModel() {
        Optional<ModelPricingRegistry.Pricing> pricing = ModelPricingRegistry.lookup("claude-sonnet-4-6");
        assertThat(pricing).isPresent();
    }

    /** Both roles run on Groq; an unpriced model would silently report llm.cost = 0. */
    @Test
    void shouldPriceBothGroqRoleModels() {
        assertThat(ModelPricingRegistry.lookup("openai/gpt-oss-120b")).isPresent();
        assertThat(ModelPricingRegistry.lookup("openai/gpt-oss-20b")).isPresent();
    }

    @Test
    void shouldReturnEmptyForUnknownModel() {
        assertThat(ModelPricingRegistry.lookup("llama3:local")).isEmpty();
    }

    @Test
    void shouldReturnEmptyForOllamaModels() {
        assertThat(ModelPricingRegistry.lookup("llama3.2")).isEmpty();
        assertThat(ModelPricingRegistry.lookup("mistral")).isEmpty();
    }

    @Test
    void shouldComputeCostFromInputTokens() {
        // claude-sonnet-4-6: $3.00/1M input
        ModelPricingRegistry.Pricing pricing = ModelPricingRegistry.lookup("claude-sonnet-4-6").orElseThrow();
        assertThat(pricing.cost(1_000_000, 0)).isCloseTo(3.00, within(0.0001));
    }

    @Test
    void shouldComputeCostFromOutputTokens() {
        // claude-sonnet-4-6: $15.00/1M output
        ModelPricingRegistry.Pricing pricing = ModelPricingRegistry.lookup("claude-sonnet-4-6").orElseThrow();
        assertThat(pricing.cost(0, 1_000_000)).isCloseTo(15.00, within(0.0001));
    }

    @Test
    void shouldComputeCombinedCost() {
        // gpt-4o: $2.50/1M input + $10.00/1M output
        // 500 input + 100 output → 0.001250 + 0.001000 = 0.002250
        ModelPricingRegistry.Pricing pricing = ModelPricingRegistry.lookup("gpt-4o").orElseThrow();
        assertThat(pricing.cost(500, 100)).isCloseTo(0.002250, within(0.000001));
    }

    @Test
    void shouldReturnZeroCostForZeroTokens() {
        ModelPricingRegistry.Pricing pricing = ModelPricingRegistry.lookup("gpt-4o").orElseThrow();
        assertThat(pricing.cost(0, 0)).isZero();
    }
}