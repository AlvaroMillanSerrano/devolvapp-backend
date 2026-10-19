# Contrato de API REST para Spring Boot

> Documento **generado** con `npm run docs` a partir del JSDoc de `src/api/*Service.js`.
> Cada fila es una llamada real que hace el front cuando `VITE_USE_MOCKS=false`.
> La forma exacta de cada respuesta está en el JSDoc del servicio y en el mock equivalente de `src/mocks/`.

**Convenciones**

- Prefijo `/api`, JSON en `camelCase`, fechas ISO-8601, importes como número decimal en euros.
- Listados paginados con la forma de Spring Data `Page<T>`: `{ content, totalElements, totalPages, number, size }` y parámetros `page` (desde 0) y `size`.
- Autenticación: cabecera `Authorization: Bearer <accessToken>` (ver `docs/SPRING_SECURITY.md`).
- Errores: `{ status, code, message, fieldErrors }` (ver `src/api/ApiError.js`).
- Los endpoints de un rol **nunca** reciben el id del usuario: el backend lo obtiene del token.

Total: **69 endpoints**.

## `authService.js` — rol: Público (salvo `me`/`logout`: autenticado)

| Método | Ruta | HU | Función front | Descripción |
|---|---|---|---|---|
| `POST` | `/api/auth/login` | HU-01.02 | `login` | <br><sub>body: { username (email o DNI/NIE), password } 200: { accessToken, refreshToken, tokenType, expiresIn, usuario: { id, nombre, apellidos, email, roles: ["ROLE_..."], ... } } 401 BAD_CREDENTIALS · 403 ACCOUNT_SUSPENDED · 429 TOO_MANY_ATTEMPTS</sub> |
| `POST` | `/api/auth/logout` | — | `logout` | invalida el refresh token en el servidor |
| `GET` | `/api/auth/me` | — | `me` | datos frescos del usuario autenticado (roles incluidos) |
| `POST` | `/api/auth/registro` | HU-01.01 | `registro` | (crea SIEMPRE una cuenta con ROLE_CIUDADANO)<br><sub>body: { nombre, apellidos, dni, telefono, email, password, direccion: { tipoVia, nombreVia, numero, piso, letra, codigoPostal, ciudad }, aceptaPrivacidad } 201 · 400 VALIDATION_ERROR (fieldErrors) · 409 DUPLICATE_* (fieldErrors)</sub> |
| `POST` | `/api/auth/password/recuperar` | HU-01.04 | `solicitarRecuperacion` | body: { email }<br><sub>Responde 202 SIEMPRE (exista o no el correo) para no revelar cuentas registradas.</sub> |
| `GET` | `/api/auth/password/token/{token}` | HU-01.04 | `validarTokenRecuperacion` | 200 válido · 410 TOKEN_EXPIRED \| TOKEN_USED |
| `POST` | `/api/auth/password/restablecer` | HU-01.04 | `restablecerPassword` | body: { token, password } · el token es de un solo uso |

## `perfilService.js` — rol: Autenticado (cualquier rol)

| Método | Ruta | HU | Función front | Descripción |
|---|---|---|---|---|
| `GET` | `/api/perfil` | — | `get` | mismo formato que el usuario del login (incluye direccion con tipoVia, nombreVia, numero, piso, letra, codigoPostal, ciudad) |
| `PUT` | `/api/perfil` | — | `actualizar` | body: { nombre, apellidos, email, telefono, direccion? (solo ciudadano) }<br><sub>El backend geocodifica la dirección (lat, lng). Los pedidos ya programados mantienen la dirección anterior. 200 → usuario + { avisoZona } · 400 VALIDATION_ERROR (fieldErrors "direccion.codigoPostal") · 409 email/teléfono duplicado</sub> |
| `PUT` | `/api/perfil/password` | — | `cambiarPassword` | body: { actual, nueva } · 400 si la actual no es correcta |
| `PUT` | `/api/perfil/password-inicial` | — | `crearPasswordInicial` | body: { nueva }<br><sub>Solo si usuario.debeCambiarPassword (cuenta creada por administración con contraseña provisional). 200 → usuario con debeCambiarPassword=false · 409 NO_PERMITIDO si ya la cambió</sub> |

## `configService.js` — rol: Autenticado (cualquier rol)

| Método | Ruta | HU | Función front | Descripción |
|---|---|---|---|---|
| `GET` | `/api/config/parametros` | HU-05.02 | `getParametros` | <br><sub>{ economia: { importePorEnvase, porcentajeCiudadano, porcentajeEmpresa, provisional }, pedidos: { maxBolsas }, impacto: { kgCo2PorEnvase: { PET, LATA, ... }, kgCo2PorKmCoche, kgCo2PorArbolAnio } }</sub> |
| `GET` | `/api/catalogo/tipos-envase` | HU-02.01 | `getTiposEnvase` | <br><sub>[{ codigo: 'PET'\|'LATA'\|'BRIK'\|'VIDRIO'\|'MEZCLADO', nombre, icono, capacidadMaximaBolsa }]</sub> |
| `GET` | `/api/catalogo/tipos-incidencia` | HU-06.01 | `getTiposIncidencia` | [{ codigo, nombre }] |
| `GET` | `/api/catalogo/codigos-postales` | HU-07.06 | `getCodigosPostales` | CP del municipio de Málaga [{ cp, barrio, lat, lng }] |

## `ciudadanoService.js` — rol: CIUDADANO

| Método | Ruta | HU | Función front | Descripción |
|---|---|---|---|---|
| `GET` | `/api/ciudadano/pedidos?estado=&page=&size=` | HU-02.03, HU-03.03 | `getPedidos` | <br><sub>estado: PENDIENTE \| EN_CAMINO \| COMPLETADA \| CANCELADA \| ACTIVOS (pendientes + en camino) Page<{ id, codigo, estado, creadoEn, fecha, horaInicio, horaFin, numBolsas, totalEnvases, importeCiudadano, tieneIncidencias }></sub> |
| `GET` | `/api/ciudadano/pedidos/{id}` | HU-02.02, HU-02.03, HU-06.04 | `getPedido` | <br><sub>resumen + { direccion, iniciadoEn, completadoEn, canceladoEn, cancelable, bolsas: [{ id, codigo, tipoEnvase, cantidad, capacidadMaxima, resultado, envasesValidos }], liquidacion: { envasesValidos, importeCiudadano } \| null, incidencias: [{ id, codigo, tipo, estado, creadaEn, resolucion }] }</sub> |
| `GET` | `/api/ciudadano/disponibilidad` | HU-03.01 | `getDisponibilidad` | <br><sub>Franjas de los próximos 14 días para el CP del ciudadano. { codigoPostal, zona: { id, nombre } \| null, huecos: [{ fecha, franjaId, horaInicio, horaFin, plazasLibres, completa }] }</sub> |
| `POST` | `/api/ciudadano/pedidos` | HU-02.01, HU-02.02, HU-03.02 | `crearPedido` | <br><sub>body: { bolsas: [{ tipoEnvase, cantidad }], fecha: 'YYYY-MM-DD', franjaId } 201 → detalle (genera el código del pedido y de cada bolsa) · 400 VALIDATION_ERROR (fieldErrors "bolsas[0].cantidad") 409 FRANJA_COMPLETA · 422 ZONA_SIN_SERVICIO</sub> |
| `POST` | `/api/ciudadano/pedidos/{id}/cancelar` | HU-03.04 | `cancelarPedido` | solo PENDIENTE, si no 409 ESTADO_NO_CANCELABLE |
| `GET` | `/api/ciudadano/impacto` | — | `getImpacto` | CO2 evitado por los envases del ciudadano<br><sub>{ co2EvitadoKg, envasesReciclados, recogidas, porTipo: [{ tipoEnvase, envases, co2Kg }], porMes: [{ mes: 'YYYY-MM', co2Kg }], zona: { nombre, co2EvitadoKg } \| null }</sub> |
| `GET` | `/api/ciudadano/monedero` | HU-05.01 | `getMonedero` | { saldoDisponible } (solo la parte del ciudadano ya validada) |
| `GET` | `/api/ciudadano/monedero/movimientos?page=&size=` | HU-05.05 | `getMovimientos` | <br><sub>Page<{ id, tipo: ABONO\|RETIRADA, fecha, importe, estado, concepto, referencia?, motivoFallo?, procesadaEn? }></sub> |
| `POST` | `/api/ciudadano/monedero/retiradas` | HU-05.03 | `solicitarRetirada` | <br><sub>body: { importe, iban } → 201 { id, codigo, estado: 'PENDIENTE', importe, saldoDisponible } 400 VALIDATION_ERROR (IBAN) · 422 SALDO_INSUFICIENTE</sub> |

## `recolectorService.js` — rol: RECOLECTOR

| Método | Ruta | HU | Función front | Descripción |
|---|---|---|---|---|
| `GET` | `/api/recolector/recogidas` | HU-04.01 | `getRecogidas` | <br><sub>Recogidas asignadas de hoy en adelante (y las que siguen en camino de días anteriores). { asignaciones: [{ zona: { id, nombre }, dias: [1..7] }], recogidas: [{ id, codigo, estado, fecha, horaInicio, horaFin, direccion, lat, lng, zona, numBolsas, totalEnvases }] }</sub> |
| `GET` | `/api/recolector/recogidas/{id}` | HU-04.01 | `getRecogida` | <br><sub>403 si no es suya. Añade { ciudadano: { nombre, telefono }, iniciadoEn, completadoEn, bolsas: [{ id, codigo, tipoEnvase, cantidad, capacidadMaxima, resultado, envasesValidos, nota }], incidencias }</sub> |
| `POST` | `/api/recolector/recogidas/{id}/iniciar` | HU-04.02 | `iniciar` | PENDIENTE → EN_CAMINO (guarda fecha y hora) |
| `PUT` | `/api/recolector/recogidas/{id}/bolsas/{bolsaId}` | HU-04.04, HU-04.03 | `registrarResultadoBolsa` | <br><sub>body: { resultado: ACEPTADA\|DISCREPANCIA\|NO_ENCONTRADA, envasesValidos?, nota? } DISCREPANCIA y NO_ENCONTRADA crean una incidencia asociada a la bolsa (HU-04.03)</sub> |
| `POST` | `/api/recolector/recogidas/{id}/completar` | HU-04.03, HU-05.02 | `completar` | EN_CAMINO → COMPLETADA<br><sub>409 BOLSAS_SIN_VERIFICAR si falta alguna bolsa. Calcula el abono (HU-05.02)</sub> |
| `POST` | `/api/recolector/recogidas/{id}/incidencias` | HU-06.01 | `registrarIncidencia` | body: { tipo, descripcion } → 201 { id, codigo, creadaEn } |
| `GET` | `/api/recolector/ruta?fecha=YYYY-MM-DD&lat=&lng=` | — | `getRuta` | <br><sub>Ruta sugerida: paradas ordenadas por franja y, dentro de cada franja, por cercanía desde el origen (nave o ubicación actual). { fecha, origen: { nombre, lat, lng }, paradas: [{ orden, ...recogida, distanciaDesdeAnteriorKm }], hechas, canceladas, distanciaKm, duracionMin }</sub> |
| `GET` | `/api/recolector/historial?page=` | — | `getHistorial` | Page<recogida completada + { completadoEn, envasesValidos, co2EvitadoKg, tieneIncidencias }> |
| `GET` | `/api/recolector/impacto` | — | `getImpacto` | CO2 evitado con las recogidas del recolector (mismo formato que el del ciudadano, sin zona) |
| `GET` | `/api/recolector/preferencias` | — | `getPreferencias` | <br><sub>{ zonaPreferida: { id, nombre } \| null, asignaciones: [{ zona, dias }], zonas: [{ id, nombre, codigosPostales }], solicitudes: [{ id, estado: PENDIENTE\|APROBADA\|RECHAZADA\|CANCELADA, creadaEn, motivo, zonaActual, zonaSolicitada, resueltaEn, resueltaPor, respuesta }] }</sub> |
| `PUT` | `/api/recolector/preferencias/zona` | — | `elegirZonaInicial` | body: { zonaId } · SOLO en el primer inicio de sesión; si ya tiene zona → 409 ZONA_YA_ELEGIDA |
| `POST` | `/api/recolector/solicitudes-zona` | — | `solicitarCambioZona` | body: { zonaId, motivo } · 409 SOLICITUD_PENDIENTE si ya hay una abierta |
| `POST` | `/api/recolector/solicitudes-zona/{id}/cancelar` | — | `cancelarSolicitudZona` | solo si sigue PENDIENTE |

## `adminService.js` — rol: ADMIN

| Método | Ruta | HU | Función front | Descripción |
|---|---|---|---|---|
| `GET` | `/api/admin/pendientes` | — | `getPendientes` | bandeja de tareas de administración<br><sub>{ transferencias: { total, importe }, solicitudesZona, incidenciasAbiertas, recogidasAtrasadas, recogidasSinRecolector, diasSinRecolector: [{ zona: { id, nombre }, diaSemana }] }</sub> |
| `GET` | `/api/admin/indicadores?desde=YYYY-MM-DD&hasta=YYYY-MM-DD` | HU-07.04, HU-07.05 | `getIndicadores` | <br><sub>{ recogidas: { solicitadas, completadas, canceladas, enCurso }, incidencias: { registradas, abiertas }, economia: { envasesValidos, importeTotal, parteEmpresa, parteCiudadanos }, impacto: { co2EvitadoKg, envasesReciclados, porTipo, porMes, totalHistoricoKg }, porZona: [{ zona, co2Kg }] }</sub> |
| `GET` | `/api/admin/incidencias?tipo=&estado=&page=&size=` | HU-06.02 | `getIncidencias` | <br><sub>Page<{ id, codigo, tipo, tipoNombre, estado: ABIERTA\|EN_REVISION\|RESUELTA, creadaEn, pedido: { id, codigo } }></sub> |
| `GET` | `/api/admin/incidencias/{id}` | HU-06.02, HU-04.04 | `getIncidencia` | <br><sub>Añade { descripcion, bolsaId, resolucion, resueltaEn, historial: [{ fecha, autor, accion }], recogida: { id, codigo, estado, fecha, horaInicio, horaFin, direccion, ciudadano, recolector, bolsas, liquidacion } }</sub> |
| `PATCH` | `/api/admin/incidencias/{id}` | HU-06.03 | `actualizarIncidencia` | body: { estado?, resolucion? } (guarda autor y fecha en el historial) |
| `GET` | `/api/admin/recogidas?fecha=&estado=&zonaId=&q=&page=` | HU-04.04 | `getRecogidas` | <br><sub>estado: PENDIENTE \| EN_CAMINO \| COMPLETADA \| CANCELADA \| ATRASADAS (días pasados sin cerrar) \| SIN_RECOLECTOR Page<{ id, codigo, estado, fecha, horaInicio, horaFin, zona, direccion, ciudadano, recolector: { id, nombre } \| null, numBolsas, totalEnvases, tieneIncidencias }></sub> |
| `GET` | `/api/admin/recogidas/{id}` | HU-04.04 | `getRecogida` | añade { creadoEn, iniciadoEn, completadoEn, canceladoEn, bolsas (con resultado), liquidacion, incidencias } |
| `PUT` | `/api/admin/recogidas/{id}/recolector` | HU-07.06 | `reasignarRecogida` | body: { recolectorId } · solo recogidas sin cerrar |
| `POST` | `/api/admin/recogidas/{id}/cancelar` | HU-03.03 | `cancelarRecogida` | cancelación autorizada (p. ej. ciudadano ausente) |
| `GET` | `/api/admin/transferencias?estado=&page=&size=` | HU-07.03 | `getTransferencias` | <br><sub>Page<{ id, codigo, ciudadano, importe, estado: PENDIENTE\|COMPLETADA\|FALLIDA, solicitadaEn }> + totales: { PENDIENTE, COMPLETADA, FALLIDA }</sub> |
| `GET` | `/api/admin/transferencias/{id}` | HU-07.03 | `getTransferencia` | añade { iban, procesadaEn, referenciaBancaria, motivoFallo, historial: [{ fecha, estado }] } |
| `POST` | `/api/admin/transferencias/{id}/resultado` | HU-05.04 | `registrarResultadoTransferencia` | <br><sub>body: { estado: COMPLETADA } (aceptar: el servidor ordena el pago y genera referenciaBancaria "TRF-AAAAMMDD-000123") \| { estado: FALLIDA, motivoFallo } (rechazar: el importe vuelve al monedero) 409 YA_PROCESADA (evita pagar dos veces)</sub> |
| `POST` | `/api/admin/transferencias/pagar-lote` | HU-05.04 | `pagarTransferencias` | body: { ids: [] } → { procesadas, remesa } · referencias generadas por el servidor (409 si alguna ya está procesada) |
| `GET` | `/api/admin/codigos-postales` | HU-07.06 | `getCodigosPostales` | CP de Málaga con la zona a la que pertenecen [{ cp, barrio, zona: { id, nombre } \| null }] |
| `GET` | `/api/admin/zonas` | HU-07.01, HU-07.06 | `getZonas` | <br><sub>[{ id, nombre, codigosPostales, pendientes, dias: [{ diaSemana (1=lunes), recolector: { id, nombre } \| null, franjas: [{ id, horaInicio, horaFin, capacidad }] }] }]</sub> |
| `GET` | `/api/admin/recolectores` | HU-07.06 | `getRecolectores` | activos [{ id, nombre, zonaPreferida, diasOcupados: [{ diaSemana, zona, zonaId }] }] |
| `POST` | `/api/admin/zonas` | HU-07.06 | `crearZona` | body: { nombre, codigosPostales: ['29001'] } |
| `PUT` | `/api/admin/zonas/{id}` | HU-07.06 | `editarZona` | body: { nombre, codigosPostales } · un CP solo puede estar en una zona |
| `DELETE` | `/api/admin/zonas/{id}` | HU-07.06 | `borrarZona` | 409 ZONA_CON_RECOGIDAS si tiene recogidas sin cerrar |
| `PUT` | `/api/admin/zonas/{id}/dias/{diaSemana}/recolector` | HU-07.06 | `asignarDia` | body: { recolectorId \| null }<br><sub>Asigna el recolector de la zona ese día de la semana. Las recogidas pendientes de ese día pasan a él. 409 RECOLECTOR_OCUPADO si ya recoge ese día en otra zona</sub> |
| `GET` | `/api/admin/solicitudes-zona?estado=` | HU-07.06 | `getSolicitudesZona` | peticiones de cambio de zona de los recolectores<br><sub>[{ id, estado, creadaEn, motivo, recolector: { id, nombre }, zonaActual, zonaSolicitada, resueltaEn, resueltaPor, respuesta }]</sub> |
| `POST` | `/api/admin/solicitudes-zona/{id}/resolver` | HU-07.06 | `resolverSolicitudZona` | body: { aprobar, moverDias, respuesta }<br><sub>Aprobar cambia la zona preferida y (moverDias) pasa sus días a la zona nueva si están libres → { solicitud, diasEnConflicto: [diaSemana] }</sub> |
| `POST` | `/api/admin/zonas/{id}/franjas` | HU-07.01 | `crearFranja` | body: { diaSemana, horaInicio, horaFin, capacidad } |
| `PUT` | `/api/admin/franjas/{id}` | HU-07.01 | `editarFranja` | las recogidas ya confirmadas no cambian |
| `DELETE` | `/api/admin/franjas/{id}` | HU-07.01 | `borrarFranja` | las recogidas ya confirmadas no se cancelan |
| `GET` | `/api/admin/usuarios?q=&rol=&page=&size=` | HU-07.02 | `getUsuarios` | <br><sub>Page<{ id, nombre, email, roles: ['CIUDADANO'\|'RECOLECTOR'\|'ADMIN'], estado: ACTIVA\|SUSPENDIDA }></sub> |
| `GET` | `/api/admin/usuarios/{id}` | HU-07.02 | `getUsuario` | añade { nombrePila, apellidos, dni, telefono, direccion, zona, altaEn, pedidos, zonaPreferida, zonasAsignadas, co2EvitadoKg } |
| `PUT` | `/api/admin/usuarios/{id}` | HU-07.02 | `editarUsuario` | body: { nombre, apellidos, email, telefono, direccion? } (mantener actualizada la información) |
| `POST` | `/api/admin/usuarios/{id}/suspender` | HU-07.02 | `suspenderUsuario` | la cuenta no podrá iniciar sesión (403 ACCOUNT_SUSPENDED) |
| `POST` | `/api/admin/usuarios/{id}/reactivar` | HU-07.02 | `reactivarUsuario` |  |
| `POST` | `/api/admin/recolectores` | HU-07.02 | `crearRecolector` | body: { nombre, apellidos, email, telefono, dni }<br><sub>201 → usuario + { passwordProvisional } (solo en esta respuesta). Al entrar deberá crear su contraseña y elegir zona</sub> |
| `POST` | `/api/admin/usuarios/{id}/password-provisional` | HU-07.02 | `restablecerPassword` | → { email, passwordProvisional } · deberá cambiarla al entrar |
