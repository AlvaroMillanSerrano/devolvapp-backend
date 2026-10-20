package es.devolvapp.backend.service;

import es.devolvapp.backend.dto.admin.*;
import es.devolvapp.backend.dto.common.PageResponse;
import es.devolvapp.backend.dto.common.UsuarioResponse;
import es.devolvapp.backend.dto.common.DireccionDto;
import es.devolvapp.backend.entity.Role;
import es.devolvapp.backend.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminService {
    private final JdbcTemplate jdbc;
    private final CiudadanoService citizenService;
    private final CodeGenerator codes;
    private final WalletService walletService;
    private final ImpactService impactService;
    private final PasswordEncoder passwordEncoder;
    private static final SecureRandom RANDOM = new SecureRandom();

    public Map<String, Object> dashboard() {
        Map<String, Object> transfers = jdbc.queryForObject("""
                SELECT COUNT(*) total,COALESCE(SUM(amount),0) importe
                FROM withdrawal WHERE status='PENDIENTE'
                """, (rs, row) -> Map.of("total", rs.getLong("total"), "importe", rs.getBigDecimal("importe")));
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("transferencias", transfers);
        response.put("solicitudesZona", count("SELECT COUNT(*) FROM zone_change_request WHERE status='PENDIENTE'"));
        response.put("incidenciasAbiertas", count("SELECT COUNT(*) FROM incident WHERE status<>'RESUELTA'"));
        response.put("recogidasAtrasadas", count("""
                SELECT COUNT(*) FROM collection WHERE collection_date<current_date AND status IN ('PENDIENTE','EN_CAMINO')
                """));
        response.put("recogidasSinRecolector", count("""
                SELECT COUNT(*) FROM collection WHERE collector_id IS NULL AND status='PENDIENTE'
                """));
        response.put("diasSinRecolector", jdbc.query("""
                SELECT z.id,z.name,t.day_of_week FROM zone z JOIN time_slot t ON t.zone_id=z.id AND t.active=true
                LEFT JOIN zone_day_collector d ON d.zone_id=z.id AND d.day_of_week=t.day_of_week
                WHERE z.active=true AND d.collector_id IS NULL GROUP BY z.id,z.name,t.day_of_week
                ORDER BY z.name,t.day_of_week
                """, (rs, row) -> Map.of("zona", Map.of("id",rs.getLong(1),"nombre",rs.getString(2)),
                "diaSemana",rs.getInt(3))));
        return response;
    }

    public Map<String, Object> indicators(LocalDate from, LocalDate to) {
        LocalDate start = from == null ? LocalDate.of(1970, 1, 1) : from;
        LocalDate end = to == null ? LocalDate.now() : to;
        if (end.isBefore(start)) throw ApiException.fieldError("VALIDATION_ERROR", "Rango de fechas no válido",
                "hasta", "La fecha final debe ser igual o posterior a la inicial");
        Map<String, Object> collections = jdbc.queryForObject("""
                SELECT COUNT(*) solicitadas,
                       COUNT(*) FILTER (WHERE status='COMPLETADA') completadas,
                       COUNT(*) FILTER (WHERE status='CANCELADA') canceladas,
                       COUNT(*) FILTER (WHERE status='EN_CAMINO') en_curso
                FROM collection WHERE collection_date BETWEEN ? AND ?
                """, (rs, row) -> Map.of("solicitadas",rs.getLong(1),"completadas",rs.getLong(2),
                "canceladas",rs.getLong(3),"enCurso",rs.getLong(4)), start, end);
        Map<String, Object> incidents = jdbc.queryForObject("""
                SELECT COUNT(*) registradas, COUNT(*) FILTER (WHERE status<>'RESUELTA') abiertas
                FROM incident WHERE created_at::date BETWEEN ? AND ?
                """, (rs, row) -> Map.of("registradas",rs.getLong(1),"abiertas",rs.getLong(2)), start, end);
        Map<String, Object> economy = jdbc.queryForObject("""
                SELECT COALESCE(SUM(s.valid_containers),0) envases,
                       COALESCE(SUM(s.total_amount),0) total,
                       COALESCE(SUM(s.company_share),0) company,
                       COALESCE(SUM(s.citizen_share),0) citizens
                FROM settlement s JOIN collection c ON c.id=s.collection_id
                WHERE c.completed_at::date BETWEEN ? AND ?
                """, (rs, row) -> Map.of("envasesValidos",rs.getLong("envases"),
                "importeTotal",rs.getBigDecimal("total"),"parteEmpresa",rs.getBigDecimal("company"),
                "parteCiudadanos",rs.getBigDecimal("citizens")), start,end);
        Map<String,Object> impact = impactService.forAll();
        List<Map<String,Object>> byZone = jdbc.query("""
                SELECT z.name,SUM(s.co2_kg) co2 FROM settlement s
                JOIN collection c ON c.id=s.collection_id JOIN zone z ON z.id=c.zone_id
                WHERE c.completed_at::date BETWEEN ? AND ? GROUP BY z.id,z.name ORDER BY z.name
                """,(rs,row)->Map.of("zona",rs.getString(1),"co2Kg",rs.getBigDecimal(2)),start,end);
        return Map.of("recogidas",collections,"incidencias",incidents,"economia",economy,
                "impacto",impact,"porZona",byZone);
    }

    public PageResponse<Map<String,Object>> incidents(String type, String status, int page, int size) {
        int p=Math.max(0,page), s=Math.min(100,Math.max(1,size));
        String where=" WHERE 1=1 "; List<Object> args=new ArrayList<>();
        if(type!=null&&!type.isBlank()){where+=" AND i.type=?";args.add(type);}
        if(status!=null&&!status.isBlank()){where+=" AND i.status=?";args.add(status);}
        Long total=jdbc.queryForObject("SELECT COUNT(*) FROM incident i"+where,Long.class,args.toArray());
        String sql="""
                SELECT i.id,i.code,i.type,it.name type_name,i.status,i.created_at,c.id collection_id,c.code collection_code
                FROM incident i JOIN incident_type it ON it.code=i.type
                JOIN collection c ON c.id=i.collection_id
                """+where+" ORDER BY i.created_at DESC LIMIT ? OFFSET ?";
        args.add(s);args.add(p*s);
        List<Map<String,Object>> rows=jdbc.query(sql,(rs,row)->{
            Map<String,Object> item=new LinkedHashMap<>();
            item.put("id",rs.getLong("id"));item.put("codigo",rs.getString("code"));
            item.put("tipo",rs.getString("type"));item.put("tipoNombre",rs.getString("type_name"));
            item.put("estado",rs.getString("status"));item.put("creadaEn",rs.getObject("created_at"));
            item.put("pedido",Map.of("id",rs.getLong("collection_id"),"codigo",rs.getString("collection_code")));
            return item;
        },args.toArray());
        return page(rows,total,p,s);
    }

    public Map<String,Object> incident(Long id) {
        Map<String,Object> result=jdbc.query("""
                SELECT i.id,i.code,i.type,it.name type_name,i.status,i.description,i.created_at,i.bag_id,
                       i.resolution,i.resolved_at,i.collection_id,c.code collection_code
                FROM incident i JOIN incident_type it ON it.code=i.type
                JOIN collection c ON c.id=i.collection_id WHERE i.id=?
                """,rs->{
            if(!rs.next())return null;
            Map<String,Object> map=new LinkedHashMap<>();
            map.put("id",rs.getLong("id"));map.put("codigo",rs.getString("code"));
            map.put("tipo",rs.getString("type"));map.put("tipoNombre",rs.getString("type_name"));
            map.put("estado",rs.getString("status"));map.put("descripcion",rs.getString("description"));
            map.put("creadaEn",rs.getObject("created_at"));map.put("bolsaId",rs.getObject("bag_id"));
            map.put("resolucion",rs.getString("resolution"));map.put("resueltaEn",rs.getObject("resolved_at"));
            map.put("pedido",Map.of("id",rs.getLong("collection_id"),"codigo",rs.getString("collection_code")));
            map.put("collectionId",rs.getLong("collection_id")); return map;
        },id);
        if(result==null)throw ApiException.notFound("Incidencia no encontrada");
        Long collectionId=(Long)result.remove("collectionId");
        result.put("historial",jdbc.query("""
                SELECT h.occurred_at,h.action,h.author_id,COALESCE(u.first_name || ' ' || u.last_name,'Sistema') author
                FROM incident_history h LEFT JOIN d_user u ON u.id=h.author_id
                WHERE h.incident_id=? ORDER BY h.occurred_at
                """,(rs,row)->Map.of("fecha",rs.getObject("occurred_at"),"accion",rs.getString("action"),
                "autor",rs.getString("author")),id));
        result.put("recogida",adminCollectionDetail(collectionId));
        return result;
    }

    @Transactional
    public Map<String,Object> updateIncident(Long id, Long adminId, ActualizarIncidenciaRequest request) {
        List<Map<String,Object>> rows=jdbc.query("SELECT status,resolution FROM incident WHERE id=? FOR UPDATE",
                (rs,row)->Map.of("status",rs.getString(1),"resolution",rs.getString(2)==null?"":rs.getString(2)),id);
        if(rows.isEmpty())throw ApiException.notFound("Incidencia no encontrada");
        String state=request.estado()==null?(String)rows.getFirst().get("status"):request.estado();
        String resolution=request.resolucion()==null?(String)rows.getFirst().get("resolution"):request.resolucion();
        if("RESUELTA".equals(state)&&(resolution==null||resolution.isBlank()))
            throw ApiException.fieldError("VALIDATION_ERROR","La resolución es obligatoria","resolucion","Indica la resolución");
        jdbc.update("UPDATE incident SET status=?,resolution=?,resolved_at=? WHERE id=?",
                state,resolution,"RESUELTA".equals(state)?OffsetDateTime.now():null,id);
        String action=request.estado()!=null?"Estado: "+state:"Resolución actualizada";
        jdbc.update("INSERT INTO incident_history(incident_id,author_id,action) VALUES (?,?,?)",id,adminId,action);
        return incident(id);
    }

    public PageResponse<Map<String,Object>> collections(LocalDate date,String status,Long zoneId,String query,int page,int size) {
        int p=Math.max(0,page),s=Math.min(100,Math.max(1,size));
        StringBuilder where=new StringBuilder(" WHERE 1=1 ");List<Object> args=new ArrayList<>();
        if(date!=null){where.append(" AND c.collection_date=?");args.add(date);}
        if(zoneId!=null){where.append(" AND c.zone_id=?");args.add(zoneId);}
        if(status!=null&&!status.isBlank()){
            switch(status){
                case "ATRASADAS"->{where.append(" AND c.collection_date<current_date AND c.status IN ('PENDIENTE','EN_CAMINO')");}
                case "SIN_RECOLECTOR"->{where.append(" AND c.collector_id IS NULL AND c.status='PENDIENTE'");}
                default->{where.append(" AND c.status=?");args.add(status);}
            }
        }
        if(query!=null&&!query.isBlank()){
            where.append(" AND (c.code ILIKE ? OR u.first_name ILIKE ? OR u.last_name ILIKE ? OR c.addr_street_name ILIKE ?)");
            String q="%"+query.trim()+"%";args.add(q);args.add(q);args.add(q);args.add(q);
        }
        Long total=jdbc.queryForObject("SELECT COUNT(*) FROM collection c JOIN d_user u ON u.id=c.citizen_id"+
                where,Long.class,args.toArray());
        String sql="""
                SELECT c.*,z.name zone,u.first_name citizen_first,u.last_name citizen_last,
                       collector.first_name collector_first,collector.last_name collector_last,
                       (SELECT COUNT(*) FROM bag b WHERE b.collection_id=c.id) num_bags,
                       COALESCE((SELECT SUM(quantity) FROM bag b WHERE b.collection_id=c.id),0) total_containers,
                       EXISTS(SELECT 1 FROM incident i WHERE i.collection_id=c.id) has_incidents
                FROM collection c LEFT JOIN zone z ON z.id=c.zone_id
                JOIN d_user u ON u.id=c.citizen_id LEFT JOIN d_user collector ON collector.id=c.collector_id
                """+where+" ORDER BY c.collection_date DESC,c.start_time LIMIT ? OFFSET ?";
        args.add(s);args.add(p*s);
        List<Map<String,Object>> rows=jdbc.query(sql,(rs,row)->adminCollectionRow(rs),args.toArray());
        return page(rows,total,p,s);
    }

    public Map<String,Object> adminCollectionDetail(Long id) {
        Map<String,Object> result=new LinkedHashMap<>(citizenService.getOrderAny(id));
        Map<String,Object> extra=jdbc.query("""
                SELECT c.collector_id,collector.first_name,collector.last_name,u.first_name citizen_first,
                       u.last_name citizen_last,u.phone_number citizen_phone,z.id zone_id,z.name zone
                FROM collection c JOIN d_user u ON u.id=c.citizen_id LEFT JOIN zone z ON z.id=c.zone_id
                LEFT JOIN d_user collector ON collector.id=c.collector_id WHERE c.id=?
                """,rs->{
            if(!rs.next())return Map.of();
            Map<String,Object> map=new LinkedHashMap<>();
            Object collectorId=rs.getObject("collector_id");
            map.put("recolector",collectorId==null?null:Map.of("id",((Number)collectorId).longValue(),
                    "nombre",rs.getString("first_name")+" "+rs.getString("last_name")));
            map.put("ciudadano",Map.of("nombre",rs.getString("citizen_first")+" "+rs.getString("citizen_last"),
                    "telefono",rs.getString("citizen_phone")==null?"":rs.getString("citizen_phone")));
            Object zoneId = rs.getObject("zone_id");
            map.put("zona", zoneId == null ? null : Map.of("id", ((Number) zoneId).longValue(),
                    "nombre", rs.getString("zone")));
            return map;
        },id);
        result.putAll(extra);
        return result;
    }

    @Transactional
    public Map<String,Object> assignCollector(Long collectionId,Long collectorId) {
        List<String> states=jdbc.query("SELECT status FROM collection WHERE id=? FOR UPDATE",
                (rs,row)->rs.getString(1),collectionId);
        if(states.isEmpty())throw ApiException.notFound("Recogida no encontrada");
        if(List.of("COMPLETADA","CANCELADA").contains(states.getFirst()))
            throw ApiException.conflict("ESTADO_NO_PERMITIDO","No se puede reasignar una recogida cerrada");
        Integer valid=jdbc.queryForObject("""
                SELECT COUNT(*) FROM d_user u JOIN user_role r ON r.user_id=u.id
                WHERE u.id=? AND r.role='RECOLECTOR' AND u.status='ACTIVA'
                """,Integer.class,collectorId);
        if(valid==null||valid==0)throw ApiException.fieldError("VALIDATION_ERROR","Recolector no válido",
                "recolectorId","Selecciona un recolector activo");
        jdbc.update("UPDATE collection SET collector_id=? WHERE id=?",collectorId,collectionId);
        return adminCollectionDetail(collectionId);
    }

    @Transactional
    public Map<String,Object> cancelCollection(Long id,Long adminId) {
        List<String> states=jdbc.query("SELECT status FROM collection WHERE id=? FOR UPDATE",(rs,row)->rs.getString(1),id);
        if(states.isEmpty())throw ApiException.notFound("Recogida no encontrada");
        if(!List.of("PENDIENTE","EN_CAMINO").contains(states.getFirst()))
            throw ApiException.conflict("ESTADO_NO_CANCELABLE","La recogida no se puede cancelar");
        jdbc.update("UPDATE collection SET status='CANCELADA',cancelled_at=now(),cancelled_by=? WHERE id=?",adminId,id);
        return adminCollectionDetail(id);
    }

    public Map<String,Object> withdrawals(String state,int page,int size) {
        int p=Math.max(0,page),s=Math.min(100,Math.max(1,size));
        String where=state==null||state.isBlank()?"":" WHERE w.status=?";
        Object[] filter=where.isEmpty()?new Object[0]:new Object[]{state};
        Long total=jdbc.queryForObject("SELECT COUNT(*) FROM withdrawal w"+where,Long.class,filter);
        List<Map<String,Object>> rows=jdbc.query("""
                SELECT w.id,w.code,w.amount,w.status,w.requested_at,u.id citizen_id,u.first_name,u.last_name
                FROM withdrawal w JOIN d_user u ON u.id=w.citizen_id
                """+where+" ORDER BY w.requested_at DESC LIMIT ? OFFSET ?",
                (rs,row)->Map.of("id",rs.getLong("id"),"codigo",rs.getString("code"),"importe",rs.getBigDecimal("amount"),
                        "estado",rs.getString("status"),"solicitadaEn",rs.getObject("requested_at"),
                        "ciudadano",Map.of("id",rs.getLong("citizen_id"),"nombre",
                                rs.getString("first_name")+" "+rs.getString("last_name"))),
                concat(filter,s,p*s));
        Map<String,Long> totals=new LinkedHashMap<>();
        jdbc.query("SELECT status,COUNT(*) amount FROM withdrawal GROUP BY status",
                (rs,row)->Map.entry(rs.getString("status"),rs.getLong("amount")))
                .forEach(entry->totals.put(entry.getKey(),entry.getValue()));
        for(String key:List.of("PENDIENTE","COMPLETADA","FALLIDA"))totals.putIfAbsent(key,0L);
        PageResponse<Map<String,Object>> result=page(rows,total,p,s);
        Map<String,Object> response=new LinkedHashMap<>();
        response.put("content",result.content());response.put("totalElements",result.totalElements());
        response.put("totalPages",result.totalPages());response.put("number",result.number());
        response.put("size",result.size());response.put("totales",totals);
        return response;
    }

    public Map<String,Object> withdrawal(Long id) {
        Map<String,Object> result=jdbc.query("""
                SELECT w.id,w.code,w.amount,w.iban,w.status,w.requested_at,w.processed_at,w.bank_reference,
                       w.failure_reason,w.citizen_id,u.first_name,u.last_name
                FROM withdrawal w JOIN d_user u ON u.id=w.citizen_id WHERE w.id=?
                """,rs->{
            if(!rs.next())return null;
            Map<String,Object> map=new LinkedHashMap<>();
            map.put("id",rs.getLong("id"));map.put("codigo",rs.getString("code"));
            map.put("importe",rs.getBigDecimal("amount"));map.put("iban",rs.getString("iban"));
            map.put("estado",rs.getString("status"));map.put("solicitadaEn",rs.getObject("requested_at"));
            map.put("procesadaEn",rs.getObject("processed_at"));map.put("referenciaBancaria",rs.getString("bank_reference"));
            map.put("motivoFallo",rs.getString("failure_reason"));
            map.put("ciudadano",Map.of("id",rs.getLong("citizen_id"),
                    "nombre",rs.getString("first_name")+" "+rs.getString("last_name")));
            return map;
        },id);
        if(result==null)throw ApiException.notFound("Transferencia no encontrada");
        result.put("historial",jdbc.query("""
                SELECT status,occurred_at FROM withdrawal_history WHERE withdrawal_id=? ORDER BY occurred_at
                """,(rs,row)->Map.of("fecha",rs.getObject("occurred_at"),"estado",rs.getString("status")),id));
        return result;
    }

    @Transactional
    public Map<String,Object> setWithdrawalResult(Long id,Long adminId,ResultadoTransferenciaRequest request) {
        List<String> states=jdbc.query("SELECT status FROM withdrawal WHERE id=? FOR UPDATE",
                (rs,row)->rs.getString(1),id);
        if(states.isEmpty())throw ApiException.notFound("Transferencia no encontrada");
        if(!"PENDIENTE".equals(states.getFirst()))throw ApiException.conflict("YA_PROCESADA","La transferencia ya fue procesada");
        if("FALLIDA".equals(request.estado())&&(request.motivoFallo()==null||request.motivoFallo().isBlank()))
            throw ApiException.fieldError("VALIDATION_ERROR","El motivo del fallo es obligatorio",
                    "motivoFallo","Indica por qué falló la transferencia");
        String reference="COMPLETADA".equals(request.estado())
                ?"TRF-"+LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)+"-"+String.format("%06d",id):null;
        jdbc.update("""
                UPDATE withdrawal SET status=?,processed_at=now(),processed_by=?,bank_reference=?,failure_reason=?
                WHERE id=?
                """,request.estado(),adminId,reference,request.motivoFallo(),id);
        jdbc.update("INSERT INTO withdrawal_history(withdrawal_id,status,author_id) VALUES (?,?,?)",
                id,request.estado(),adminId);
        return withdrawal(id);
    }

    @Transactional
    public Map<String,Object> payBatch(Long adminId,List<Long> ids) {
        List<Long> distinct=ids.stream().distinct().toList();
        if(distinct.size()!=ids.size())throw ApiException.badRequest("VALIDATION_ERROR","La lista contiene ids duplicados");
        List<Map<String,Object>> rows=new ArrayList<>();
        for(Long id:distinct) {
            List<Map<String,Object>> found=jdbc.query("""
                    SELECT id,status,amount FROM withdrawal WHERE id=? FOR UPDATE
                    """,(rs,row)->Map.of("id",rs.getLong("id"),"status",rs.getString("status"),
                    "amount",rs.getBigDecimal("amount")),id);
            if(found.isEmpty())throw ApiException.notFound("No existe la transferencia "+id);
            rows.add(found.getFirst());
        }
        if(rows.stream().anyMatch(row->!"PENDIENTE".equals(row.get("status"))))
            throw ApiException.conflict("YA_PROCESADA","El lote contiene transferencias ya procesadas");
        BigDecimal total=rows.stream().map(row->(BigDecimal)row.get("amount")).reduce(BigDecimal.ZERO,BigDecimal::add);
        String remittanceCode=codes.remittance();
        Long remittanceId=jdbc.queryForObject("""
                INSERT INTO remittance(code,created_by,transfer_count,total) VALUES (?,?,?,?) RETURNING id
                """,Long.class,remittanceCode,adminId,rows.size(),total);
        for(Long id:distinct) {
            String reference="TRF-"+LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)+"-"+String.format("%06d",id);
            jdbc.update("""
                    UPDATE withdrawal SET status='COMPLETADA',processed_at=now(),processed_by=?,
                    bank_reference=?,remittance_id=? WHERE id=?
                    """,adminId,reference,remittanceId,id);
            jdbc.update("INSERT INTO withdrawal_history(withdrawal_id,status,author_id) VALUES (?,'COMPLETADA',?)",
                    id,adminId);
        }
        return Map.of("procesadas",rows.size(),"remesa",Map.of("id",remittanceId,"codigo",remittanceCode,"total",total));
    }

    public List<Map<String,Object>> postalCodes(boolean includeZone) {
        return jdbc.query("""
                SELECT p.code,p.neighbourhood,p.lat,p.lng,z.id zone_id,z.name zone_name
                FROM zip_code p LEFT JOIN zone z ON z.id=p.zone_id ORDER BY p.code
                """,(rs,row)->{
            Map<String,Object> item=new LinkedHashMap<>();
            item.put("cp",rs.getString("code").trim());item.put("barrio",rs.getString("neighbourhood"));
            if(includeZone) {
                Object zoneId=rs.getObject("zone_id");
                item.put("zona",zoneId==null?null:Map.of("id",((Number)zoneId).longValue(),"nombre",rs.getString("zone_name")));
            } else {
                item.put("lat",rs.getBigDecimal("lat"));item.put("lng",rs.getBigDecimal("lng"));
            }
            return item;
        });
    }

    public List<Map<String,Object>> zones() {
        List<Long> ids=jdbc.query("SELECT id FROM zone WHERE active=true ORDER BY name",(rs,row)->rs.getLong(1));
        List<Map<String,Object>> result=new ArrayList<>();
        for(Long id:ids) {
            Map<String,Object> zone=jdbc.queryForObject("SELECT id,name FROM zone WHERE id=?",
                    (rs,row)->new LinkedHashMap<>(Map.of("id",rs.getLong(1),"nombre",rs.getString(2))),id);
            zone.put("codigosPostales",jdbc.query("SELECT RTRIM(code) FROM zip_code WHERE zone_id=? ORDER BY code",
                    (rs,row)->rs.getString(1),id));
            zone.put("pendientes",count("SELECT COUNT(*) FROM collection WHERE zone_id="+id+" AND status='PENDIENTE'"));
            List<Map<String,Object>> days=new ArrayList<>();
            for(int day=1;day<=7;day++) {
                List<Map<String,Object>> collector=jdbc.query("""
                        SELECT u.id,u.first_name,u.last_name FROM zone_day_collector d
                        JOIN d_user u ON u.id=d.collector_id WHERE d.zone_id=? AND d.day_of_week=?
                        """,(rs,row)->Map.of("id",rs.getLong(1),"nombre",rs.getString(2)+" "+rs.getString(3)),id,day);
                List<Map<String,Object>> slots=jdbc.query("""
                        SELECT id,start_time,end_time,capacity FROM time_slot
                        WHERE zone_id=? AND day_of_week=? AND active=true ORDER BY start_time
                        """,(rs,row)->Map.of("id",rs.getLong("id"),"horaInicio",rs.getObject("start_time"),
                        "horaFin",rs.getObject("end_time"),"capacidad",rs.getInt("capacity")),id,day);
                Map<String,Object> dayData=new LinkedHashMap<>();
                dayData.put("diaSemana",day);dayData.put("recolector",collector.isEmpty()?null:collector.getFirst());
                dayData.put("franjas",slots);days.add(dayData);
            }
            zone.put("dias",days);result.add(zone);
        }
        return result;
    }

    public List<Map<String,Object>> collectors() {
        List<Long> ids=jdbc.query("""
                SELECT u.id FROM d_user u JOIN user_role r ON r.user_id=u.id
                WHERE r.role='RECOLECTOR' AND u.status='ACTIVA' ORDER BY u.last_name,u.first_name
                """,(rs,row)->rs.getLong(1));
        List<Map<String,Object>> result=new ArrayList<>();
        for(Long id:ids) {
            Map<String,Object> item=jdbc.queryForObject("""
                    SELECT u.id,u.first_name,u.last_name,u.preferred_zone_id,z.name preferred_zone
                    FROM d_user u LEFT JOIN zone z ON z.id=u.preferred_zone_id WHERE u.id=?
                    """,(rs,row)->{
                Map<String,Object> map=new LinkedHashMap<>();
                map.put("id",rs.getLong("id"));map.put("nombre",rs.getString("first_name")+" "+rs.getString("last_name"));
                Object zone=rs.getObject("preferred_zone_id");
                map.put("zonaPreferida",zone==null?null:Map.of("id",((Number)zone).longValue(),"nombre",rs.getString("preferred_zone")));
                return map;
            },id);
            item.put("diasOcupados",jdbc.query("""
                    SELECT d.day_of_week,z.id zone_id,z.name FROM zone_day_collector d JOIN zone z ON z.id=d.zone_id
                    WHERE d.collector_id=? ORDER BY d.day_of_week
                    """,(rs,row)->Map.of("diaSemana",rs.getInt(1),"zona",rs.getString(3),"zonaId",rs.getLong(2)),id));
            result.add(item);
        }
        return result;
    }

    @Transactional
    public Map<String,Object> createZone(ZonaRequest request) {
        Long id;
        try {
            id=jdbc.queryForObject("INSERT INTO zone(name) VALUES (?) RETURNING id",Long.class,request.nombre());
        } catch(org.springframework.dao.DuplicateKeyException ex) {
            throw ApiException.conflict("ZONA_DUPLICADA","Ya existe una zona con ese nombre");
        }
        assignPostalCodes(id,request.codigosPostales());
        return zones().stream().filter(zone->zone.get("id").equals(id)).findFirst().orElseThrow();
    }

    @Transactional
    public Map<String,Object> updateZone(Long id,ZonaRequest request) {
        ensureZone(id);
        try {
            jdbc.update("UPDATE zone SET name=? WHERE id=?",request.nombre(),id);
        } catch(org.springframework.dao.DuplicateKeyException ex) {
            throw ApiException.conflict("ZONA_DUPLICADA","Ya existe una zona con ese nombre");
        }
        List<String> oldCodes=jdbc.query("SELECT RTRIM(code) FROM zip_code WHERE zone_id=?",(rs,row)->rs.getString(1),id);
        for(String code:oldCodes)if(!request.codigosPostales().contains(code))
            jdbc.update("UPDATE zip_code SET zone_id=NULL WHERE code=? AND zone_id=?",code,id);
        assignPostalCodes(id,request.codigosPostales());
        return zones().stream().filter(zone->zone.get("id").equals(id)).findFirst().orElseThrow();
    }

    @Transactional
    public void deleteZone(Long id) {
        ensureZone(id);
        Integer open=jdbc.queryForObject("""
                SELECT COUNT(*) FROM collection WHERE zone_id=? AND status IN ('PENDIENTE','EN_CAMINO')
                """,Integer.class,id);
        if(open!=null&&open>0)throw ApiException.conflict("ZONA_CON_RECOGIDAS","La zona tiene recogidas sin cerrar");
        jdbc.update("UPDATE zone SET active=false WHERE id=?",id);
    }

    @Transactional
    public Map<String,Object> assignDay(Long zoneId,int day,Long collectorId) {
        ensureZone(zoneId);
        if(day<1||day>7)throw ApiException.fieldError("VALIDATION_ERROR","Día de semana no válido","diaSemana","Usa un valor entre 1 y 7");
        if(collectorId==null) {
            jdbc.update("DELETE FROM zone_day_collector WHERE zone_id=? AND day_of_week=?",zoneId,day);
            jdbc.update("""
                    UPDATE collection SET collector_id=NULL WHERE zone_id=? AND status='PENDIENTE'
                    AND EXTRACT(ISODOW FROM collection_date)=?
                    """,zoneId,day);
        } else {
            Integer active=jdbc.queryForObject("""
                    SELECT COUNT(*) FROM d_user u JOIN user_role r ON r.user_id=u.id
                    WHERE u.id=? AND u.status='ACTIVA' AND r.role='RECOLECTOR'
                    """,Integer.class,collectorId);
            if(active==null||active==0)throw ApiException.fieldError("VALIDATION_ERROR","Recolector no válido",
                    "recolectorId","Selecciona un recolector activo");
            Integer busy=jdbc.queryForObject("""
                    SELECT COUNT(*) FROM zone_day_collector
                    WHERE collector_id=? AND day_of_week=? AND zone_id<>?
                    """,Integer.class,collectorId,day,zoneId);
            if(busy!=null&&busy>0)throw ApiException.conflict("RECOLECTOR_OCUPADO",
                    "El recolector ya tiene asignada otra zona ese día");
            jdbc.update("""
                    INSERT INTO zone_day_collector(zone_id,day_of_week,collector_id) VALUES (?,?,?)
                    ON CONFLICT(zone_id,day_of_week) DO UPDATE SET collector_id=EXCLUDED.collector_id
                    """,zoneId,day,collectorId);
            jdbc.update("""
                    UPDATE collection SET collector_id=? WHERE zone_id=? AND status='PENDIENTE'
                    AND EXTRACT(ISODOW FROM collection_date)=?
                    """,collectorId,zoneId,day);
        }
        return zones().stream().filter(zone->zone.get("id").equals(zoneId)).findFirst().orElseThrow();
    }

    @Transactional
    public Map<String,Object> createSlot(Long zoneId,FranjaRequest request) {
        ensureZone(zoneId);validateSlot(request);
        Long id=jdbc.queryForObject("""
                INSERT INTO time_slot(zone_id,day_of_week,start_time,end_time,capacity) VALUES (?,?,?,?,?) RETURNING id
                """,Long.class,zoneId,request.diaSemana(),request.horaInicio(),request.horaFin(),request.capacidad());
        return slot(id);
    }

    @Transactional
    public Map<String,Object> updateSlot(Long id,FranjaRequest request) {
        validateSlot(request);
        Integer exists=jdbc.queryForObject("SELECT COUNT(*) FROM time_slot WHERE id=? AND active=true",Integer.class,id);
        if(exists==null||exists==0)throw ApiException.notFound("Franja no encontrada");
        jdbc.update("UPDATE time_slot SET day_of_week=?,start_time=?,end_time=?,capacity=? WHERE id=?",
                request.diaSemana(),request.horaInicio(),request.horaFin(),request.capacidad(),id);
        return slot(id);
    }

    @Transactional
    public void deleteSlot(Long id) {
        Integer exists=jdbc.queryForObject("SELECT COUNT(*) FROM time_slot WHERE id=? AND active=true",Integer.class,id);
        if(exists==null||exists==0)throw ApiException.notFound("Franja no encontrada");
        jdbc.update("UPDATE time_slot SET active=false WHERE id=?",id);
    }

    public List<Map<String,Object>> zoneRequests(String state) {
        String where=state==null||state.isBlank()?"":" AND r.status=?";
        Object[] args=where.isEmpty()?new Object[0]:new Object[]{state};
        return jdbc.query("""
                SELECT r.id,r.status,r.created_at,r.reason,r.resolved_at,r.response,
                       r.current_zone_id,old.name current_zone,r.requested_zone_id,new.name requested_zone,
                       u.id collector_id,u.first_name,u.last_name
                FROM zone_change_request r JOIN d_user u ON u.id=r.collector_id
                LEFT JOIN zone old ON old.id=r.current_zone_id JOIN zone new ON new.id=r.requested_zone_id
                WHERE 1=1
                """+where+" ORDER BY r.created_at DESC",(rs,row)->zoneRequest(rs),args);
    }

    @Transactional
    public Map<String,Object> resolveZoneRequest(Long id,Long adminId,ResolverSolicitudRequest request) {
        List<Map<String,Object>> rows=jdbc.query("""
                SELECT collector_id,current_zone_id,requested_zone_id,status FROM zone_change_request
                WHERE id=? FOR UPDATE
                """,(rs,row)->{
            Map<String,Object> map=new LinkedHashMap<>();map.put("collector",rs.getLong(1));
            map.put("current",rs.getObject(2));map.put("requested",rs.getLong(3));map.put("status",rs.getString(4));return map;
        },id);
        if(rows.isEmpty())throw ApiException.notFound("Solicitud no encontrada");
        Map<String,Object> data=rows.getFirst();
        if(!"PENDIENTE".equals(data.get("status")))throw ApiException.conflict("ESTADO_NO_PERMITIDO","La solicitud ya está resuelta");
        Long collector=(Long)data.get("collector"),target=(Long)data.get("requested");
        List<Integer> conflicts=new ArrayList<>();
        if(request.aprobar()) {
            jdbc.update("UPDATE d_user SET preferred_zone_id=?,updated_at=now() WHERE id=?",target,collector);
            if(request.moverDias()&&data.get("current")!=null) {
                Long current=((Number)data.get("current")).longValue();
                List<Integer> days=jdbc.query("SELECT day_of_week FROM zone_day_collector WHERE collector_id=? AND zone_id=?",
                        (rs,row)->rs.getInt(1),collector,current);
                for(Integer day:days) {
                    Integer occupied=jdbc.queryForObject("""
                            SELECT COUNT(*) FROM zone_day_collector WHERE zone_id=? AND day_of_week=? AND collector_id<>?
                            """,Integer.class,target,day,collector);
                    if(occupied!=null&&occupied>0){conflicts.add(day);continue;}
                    jdbc.update("DELETE FROM zone_day_collector WHERE collector_id=? AND zone_id=? AND day_of_week=?",
                            collector,current,day);
                    jdbc.update("""
                            INSERT INTO zone_day_collector(zone_id,day_of_week,collector_id) VALUES (?,?,?)
                            ON CONFLICT(zone_id,day_of_week) DO UPDATE SET collector_id=EXCLUDED.collector_id
                            """,target,day,collector);
                    jdbc.update("""
                            UPDATE collection SET collector_id=? WHERE zone_id=? AND status='PENDIENTE'
                            AND EXTRACT(ISODOW FROM collection_date)=?
                            """,collector,target,day);
                }
            }
        }
        jdbc.update("""
                UPDATE zone_change_request SET status=?,resolved_at=now(),resolved_by=?,response=? WHERE id=?
                """,request.aprobar()?"APROBADA":"RECHAZADA",adminId,request.respuesta(),id);
        return Map.of("solicitud",zoneRequestById(id),"diasEnConflicto",conflicts);
    }

    public PageResponse<Map<String,Object>> users(String query,String role,int page,int size) {
        int p=Math.max(0,page),s=Math.min(100,Math.max(1,size));
        StringBuilder where=new StringBuilder(" WHERE 1=1 ");List<Object> args=new ArrayList<>();
        if(query!=null&&!query.isBlank()){where.append(" AND (u.first_name ILIKE ? OR u.last_name ILIKE ? OR u.email ILIKE ? OR u.id_card ILIKE ?");
            String q="%"+query.trim()+"%";for(int i=0;i<4;i++)args.add(q);where.append(")");}
        if(role!=null&&!role.isBlank()){where.append(" AND EXISTS(SELECT 1 FROM user_role f WHERE f.user_id=u.id AND f.role=?)");args.add(role);}
        long total=jdbc.queryForObject("SELECT COUNT(*) FROM d_user u"+where,Long.class,args.toArray());
        List<Map<String,Object>> rows=jdbc.query("SELECT u.id,u.first_name,u.last_name,u.email,u.status FROM d_user u"+where+
                " ORDER BY u.last_name,u.first_name LIMIT ? OFFSET ?",(rs,row)->{
            Long id=rs.getLong("id");return Map.of("id",id,"nombre",rs.getString("first_name")+" "+rs.getString("last_name"),
                    "email",rs.getString("email"),"estado",rs.getString("status"),"roles",userRoles(id));
        },concat(args.toArray(),s,p*s));
        return page(rows,total,p,s);
    }

    public Map<String,Object> user(Long id) {
        Map<String,Object> user=jdbc.query("""
                SELECT u.*,z.name preferred_zone,a.street_type,a.street_name,a.street_number,a.floor,a.letter,a.zip_code,a.city
                FROM d_user u LEFT JOIN zone z ON z.id=u.preferred_zone_id LEFT JOIN address a ON a.user_id=u.id WHERE u.id=?
                """,rs->{if(!rs.next())return null;Map<String,Object> m=new LinkedHashMap<>();
            m.put("id",rs.getLong("id"));m.put("nombrePila",rs.getString("first_name"));m.put("apellidos",rs.getString("last_name"));
            m.put("nombre",rs.getString("first_name")+" "+rs.getString("last_name"));m.put("email",rs.getString("email"));
            m.put("dni",rs.getString("id_card"));m.put("telefono",rs.getString("phone_number"));m.put("estado",rs.getString("status"));
            m.put("altaEn",rs.getObject("created_at"));m.put("zonaPreferida",rs.getString("preferred_zone"));
            if(rs.getString("zip_code")!=null)m.put("direccion",new DireccionDto(rs.getString("street_type"),rs.getString("street_name"),
                    rs.getString("street_number"),rs.getString("floor"),rs.getString("letter"),rs.getString("zip_code"),rs.getString("city"),null,null));
            return m;},id);
        if(user==null)throw ApiException.notFound("Usuario no encontrado");
        user.put("roles",userRoles(id));
        user.put("pedidos",count("SELECT COUNT(*) FROM collection WHERE citizen_id="+id));
        user.put("zonasAsignadas",jdbc.query("SELECT d.day_of_week,z.id,z.name FROM zone_day_collector d JOIN zone z ON z.id=d.zone_id WHERE d.collector_id=? ORDER BY d.day_of_week",
                (rs,row)->Map.of("diaSemana",rs.getInt(1),"zonaId",rs.getLong(2),"zona",rs.getString(3)),id));
        user.put("co2EvitadoKg",jdbc.queryForObject("SELECT COALESCE(SUM(s.co2_kg),0) FROM settlement s JOIN collection c ON c.id=s.collection_id WHERE c.citizen_id=?",BigDecimal.class,id));
        return user;
    }

    @Transactional public Map<String,Object> editUser(Long id,EditarUsuarioRequest request) {
        if(jdbc.queryForObject("SELECT COUNT(*) FROM d_user WHERE id=?",Integer.class,id)==0)throw ApiException.notFound("Usuario no encontrado");
        try {jdbc.update("UPDATE d_user SET first_name=?,last_name=?,email=?,phone_number=?,updated_at=now() WHERE id=?",
                request.nombre(),request.apellidos(),request.email().toLowerCase(),request.telefono(),id);}catch(org.springframework.dao.DuplicateKeyException ex){
            throw ApiException.conflict("USUARIO_DUPLICADO","El email o teléfono ya está registrado");}
        if(request.direccion()!=null){DireccionDto a=request.direccion();
            int updated=jdbc.update("""
                    INSERT INTO address(user_id,street_type,street_name,street_number,floor,letter,zip_code,city,lat,lng)
                    SELECT ?,?,?,?,?,?,z.code,?,z.lat,z.lng FROM zip_code z WHERE z.code=?
                    ON CONFLICT(user_id) DO UPDATE SET street_type=EXCLUDED.street_type,street_name=EXCLUDED.street_name,
                    street_number=EXCLUDED.street_number,floor=EXCLUDED.floor,letter=EXCLUDED.letter,zip_code=EXCLUDED.zip_code,
                    city=EXCLUDED.city,lat=EXCLUDED.lat,lng=EXCLUDED.lng""",
                    id,a.tipoVia(),a.nombreVia(),a.numero(),a.piso(),a.letra(),a.ciudad(),a.codigoPostal());
            if(updated==0)throw ApiException.fieldError("VALIDATION_ERROR","Código postal desconocido","direccion.codigoPostal","El código postal no existe");}
        return user(id);
    }

    @Transactional public Map<String,Object> setUserStatus(Long id,boolean active) {
        int changed=jdbc.update("UPDATE d_user SET status=?,updated_at=now() WHERE id=?",active?"ACTIVA":"SUSPENDIDA",id);
        if(changed==0)throw ApiException.notFound("Usuario no encontrado");
        if(!active)jdbc.update("UPDATE refresh_token SET revoked_at=now() WHERE user_id=? AND revoked_at IS NULL",id);
        return user(id);
    }

    @Transactional public Map<String,Object> createCollector(CrearRecolectorRequest request) {
        String password=temporaryPassword();Long id;
        try{id=jdbc.queryForObject("""
                INSERT INTO d_user(first_name,last_name,email,id_card,phone_number,password_hash,must_change_password)
                VALUES (?,?,?,?,?,?,true) RETURNING id""",Long.class,request.nombre(),request.apellidos(),request.email().toLowerCase(),request.dni(),request.telefono(),passwordEncoder.encode(password));}
        catch(org.springframework.dao.DuplicateKeyException ex){throw ApiException.conflict("USUARIO_DUPLICADO","Email, DNI o teléfono ya registrado");}
        jdbc.update("INSERT INTO user_role(user_id,role) VALUES (?,'RECOLECTOR')",id);
        return Map.of("usuario",user(id),"passwordProvisional",password);
    }

    @Transactional public Map<String,Object> temporaryPassword(Long id) {
        String password=temporaryPassword();int n=jdbc.update("UPDATE d_user SET password_hash=?,must_change_password=true,updated_at=now() WHERE id=?",
                passwordEncoder.encode(password),id);if(n==0)throw ApiException.notFound("Usuario no encontrado");
        jdbc.update("UPDATE refresh_token SET revoked_at=now() WHERE user_id=? AND revoked_at IS NULL",id);
        return Map.of("email",jdbc.queryForObject("SELECT email FROM d_user WHERE id=?",String.class,id),"passwordProvisional",password);
    }

    private String temporaryPassword(){byte[] bytes=new byte[12];RANDOM.nextBytes(bytes);return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);}
    private List<String> userRoles(Long id){return jdbc.query("SELECT role FROM user_role WHERE user_id=? ORDER BY role",(rs,row)->rs.getString(1),id);}
    private long count(String sql){Long n=jdbc.queryForObject(sql,Long.class);return n==null?0:n;}
    private Object[] concat(Object[] args,Object... extra){Object[] all=java.util.Arrays.copyOf(args,args.length+extra.length);System.arraycopy(extra,0,all,args.length,extra.length);return all;}
    private <T> PageResponse<T> page(List<T> rows,long total,int number,int size){return new PageResponse<>(rows,total,(int)Math.ceil(total/(double)size),number,size);}

    private void ensureZone(Long id){if(count("SELECT COUNT(*) FROM zone WHERE id="+id+" AND active=true")==0)throw ApiException.notFound("Zona no encontrada");}
    private void validateSlot(FranjaRequest r){if(r.horaInicio()==null||r.horaFin()==null||!r.horaFin().isAfter(r.horaInicio()))
        throw ApiException.fieldError("VALIDATION_ERROR","Franja no válida","horaFin","Debe ser posterior a horaInicio");}
    private Map<String,Object> slot(Long id){return jdbc.query("SELECT id,zone_id,day_of_week,start_time,end_time,capacity FROM time_slot WHERE id=?",
            rs->{if(!rs.next())throw ApiException.notFound("Franja no encontrada");return Map.of("id",rs.getLong(1),"zonaId",rs.getLong(2),
                    "diaSemana",rs.getInt(3),"horaInicio",rs.getObject(4),"horaFin",rs.getObject(5),"capacidad",rs.getInt(6));},id);}
    private void assignPostalCodes(Long zoneId,List<String> codes){
        for(String cp:codes){
            List<Long> assignment=jdbc.query("SELECT zone_id FROM zip_code WHERE code=?",(rs,row)->{
                Object assigned=rs.getObject(1);return assigned==null?null:((Number)assigned).longValue();},cp);
            if(assignment.isEmpty())throw ApiException.fieldError("VALIDATION_ERROR","Código postal no válido","codigosPostales","CP no existente: "+cp);
            if(assignment.getFirst()!=null&&!assignment.getFirst().equals(zoneId))
                throw ApiException.conflict("CP_ASIGNADO","El código postal "+cp+" ya pertenece a otra zona");
            int found=jdbc.update("UPDATE zip_code SET zone_id=? WHERE code=?",zoneId,cp);
            if(found==0)throw ApiException.fieldError("VALIDATION_ERROR","Código postal no válido","codigosPostales","CP no existente: "+cp);}
    }
    private Map<String,Object> adminCollectionRow(java.sql.ResultSet rs)throws java.sql.SQLException{
        Map<String,Object> m=new LinkedHashMap<>();m.put("id",rs.getLong("id"));m.put("codigo",rs.getString("code"));m.put("estado",rs.getString("status"));
        m.put("fecha",rs.getObject("collection_date"));m.put("horaInicio",rs.getObject("start_time"));m.put("horaFin",rs.getObject("end_time"));
        m.put("zona",rs.getString("zone"));m.put("direccion",rs.getString("addr_street_type")+" "+rs.getString("addr_street_name")+" "+rs.getString("addr_street_number"));
        m.put("ciudadano",rs.getString("citizen_first")+" "+rs.getString("citizen_last"));
        Object collector=rs.getObject("collector_id");m.put("recolector",collector==null?null:Map.of("id",((Number)collector).longValue(),
                "nombre",rs.getString("collector_first")+" "+rs.getString("collector_last")));
        m.put("numBolsas",rs.getInt("num_bags"));m.put("totalEnvases",rs.getInt("total_containers"));m.put("tieneIncidencias",rs.getBoolean("has_incidents"));return m;
    }
    private Map<String,Object> zoneRequest(java.sql.ResultSet rs)throws java.sql.SQLException{
        Map<String,Object> m=new LinkedHashMap<>();m.put("id",rs.getLong("id"));m.put("estado",rs.getString("status"));
        m.put("creadaEn",rs.getObject("created_at"));m.put("motivo",rs.getString("reason"));m.put("resueltaEn",rs.getObject("resolved_at"));
        m.put("respuesta",rs.getString("response"));m.put("recolector",Map.of("id",rs.getLong("collector_id"),"nombre",rs.getString("first_name")+" "+rs.getString("last_name")));
        m.put("zonaActual",rs.getString("current_zone"));m.put("zonaSolicitada",rs.getString("requested_zone"));return m;
    }
    private Map<String,Object> zoneRequestById(Long id){return zoneRequests(null).stream().filter(r->r.get("id").equals(id)).findFirst().orElseThrow(()->ApiException.notFound("Solicitud no encontrada"));}
}
