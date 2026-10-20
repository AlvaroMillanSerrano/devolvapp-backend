# Fase 06 — Recogidas, bolsas, incidencias y liquidación

## Objetivo

Implementar el ciclo operativo del recolector y cerrar cada recogida con bolsas verificadas, incidencias e impacto económico consistentes.

## Dependencias

- Fase 05: pedidos persistidos y asignación disponible.
- Fases 01–03: contrato, seguridad y catálogos de envases/incidencias confirmados.
- Revisar `collection`, `bag`, `bag_content`, `settlement`, `incident` e `incident_history` en `V1`.

## Alcance

- Recursos `/api/recolector` de recogidas: lista, detalle, inicio, verificación de bolsas, finalización e incidencias.
- Autorizar exclusivamente al recolector asignado para operar sobre la recogida.
- Transiciones: iniciar solo `PENDIENTE`; completar solo `EN_CAMINO`.
- Verificar todas las bolsas antes de completar: `ACEPTADA` cuenta la cantidad enviada; `NO_ENCONTRADA` cuenta cero; `DISCREPANCIA` exige cantidad válida no superior a la enviada.
- Registrar las incidencias y entradas de historial exigidas por cada transición.
- Completar la recogida y crear una única liquidación en una transacción; preservar idempotencia y evitar liquidaciones duplicadas.

## Componentes a revisar

Controladores/servicios/repositorios de recogidas, mapeo de bolsas y contenidos, catálogos, entidades de liquidación e incidencias, consultas de autorización y manejo de errores.

## Pruebas y aceptación

- Cubrir inicio/completado permitidos y transiciones inválidas, así como recolector no asignado.
- Probar cada resultado de bolsa, cantidades inválidas, bolsa sin verificar y datos de envase no configurados.
- Verificar la escritura de historial de incidencias y reglas de resolución del esquema.
- Probar rollback ante fallo durante finalización/liquidación y concurrencia/reintento sin liquidación duplicada.
- Confirmar que el resumen económico/CO₂ respeta parámetros y reglas aprobados; usar `BigDecimal` para dinero.
