# Copilot Instructions — DevolvApp Backend

## Proyecto y fuente de verdad
Backend `es.devolvapp.backend` con Spring Boot 4, Java, JPA, Flyway, PostgreSQL, Lombok, MapStruct, Spring Security, JWT y Redis. Implementa los 69 endpoints definidos en `web/docs/API_ENDPOINTS.md`.

Antes de cambiar código, inspecciona el contrato, las entidades, repositorios, servicios, controladores, migraciones y pruebas relacionadas. La única migración de base de datos válida es `V1__create_tables.sql`; úsala como referencia del modelo de datos. No inventes rutas, campos, estados ni respuestas.

## Reglas de desarrollo
- Respeta rutas, JSON, códigos HTTP, nombres de campos y reglas de `web/docs/API_ENDPOINTS.md`.
- Mantén capas separadas: `controller` HTTP, `service` negocio/transacciones, `repository` datos, `entity` persistencia, `dto` contrato y `mapper(s)` MapStruct.
- Reutiliza y adapta el código existente; evita duplicados y refactors ajenos a la tarea.
- No devuelvas entidades JPA directamente: usa DTO.
- Valida entradas con Jakarta Validation y devuelve errores de campo con los nombres que espera el contrato.
- Usa inyección por constructor, consultas parametrizadas, paginación estable y evita N+1 mediante proyecciones/consultas adecuadas.
- Usa `BigDecimal` para dinero y tipos de fecha/hora coherentes con el esquema y el JSON existente.
- Usa `@Transactional` cuando una operación escriba varias tablas. Protege operaciones concurrentes críticas con bloqueos y restricciones de BD.
- No hardcodees datos sensibles, no registres contraseñas/tokens/IBAN completos y no ocultes errores con respuestas de éxito falsas.
- No afirmes que una prueba pasa si no la has ejecutado.

## Errores de API
Formato común:
`{ "status": 400, "code": "VALIDATION_ERROR", "message": "...", "fieldErrors": { "campo": "detalle" } }`

Usa `ApiError`, `ApiException` y `@RestControllerAdvice` (`ApiExceptionHandler`). Traduce errores de validación a `400 VALIDATION_ERROR`, autenticación a `401`, permisos a `403 FORBIDDEN`, conflictos a `409`, reglas no procesables a `422` y tokens inválidos/expirados según contrato. No uses el formato antiguo `{ error, details }`.

Conserva los códigos de negocio definidos por el contrato, entre ellos: `BAD_CREDENTIALS`, `ACCOUNT_SUSPENDED`, `TOO_MANY_ATTEMPTS`, `DUPLICATE_EMAIL`, `DUPLICATE_DNI`, `DUPLICATE_TELEFONO`, `TOKEN_EXPIRED`, `TOKEN_USED`, `NO_PERMITIDO`, `FRANJA_COMPLETA`, `ZONA_SIN_SERVICIO`, `ESTADO_NO_CANCELABLE`, `SALDO_INSUFICIENTE`, `BOLSAS_SIN_VERIFICAR`, `ZONA_YA_ELEGIDA`, `SOLICITUD_PENDIENTE`, `YA_PROCESADA`, `ZONA_CON_RECOGIDAS`, `RECOLECTOR_OCUPADO`.

## Seguridad
Implementa/verifica `JwtService`, `JwtAuthFilter`, `AuthUser`, `LoginAttemptService` y `SecurityConfig`. JWT stateless; principal con `id`, `email`, `roles`; contraseñas con BCrypt; intentos fallidos con Redis si está configurado; CORS con orígenes explícitos (no `*` con credenciales).

- Públicos según contrato: login, registro y recuperación/restablecimiento de contraseña.
- `/api/ciudadano/**` requiere `CIUDADANO`; `/api/recolector/**`, `RECOLECTOR`; `/api/admin/**`, `ADMIN`; resto autenticado salvo excepción documentada.
- Obtén el usuario desde `@AuthenticationPrincipal AuthUser me`, no de IDs enviados por el cliente.
- Al registrar, asigna `CIUDADANO`; nunca permitas autoasignación de roles privilegiados.
- Guarda hashes de refresh/reset tokens, aplica vencimiento y uso único, y revoca sesiones al cambiar contraseña o suspender usuario.
- Respuestas de recuperación deben evitar revelar si un email existe.

## Modelo y persistencia
Alinea JPA exclusivamente con `V1__create_tables.sql`. Revisa especialmente `User`: `d_user` usa `first_name`, `last_name`, `password_hash`, `status`, `must_change_password`, `preferred_zone_id`; roles en `user_role` y dirección en `address`. No supongas que saldo o rol son columnas de `d_user`.

Entidades/tablas principales: `User/d_user`, `Address/address`, `RefreshToken/refresh_token`, `PasswordResetToken/password_reset_token`, `Zone/zone`, `ZipCode/zip_code`, `ZoneDayCollector/zone_day_collector`, `TimeSlot/time_slot`, `ZoneChangeRequest/zone_change_request`, `CollectionOrder/collection`, `Bag/bag`, `BagContent/bag_content`, `ContainerCatalog/container_type`, `Settlement/settlement`, `IncidentType/incident_type`, `Incident/incident`, `IncidentHistory/incident_history`, `AppParameter/app_parameter`, `Withdrawal/withdrawal`, `WithdrawalHistory/withdrawal_history`, `Remittance/remittance`.

Estados de referencia (confirma esquema/contrato): usuario `ACTIVA/SUSPENDIDA`; recogida `PENDIENTE/EN_CAMINO/COMPLETADA/CANCELADA`; bolsa `ACEPTADA/DISCREPANCIA/NO_ENCONTRADA`; incidencia `ABIERTA/EN_REVISION/RESUELTA`; retirada `PENDIENTE/COMPLETADA/FALLIDA`; solicitud de zona `PENDIENTE/APROBADA/RECHAZADA/CANCELADA`.

Usa `PageResponse<T>` con `content`, `totalElements`, `totalPages`, `number`, `size`; no expongas directamente `Page`. Reutiliza DTO comunes (`UsuarioResponse`, `DireccionDto`, `ZonaRef`, `PersonaRef`, `BolsaDto`, `IncidenciaResumenDto`, `LiquidacionDto`, `ImpactoResponse`) y servicios compartidos cuando proceda: `ParameterService`, `CodeGenerator`, `WalletService`, `ImpactService`, `SettlementService` y mappers MapStruct.

## Reglas críticas de negocio
- **Auth/perfil:** unicidad de email/DNI/teléfono; comprobar código postal; nunca modificar la dirección histórica copiada en `collection.addr_*` al editar perfil.
- **Pedidos:** filtrar siempre por ciudadano autenticado; validar zona con servicio, franja activa/zona/día, capacidad, máximo de bolsas y tipo/cantidad. Contar ocupadas las no canceladas. Bloquear la comprobación de capacidad frente a concurrencia. Solo cancelar pedidos `PENDIENTE` (`409 ESTADO_NO_CANCELABLE`).
- **Recogidas:** solo opera el recolector asignado. Iniciar solo `PENDIENTE`, completar solo `EN_CAMINO`. Verificar todas las bolsas antes de completar. `ACEPTADA` = cantidad enviada; `NO_ENCONTRADA` = 0; `DISCREPANCIA` requiere cantidad válida no superior a la enviada. Registrar incidencias e historial cuando corresponda. Completar y crear `Settlement` en una transacción; impedir liquidaciones duplicadas.
- **Monedero:** saldo = suma de `settlement.citizen_share` menos retiradas cuyo estado no sea `FALLIDA`. IBAN validado con checksum mod-97; importe positivo y saldo suficiente (`422 SALDO_INSUFICIENTE`).
- **Transferencias:** bloquear retirada y verificar `PENDIENTE` antes de procesar (`409 YA_PROCESADA`). En lote, validar/bloquear todas antes de escribir para evitar resultados parciales. Registrar historial.
- **Incidencias:** validar tipo; registrar cada transición en `incident_history`; exigir resolución al pasar a `RESUELTA` si lo exige el esquema.
- **Zonas/franjas:** un CP no puede pertenecer a dos zonas; evitar borrar zonas con recogidas históricas; franjas con borrado lógico (`active=false`); evitar asignaciones incompatibles de recolectores (`409 RECOLECTOR_OCUPADO`).
- **Usuarios admin:** no permitir auto-suspensión si el contrato lo prohíbe; revocar tokens al suspender; crear recolectores con contraseña aleatoria segura, BCrypt y `must_change_password=true`. Mostrar la contraseña provisional solo en la respuesta autorizada, nunca en logs ni BD en claro.

## Módulos y endpoints
Usa `web/docs/API_ENDPOINTS.md` para los detalles exactos de payloads, filtros, permisos y respuestas.

- **Auth `/api/auth`:** login, logout, me, registro, recuperar contraseña, validar token y restablecer contraseña.
- **Perfil `/api/perfil`:** consultar/actualizar perfil, cambiar contraseña y contraseña inicial.
- **Config/catálogos:** `/api/config/parametros`, `/api/catalogo/tipos-envase`, `/api/catalogo/tipos-incidencia`, `/api/catalogo/codigos-postales`.
- **Ciudadano `/api/ciudadano`:** pedidos (listar/detalle/disponibilidad/crear/cancelar), impacto, monedero, movimientos y retiradas.
- **Recolector `/api/recolector`:** recogidas (listar/detalle/iniciar/verificar/completar/incidencias), ruta, historial, impacto, preferencias y solicitudes de zona.
- **Admin `/api/admin`:** pendientes/indicadores, incidencias, recogidas/asignaciones, transferencias/pagos por lote, zonas/CP/franjas/días, solicitudes de zona y usuarios/recolectores.

Divide administración en controladores por recurso; evita un único controlador gigante. `POST /api/auth/refresh` es una recomendación de la guía, no lo añadas al contrato sin documentarlo y coordinarlo con el frontend.

## Flyway y base de datos
Mantén únicamente `V1__create_tables.sql` como migración de base de datos y no crees migraciones adicionales salvo petición explícita. Ajusta entidades y repositorios al esquema definido en V1; no uses tablas o columnas que no existan en esa migración. Los datos iniciales previstos incluyen secuencias de códigos, tipos de incidencia, CP de Málaga verificados, parámetros de la nave/importe y admin inicial con contraseña BCrypt segura; no inventes datos ni credenciales. Considera `zone.active` para borrado lógico si existe en V1.

## Orden de implementación
1. Auditar esquema, migraciones, `User`, errores y seguridad JWT.
2. Auth y perfil.
3. Catálogos y parámetros.
4. Zonas, CP, franjas y asignaciones admin.
5. Pedidos y disponibilidad de ciudadano.
6. Recogidas, bolsas, incidencias y liquidación.
7. Monedero y transferencias.
8. Indicadores, impacto, rutas, preferencias, solicitudes de zona y gestión de usuarios.
9. Pruebas de integración completas.

Implementa por bloques pequeños; no intentes hacer todos los endpoints en un solo cambio.

## Pruebas y flujo de trabajo
Para cada tarea:
1. Lee la sección correspondiente del contrato y el código relacionado.
2. Identifica discrepancias entre contrato, SQL y código; no las resuelvas silenciosamente.
3. Implementa el cambio mínimo completo por capas.
4. Añade pruebas de éxito, validación, permisos, errores de negocio y persistencia.
5. Ejecuta las pruebas disponibles (por ejemplo `./gradlew test` o `gradlew.bat test`, tras verificar el wrapper y los scripts).
6. Revisa el diff, migraciones, seguridad, N+1 y compatibilidad del JSON.
7. Resume archivos cambiados, pruebas realmente ejecutadas y pendientes.

Una tarea está terminada cuando cumple el contrato, valida y autoriza correctamente, conserva la integridad transaccional, usa errores acordados y tiene pruebas ejecutadas o limitaciones claramente declaradas.
