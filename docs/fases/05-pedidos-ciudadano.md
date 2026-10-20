# Fase 05 — Pedidos ciudadano

## Objetivo

Completar disponibilidad, creación, consulta y cancelación de pedidos de recogida para el ciudadano autenticado.

## Dependencias

- Fases 01–04: contrato, autenticación, parámetros/catálogos y planificación de zonas/franjas confirmados.
- El servicio actual de creación contiene validaciones y persistencia parciales; revisarlo y extenderlo sin duplicar comportamiento.

## Alcance

- Operaciones `/api/ciudadano` de pedidos: listar, detalle, disponibilidad, crear y cancelar, conforme al contrato confirmado.
- Vincular el pedido al ciudadano autenticado, no a un `citizenId` confiado desde la petición.
- Validar cobertura de la dirección, zona, CP, fecha, día, franja activa y capacidad.
- Copiar los datos de dirección y horario a la recogida como instantánea inmutable.
- Validar límite de bolsas, tipos y cantidades; guardar contenidos de cada bolsa conforme al modelo `bag`/`bag_content`.
- Contar como ocupados los pedidos no cancelados y proteger la capacidad ante concurrencia.
- Permitir cancelación solo si el estado es `PENDIENTE`; respetar `409 ESTADO_NO_CANCELABLE`.

## Componentes a revisar

`CreateCollectionRequest`, DTO de respuesta, `CollectionOrderController`, `CollectionOrderService`, entidades de recogida/bolsa/contenido, repositorios y consultas de disponibilidad/capacidad.

## Pruebas y aceptación

- Cubrir creación válida, varias bolsas y contenidos mixtos; zona/CP no atendido; fecha o franja incompatibles; capacidad agotada; exceso de bolsas o cantidades.
- Probar filtrado por usuario autenticado, detalle de otro usuario, listados, cancelación permitida y estado no cancelable.
- Probar concurrencia para evitar sobreasignar la capacidad de una franja.
- Verificar persistencia y que editar el domicilio del usuario no modifique la instantánea del pedido ya creado.

## Restricciones

No inventar payloads ni respuestas si el contrato API sigue sin estar disponible. Usar errores, códigos y campos acordados.
