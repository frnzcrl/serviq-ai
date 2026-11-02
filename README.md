
# Serviq AI

**Service operations, intelligently connected.**

Serviq AI is an ongoing full-stack development project focused on service appointment management, customer records, and workflow automation.

The platform combines a Spring Boot backend with a React frontend and n8n-based integrations. Its architecture is being developed with a focus on reliable event delivery, maintainability, and extensibility.

## Technology Stack

| Layer | Technology |
|---|---|
| Backend | Java 21, Spring Boot, Maven |
| Frontend | React, TypeScript, Vite |
| Database | MySQL |
| Database Migrations | Flyway |
| Workflow Automation | n8n |
| API Architecture | REST |
| Integration | HTTP Webhooks, JSON |
| Development Tools | IntelliJ IDEA, VS Code, Git, GitHub |

## Implemented Capabilities

### Customer Management
- Customer data persistence using MySQL
- Database schema management through Flyway
- Backend REST API for customer operations

### Appointment Event Processing
- Backend processing of appointment-related events
- Integration between Spring Boot and n8n
- HTTP-based event delivery to automation workflows

### Background Event Delivery
- Scheduled background delivery worker
- Asynchronous delivery of integration events
- Tracking of successful webhook delivery responses

### Workflow Automation
- n8n integration for external workflow execution
- Event-driven communication between backend services and automation workflows
- Foundation for extending integrations without tightly coupling external workflow logic to the core backend

## Work in Progress

- Duplicate appointment event detection and handling
- Improving event-processing reliability and recovery
- Expanding appointment management capabilities
- Building the frontend experience

## Planned Capabilities

- Customer service history and work orders
- Staff scheduling and assignment
- Automated customer notifications
- AI-assisted customer communication
- Service estimates and operational reporting
- Additional third-party integrations
- Authentication and access control

These features are planned and are not represented as completed functionality.

## Architecture Overview

```text
React + TypeScript
        |
        v
Spring Boot REST API
        |
        +---- MySQL Database
        |
        +---- Background Event Worker
                    |
                    v
                HTTP Webhook
                    |
                    v
                   n8n
                    |
                    v
             Automation Workflows
```

## Engineering Focus

Serviq AI is also a practical project for developing and demonstrating backend engineering concepts, including:

- Separation of business logic and integration workflows
- Database versioning and schema migrations
- Background processing
- Event-driven integration patterns
- Delivery status tracking
- Duplicate-event prevention
- Error handling and recovery
- Maintainable API and service design

## Development Status

**Active Development**

Serviq AI is not yet a production-ready SaaS application. Features are being implemented and tested incrementally, with particular attention to backend reliability and workflow integration.

## Repository

Developed and maintained by [Frenz Carl Asibuque](https://github.com/frnzcrl).

GitHub: https://github.com/frnzcrl/serviq-ai
