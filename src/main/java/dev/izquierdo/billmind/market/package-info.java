/**
 * Bounded context: Market — ingests external electricity price events from Kafka
 * ({@code market.electricity-price-updated}) via {@code ElectricityPriceConsumer} and
 * persists them as {@code electricity_rates}, the reference dataset the comparison
 * engine reads. Exposes {@code GET} and {@code DELETE /api/v1/admin/market-rates} — the corpus is
 * admin-only in both directions, so it is served from inside the admin tree.
 */
package dev.izquierdo.billmind.market;