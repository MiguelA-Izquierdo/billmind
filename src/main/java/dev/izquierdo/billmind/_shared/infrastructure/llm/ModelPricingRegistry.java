package dev.izquierdo.billmind._shared.infrastructure.llm;

import java.util.Map;
import java.util.Optional;

final class ModelPricingRegistry {

    record Pricing(double inputPer1M, double outputPer1M) {
        double cost(int inputTokens, int outputTokens) {
            return (inputTokens / 1_000_000.0) * inputPer1M
                 + (outputTokens / 1_000_000.0) * outputPer1M;
        }
    }

    // USD per 1M tokens — approximate list prices as of 2026-08
    private static final Map<String, Pricing> PRICES = Map.ofEntries(
        // OpenAI
        Map.entry("gpt-4o",               new Pricing(2.50,  10.00)),
        Map.entry("gpt-4o-mini",          new Pricing(0.15,   0.60)),
        Map.entry("gpt-4.1",              new Pricing(2.00,   8.00)),
        Map.entry("gpt-4.1-mini",         new Pricing(0.40,   1.60)),
        // Anthropic
        Map.entry("claude-opus-4-7",      new Pricing(15.00, 75.00)),
        Map.entry("claude-sonnet-4-6",    new Pricing( 3.00, 15.00)),
        Map.entry("claude-haiku-4-5-20251001", new Pricing(0.80, 4.00)),
        // Groq — the Llama entries are gone: GroqCloud shut them down on 2026-08-16, leaving
        // the two gpt-oss models as its only production text models (qwen3.6-27b is preview).
        Map.entry("openai/gpt-oss-120b",      new Pricing(0.15, 0.60)),
        Map.entry("openai/gpt-oss-20b",       new Pricing(0.075, 0.30)),
        Map.entry("qwen/qwen3.6-27b",         new Pricing(0.60, 3.00)),
        // Gemini
        Map.entry("gemini-2.5-flash",  new Pricing(0.15,  0.60)),
        Map.entry("gemini-2.0-flash",  new Pricing(0.10,  0.40)),
        Map.entry("gemini-1.5-pro",    new Pricing(1.25,  5.00)),
        Map.entry("gemini-1.5-flash",  new Pricing(0.075, 0.30))
        // Ollama: local inference, no cost
    );

    private ModelPricingRegistry() {}

    static Optional<Pricing> lookup(String model) {
        return Optional.ofNullable(PRICES.get(model));
    }
}