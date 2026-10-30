# Serviq AI — Database Architecture

## 1. Design principles

- PostgreSQL is the source of truth for operational data.
- Spring Boot owns business rules and database transactions.
- AI interprets requests but cannot bypass booking rules.
- n8n handles external workflows and notifications.
- Flyway manages versioned database migrations.
- All operational records use UUID primary keys.
- Timestamps use TIMESTAMPTZ and UTC storage.
- Customer-facing reference numbers are separate from UUIDs.
- Business history must remain traceable.
- Sensitive customer data must not appear in application logs.

## 2. Domain boundaries

### Customer Management
- customer
- customer_address
- service_asset

Customers may own multiple assets and have multiple addresses.
Email addresses are not assumed to be globally unique.

### Service Intake
- service_request
- service_request_attachment

A service request records a reported issue and its origin.
Requests may originate from staff, customers, or AI-assisted intake.

### Workforce
- technician
- technician_skill
- technician_working_hours
- technician_time_off

Technician availability depends on working hours, absences,
skills, assigned work, and active bookings.

### Scheduling
- appointment
- appointment_technician

Appointments have start and end times.
A booking may have one or multiple assigned technicians.
Active bookings must not overlap for the same technician.
Cancelled bookings must not block future availability.

### Work Execution
- work_order
- work_order_note
- work_order_status_history

Work orders have controlled lifecycle transitions.
Status history is append-only.
Notes preserve author and creation time.

### AI Service Advisor
- ai_interaction

Store structured interpretations, confidence or review state,
and references to the resulting service request.
Minimize stored personal or sensitive prompt data.

### Automation
- outbox_event
- automation_execution

Business transactions write outbox events atomically.
A separate dispatcher delivers events to n8n.
Delivery may be retried, so consumers must be idempotent.

## 3. Core relationships

customer 1:N customer_address
customer 1:N service_asset
customer 1:N service_request

service_asset 1:N service_request
service_request 1:N work_order
work_order 1:N appointment
appointment N:M technician (via appointment_technician)

work_order 1:N work_order_status_history
work_order 1:N work_order_note

service_request 1:N ai_interaction
outbox_event 1:N automation_execution

## 4. Business invariants

1. A service asset must belong to a valid customer.
2. A request's asset must belong to its customer.
3. An appointment must have an end time after its start time.
4. A technician cannot have overlapping active assignments.
5. Booking requires eligible technician availability.
6. Work-order transitions must follow approved state rules.
7. Service history must not be silently overwritten.
8. A failed notification must not roll back a valid booking.
9. Repeated API requests must not create duplicate bookings
   when an idempotency key is supplied.
10. Data changes requiring multiple writes must be atomic.

## 5. Scheduling concurrency

Use PostgreSQL range types and an exclusion constraint to
prevent overlapping active technician assignments.

Store occupied time ranges for each technician assignment.
Apply a GiST exclusion constraint on technician ID and range.
Exclude cancelled or released assignments.

Application-level availability checks improve user experience,
but the database constraint is the final concurrency safeguard.

Use a bounded transaction for booking creation.
Translate conflicts into HTTP 409 responses.

## 6. Indexing principles

Index based on actual query patterns:

- Customer lookup by identity and contact information.
- Assets belonging to a customer.
- Requests by customer, status, and creation time.
- Work orders by status, priority, and assignment.
- Appointments by start time and technician.
- Outbox events awaiting delivery.

Use composite and partial indexes where justified.
Avoid indexing every column.

## 7. API conventions

Base path: /api/v1

- Validate requests at API boundaries.
- Use DTOs rather than exposing JPA entities.
- Paginate collections.
- Return consistent error structures.
- Use appropriate 400, 404, 409, and 422 responses.
- Keep authorization separate from customer identity.
- Document APIs using OpenAPI.

## 8. Migration policy

- Never modify a migration already applied to a shared DB.
- Add new versioned Flyway migrations for schema changes.
- Review constraints, indexes, and rollback implications.
- Validate migrations against an empty database.
- Preserve existing data when evolving a schema.

## 9. Implementation order

1. Extend the existing customer foundation safely.
2. Establish addresses and registered service assets.
3. Add service requests and lifecycle validation.
4. Add technicians, skills, and working schedules.
5. Add appointments and concurrency protection.
6. Add work orders and status history.
7. Add outbox delivery and n8n integration.
8. Add validated AI Service Advisor workflows.
