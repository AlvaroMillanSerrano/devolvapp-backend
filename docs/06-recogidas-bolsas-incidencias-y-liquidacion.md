# Fase 06 — Recogidas, bolsas, incidencias y liquidación

## Objetivo

Implementar el ciclo operativo del recolector y cerrar cada recogida con bolsas verificadas, incidencias e impacto económico consistentes.

## Dependencias

- Fase 05: pedidos persistidos y asignación disponible.
- Fases 01–03: contrato, seguridad y catálogos de envases/incidencias confirmados.
- Revisar `collection`, `bag`, `bag_content`, `settlement`, `incident` e `incident_history` en `V1`.

## Alcance

### Endpoints asignados a esta fase

| # | Método | Endpoint |
|---:|---|---|
| 25 | GET | `/api/recolector/recogidas` |
| 26 | GET | `/api/recolector/recogidas/{id}` |
| 27 | POST | `/api/recolector/recogidas/{id}/iniciar` |
| 28 | PUT | `/api/recolector/recogidas/{id}/bolsas/{bolsaId}` |
| 29 | POST | `/api/recolector/recogidas/{id}/completar` |
| 30 | POST | `/api/recolector/recogidas/{id}/incidencias` |

La consulta y gestión administrativa de incidencias (**#40–42**) queda en la fase 08 bajo responsabilidad de Álvaro. La administración de recogidas (**#43–46**) queda en la fase 08 bajo responsabilidad de Javier, coordinando con Jorge las reglas de asignación del endpoint #45.

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
