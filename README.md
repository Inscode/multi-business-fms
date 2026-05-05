# Multi-Business Financial Management System

A production-grade, role-based financial operations platform built to manage billing, stock, cheque tracking, payroll, and cross-business reporting across multiple business units.

> Built to solve a real operational problem — preventing revenue leakage by tracking every bill from entry to confirmation, with full worker accountability at every step.

---

## The Problem This Solves

Workers collect money from customers and hand bills and cash or cheques to the business owner each evening. Without a tracking system:

- Bills go missing and money gets pocketed
- No way to verify if all bills are accounted for
- No visibility into who holds which bill at any point
- Stock levels unknown between monthly counts
- Cheque clearance tracked on paper, bounce recoveries forgotten

This system closes every one of those gaps.

---

## Features

### Bill Management

- Cash and cheque bill entry by accountant
- Bill assignment to specific workers for accountability
- Multi-step confirmation flow: Entered → Assigned → Received → Confirmed
- Owner confirms receipt each evening with timestamp
- Draft bills for immediate walk-in customers, converted to real bills later
- Manual bills linked to system bill numbers for month-end reconciliation
- Unconfirmed bills flagged automatically — worker is accountable

### Cheque Tracking

- Full cheque lifecycle: Details Pending → Complete → Deposited → Cleared → Bounced
- Owner confirms receipt without needing to enter bank details
- Accountant fills cheque details next morning
- Automatic bounce recovery record created on bounce
- Recovery tracked: Open → Partially Recovered → Recovered → Written Off

### Stock Management 

- Supplier stock receipts tracked with cost
- System bill monthly summary entry with bill number references
- Manual bill spreadsheet-style bulk entry per customer
- Combined monthly total for bulk purchase discount claim
- Stock auto-deducted from both system and manual entries
- Retail shop stock transfers tracked separately
- Return management: Resellable items restored to stock, damaged items logged

### Payroll and Attendance

- Daily attendance entry by accountant (present, absent, late)
- Leave management with types and no-pay flag
- Monthly salary calculation with all deductions
- Worker advance and loan tracking with salary deduction

### Daily Operations

- Petty cash and daily expense logging by category
- Owner confirms expenses each evening
- Supplier payment tracking (outgoing cash and cheques)
- Daily cash reconciliation — expected vs confirmed gap report
- Inter-business transfer logging with settlement tracking

### Reporting

- Cross-business P&L per month
- Monthly stock report: opening, received, sold, closing
- Worker bill accountability report
- Cheque clearance schedule
- Manual bill cross-check view per customer

### Access Control

- Three roles: Admin, Owner, Accountant
- JWT-based stateless authentication
- Role-based route protection on every endpoint
- All actions logged in audit trail with user and timestamp

---

## Tech Stack

### Backend

| Technology      | Purpose                          |
| --------------- | -------------------------------- |
| Java 21         | Language                         |
| Spring Boot 3.5 | Application framework            |
| Spring Security | Authentication and authorisation |
| Spring Data JPA | Database access layer            |
| JWT (jjwt 0.12) | Stateless token authentication   |
| Flyway          | Versioned database migrations    |
| Lombok          | Boilerplate reduction            |
| MapStruct       | DTO mapping                      |
| Bean Validation | Request validation               |
| PostgreSQL      | Primary database                 |

### Frontend

| Technology        | Purpose                      |
| ----------------- | ---------------------------- |
| Angular 18        | UI framework                 |
| Angular Material  | Component library            |
| NgRx              | State management             |
| Reactive Forms    | Form handling and validation |
| ApexCharts        | Dashboard charts             |
| HTTP Interceptors | JWT attachment               |
| Route Guards      | Role-based page protection   |

### Infrastructure

| Technology     | Purpose                 |
| -------------- | ----------------------- |
| Docker         | Containerisation        |
| Docker Compose | Local development stack |
| GitHub Actions | CI/CD pipeline          |
| Vercel         | Frontend hosting        |
| Render         | Backend hosting         |
| Neon           | Managed PostgreSQL      |

---

## Architecture

```
┌─────────────────────────────────────────────┐
│           Angular 18 (Vercel)               │
│   Components → Services → HTTP Client       │
└──────────────────┬──────────────────────────┘
                   │ HTTPS + JWT
┌──────────────────▼──────────────────────────┐
│         Spring Boot 3.5 (Render)            │
│  Controller → Service → Repository          │
│  Spring Security JWT Filter Chain           │
└──────────────────┬──────────────────────────┘
                   │ JPA / SQL
┌──────────────────▼──────────────────────────┐
│          PostgreSQL (Neon)                  │
│          27 tables, Flyway managed          │
└─────────────────────────────────────────────┘
```

---

## Project Structure

```
multi-business-fms/
├── backend/                          # Spring Boot
│   ├── src/main/java/com/multi/finance/
│   │   ├── config/                   # Security, CORS config
│   │   ├── controller/               # REST endpoints
│   │   ├── dto/
│   │   │   ├── request/              # Incoming request bodies
│   │   │   └── response/             # Outgoing response bodies
│   │   ├── entity/                   # JPA entities
│   │   ├── exception/                # Global error handling
│   │   ├── repository/               # Spring Data repositories
│   │   ├── security/                 # JWT filter, auth config
│   │   ├── service/
│   │   │   └── impl/                 # Business logic
│   │   └── util/                     # JWT utility
│   ├── src/main/resources/
│   │   ├── db/migration/             # Flyway SQL files
│   │   │   ├── V1__initial_schema.sql
│   │   │   ├── V2__convert_enums_to_varchar.sql
│   │   │   ├── V3__remove_assignment_column.sql
│   │   │   └── V4__update_worker_structure.sql
│   │   └── application.yml
│   ├── Dockerfile
│   └── pom.xml
│
├── frontend/                         # Angular 18
│   ├── src/
│   │   ├── app/
│   │   │   ├── core/
│   │   │   │   ├── guards/           # Route guards
│   │   │   │   ├── interceptors/     # JWT interceptor
│   │   │   │   └── services/         # Auth, API services
│   │   │   ├── shared/
│   │   │   │   ├── components/       # Shared UI components
│   │   │   │   └── models/           # TypeScript interfaces
│   │   │   ├── features/
│   │   │   │   ├── auth/             # Login screen
│   │   │   │   ├── dashboard/        # Admin dashboard
│   │   │   │   ├── bills/            # Bill entry and list
│   │   │   │   ├── cheques/          # Cheque tracking
│   │   │   │   ├── stock/            # Stock management
│   │   │   │   ├── workers/          # Worker management
│   │   │   │   ├── payroll/          # Salary and attendance
│   │   │   │   ├── owner/            # Owner evening screen
│   │   │   │   └── reports/          # P&L and reports
│   │   │   └── app.routes.ts
│   │   └── environments/
│   ├── Dockerfile
│   └── package.json
│
├── docker-compose.yml                # Full local stack
├── .github/
│   └── workflows/
│       └── ci.yml                    # GitHub Actions
└── README.md
```

---

## Getting Started

### Prerequisites

```
Java 21
Node.js 20+
Docker Desktop
```

### Run with Docker Compose

```bash
git clone https://github.com/yourusername/multi-business-fms.git
cd multi-business-fms
docker-compose up
```

This starts PostgreSQL, Spring Boot backend, and Angular frontend together.

```
Frontend  → http://localhost:4200
Backend   → http://localhost:8080
Database  → localhost:5432
```

### Run Manually

**Backend**

```bash
cd backend
# Set environment variables
export DB_URL=jdbc:postgresql://localhost:5432/financedb
export DB_USERNAME=postgres
export DB_PASSWORD=postgres
export JWT_SECRET=your-secret-key

mvn spring-boot:run
```

**Frontend**

```bash
cd frontend
npm install
ng serve
```

---

## API Endpoints

### Authentication

```
POST /api/auth/login          → Login, returns JWT token
```

### Users (Admin only)

```
POST   /api/users             → Create user
GET    /api/users             → List all users
PUT    /api/users/{id}        → Update user
DELETE /api/users/{id}        → Deactivate user
PATCH  /api/users/{id}/activate → Activate user
```

### Workers (Admin only for create/update)

```
POST   /api/workers                          → Create worker
GET    /api/workers                          → List all workers
GET    /api/workers/worker-type/{type}       → Filter by type
PUT    /api/workers/{id}                     → Update worker
DELETE /api/workers/{id}                     → Deactivate worker
PATCH  /api/workers/{id}/activate            → Activate worker
```

### Bills

```
POST   /api/bills             → Create bill
GET    /api/bills             → List bills (filterable)
GET    /api/bills/today       → Today's bills for owner screen
PUT    /api/bills/{id}/assign → Assign to worker
PATCH  /api/bills/{id}/receive   → Mark received
PATCH  /api/bills/{id}/confirm   → Owner confirms
```

### Cheques

```
GET    /api/cheques/pending-details  → Details needed
PATCH  /api/cheques/{id}/details     → Enter cheque details
PATCH  /api/cheques/{id}/deposit     → Mark deposited
PATCH  /api/cheques/{id}/clear       → Mark cleared
PATCH  /api/cheques/{id}/bounce      → Mark bounced
```

---

## Database Schema

27 tables managed by Flyway migrations.

Key tables:

```
users                    → system users with roles
workers                  → delivery, sales, shop workers
bills                    → all bill types and statuses
bill_items               → line items per bill
cheque_details           → cheque lifecycle per bill
bounce_recoveries        → bounce recovery tracking
returns                  → customer return management
stock_receipts           → supplier deliveries
system_bill_summaries    → monthly Rainco system summary
petty_cash               → daily expense tracking
salary_records           → monthly payroll
attendance               → daily worker attendance
leave_records            → leave management
worker_advances          → salary advance tracking
inter_business_transfers → cross-business fund transfers
audit_log                → full action history
```

---

## CI/CD Pipeline

Every push to `main` triggers GitHub Actions:

```
1. Run backend tests (JUnit 5 + Mockito)
2. Build Spring Boot JAR
3. Run frontend tests (Jasmine)
4. Build Angular production bundle
5. Deploy backend to Render
6. Deploy frontend to Vercel
```

---

## Testing

```bash
# Backend tests
cd backend
mvn test

# Frontend tests
cd frontend
ng test
```

---

## Environment Variables

### Backend

```
DB_URL          → PostgreSQL JDBC URL
DB_USERNAME     → Database username
DB_PASSWORD     → Database password
JWT_SECRET      → Minimum 32 character secret key
JWT_EXPIRATION  → Token expiry in milliseconds (86400000 = 24h)
```

### Frontend

```
API_URL         → Backend base URL
```

---

## Roadmap

- [ ] Natural language bill query using AI
- [ ] Email notifications for cheque clearance
- [ ] Detailed retail shop stock management
- [ ] Mobile-optimised owner confirmation screen
- [ ] Kafka event streaming for scale

---

## License

MIT License — free to use and modify.

---

## Author

Built by Insaf Ahmedh
[LinkedIn](https://www.linkedin.com/in/insaf-ahmedh/) · [GitHub](https://inscode.github.io/)
