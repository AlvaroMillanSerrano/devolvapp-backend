# Fase 08 — Operación, impacto y gestión

## Objetivo

Completar las capacidades transversales y administrativas restantes una vez disponibles los flujos principales de usuarios, zonas, pedidos, recogidas y pagos.

## Dependencias

- Fases 01–07 para contratos, autorización y datos funcionales compartidos.
- El inventario de rutas está en `web/docs/API_ENDPOINTS.md`; acordar los detalles funcionales que falten y comprobar si alguna parte ya fue cubierta en fases previas.

## Alcance

### Endpoints asignados a esta fase

| # | Responsable | Método | Endpoint |
|---:|---|---|---|
| 21 | Javier | GET | `/api/ciudadano/impacto` |
| 31 | Álvaro | GET | `/api/recolector/ruta` |
| 32 | Álvaro | GET | `/api/recolector/historial` |
| 33 | Álvaro | GET | `/api/recolector/impacto` |
| 34 | Álvaro | GET | `/api/recolector/preferencias` |
| 35 | Álvaro | PUT | `/api/recolector/preferencias/zona` |
| 36 | Álvaro | POST | `/api/recolector/solicitudes-zona` |
| 37 | Álvaro | POST | `/api/recolector/solicitudes-zona/{id}/cancelar` |
| 38 | Dani coordina; implementación por acordar | GET | `/api/admin/pendientes` |
| 39 | Dani coordina; implementación por acordar | GET | `/api/admin/indicadores` |
| 40 | Álvaro | GET | `/api/admin/incidencias` |
| 41 | Álvaro | GET | `/api/admin/incidencias/{id}` |
| 42 | Álvaro | PATCH | `/api/admin/incidencias/{id}` |
| 43 | Javier | GET | `/api/admin/recogidas` |
| 44 | Javier | GET | `/api/admin/recogidas/{id}` |
| 45 | Javier; reglas con Jorge | PUT | `/api/admin/recogidas/{id}/recolector` |
| 46 | Javier | POST | `/api/admin/recogidas/{id}/cancelar` |
| 53 | Dani | GET | `/api/admin/recolectores` |
| 63 | Dani | GET | `/api/admin/usuarios` |
| 64 | Dani | GET | `/api/admin/usuarios/{id}` |
| 65 | Dani | PUT | `/api/admin/usuarios/{id}` |
| 66 | Dani | POST | `/api/admin/usuarios/{id}/suspender` |
| 67 | Dani | POST | `/api/admin/usuarios/{id}/reactivar` |
| 68 | Dani | POST | `/api/admin/recolectores` |
| 69 | Dani | POST | `/api/admin/usuarios/{id}/password-provisional` |

Las rutas administrativas de zonas y solicitudes de zona (**#51–52, #54–62**) se implementan en la fase 04 por Jorge; transferencias (**#47–50**) en la fase 07 por Javier. Los endpoints #38–39 son agregaciones transversales: Dani coordina el desglose por fuentes de datos y acuerda los propietarios antes de asignar implementación.

- Indicadores y pendientes de administración.
- Impacto del ciudadano/recolector y consultas de rutas e historial.
- Preferencias de recolector y solicitudes de cambio de zona; resolverlas con controles de estado y autorización.
- Gestión administrativa de usuarios, recolectores, incidencias, asignaciones y recursos no cubiertos anteriormente.
- Dividir administración en controladores por recurso; evitar un controlador monolítico.
- Al crear recolectores, generar contraseña provisional aleatoria segura, almacenar solo BCrypt, marcar `must_change_password=true` y mostrarla únicamente en la respuesta autorizada.
- Al suspender usuarios, aplicar restricciones contractuales y revocar tokens; no permitir auto-suspensión si el contrato lo prohíbe.

## Componentes a revisar

Controladores y servicios admin/recolector; servicios compartidos `ImpactService`, `ParameterService`, `CodeGenerator` y mappers; consultas paginadas; seguridad y auditoría de estados.

## Pruebas y aceptación

- Cubrir roles y propiedad de datos para cada flujo; no confiar en IDs enviados por cliente.
- Probar cambios y transiciones de solicitud de zona, conflictos con asignaciones/recogidas y reglas para eliminación.
- Probar suspensión, revocación de tokens, cambio obligatorio de contraseña y gestión de credenciales sin exponer secretos en logs o BD.
- Validar cálculos de impacto y agregaciones contra los datos y parámetros acordados.
- Verificar `PageResponse<T>` (`content`, `totalElements`, `totalPages`, `number`, `size`) donde el contrato requiera paginación.

## Control de alcance

Usar el contrato confirmado para determinar qué endpoints corresponden aquí y evitar duplicar capacidades definidas en las fases 04–07.
