# Fase 07 — Monedero y transferencias

## Objetivo

Implementar el saldo y los movimientos del ciudadano, además de la solicitud y procesamiento seguro de retiradas y remesas.

## Dependencias

- Fase 06: liquidaciones verificadas disponibles.
- Fases 01–03: contrato, errores y parámetros confirmados.
- Revisar `settlement`, `withdrawal`, `withdrawal_history` y `remittance` en `V1`.

## Alcance

- Recursos `/api/ciudadano` de monedero, movimientos y solicitud de retirada.
- Saldo calculado como suma de `settlement.citizen_share` menos retiradas cuyo estado no sea `FALLIDA`.
- Validar IBAN mediante checksum mod-97, importe positivo y saldo suficiente; insuficiencia como `422 SALDO_INSUFICIENTE`.
- Recursos `/api/admin` para procesamiento de transferencias y pagos por lote, según contrato.
- Bloquear la retirada y comprobar estado `PENDIENTE` antes de procesarla (`409 YA_PROCESADA`).
- En lote, validar y bloquear todas las retiradas antes de escribir para evitar procesamiento parcial; registrar historial y asociar remesa conforme al contrato.
- No registrar IBAN completo ni información bancaria sensible en logs.

## Componentes a revisar

Servicios de saldo/pagos existentes o compartidos (`WalletService`, `SettlementService`, `CodeGenerator`), entidades/repositorios de retirada, remesa e historial, autorización, consultas y errores.

## Pruebas y aceptación

- Probar saldo derivado con liquidaciones y retiradas en estados relevantes.
- Cubrir checksum IBAN válido/inválido, importe no positivo, saldo insuficiente y usuario ajeno.
- Probar dos procesos concurrentes/repetidos sobre una retirada y la respuesta para una ya procesada.
- Probar lote completamente válido y lote con una entrada inválida; no debe quedar una actualización parcial.
- Verificar historial, referencias de transferencia y montos con `BigDecimal`.
