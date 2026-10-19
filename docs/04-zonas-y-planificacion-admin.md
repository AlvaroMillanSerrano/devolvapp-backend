# Fase 04 — Zonas y planificación admin

## Objetivo

Dar soporte a la configuración administrativa de zonas, cobertura postal, días de recogida, franjas y asignaciones de recolectores respetando el historial y las restricciones del dominio.

## Dependencias

- Fases 01–03: contrato, roles y catálogos/parámetros confirmados.
- Revisar `zone`, `zip_code`, `zone_day_collector`, `time_slot` y `zone_change_request` en `V1`.

## Alcance

### Endpoints asignados a esta fase

| # | Método | Endpoint |
|---:|---|---|
| 51 | GET | `/api/admin/codigos-postales` |
| 52 | GET | `/api/admin/zonas` |
| 54 | POST | `/api/admin/zonas` |
| 55 | PUT | `/api/admin/zonas/{id}` |
| 56 | DELETE | `/api/admin/zonas/{id}` |
| 57 | PUT | `/api/admin/zonas/{id}/dias/{diaSemana}/recolector` |
| 58 | POST | `/api/admin/zonas/{id}/franjas` |
| 59 | PUT | `/api/admin/franjas/{id}` |
| 60 | DELETE | `/api/admin/franjas/{id}` |
| 61 | GET | `/api/admin/solicitudes-zona` |
| 62 | POST | `/api/admin/solicitudes-zona/{id}/resolver` |

El listado de recolectores (**#53**) y la gestión de usuarios/recolectores (**#63–69**) son responsabilidad de Dani en la fase 08. Las solicitudes del propio recolector (**#34–37**) son responsabilidad de Álvaro en la fase 08.

- Recursos `/api/admin` relativos a zonas, códigos postales, franjas, días y asignaciones, con sus rutas y operaciones exactas tomadas del contrato.
- Validar formato y pertenencia de CP; evitar que un CP activo pertenezca a dos zonas.
- Evitar borrar zonas con recogidas históricas; usar borrado lógico de franjas (`active=false`).
- Respetar las restricciones de asignación de recolectores por día y reportar conflicto `RECOLECTOR_OCUPADO` cuando corresponda.
- Aplicar permisos de administrador y reglas para solicitudes de cambio de zona en coordinación con la fase 08.

## Componentes a revisar

Contrato admin; entidades/repositorios/consultas para zonas y planificación; validación; autorización; errores comunes; referencias históricas desde recogidas.

## Pruebas y aceptación

- Probar altas/consultas/actualizaciones y errores de datos duplicados o inválidos según contrato.
- Probar CP sin servicio, asignación a zona y prevención de duplicidad entre zonas.
- Probar borrado lógico de franja y protección de zonas referenciadas históricamente.
- Probar asignación incompatible de recolectores, permisos no-admin y persistencia en PostgreSQL.
- Mantener consultas paginadas estables si el contrato define listados paginados.

## Consideración

No introducir tablas o columnas que no existan en `V1`; documentar cualquier discrepancia antes de implementar.
