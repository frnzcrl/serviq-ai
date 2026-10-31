package com.serviq.platform.workorder.application;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class WorkOrderServiceTest {
    @Test void validTransitions() {
        assertTrue(WorkOrderService.allowed("OPEN","IN_PROGRESS"));
        assertTrue(WorkOrderService.allowed("IN_PROGRESS","COMPLETED"));
        assertTrue(WorkOrderService.allowed("IN_PROGRESS","ON_HOLD"));
        assertTrue(WorkOrderService.allowed("ON_HOLD","IN_PROGRESS"));
    }
    @Test void rejectInvalidTransitions() {
        assertFalse(WorkOrderService.allowed("OPEN","COMPLETED"));
        assertFalse(WorkOrderService.allowed("COMPLETED","IN_PROGRESS"));
        assertFalse(WorkOrderService.allowed("CANCELLED","OPEN"));
        assertFalse(WorkOrderService.allowed("OPEN","OPEN"));
    }
}
