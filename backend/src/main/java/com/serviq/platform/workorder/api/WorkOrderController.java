package com.serviq.platform.workorder.api;
import com.serviq.platform.workorder.application.WorkOrderService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.*;

@RestController
@RequestMapping("/api/v1/work-orders")
public class WorkOrderController {
    private final WorkOrderService service;
    public WorkOrderController(WorkOrderService service) { this.service = service; }

    @PostMapping
    public ResponseEntity<WorkOrderResponse> open(@Valid @RequestBody OpenWorkOrderRequest request) {
        WorkOrderResponse result = service.open(request);
        return ResponseEntity.created(URI.create("/api/v1/work-orders/" + result.workOrderId())).body(result);
    }
    @GetMapping("/{id}")
    public WorkOrderResponse get(@PathVariable UUID id) { return service.get(id); }
    @GetMapping
    public List<WorkOrderResponse> list(@RequestParam UUID requestId) { return service.byRequest(requestId); }
    @PatchMapping("/{id}/status")
    public WorkOrderResponse status(@PathVariable UUID id, @Valid @RequestBody WorkOrderStatusRequest request) {
        return service.transition(id, request);
    }
    @PatchMapping("/{id}/details")
    public WorkOrderResponse details(@PathVariable UUID id, @RequestBody WorkOrderDetailsRequest request) {
        return service.updateDetails(id, request);
    }
    @PostMapping("/{id}/notes")
    public ResponseEntity<Map<String, Object>> note(@PathVariable UUID id, @Valid @RequestBody WorkOrderNoteRequest request) {
        UUID noteId = service.addNote(id, request);
        return ResponseEntity.created(URI.create("/api/v1/work-orders/"+id+"/notes")).body(Map.of("noteId",noteId));
    }
    @GetMapping("/{id}/notes")
    public List<Map<String,Object>> notes(@PathVariable UUID id) { return service.notes(id); }
    @PostMapping("/{id}/lines")
    public ResponseEntity<Map<String,Object>> line(@PathVariable UUID id, @Valid @RequestBody WorkOrderLineRequest request) {
        UUID lineId = service.addLine(id, request);
        return ResponseEntity.created(URI.create("/api/v1/work-orders/"+id+"/lines")).body(Map.of("lineId",lineId));
    }
    @GetMapping("/{id}/lines")
    public List<Map<String,Object>> lines(@PathVariable UUID id) { return service.lines(id); }
    @GetMapping("/{id}/history")
    public List<Map<String,Object>> history(@PathVariable UUID id) { return service.history(id); }
}
