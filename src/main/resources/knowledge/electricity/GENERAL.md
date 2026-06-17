# Documento RAG: Guía General de Facturación Eléctrica — FAQ, Lectura de Factura y Optimización
<!-- docType: GENERAL | vigente_desde: 2021-06-01 | tarifas: 2.0TD, 3.0TD, general -->

---

## SECCIÓN 1: Cómo Leer una Factura Eléctrica

### 1.1 Estructura general de una factura doméstica

Una factura eléctrica doméstica en España tiene siempre la siguiente estructura:

**Bloque 1 — Datos del contrato:**
- Nombre del titular y dirección del suministro.
- CUPS (Código Universal de Punto de Suministro): identificador único del punto.
- Tarifa de acceso (2.0TD en doméstico).
- Potencia contratada en cada periodo (P1, P2, P3 en kW).
- Comercializadora y número de contrato.
- Distribuidora responsable de la red.

**Bloque 2 — Período de facturación:**
- Fecha de inicio y fecha de fin del período.
- Número de días facturados.
- Tipo de lectura: real o estimada.
- Lecturas del contador al inicio y al final del período.

**Bloque 3 — Término de potencia:**
- Potencia contratada × precio unitario (€/kW·día o €/kW·año) × días facturados.
- Se desglosa por periodo (P1, P2, P3) si las potencias son distintas.
- Este importe es fijo independientemente del consumo.

**Bloque 4 — Término de energía:**
- Consumo en kWh por cada período tarifario (P1, P2, P3).
- Precio de cada período en €/kWh.
- Importe = suma de (consumo × precio) de cada período.

**Bloque 5 — Otros cargos:**
- Alquiler del equipo de medida (contador).
- Penalización por exceso de potencia (maxímetro), si aplica.
- Reactiva, si aplica (principalmente en 3.0TD).

**Bloque 6 — Impuestos:**
- IEE: 5,11269% sobre la suma de todos los conceptos anteriores.
- IVA: tipo vigente en el período sobre la suma total incluyendo IEE.

**Bloque 7 — Total a pagar:**
- Suma de todos los conceptos anteriores.
- Importe del bono social (descuento), si aplica.
- Forma de pago y fecha de cargo.

### 1.2 Ejemplo de factura simplificada (2.0TD, PVPC)

**Datos del ejemplo:**
- Potencia contratada: 4,6 kW (igual en todos los periodos)
- Días facturados: 30
- Consumo P1 (punta): 45 kWh a 0,25 €/kWh
- Consumo P2 (llano): 80 kWh a 0,18 €/kWh
- Consumo P3 (valle): 95 kWh a 0,10 €/kWh

```
TÉRMINO DE POTENCIA:
  4,6 kW × 0,14 €/kW·día × 30 días = 19,32 €

TÉRMINO DE ENERGÍA:
  P1: 45 kWh × 0,25 €/kWh = 11,25 €
  P2: 80 kWh × 0,18 €/kWh = 14,40 €
  P3: 95 kWh × 0,10 €/kWh = 9,50 €
  Subtotal energía: 35,15 €

ALQUILER CONTADOR:
  0,81 €/mes = 0,81 €

SUBTOTAL ANTES DE IMPUESTOS: 55,28 €

IEE (5,11269% sobre 54,47 €*): 2,78 €
  *el alquiler del contador no siempre entra en la base del IEE

SUBTOTAL + IEE: 58,06 €

IVA (10% sobre 58,06 €): 5,81 €

TOTAL A PAGAR: 63,87 €
```

*Nota: Los precios del ejemplo son orientativos. Los precios reales PVPC varían hora a hora.*

### 1.3 Cómo verificar que la factura es correcta

Pasos para comprobar una factura:

1. **Verificar el período**: comprobar que las fechas coinciden con el período esperado y que no hay solapamiento con la factura anterior.
2. **Verificar las lecturas**: comparar las lecturas del contador en la factura con las que aparecen en el portal de la distribuidora o en el propio contador.
3. **Verificar la potencia**: confirmar que la potencia contratada en la factura coincide con la que se pactó.
4. **Verificar el precio de energía**: si es PVPC, comparar los precios medios por período con los publicados en ESIOS para ese mes.
5. **Verificar los impuestos**: calcular el IEE y el IVA manualmente para comprobar que son correctos.
6. **Verificar el total**: sumar todos los conceptos y comparar con el total facturado.

---

## SECCIÓN 2: Preguntas Frecuentes (FAQ)

### 2.1 ¿Por qué subió mi factura este mes?

Las causas más habituales de subida de la factura son:

**Causas relacionadas con el consumo:**
- Aumento real del consumo (más días en casa, temporada de calefacción/refrigeración, nuevo electrodoméstico).
- Lectura de regularización: se acumularon facturas estimadas por debajo del consumo real y ahora se ajusta.
- Más días en el período de facturación que el mes anterior.

**Causas relacionadas con el precio:**
- Aumento del precio del mercado mayorista (si está en PVPC o en contrato indexado).
- Cambio de tarifa por parte de la comercializadora (en mercado libre, al finalizar el período de contrato).
- Subida de peajes o cargos del sistema (habitualmente en enero de cada año).
- Fin de medidas de reducción de IVA o IEE que estaban vigentes el mes anterior.

**Causas relacionadas con el contrato:**
- Aumento de la potencia contratada (propio o por modificación sin comunicación).
- Fin de una promoción o descuento temporal de la comercializadora.

### 2.2 ¿Qué es el pico de potencia y cómo me afecta?

El "pico de potencia" es el momento en que se alcanza la mayor demanda simultánea en el hogar. Por ejemplo, si se tienen encendidos al mismo tiempo el horno (2,2 kW), la lavadora (2 kW), el lavavajillas (1,8 kW) y la vitrocerámica (3,5 kW), la demanda total es de 9,5 kW. Si la potencia contratada es 5,75 kW, el ICP (Interruptor de Control de Potencia) saltará y cortará el suministro.

**Opciones cuando salta el ICP:**
- Apagar algunos aparatos y volver a conectar el ICP (solución inmediata).
- Contratar más potencia si los picos son frecuentes (solución permanente pero más cara).
- Escalonar el uso de aparatos de alto consumo para no coincidir (solución sin coste).

### 2.3 ¿Me conviene cambiar al mercado libre o quedarme en PVPC?

Depende de varios factores:

**Quedarse en PVPC puede convenir si:**
- Se es flexible con el horario y se puede concentrar el consumo en horas valle.
- Históricamente, en períodos de precios bajos del mercado, el PVPC es más barato.
- Se tiene derecho al bono social (solo compatible con PVPC).
- Se prefiere no tener permanencia ni ataduras contractuales.

**Ir al mercado libre puede convenir si:**
- Se prefiere precio fijo para presupuestar sin sorpresas.
- La comercializadora ofrece un precio competitivo con descuentos atractivos.
- El perfil de consumo no permite aprovechar las horas valle.
- Se valoran servicios adicionales que ofrece la comercializadora.

**Cómo comparar**: usar el comparador oficial de la CNMC (https://comparadorluz.cnmc.es) que muestra el coste estimado de diferentes ofertas según el perfil de consumo real.

### 2.4 ¿Qué potencia me conviene contratar?

La potencia óptima depende de los aparatos del hogar y de cuántos se usan simultáneamente. Regla general:

- **2,3 kW**: muy básico, solo iluminación y pequeños electrodomésticos.
- **3,45 kW**: cocina eléctrica modesta + electrodomésticos básicos.
- **4,6 kW**: hogar medio con vitrocerámica, lavadora, frigorífico y algunos más.
- **5,75 kW**: hogar con varios grandes electrodomésticos simultáneos.
- **6,9 kW o más**: hogar con aire acondicionado, vitrocerámica de inducción potente, o varios equipos de alto consumo simultáneos.

**Consejo**: contratar potencia de más es más caro en el término de potencia pero evita cortes. Contratar de menos ahorra en potencia pero puede generar cortes frecuentes y penalización por maxímetro.

### 2.5 ¿Qué es una lectura estimada y cómo me afecta?

Una lectura estimada es cuando la distribuidora calcula el consumo sin haber leído el contador realmente. Esto puede ocurrir si:
- El contador no tiene telemedida activa.
- El lector no pudo acceder al contador.
- Hay problemas técnicos con la comunicación del contador inteligente.

La estimación se basa en el consumo histórico del punto de suministro. Si la estimación es muy diferente al consumo real, cuando se tome lectura real habrá una "factura de regularización" que puede ser mucho más alta o más baja de lo habitual.

**Qué hacer**: si recibes varias facturas estimadas consecutivas, contacta con tu distribuidora para solucionar el problema de lectura. También puedes aportar tú mismo la lectura del contador.

### 2.6 ¿Por qué tengo dos empresas en mi factura?

Es posible que la factura mencione tanto la comercializadora (la empresa con la que tienes el contrato) como la distribuidora (la empresa propietaria de la red). Esto es normal:

- **Comercializadora**: te vende la electricidad y te emite la factura. Ejemplo: Endesa, Iberdrola, Holaluz, etc.
- **Distribuidora**: gestiona la red que lleva la electricidad hasta tu casa. Ejemplo: I-DE, E-Distribución, UFD, etc.

En algunos casos, la comercializadora y la distribuidora forman parte del mismo grupo empresarial (Endesa / E-Distribución; Iberdrola / I-DE), pero legalmente son entidades separadas.

### 2.7 ¿Puedo cambiar de comercializadora sin coste?

Sí, en la mayoría de los casos. Las condiciones de cambio son:

- **Sin permanencia**: puedes cambiar en cualquier momento sin penalización.
- **Con permanencia**: si tu contrato tiene permanencia y cancelas antes del plazo acordado, la comercializadora puede cobrarte la penalización pactada en el contrato.
- **El proceso**: comunica el cambio a la nueva comercializadora, que gestionará todo el proceso. No hay corte de suministro. El cambio suele tardar entre 15 y 30 días.
- **Los peajes**: no cambian al cambiar de comercializadora. Solo cambia el precio de la energía y el margen de comercialización.

### 2.8 ¿Qué hago si creo que me han facturado mal?

Pasos a seguir:

1. **Revisa la factura** con detalle usando la guía de lectura de este documento.
2. **Compara las lecturas** del contador con las registradas en el portal de tu distribuidora.
3. **Contacta con la comercializadora** por escrito (email o formulario web) detallando el error que crees haber detectado. Guarda copia de la reclamación.
4. **Plazo de respuesta**: la comercializadora tiene 1 mes para responder.
5. **Si no hay solución**: presenta reclamación ante el servicio de atención al cliente de la distribuidora (si el error es de lecturas o peajes) o ante la Junta Arbitral de Consumo o el organismo regulador autonómico.
6. **Si el error persiste**: la CNMC tiene competencia para resolver disputas de acceso a red.

### 2.9 ¿Qué consumo es "normal" para un hogar español?

Los consumos medios de referencia en España según el tamaño del hogar:

| Tipo de hogar | Consumo anual aproximado |
|---------------|-------------------------|
| 1 persona, piso pequeño | 1.200 – 2.000 kWh/año |
| 2 personas, piso | 2.500 – 3.500 kWh/año |
| Familia de 3-4 personas, casa | 3.500 – 5.500 kWh/año |
| Familia con calefacción eléctrica | 6.000 – 12.000 kWh/año |
| Chalet con piscina y A/A | 8.000 – 20.000 kWh/año |

*Nota: Los valores varían mucho según la climatología de la zona, el aislamiento del edificio y los hábitos de consumo.*

---

## SECCIÓN 3: Checklist de Posibles Errores en la Factura

### 3.1 Errores más comunes en facturas domésticas

Usa esta lista para revisar si tu factura puede tener algún problema:

**Errores en lecturas:**
- [ ] ¿Las lecturas del contador en la factura coinciden con las del portal de la distribuidora?
- [ ] ¿La factura indica "estimada" pero debería ser real (tienes contador inteligente)?
- [ ] ¿Hay una factura de regularización grande que compensa lecturas estimadas incorrectas?
- [ ] ¿El consumo total es razonablemente comparable al de períodos similares del año anterior?

**Errores en potencia:**
- [ ] ¿La potencia contratada en la factura es la que acordaste al contratar o al último cambio?
- [ ] ¿La potencia facturada corresponde a kW o a kVA? (deben ser equivalentes en monofásico).
- [ ] ¿Hay cargo por exceso de potencia (maxímetro) que no esperabas?

**Errores en tarifa:**
- [ ] ¿La tarifa de acceso es 2.0TD? (debe serlo para potencias hasta 15 kW desde junio 2021).
- [ ] ¿Aparecen tres periodos de energía (P1, P2, P3)? (deben aparecer en 2.0TD).
- [ ] ¿Los precios por período son coherentes con los publicados para ese mes en ESIOS (si es PVPC)?

**Errores en impuestos:**
- [ ] ¿El IEE se ha calculado al 5,11269% sobre la base correcta?
- [ ] ¿El IVA corresponde al tipo vigente en el período de facturación?
- [ ] ¿El alquiler del contador lleva IVA al tipo general (21%) y no al tipo reducido?

**Errores de contrato:**
- [ ] ¿Hay un aumento de tarifa o cambio de condiciones que no te comunicaron previamente?
- [ ] ¿Se ha renovado automáticamente un contrato con permanencia en condiciones menos favorables?
- [ ] ¿Hay cargos adicionales (servicios extra, seguros, mantenimiento) que no contrataste?

### 3.2 Señales de alerta en la factura

Presta especial atención si:

- La factura es más del 50% más alta que el mismo mes del año anterior sin causa aparente.
- Aparece el concepto "regularización" con importe elevado.
- Los precios de energía son muy diferentes a los publicados en ESIOS para ese período.
- La potencia contratada ha cambiado sin que lo hayas solicitado.
- Aparece cargo por "exceso de potencia" o "reactiva" que no aparecía antes.

---

## SECCIÓN 4: Comparativa PVPC vs. Mercado Libre

### 4.1 Tabla comparativa

| Criterio | PVPC | Mercado Libre (precio fijo) | Mercado Libre (indexado) |
|----------|------|----------------------------|--------------------------|
| Precio energía | Variable hora a hora | Fijo durante contrato | Variable (similar a PVPC) |
| Transparencia precio | Alta (publicado en ESIOS) | Media (depende de contrato) | Alta |
| Riesgo precio | Alto si mercado sube | Bajo | Alto si mercado sube |
| Bono social | Sí | No | No |
| Permanencia | No | A veces | A veces |
| Optimización horaria | Muy importante | Menos relevante (precio plano) | Importante |
| Margen comercializadora | Regulado (bajo) | Libre (variable) | Libre (variable) |

### 4.2 Cuándo es mejor el PVPC

El PVPC convierte en ganador cuando:
- El precio del mercado mayorista es bajo (períodos de mucha renovable, poca demanda).
- El consumidor puede desplazar consumos a horas valle.
- El consumidor tiene derecho al bono social.
- El consumidor valora la flexibilidad sin permanencia.

### 4.3 Cuándo es mejor el mercado libre con precio fijo

El mercado libre con precio fijo gana cuando:
- El mercado mayorista está en niveles altos de forma prolongada.
- El consumidor no puede o no quiere cambiar sus hábitos de consumo según el horario.
- La comercializadora ofrece un precio competitivo con descuentos especiales (promociones de captación).
- El consumidor prefiere predecibilidad en la factura para planificar su presupuesto.

---

## SECCIÓN 5: Estacionalidad y Precios por Época del Año

### 5.1 Patrones típicos del precio de la electricidad en España

El precio de la electricidad en España no es uniforme a lo largo del año. Los patrones históricos más habituales son:

**Invierno (diciembre-febrero):**
- Precios habitualmente elevados por alta demanda de calefacción.
- Los días más fríos generan los picos más altos del año.
- Menor producción solar pero puede haber buena producción eólica.

**Primavera (marzo-mayo):**
- Precios generalmente más moderados.
- Aumento de la producción solar e hidráulica (si hay lluvias).
- Menor demanda que en invierno o verano.

**Verano (junio-agosto):**
- Precios elevados por demanda de refrigeración (aire acondicionado).
- Producción solar muy alta, especialmente en las horas centrales del día.
- Las horas centrales del día pueden ser más baratas de lo esperado en días soleados (por la solar), mientras que la tarde-noche es cara.

**Otoño (septiembre-noviembre):**
- Precios variables según la climatología.
- Transición similar a la primavera, generalmente moderados.

### 5.2 Impacto del precio del gas natural en la factura

Dado que las centrales de gas de ciclo combinado son frecuentemente las "marginales" que fijan el precio del pool, el precio del gas natural en los mercados internacionales tiene un impacto directo en la factura eléctrica:

- **Gas caro** (como en 2021-2022 por tensiones geopolíticas) → electricidad cara.
- **Gas barato** → electricidad más barata.
- **Mucha energía renovable** → el gas entra menos como marginal y el precio puede bajar.

### 5.3 Consejos para reducir la factura según la época del año

**Todo el año:**
- Desplazar la lavadora, lavavajillas y carga del coche eléctrico a horas valle (noche y fin de semana).
- Usar electrodomésticos eficientes (clase A o superior).
- Aislar bien el hogar para reducir calefacción y refrigeración.

**Invierno:**
- Usar calefacción de gas o bomba de calor en lugar de resistencias eléctricas puras.
- Programar la calefacción para que trabaje más en horas valle (noche) y mantener la temperatura acumulada.
- Bajar la temperatura del agua caliente sanitaria al mínimo necesario.

**Verano:**
- Usar el aire acondicionado en modo "pre-enfriamiento" en horas más baratas.
- Ventilar de noche cuando el exterior es fresco.
- Usar persianas y cortinas para reducir la carga térmica solar durante el día.

---

## SECCIÓN 6: Consumos por Aparato y Cálculo de Costes

### 6.1 Potencia típica de los principales electrodomésticos

| Aparato | Potencia típica | Consumo típico/uso |
|---------|----------------|-------------------|
| Frigorífico A+++ | 0,05 kW promedio | 120-200 kWh/año |
| Lavadora 60°C | 2,0 – 2,5 kW | 1,0 – 1,5 kWh/ciclo |
| Lavavajillas | 1,8 – 2,2 kW | 1,0 – 1,5 kWh/ciclo |
| Secadora | 2,5 – 4,0 kW | 3,0 – 5,0 kWh/ciclo |
| Horno eléctrico | 2,0 – 3,5 kW | 0,8 – 1,5 kWh/hora |
| Vitrocerámica (1 fuego grande) | 1,5 – 2,0 kW | variable |
| Inducción (1 fuego grande) | 2,0 – 3,5 kW | variable |
| Aire acondicionado 2500 frig. | 0,7 – 1,5 kW | 0,7 – 1,5 kWh/hora |
| Calefactor eléctrico (resistencia) | 0,5 – 2,0 kW | 0,5 – 2,0 kWh/hora |
| Bomba de calor (equivalente 2500 frig.) | 0,6 – 0,9 kW | 0,6 – 0,9 kWh/hora |
| Televisor LED 55" | 0,05 – 0,12 kW | 0,05 – 0,12 kWh/hora |
| Ordenador portátil | 0,04 – 0,08 kW | 0,04 – 0,08 kWh/hora |
| Iluminación LED (hogar completo) | 0,05 – 0,20 kW | variable |
| Calentador de agua (termo eléctrico) | 1,5 – 2,5 kW | 1,5 – 3,0 kWh/día |
| Coche eléctrico (carga lenta) | 3,7 kW | 5 – 20 kWh/carga |

### 6.2 Cómo calcular el coste de un aparato

Fórmula básica:

```
Coste (€) = Potencia (kW) × Horas de uso × Precio energía (€/kWh)
```

**Ejemplo**: una lavadora de 2 kW que funciona 1,5 horas en periodo valle (0,10 €/kWh):
```
Coste = 2 kW × 1,5 h × 0,10 €/kWh = 0,30 €
```

El mismo ciclo en periodo punta (0,25 €/kWh) costaría:
```
Coste = 2 kW × 1,5 h × 0,25 €/kWh = 0,75 €
```

Hacer la lavadora en horas valle en lugar de en horas punta ahorra 0,45 € por ciclo. Con 5 lavadoras por semana, el ahorro anual es de unos 117 €.
