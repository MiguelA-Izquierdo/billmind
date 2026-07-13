/**
 * Bounded context: Market — ingests external electricity price events from Kafka
 * ({@code market.electricity-price-updated}) via {@code ElectricityPriceConsumer} and
 * persists them as {@code electricity_rates}, the reference dataset the comparison
 * engine reads. Exposes {@code GET /api/v1/market-rates}.
 */
package dev.izquierdo.billmind.market;