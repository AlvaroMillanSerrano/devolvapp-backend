# Fase 08 — Operación, impacto y gestión

## Objetivo

Completar las capacidades transversales y administrativas restantes una vez disponibles los flujos principales de usuarios, zonas, pedidos, recogidas y pagos.

## Dependencias

- Fases 01–07 para contratos, autorización y datos funcionales compartidos.
- Confirmar contra el contrato las operaciones exactas y si alguna parte ya fue cubierta en fases previas.

## Alcance

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
