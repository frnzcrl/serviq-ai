package com.serviq.platform.workorder.application;

import com.serviq.platform.workorder.api.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;

@Service
public class WorkOrderService {
    private final NamedParameterJdbcTemplate jdbc;
    public WorkOrderService(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    private static MapSqlParameterSource args(Object... values) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        for (int i = 0; i < values.length; i += 2) p.addValue((String) values[i], values[i+1]);
        return p;
    }
    private static ResponseStatusException invalid(String message) { return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, message); }
    private static ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
    private static String trim(String value) { return value == null ? null : value.trim(); }
    private static String required(String value, String name) {
        String cleaned = trim(value);
        if (cleaned == null || cleaned.isEmpty()) throw invalid(name + " is required");
        return cleaned;
    }
    private boolean exists(String sql, MapSqlParameterSource p) {
        return !jdbc.query(sql, p, (rs, n) -> 1).isEmpty();
    }
    private void ensureExists(UUID id) {
        if (!exists("SELECT 1 FROM serviq_work_order WHERE work_order_id=:id", args("id", id)))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Work order not found");
    }
    private WorkOrderResponse map(java.sql.ResultSet rs, int n) throws java.sql.SQLException {
        return new WorkOrderResponse(rs.getObject("work_order_id",UUID.class),
            rs.getLong("work_order_number"), rs.getObject("request_id",UUID.class),
            rs.getString("work_order_status"), rs.getString("diagnosis"), rs.getString("resolution_notes"),
            rs.getObject("opened_at", OffsetDateTime.class), rs.getObject("started_at", OffsetDateTime.class),
            rs.getObject("completed_at", OffsetDateTime.class), rs.getObject("updated_at", OffsetDateTime.class));
    }
    private static final String SELECT = """
        SELECT work_order_id, work_order_number, request_id, work_order_status,
               diagnosis, resolution_notes, opened_at, started_at, completed_at, updated_at
        FROM serviq_work_order
        """;
    @Transactional(readOnly=true)
    public WorkOrderResponse get(UUID id) {
        return jdbc.query(SELECT+" WHERE work_order_id=:id",args("id",id),this::map).stream()
            .findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"Work order not found"));
    }
    @Transactional(readOnly=true)
    public List<WorkOrderResponse> byRequest(UUID requestId) {
        return jdbc.query(SELECT+" WHERE request_id=:requestId",args("requestId",requestId),this::map);
    }
    @Transactional
    public WorkOrderResponse open(OpenWorkOrderRequest input) {
        UUID requestId = input.requestId();
        MapSqlParameterSource p=args("requestId",requestId);
        List<String> statuses = jdbc.query("SELECT request_status FROM serviq_service_request WHERE request_id=:requestId FOR UPDATE",p,(rs,n)->rs.getString(1));
        if (statuses.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Service request not found");
        if (Set.of("CANCELLED","RESOLVED").contains(statuses.getFirst())) throw invalid("Service request is closed");
        if (exists("SELECT 1 FROM serviq_work_order WHERE request_id=:requestId",p)) throw conflict("Work order already exists for this request");
        if (input.appointmentId()!=null) {
            p.addValue("appointmentId",input.appointmentId());
            if (!exists("SELECT 1 FROM serviq_appointment WHERE appointment_id=:appointmentId AND request_id=:requestId AND appointment_status <> 'CANCELLED'",p))
                throw invalid("Appointment must belong to this request and not be cancelled");
        }
        UUID id=UUID.randomUUID(); p.addValue("id",id).addValue("historyId", UUID.randomUUID());
        jdbc.update("INSERT INTO serviq_work_order(work_order_id,request_id) VALUES (:id,:requestId)",p);
        if (input.appointmentId()!=null) jdbc.update("INSERT INTO serviq_work_order_appointment(work_order_id,appointment_id) VALUES (:id,:appointmentId)",p);
        jdbc.update("""
            INSERT INTO serviq_work_order_status_history
            (status_history_id,work_order_id,previous_status,new_status,changed_by,change_reason)
            VALUES (:historyId,:id,NULL,'OPEN','system','Work order created')
            """,p);
        return get(id);
    }
    static boolean allowed(String from,String to) {
        return switch(from) {
            case "OPEN" -> Set.of("IN_PROGRESS","CANCELLED").contains(to);
            case "IN_PROGRESS" -> Set.of("ON_HOLD","COMPLETED","CANCELLED").contains(to);
            case "ON_HOLD" -> Set.of("IN_PROGRESS","CANCELLED").contains(to);
            default -> false;
        };
    }
    @Transactional
    public WorkOrderResponse transition(UUID id,WorkOrderStatusRequest input) {
        MapSqlParameterSource p=args("id",id);
        List<String> current=jdbc.query("SELECT work_order_status FROM serviq_work_order WHERE work_order_id=:id FOR UPDATE",p,(rs,n)->rs.getString(1));
        if (current.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Work order not found");
        String next=required(input.status(),"status").toUpperCase(Locale.ROOT);
        String previous=current.getFirst();
        if (!allowed(previous,next)) throw conflict("Invalid work order transition: "+previous+" -> "+next);
        p.addValue("next",next).addValue("previous",previous).addValue("actor",required(input.changedBy(),"changedBy"))
            .addValue("reason",trim(input.reason())).addValue("historyId",UUID.randomUUID());
        jdbc.update("""
            UPDATE serviq_work_order SET work_order_status=:next, updated_at=now(),
            started_at=CASE WHEN :next='IN_PROGRESS' AND started_at IS NULL THEN now() ELSE started_at END,
            completed_at=CASE WHEN :next='COMPLETED' THEN now() ELSE completed_at END
            WHERE work_order_id=:id
            """,p);
        jdbc.update("""
            INSERT INTO serviq_work_order_status_history
            (status_history_id,work_order_id,previous_status,new_status,change_reason,changed_by)
            VALUES (:historyId,:id,:previous,:next,:reason,:actor)
            """,p);
        // Synchronize high-level request status when work begins or ends.
        if (next.equals("IN_PROGRESS")) jdbc.update("""
            UPDATE serviq_service_request SET request_status='IN_PROGRESS',updated_at=now()
            WHERE request_id=(SELECT request_id FROM serviq_work_order WHERE work_order_id=:id)
              AND request_status NOT IN ('RESOLVED','CANCELLED')
            """,p);
        if (next.equals("COMPLETED")) jdbc.update("""
            UPDATE serviq_service_request SET request_status='RESOLVED',updated_at=now()
            WHERE request_id=(SELECT request_id FROM serviq_work_order WHERE work_order_id=:id)
            """,p);
        return get(id);
    }
    @Transactional
    public WorkOrderResponse updateDetails(UUID id,WorkOrderDetailsRequest input) {
        MapSqlParameterSource p=args("id",id);
        List<String> statuses=jdbc.query("SELECT work_order_status FROM serviq_work_order WHERE work_order_id=:id FOR UPDATE",p,(rs,n)->rs.getString(1));
        if(statuses.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Work order not found");
        if(Set.of("COMPLETED","CANCELLED").contains(statuses.getFirst())) throw conflict("Closed work order cannot be edited");
        p.addValue("diagnosis",trim(input.diagnosis())).addValue("resolution",trim(input.resolutionNotes()));
        jdbc.update("UPDATE serviq_work_order SET diagnosis=:diagnosis,resolution_notes=:resolution,updated_at=now() WHERE work_order_id=:id",p);
        return get(id);
    }
    @Transactional
    public UUID addNote(UUID id,WorkOrderNoteRequest input) {
        ensureExists(id);
        UUID noteId=UUID.randomUUID();
        jdbc.update("""
            INSERT INTO serviq_work_order_note(note_id,work_order_id,note_text,created_by)
            VALUES (:noteId,:id,:text,:author)
            """,args("noteId",noteId,"id",id,"text",required(input.text(),"text"),"author",required(input.createdBy(),"createdBy")));
        return noteId;
    }
    @Transactional
    public UUID addLine(UUID id,WorkOrderLineRequest input) {
        MapSqlParameterSource p=args("id",id);
        List<String> statuses=jdbc.query("SELECT work_order_status FROM serviq_work_order WHERE work_order_id=:id FOR UPDATE",p,(rs,n)->rs.getString(1));
        if(statuses.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Work order not found");
        if(Set.of("COMPLETED","CANCELLED").contains(statuses.getFirst())) throw conflict("Closed work order cannot be charged");
        String type=required(input.type(),"type").toUpperCase(Locale.ROOT);
        if (!Set.of("LABOR","PART","FEE").contains(type)) throw invalid("type must be LABOR, PART or FEE");
        String currency=required(input.currencyCode(),"currencyCode").toUpperCase(Locale.ROOT);
        if(!currency.matches("[A-Z]{3}")) throw invalid("currencyCode must have 3 letters");
        if(input.quantity()==null || input.quantity().compareTo(BigDecimal.ZERO)<=0 || input.quantity().scale()>3)
            throw invalid("quantity must be positive with up to 3 decimal places");
        if(input.unitPrice()==null || input.unitPrice().compareTo(BigDecimal.ZERO)<0 || input.unitPrice().scale()>2)
            throw invalid("unitPrice must be non-negative with up to 2 decimal places");
        UUID lineId=UUID.randomUUID();
        p.addValue("lineId",lineId).addValue("type",type).addValue("description",required(input.description(),"description"))
            .addValue("quantity",input.quantity()).addValue("price",input.unitPrice())
            .addValue("currency",currency).addValue("notes",trim(input.notes()));
        jdbc.update("""
            INSERT INTO serviq_work_order_line(line_id,work_order_id,line_type,description,quantity,unit_price,currency_code,line_notes)
            VALUES (:lineId,:id,:type,:description,:quantity,:price,:currency,:notes)
            """,p);
        return lineId;
    }
    @Transactional(readOnly=true)
    public List<Map<String,Object>> history(UUID id) {
        ensureExists(id);
        return jdbc.queryForList("""
            SELECT status_history_id,previous_status,new_status,change_reason,changed_by,changed_at
            FROM serviq_work_order_status_history WHERE work_order_id=:id ORDER BY changed_at,status_history_id
            """,args("id",id));
    }
    @Transactional(readOnly=true)
    public List<Map<String,Object>> notes(UUID id) {
        ensureExists(id);
        return jdbc.queryForList("""
            SELECT note_id,note_text,created_by,created_at
            FROM serviq_work_order_note WHERE work_order_id=:id ORDER BY created_at,note_id
            """,args("id",id));
    }
    @Transactional(readOnly=true)
    public List<Map<String,Object>> lines(UUID id) {
        ensureExists(id);
        return jdbc.queryForList("""
            SELECT line_id,line_type,description,quantity,unit_price,currency_code,line_notes,created_at,
                   quantity*unit_price AS line_total
            FROM serviq_work_order_line WHERE work_order_id=:id ORDER BY created_at,line_id
            """,args("id",id));
    }
}
