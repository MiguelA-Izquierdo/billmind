-- The power term (término de potencia) of an electricity invoice, in euros per contracted kW
-- per day. Without it the comparison engine could only weigh the energy term, which both
-- understated the saving and ranked offers on half their cost.
--
-- 2.0TD bills two power periods (punta-llano and valle) against the single contracted kW that
-- invoices.contracted_power_kw already holds, so two prices are enough. Nullable: an invoice that
-- prints only the contracted kW still ingests, and the engine degrades to an energy-only
-- comparison instead of rejecting the upload.

ALTER TABLE invoices ADD COLUMN IF NOT EXISTS power_price_p1_per_kw_day numeric(10,6);
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS power_price_p2_per_kw_day numeric(10,6);