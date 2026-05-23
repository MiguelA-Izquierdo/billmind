package dev.izquierdo.billmind.market.application.query;

import dev.izquierdo.billmind._shared.application.query.Query;
import dev.izquierdo.billmind.market.domain.model.ElectricityRate;

import java.util.List;

public record GetElectricityRatesQuery() implements Query<List<ElectricityRate>> {}