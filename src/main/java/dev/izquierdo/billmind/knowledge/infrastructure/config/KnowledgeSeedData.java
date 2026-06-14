package dev.izquierdo.billmind.knowledge.infrastructure.config;

import dev.izquierdo.billmind.knowledge.application.command.IngestDocumentCommand;
import dev.izquierdo.billmind.knowledge.domain.model.DocType;

import java.util.List;
import java.util.UUID;

public class KnowledgeSeedData {

    public static List<IngestDocumentCommand> exampleDocuments() {
        return List.of(
                new IngestDocumentCommand(
                        UUID.fromString("a1b2c3d4-0001-0000-0000-000000000000"),
                        DocType.GLOSSARY,
                        "Glosario de Facturación Eléctrica",
                        "BillMind / Elaboración propia",
                        """
                        CUPS (Código Universal de Punto de Suministro): Código único que identifica el punto de conexión a la red eléctrica de un consumidor. Tiene 20 o 22 caracteres y comienza por 'ES'. Es el identificador del contrato de acceso a la red.

                        Término de potencia (TP): Cargo fijo mensual que el consumidor paga por la potencia eléctrica que tiene contratada, independientemente del consumo real. Se calcula multiplicando los kW contratados por el precio del kW y el número de días del periodo de facturación. En la tarifa 2.0TD existen dos periodos de potencia: P1 (punta) y P2 (llano/valle).

                        Término de energía (TE): Cargo variable que depende de la energía eléctrica consumida en kWh durante el periodo de facturación. En tarifas con discriminación horaria (DH), el precio varía según la franja horaria: punta, llano y valle.

                        Peaje de acceso (o peaje de red): Tarifa regulada por el Ministerio para la Transición Ecológica y el CNMC. Cubre los costes de transporte y distribución de la electricidad. Se divide en término de potencia de acceso y término de energía de acceso.

                        Cargo regulado: Importe que financia el sistema eléctrico nacional (primas a renovables, extra-peninsulares, etc.). Lo fija el Gobierno y forma parte de la factura junto con los peajes de acceso.

                        Impuesto especial sobre la electricidad (IEE): Impuesto que grava el consumo de electricidad en España. Se aplica sobre la suma del término de potencia y el término de energía antes de aplicar el IVA. El tipo general es del 5,11269632%, aunque ha habido reducciones temporales en el contexto de la crisis energética.

                        IVA (Impuesto sobre el Valor Añadido): Se aplica al total de la factura (incluyendo el IEE). El tipo general es del 21%, aunque ha habido reducciones al 10% o 5% durante periodos de alta inflación energética.

                        PVPC (Precio Voluntario para el Pequeño Consumidor): Tarifa regulada por el Gobierno para consumidores con potencia contratada de hasta 10 kW. El precio de la energía varía hora a hora según el mercado mayorista (OMIE). Lo comercializa la comercializadora de referencia (COR).

                        Comercializadora de referencia (COR): Empresa autorizada a comercializar electricidad a precio PVPC. En España, las principales son Endesa Energía XXI, Iberdrola Comercialización de Último Recurso, Naturgy Iberia y EDP Comercializadora de Último Recurso.

                        Discriminación horaria (DH): Modalidad de tarifa en la que el precio de la energía consumida varía según la hora del día. En la tarifa 2.0TD hay tres periodos: punta (más caro), llano y valle (más barato). Permite ahorrar desplazando el consumo a horas de menor precio.

                        REE (Red Eléctrica de España): Operador del sistema eléctrico español. Gestiona el transporte de alta tensión y garantiza el equilibrio entre oferta y demanda en tiempo real.

                        CNMC (Comisión Nacional de Mercados y la Competencia): Organismo regulador independiente que supervisa el sector energético español, aprueba los peajes de acceso y vela por la competencia en el mercado eléctrico.

                        Contador inteligente (telemedida): Contador que registra el consumo por franjas horarias y permite la telegestión. Su instalación es obligatoria desde 2018. Habilita la contratación de tarifas con discriminación horaria.

                        Potencia contratada: La potencia máxima (en kW) que el consumidor tiene derecho a usar simultáneamente. Si se supera, el ICP (Interruptor de Control de Potencia) o el limitador de potencia corta el suministro. Debe ajustarse a las necesidades reales para evitar pagar por potencia no utilizada.

                        Bono social eléctrico: Descuento aplicado a la factura de consumidores vulnerables o en riesgo de exclusión social. Lo gestiona la CNMC y lo financia el conjunto del sistema eléctrico.
                        """,
                        null, null
                ),
                new IngestDocumentCommand(
                        UUID.fromString("a1b2c3d4-0002-0000-0000-000000000000"),
                        DocType.REE_GUIDE,
                        "Tarifa 2.0TD: Discriminación Horaria y Periodos de Facturación",
                        "REE / CNMC — Guía de la tarifa de acceso 2.0TD",
                        """
                        La tarifa de acceso 2.0TD es la tarifa estándar para consumidores domésticos con potencia contratada de hasta 15 kW. Entró en vigor el 1 de junio de 2021, sustituyendo a las antiguas tarifas 2.0A, 2.0DHA y 2.1.

                        PERIODOS HORARIOS DE LA TARIFA 2.0TD

                        La tarifa 2.0TD divide el día en tres periodos horarios según el nivel de demanda en la red:

                        Periodo Punta (P1): Es el periodo de mayor coste. En días laborables (lunes a viernes), corresponde a las horas de máxima demanda eléctrica. Generalmente de 10:00 a 14:00 y de 18:00 a 22:00 horas. El precio de la energía en este periodo es el más elevado.

                        Periodo Llano (P2): Es el periodo de coste intermedio. En días laborables, corresponde a las horas de transición. Generalmente de 08:00 a 10:00, de 14:00 a 18:00 y de 22:00 a 24:00 horas. El precio es inferior al punta pero superior al valle.

                        Periodo Valle (P3): Es el periodo de menor coste. Comprende las horas nocturnas (de 00:00 a 08:00) y todos los fines de semana y festivos nacionales durante las 24 horas. Es el momento más económico para consumir electricidad.

                        IMPORTANTE: Los horarios se aplican en hora oficial peninsular. En las Islas Canarias se aplica el horario canario (una hora menos). Los festivos locales NO cuentan como valle; solo los festivos nacionales.

                        POTENCIA EN LA TARIFA 2.0TD

                        La tarifa 2.0TD permite contratar dos niveles de potencia:
                        - Potencia P1 (punta): Potencia disponible en el periodo punta y llano. Determina el límite máximo de consumo simultáneo en las horas de mayor demanda.
                        - Potencia P2 (valle): Potencia disponible en el periodo valle. Puede ser mayor que P1, permitiendo usar más potencia en horas nocturnas o de fin de semana a un menor coste.

                        El término de potencia se factura en €/kW por día contratado, multiplicado por los días del periodo.

                        CÓMO APROVECHAR LA DISCRIMINACIÓN HORARIA

                        Para sacar partido a la tarifa 2.0TD con discriminación horaria, conviene:
                        1. Programar electrodomésticos de alto consumo (lavavajillas, lavadora, secadora) para el periodo valle: noche o fin de semana.
                        2. Cargar vehículos eléctricos por la noche (periodo valle).
                        3. Usar el aire acondicionado o calefacción en periodos llano o valle en la medida de lo posible.
                        4. Revisar si merece la pena contratar una potencia P2 superior a P1 si se concentra el consumo intensivo en fines de semana.

                        COMPARATIVA CON TARIFA FIJA

                        La tarifa 2.0TD con discriminación horaria puede suponer un ahorro significativo (15-30%) respecto a una tarifa fija si el consumidor es capaz de desplazar el 40% o más de su consumo al periodo valle. En cambio, si el perfil de consumo es rígido (no se puede modificar horariamente), una tarifa fija puede resultar más predecible y conveniente.

                        El PVPC también sigue los periodos horarios de la 2.0TD, por lo que las recomendaciones de gestión del consumo aplican igualmente a los consumidores PVPC.
                        """,
                        null, null
                ),
                new IngestDocumentCommand(
                        UUID.fromString("a1b2c3d4-0003-0000-0000-000000000000"),
                        DocType.GENERAL,
                        "PVPC vs Tarifa Fija: Cuándo Conviene Cada Una",
                        "BillMind / Elaboración propia",
                        """
                        PVPC (PRECIO VOLUNTARIO PARA EL PEQUEÑO CONSUMIDOR)

                        El PVPC es la tarifa regulada por el Gobierno español para consumidores domésticos con potencia hasta 10 kW. Su precio varía hora a hora en función del mercado mayorista de electricidad (OMIE — Operador del Mercado Ibérico de la Energía).

                        Ventajas del PVPC:
                        - En periodos de precios bajos en el mercado mayorista, puede ser significativamente más barato que las tarifas fijas del mercado libre.
                        - Total transparencia: el precio de cada hora está publicado en la web de REE (ESIOS) con antelación.
                        - No tiene costes de permanencia ni penalizaciones por cambio de comercializadora.
                        - Permite acceder al Bono Social eléctrico si se cumplen los requisitos de vulnerabilidad.

                        Desventajas del PVPC:
                        - El precio puede dispararse en episodios de alta demanda, frío extremo o escasez de renovables, generando facturas muy elevadas.
                        - Requiere un perfil de consumo flexible para aprovechar las horas baratas.
                        - La incertidumbre mensual dificulta la planificación del presupuesto familiar.

                        Perfil ideal para el PVPC: hogar con hábitos flexibles, contador inteligente instalado, alto consumo concentrable en horas valle (noche, fin de semana), o consumidor que disponga de apps de monitorización del precio horario.

                        TARIFA FIJA DEL MERCADO LIBRE

                        Una tarifa fija del mercado libre garantiza un precio estable por kWh durante toda la vigencia del contrato (normalmente 12 meses). No varía con el mercado mayorista.

                        Ventajas de la tarifa fija:
                        - Previsibilidad total: la factura mensual es proporcional al consumo sin sorpresas por picos de precio.
                        - Adecuada para perfiles de consumo rígidos (horario de trabajo fijo, imposibilidad de desplazar cargas).
                        - Reduce el riesgo en periodos de alta volatilidad del mercado energético.

                        Desventajas de la tarifa fija:
                        - En periodos de precios bajos en el mercado mayorista, el consumidor no se beneficia de esas bajadas.
                        - Las comercializadoras incluyen un margen de beneficio, por lo que el precio base suele ser superior al precio medio esperado del PVPC.
                        - Suelen tener penalización por baja anticipada del contrato.

                        Perfil ideal para la tarifa fija: hogar con consumo rígido en horario de punta, bajo consumo total, alto perfil de aversión al riesgo económico, o empresas que necesitan presupuestos energéticos estables.

                        ¿CUÁNDO CAMBIAR DE TARIFA?

                        Conviene revisar la tarifa contratada al menos una vez al año. Un cambio al PVPC puede ser favorable si:
                        - El precio medio del PVPC en los últimos 12 meses ha sido inferior al precio fijo contratado.
                        - Se tiene o se puede instalar un contador inteligente con telemedida.
                        - El hogar puede concentrar más del 40% del consumo en horas valle.

                        Un cambio a tarifa fija puede ser favorable si:
                        - Los precios del mercado mayorista se encuentran en mínimos históricos y se espera subida.
                        - El hogar tiene consumo concentrado en horas punta que no puede desplazarse.
                        - Se valora la estabilidad en la factura por encima del ahorro potencial.

                        El cambio de comercializadora en España es gratuito y el suministro no se interrumpe. El proceso tarda entre 7 y 21 días hábiles.
                        """,
                        null, null
                ),
                new IngestDocumentCommand(
                        UUID.fromString("a1b2c3d4-0004-0000-0000-000000000000"),
                        DocType.CNMC_CIRCULAR,
                        "Metodología de Cálculo de Peajes de Acceso a la Red",
                        "CNMC — Marco regulatorio de peajes de acceso y cargos del sistema eléctrico",
                        """
                        Los peajes de acceso a las redes de transporte y distribución de electricidad son las tarifas reguladas que los consumidores pagan por el uso de las infraestructuras eléctricas. Son aprobados por la CNMC (Comisión Nacional de Mercados y la Competencia) y revisados periódicamente.

                        COMPONENTES DE LOS PEAJES DE ACCESO

                        Los peajes de acceso tienen dos componentes principales:

                        1. Peaje de acceso de potencia (€/kW día): Remunera la disponibilidad de la red para atender la demanda máxima del consumidor. Se factura por la potencia contratada independientemente del consumo real. En la tarifa 2.0TD existen dos peajes de potencia: P1 (periodo punta) y P2 (periodo valle).

                        2. Peaje de acceso de energía (€/kWh): Remunera el uso de la red durante el consumo efectivo de electricidad. Se factura por los kWh consumidos en cada periodo horario. Los precios son distintos para punta, llano y valle.

                        CARGOS DEL SISTEMA ELÉCTRICO

                        Además de los peajes de acceso, la factura incluye los cargos del sistema eléctrico, que financian:
                        - Primas al régimen especial (energías renovables, cogeneración, residuos).
                        - Costes de los territorios no peninsulares (Canarias, Baleares, Ceuta y Melilla).
                        - Anualidades del déficit tarifario acumulado de años anteriores.
                        - Otros costes del sistema establecidos en la normativa.

                        METODOLOGÍA DE REVISIÓN

                        La CNMC aprueba los peajes y cargos para cada periodo regulatorio mediante circular. Los criterios de revisión incluyen:
                        - Evolución de los costes de transporte y distribución reconocidos.
                        - Variación de la demanda y el número de puntos de suministro.
                        - Recuperación de inversiones en infraestructuras reguladas.
                        - Garantía de ingresos suficientes para los operadores de red.

                        Los peajes se publican en el BOE y en la web de la CNMC. El consumidor puede consultar los peajes aplicables a su contrato en función de su nivel de tensión y potencia contratada.

                        IMPACTO EN LA FACTURA

                        Los peajes de acceso y cargos del sistema suponen típicamente entre el 40% y el 55% del total de la factura eléctrica de un consumidor doméstico (excluidos impuestos). El resto corresponde al coste de la energía en el mercado mayorista y el margen de la comercializadora.

                        RECLAMACIONES Y CONSULTAS

                        El consumidor que considere que los peajes aplicados en su factura son incorrectos puede:
                        - Solicitar aclaraciones a su comercializadora.
                        - Reclamar ante la CNMC a través de su portal de reclamaciones.
                        - Consultar los peajes publicados oficialmente para verificar la correcta aplicación.
                        """,
                        null, null
                ),
                new IngestDocumentCommand(
                        UUID.fromString("a1b2c3d4-0005-0000-0000-000000000000"),
                        DocType.GENERAL,
                        "Cómo Leer y Entender tu Factura de Electricidad",
                        "BillMind / Guía práctica para el consumidor",
                        """
                        Entender la factura de electricidad es el primer paso para controlar el gasto energético del hogar. A continuación se explica cada sección y concepto que aparece en una factura doméstica española estándar.

                        1. DATOS DEL CONTRATO
                        En la parte superior de la factura aparecen:
                        - CUPS: El código único de tu punto de suministro. Siempre empieza por 'ES'. Necesitarás este dato para cambiar de comercializadora.
                        - Potencia contratada: Los kW que tienes contratados. En la tarifa 2.0TD aparecen dos valores: P1 (punta) y P2 (valle).
                        - Tarifa de acceso: Indica el tipo de tarifa contratada (2.0TD, 3.0TD, etc.).
                        - Periodo de facturación: Fechas de inicio y fin del periodo que se factura, normalmente entre 28 y 35 días.

                        2. DESGLOSE DE LA FACTURA

                        Término de potencia: Se calcula así: potencia contratada (kW) × precio del kW/día × número de días del periodo. Es el cargo fijo por tener el suministro disponible, independientemente de si consumes o no. Si pagas por demasiada potencia, reducirla es la mejora más inmediata de ahorro.

                        Término de energía: Se calcula así: kWh consumidos × precio del kWh por periodo horario. En tarifas con discriminación horaria, aparecen los kWh consumidos en cada franja (punta, llano, valle) con su precio correspondiente.

                        Alquiler de equipos: Coste mensual del contador si es de alquiler. Con los contadores inteligentes (telemedida) suele ser un cargo pequeño pero fijo.

                        3. IMPUESTOS
                        Impuesto Especial sobre la Electricidad (IEE): Se aplica sobre la suma del término de potencia y el término de energía. Tipo: 5,11269632% (puede estar reducido temporalmente por disposición gubernamental).
                        IVA (21%): Se aplica sobre el total de los conceptos anteriores incluyendo el IEE. El tipo puede haber sido reducido al 10% o 5% temporalmente por medidas anticrisis.

                        4. LECTURA DEL CONTADOR
                        La factura indica la lectura inicial y final del contador en kWh. La diferencia es el consumo del periodo. Verifica siempre que las lecturas son reales (no estimadas). Si el contador es de telemedida, las lecturas deberían ser siempre reales.

                        5. SEÑALES DE ALERTA EN LA FACTURA
                        - Potencia contratada muy superior al máximo consumo simultáneo registrado: considera reducirla.
                        - Consumo en horas punta muy elevado con discriminación horaria: revisa si puedes desplazar cargas.
                        - Lecturas estimadas repetidas: solicita la revisión del contador o verifica su accesibilidad.
                        - Precio por kWh muy superior al mercado actual: compara con otras ofertas disponibles.

                        6. CÓMO COMPARAR FACTURAS
                        Para comparar facturas de distintos periodos o comercializadoras:
                        - Usa el precio total en €/kWh efectivo (divide el total de la factura entre los kWh consumidos).
                        - Separa el coste fijo (potencia) del variable (energía) para identificar dónde está el mayor gasto.
                        - Ten en cuenta que la duración del periodo de facturación varía: normaliza siempre a €/día o €/mes para comparaciones justas.

                        7. DERECHOS DEL CONSUMIDOR
                        - Cambio gratuito de comercializadora sin coste ni interrupción del suministro.
                        - Derecho a recibir facturas por medición real al menos una vez al año.
                        - Acceso gratuito a los datos de consumo horario a través del portal de tu distribuidora.
                        - Reclamación gratuita ante la CNMC si consideras que la factura es incorrecta.
                        """,
                        null, null
                )
                ,
                new IngestDocumentCommand(
                        UUID.fromString("a1b2c3d4-0006-0000-0000-000000000000"),
                        DocType.GENERAL,
                        "Preguntas Frecuentes sobre Facturas de Luz",
                        "BillMind / FAQ consumidor",
                        """
                        ¿Por qué me ha subido tanto la factura este mes?
                        Las facturas pueden dispararse por varias razones: un aumento del precio de la energía en el mercado mayorista (especialmente en PVPC), un periodo de facturación más largo de lo habitual, un consumo mayor por frío o calor extremo, o un error en la lectura del contador. Revisa el número de días facturados y si las lecturas son reales o estimadas.

                        ¿Me están cobrando de más? ¿Cómo lo sé?
                        Compara el precio por kWh efectivo dividiendo el total de la factura entre los kWh consumidos. Si ese precio es muy superior al mercado actual, es posible que tengas una tarifa cara o que haya un error. Puedes reclamar a tu comercializadora o a la CNMC si sospechas un cobro incorrecto.

                        ¿Qué hago si la factura me parece rara o incorrecta?
                        Primero contacta con tu comercializadora y pide explicación del desglose. Si no te convencen, puedes presentar una reclamación formal ante la CNMC de forma gratuita a través de su portal web. Guarda siempre una copia de tus facturas.

                        ¿Por qué pago aunque no consuma nada de luz?
                        La factura tiene una parte fija (término de potencia) que pagas por tener el suministro disponible, independientemente de si enciendes algo o no. Es como el abono de una autopista: pagas por poder usarla. Si llevas mucho tiempo sin usar el suministro, considera reducir la potencia contratada.

                        ¿Merece la pena poner la lavadora de noche?
                        Sí, si tienes discriminación horaria (tarifa 2.0TD). Por la noche, en periodo valle, el precio de la energía es significativamente más barato. Lo mismo aplica los fines de semana y festivos nacionales. El lavavajillas, la secadora y la carga del coche eléctrico son los electrodomésticos donde más se nota el ahorro.

                        ¿Qué es eso del CUPS que pone en la factura?
                        Es el código que identifica tu punto de suministro, como el DNI de tu conexión a la red. Siempre empieza por ES y tiene 20 o 22 caracteres. Lo necesitarás si quieres cambiar de comercializadora.

                        ¿Puedo cambiarme de compañía de luz fácilmente?
                        Sí, el cambio es gratuito y el suministro no se interrumpe en ningún momento. Tardas entre 7 y 21 días hábiles. Solo necesitas tu CUPS y aceptar el nuevo contrato.

                        ¿Qué diferencia hay entre la luz de mercado libre y el PVPC?
                        El PVPC es una tarifa regulada por el Gobierno con precio variable hora a hora según el mercado. El mercado libre ofrece precios fijos o indexados acordados con la comercializadora. El PVPC puede ser más barato si consumes en horas baratas; el mercado libre da más estabilidad en la factura.

                        Mi factura tiene lecturas estimadas, ¿es normal?
                        No debería ocurrir con frecuencia si tienes contador inteligente (telemedida), ya que envía lecturas reales automáticamente. Si ves estimaciones repetidas, pide a tu distribuidora que revise el contador o que verifique si hay problemas de comunicación con el telemedidor.

                        ¿Por qué hay tantos impuestos en la factura?
                        Además del coste de la energía, pagas peajes de red (transporte y distribución), cargos del sistema (financian renovables y costes insulares), el Impuesto Especial sobre la Electricidad (5,11%) y el IVA (21%, aunque puede estar reducido temporalmente). Entre peajes, cargos e impuestos pueden suponer más del 50% de tu factura.
                        """,
                        null, null
                )
        );
    }
}