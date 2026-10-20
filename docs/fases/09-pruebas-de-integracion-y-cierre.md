# Fase 09 — Pruebas de integración y cierre

## Objetivo

Validar el comportamiento integrado de los flujos y cerrar el trabajo solo cuando contrato, persistencia, autorización y reglas de negocio estén verificadas.

## Dependencias

- Fases 01–08 implementadas y con pruebas enfocadas; resolver o listar explícitamente todo bloqueo conocido.
- PostgreSQL local disponible para las pruebas HTTP/de integración. Según `proyecto.md`, levantarlo con `docker compose up -d postgres` cuando sea necesario.

## Alcance

- Ejecutar pruebas unitarias/de validación sin base de datos y pruebas de integración HTTP con PostgreSQL.
- Verificar flujos completos: registro/autenticación, perfil, pedido, recogida/verificación, liquidación, saldo/retiro y operaciones administrativas relevantes.
- Revisar compatibilidad exacta de rutas, JSON, validaciones, roles, estados y códigos HTTP con el contrato confirmado.
- Revisar transacciones y concurrencia: capacidad de franja, finalización/liquidación y transferencias en lote.
- Revisar integridad de consultas, paginación, N+1, restricciones de `V1`, manejo de errores y ausencia de secretos en logs.
- Ejecutar las comprobaciones del repositorio y revisar el diff final; no declarar pruebas aprobadas si no se ejecutaron.

## Pruebas y comandos

- Ejecutar primero las pruebas focalizadas correspondientes a los flujos afectados.
- Ejecutar `.\gradlew.bat test` desde la raíz del repositorio para la suite de pruebas.
- Ejecutar pruebas HTTP de integración solo con PostgreSQL disponible; comprobar también limpieza/aislamiento de los datos de prueba.
- Ejecutar `.\gradlew.bat build` si se requiere verificar compilación y empaquetado final.

## Criterios de cierre

- Las pruebas acordadas pasan; las omitidas o bloqueadas quedan identificadas con causa.
- El contrato JSON/API y los permisos coinciden con las fuentes confirmadas.
- Las operaciones multitabla críticas son transaccionales y las operaciones concurrentes están protegidas.
- No se incorporaron tablas/migraciones no autorizadas ni se alteraron cambios preexistentes ajenos.
- El resumen final lista archivos cambiados, pruebas realmente ejecutadas y limitaciones pendientes.
