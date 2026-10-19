# Fase 03 — Catálogos y parámetros

## Objetivo

Exponer los parámetros y catálogos compartidos requeridos por el contrato y asegurar que los módulos de negocio consuman una fuente coherente.

## Dependencias

- Fases 01 y 02: contrato confirmado y autenticación/autorización base disponible.
- Revisar filas iniciales y tablas `app_parameter`, `container_type` e `incident_type` de `V1__create_tables.sql`.

## Alcance

### Endpoints asignados a esta fase

| # | Método | Endpoint |
|---:|---|---|
| 12 | GET | `/api/config/parametros` |
| 13 | GET | `/api/catalogo/tipos-envase` |
| 14 | GET | `/api/catalogo/tipos-incidencia` |
| 15 | GET | `/api/catalogo/codigos-postales` |

- `/api/config/parametros`.
- `/api/catalogo/tipos-envase`, `/api/catalogo/tipos-incidencia` y `/api/catalogo/codigos-postales`.
- Definir el servicio compartido de parámetros (`ParameterService`) si el patrón existente lo requiere; sustituir lecturas duplicadas cuando sea parte directa de este alcance.
- Alinear códigos de catálogo con los nombres de enum y códigos SQL; por ejemplo, `ContainerType.name()` debe corresponder al código persistido.
- Validar disponibilidad de datos de inicio sin inventar parámetros, códigos postales ni credenciales.

## Componentes a revisar

Contrato y permisos de cada recurso; entidades/repositorios de catálogos; consultas SQL existentes; servicios que leen `app_parameter`; configuración de carga de datos y pruebas.

## Pruebas y aceptación

- Verificar formato, contenido, acceso y errores definidos por el contrato.
- Probar catálogos con datos presentes y comportamiento acordado ante datos ausentes o configuración inválida; no devolver valores de éxito ficticios.
- Verificar consistencia entre códigos, enums y filas de `V1`.
- Confirmar que los módulos funcionales pueden reutilizar parámetros sin duplicar reglas o valores hardcodeados.

## Fuera de alcance

Cambios de esquema o datos de catálogo no respaldados por `V1` o por una decisión explícita. No crear migraciones adicionales.
