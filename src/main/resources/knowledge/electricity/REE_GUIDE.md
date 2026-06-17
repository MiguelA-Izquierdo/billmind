# Documento RAG: Guía REE — PVPC, Mercado Eléctrico y Perfil de Consumo
<!-- docType: REE_GUIDE | vigente_desde: 2021-06-01 | tarifas: 2.0TD, 3.0TD -->

---

## SECCIÓN 1: Red Eléctrica de España (REE) y su Rol en el Sistema

### 1.1 Qué es REE

Red Eléctrica de España (REE), actualmente denominada Red Eléctrica Corporación o REE, es la empresa responsable del transporte de electricidad en España y del operador del sistema eléctrico peninsular. Sus funciones principales son:

- **Transporte de electricidad**: gestiona la red de alta tensión (400 kV, 220 kV y 132 kV).
- **Operación del sistema**: garantiza el equilibrio entre generación y consumo en tiempo real.
- **Publicación de datos**: a través de ESIOS (Información del Sistema Eléctrico Español) publica precios, consumos y datos del sistema en tiempo real.

REE no vende electricidad a los consumidores finales. Su función es mantener la red y el sistema en funcionamiento.

### 1.2 ESIOS: la plataforma de datos de REE

ESIOS (https://www.esios.ree.es) es el portal de transparencia de datos del sistema eléctrico español. Publica:

- Precio horario del mercado diario (OMIE).
- Precio del PVPC hora a hora.
- Componentes desglosadas del PVPC.
- Perfiles de consumo por tipo de consumidor.
- Producción de electricidad por tecnología (solar, eólica, nuclear, etc.).
- Demanda eléctrica en tiempo real.

---

## SECCIÓN 2: El Precio Voluntario al Pequeño Consumidor (PVPC)

### 2.1 Qué es el PVPC

El Precio Voluntario al Pequeño Consumidor (PVPC) es la tarifa regulada de electricidad para consumidores domésticos y pequeños negocios en España. Está disponible para consumidores con potencia contratada de hasta 10 kW (aunque también puede contratarse hasta 15 kW en algunos casos).

El PVPC no es un precio fijo: varía hora a hora en función del precio del mercado mayorista de electricidad. Esto significa que consumir a las 3:00 de la madrugada puede ser significativamente más barato que consumir a las 19:00 de un día laborable en invierno.

### 2.2 Quién puede acogerse al PVPC

Pueden contratar el PVPC:
- Consumidores domésticos con potencia contratada de hasta 10 kW.
- Consumidores con derecho a bono social (independientemente de la potencia).
- Pequeños negocios con potencia hasta 10 kW que no hayan optado por mercado libre.

El PVPC lo ofrecen las comercializadoras de referencia (CUR), que son: Endesa Energía XXI, Iberdrola Comercialización de Último Recurso, Naturgy Iberia, EDP Comercializadora de Último Recurso y Repsol Electricidad y Gas (entre otras designadas).

### 2.3 Componentes del precio PVPC

El precio del PVPC se calcula sumando varias componentes:

**Componentes de mercado (variables):**
- **Precio del mercado diario (pool)**: precio al que se cruzan oferta y demanda en OMIE para cada hora del día siguiente. Es la componente más volátil.
- **Servicios de ajuste del sistema**: pagos por servicios que garantizan la fiabilidad del sistema (reservas, regulación de frecuencia, etc.).
- **Coste de desvíos**: coste de las diferencias entre la energía programada y la realmente consumida.

**Componentes reguladas (fijas o revisables periódicamente):**
- **Peaje de acceso a redes**: fijado por la CNMC (ver documento CNMC_CIRCULAR).
- **Cargos del sistema**: fijados por el MITECO.
- **Margen de comercialización regulado**: retribución fija de la comercializadora de referencia.

**Impuestos:**
- **Impuesto Especial sobre la Electricidad (IEE)**: 5,11269% sobre la base imponible (energía + potencia + otros cargos, antes de IVA).
- **IVA**: actualmente al 10% para consumidores domésticos (puede variar según normativa vigente).

### 2.4 Cómo se calcula el precio horario del PVPC

El precio del PVPC para cada hora del día siguiente se publica por la tarde del día anterior en ESIOS. El cálculo simplificado es:

```
Precio PVPC (€/kWh) = Precio mercado diario + Servicios ajuste + Peaje energía (periodo) + Cargos energía
```

La factura PVPC mensual multiplica el precio de cada hora por el consumo de esa hora, sumando el total del periodo de facturación. Esto requiere que el contador del consumidor sea inteligente (telemedida) y envíe lecturas horarias a la distribuidora.

### 2.5 PVPC con discriminación horaria vs. sin discriminación

Antes de junio de 2021, el PVPC podía contratarse con o sin discriminación horaria. Con la nueva tarifa 2.0TD, todos los consumidores PVPC tienen automáticamente tres periodos (punta, llano y valle), por lo que la discriminación horaria es obligatoria.

No existe ya la opción de "PVPC sin discriminación horaria" para nuevos contratos desde junio de 2021.

---

## SECCIÓN 3: El Mercado Libre de Electricidad

### 3.1 Qué es el mercado libre

El mercado libre es la alternativa al PVPC. En el mercado libre, el consumidor negocia el precio de la electricidad directamente con una comercializadora, sin que ese precio esté regulado por el gobierno. Cualquier empresa puede ser comercializadora en el mercado libre, previo registro en la CNMC.

En el mercado libre, la comercializadora asume el riesgo del precio del mercado mayorista y ofrece al consumidor:
- **Precio fijo**: el kWh cuesta lo mismo durante todo el período del contrato, independientemente del precio del mercado.
- **Precio indexado**: el precio varía con el mercado (similar al PVPC pero con el margen de la comercializadora libre).
- **Precio con discriminación horaria propia**: la comercializadora diseña sus propios periodos horarios (que pueden diferir de los periodos regulados).

### 3.2 Diferencias clave entre PVPC y mercado libre

| Característica | PVPC | Mercado Libre |
|---------------|------|---------------|
| Precio energía | Variable hora a hora | Fijo, indexado o con periodos propios |
| Quién lo regula | CNMC / MITECO | Libre acuerdo entre partes |
| Permanencia | Sin permanencia | Puede tener permanencia |
| Peajes de acceso | Regulados (iguales) | Regulados (iguales) |
| IEE e IVA | Igual | Igual |
| Margen comercializadora | Regulado (bajo) | Libre (variable) |
| Bono social | Compatible | No compatible (salvo excepciones) |

**Punto clave**: los peajes de acceso e impuestos son iguales en PVPC y mercado libre. Solo difiere el precio de la energía y el margen de la comercializadora.

### 3.3 Cuándo puede convenir el mercado libre

El mercado libre puede ser ventajoso cuando:
- El precio del mercado mayorista está alto de forma sostenida y el consumidor prefiere precio fijo para presupuestar.
- La comercializadora ofrece un precio competitivo como resultado de estrategias de captación.
- El consumidor tiene un perfil de consumo concentrado en horas punta (y le ofrecen precio plano).

El mercado libre puede ser desventajoso cuando:
- El mercado mayorista baja y el consumidor está atado a un precio fijo alto.
- La comercializadora cobra márgenes elevados no transparentes.
- El consumidor no presta atención a las condiciones del contrato y renovaciones automáticas.

---

## SECCIÓN 4: Perfil de Consumo y Curva de Carga

### 4.1 Qué es el perfil de consumo

El perfil de consumo es la distribución temporal del consumo eléctrico de un suministro. Indica cuánta energía se consume en cada hora del día o en cada periodo tarifario.

Para calcular correctamente el coste de la factura en tarifas con discriminación horaria (como el PVPC o la 2.0TD), es imprescindible conocer el perfil de consumo. Un mismo consumo mensual total puede tener costes muy diferentes según cuándo se concentre.

### 4.2 Contadores inteligentes y telemedida

Desde 2019, todos los consumidores en España con potencia contratada superior a 15 kW debían tener contador inteligente. Para consumidores domésticos (hasta 15 kW), el plazo de sustitución fue gradual y se completó en su mayoría en 2021.

Los contadores inteligentes (tipo PRIME o G3-PLC) permiten:
- Registrar el consumo hora a hora.
- Telemedida: envío automático de lecturas a la distribuidora sin visita del lector.
- Conocer el consumo en tiempo real a través de la web o app de la distribuidora.
- Aplicar correctamente la discriminación horaria del PVPC o 2.0TD.

### 4.3 Perfiles sintéticos para consumidores sin telemedida

Cuando un contador no tiene capacidad de telemedida o no envía lecturas horarias, la distribuidora aplica un **perfil sintético de consumo** para distribuir el consumo total registrado entre las horas del periodo de facturación.

REE publica los perfiles sintéticos de consumo (también llamados "perfiles de REE") en ESIOS. Estos perfiles son curvas normalizadas que representan el comportamiento promedio de los consumidores según el tipo de tarifa. Se usan para:

- Liquidar el PVPC cuando no hay datos reales horarios.
- Calcular el coste estimado de la factura.
- Resolver las liquidaciones entre comercializadoras y distribuidoras.

### 4.4 Cómo optimizar el perfil de consumo

Para reducir la factura aprovechando la discriminación horaria:

- **Periodo Valle (P3)**: usar electrodomésticos de alto consumo en horas nocturnas y fines de semana. El precio es el más bajo.
- **Periodo Llano (P2)**: consumo moderado en horas de transición.
- **Periodo Punta (P1)**: minimizar el uso de calefacción eléctrica, aire acondicionado y otros grandes consumos en horas de tarde (18:00-22:00) y mediodía (10:00-14:00) de días laborables.

El ahorro potencial puede ser significativo: el precio en P1 puede ser 3 o 4 veces más caro que en P3.

---

## SECCIÓN 5: El Mercado Mayorista (Pool)

### 5.1 Cómo funciona el mercado diario

El mercado diario de electricidad en España lo gestiona OMIE (Operador del Mercado Ibérico de Energía). Funciona como una subasta diaria:

- Los productores de electricidad ofertan a qué precio están dispuestos a vender su energía hora a hora del día siguiente.
- Los compradores (comercializadoras) indican cuánta energía necesitan comprar y a qué precio máximo.
- OMIE cruza oferta y demanda hora a hora y fija el precio marginalista: el precio de la última unidad necesaria para cubrir la demanda determina el precio de TODA la energía de esa hora (sistema marginalista).

### 5.2 Factores que afectan al precio del pool

El precio del mercado mayorista varía mucho según:

- **Precio del gas natural**: las centrales de gas de ciclo combinado suelen ser las "marginales" que fijan el precio en muchas horas. Cuando sube el gas, sube el precio de la luz.
- **Producción renovable**: cuando hay mucho viento o sol, hay mucha energía barata disponible y el precio baja.
- **Demanda**: en picos de frío o calor, la demanda sube y el precio también.
- **Precio del CO₂**: las centrales de gas pagan derechos de emisión de CO₂ (mercado ETS europeo). Si el CO₂ sube, el gas es más caro y el pool sube.
- **Interconexiones internacionales**: si Francia tiene exceso de nuclear barata, puede exportar a España y bajar el precio.
- **Hidráulica**: en años de mucha lluvia y embalses llenos, la hidráulica barata baja el precio del pool.

### 5.3 Relación entre el pool y la factura doméstica

Para los consumidores PVPC, la relación entre el pool y la factura es directa: si el pool sube, la factura sube (en el componente de energía). Para los consumidores en mercado libre con precio fijo, el pool no afecta directamente su factura, aunque sí al coste para la comercializadora.

---

## SECCIÓN 6: Lecturas y Facturación

### 6.1 Tipos de lectura

- **Lectura real**: tomada por el lector de la distribuidora o por telemedida del contador inteligente. Es la más exacta.
- **Lectura estimada**: cuando no se puede tomar lectura real, la distribuidora estima el consumo basándose en el histórico. La factura indica claramente si es estimada.
- **Lectura de regularización**: cuando, tras varias lecturas estimadas, se toma una lectura real que ajusta las diferencias acumuladas. Puede provocar facturas muy elevadas o muy bajas respecto a las estimadas previas.

### 6.2 Ciclos de facturación

- La mayoría de los consumidores domésticos reciben factura mensual o bimestral.
- Con contador inteligente y telemedida, la facturación es generalmente mensual y basada en consumo real horario.
- Sin telemedida, puede haber ciclos bimestrales o trimestrales con lecturas estimadas.

### 6.3 Cómo verificar las lecturas

El consumidor puede verificar las lecturas en:
- La web o app de su distribuidora (Endesa, Iberdrola, Naturgy, etc.).
- El portal CUPS (Código Universal de Punto de Suministro) de su distribuidora.
- El propio contador inteligente, que muestra el consumo acumulado.

Si hay discrepancias entre el contador y la factura, el consumidor puede solicitar una relecture o reclamar ante la distribuidora.
