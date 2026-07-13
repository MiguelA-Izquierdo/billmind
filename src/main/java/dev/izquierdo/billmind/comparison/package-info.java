/**
 * Bounded context: Comparison — deterministic savings engine. On every upload it
 * compares the user's invoice against current {@code market/} rates and produces a
 * quantified overpayment result, embedded in the {@code POST /api/v1/invoices}
 * response and served by {@code GET /api/v1/invoices/{id}/comparison}.
 */
package dev.izquierdo.billmind.comparison;