# SerenityLine API

Backend API for **SerenityLine**, a deployed personal finance forecasting SaaS that helps users project future liquidity, manage recurring transactions, and compare financial scenarios before making important decisions.

SerenityLine is not just a budgeting tool. Its core goal is to help users answer a practical question:

> “Can I afford this decision without compromising my financial peace of mind over the coming months?”

## Links

* Live application: [serenityline.me](https://serenityline.me/)
* Frontend repository: [serenityline-web](https://github.com/Samuel-Valentini/serenityline-web)
* Author GitHub profile: [Samuel-Valentini](https://github.com/Samuel-Valentini)

A short demo video is available in the [How it works](https://serenityline.me/come-funziona#tutorial-serenityline) section of the live application.

Registration is required to use the private application area.

## Project overview

SerenityLine allows users to:

* manage accounts and financial positions;
* define recurring income and expenses;
* generate a forward-looking liquidity projection;
* simulate future decisions such as a major expense, a job change, a loan payoff, or a new investment;
* compare the current projection with alternative simulated scenarios;
* organize transactions through categories and financial priorities;
* manage email-related flows through a reliable email outbox pattern;
* export user data and support account deletion flows.

This backend was developed as the main backend component of my Full-Stack Development capstone project.

The goal was to build a production-oriented MVP: not only a functional prototype, but a backend designed with realistic concerns in mind, including security, data separation, test coverage, maintainability, deployment, and a clear domain model.

## What this project demonstrates

This repository is intended to demonstrate backend development skills in a realistic application context, including:

* Java and Spring Boot REST API development;
* domain modeling for a non-trivial business case;
* relational database design with PostgreSQL;
* authentication and session management;
* JWT access-token authentication;
* refresh-token rotation with HttpOnly refresh-token cookies;
* user/group-based data separation;
* DTO-based API design;
* validation and error handling;
* automated testing;
* deployment of a backend service with a managed PostgreSQL database;
* integration with an external email provider;
* production-oriented handling of sensitive personal and financial data.

## Backend scope

This repository contains the Java/Spring Boot backend of SerenityLine.

Main responsibilities:

* REST API design and implementation;
* authentication and session management;
* user and group management;
* account and bucket management;
* recurring transaction logic;
* transaction management;
* category and financial-priority management;
* simulation support;
* email outbox management;
* support contact flow;
* data export flow;
* account deletion and account restore flows;
* persistence layer and database schema;
* validation and error handling;
* backend testing.

## Main domain areas

The backend domain model is organized around the following areas:

* Users
* User groups
* Sessions and refresh tokens
* Auth action tokens
* Email outbox
* Account deletion and restore flows
* Data export
* Accounts
* Buckets
* Credit cards
* Recurring transactions
* Transactions
* Categories
* Financial priorities
* Simulations
* Future billing/payments structure prepared at schema level, not active in the MVP

## Key features

### Authentication and authorization

* User registration and login
* Password hashing with BCrypt
* Password strength checks
* JWT-based access tokens
* Refresh-token flow
* Refresh-token rotation
* Refresh tokens stored in HttpOnly cookies
* Session tracking
* Selective logout support
* Logout from all devices
* Temporary action tokens for email verification, password reset, email change, invitation, restore-account, and 2FA-related flows
* Email-based 2FA support
* User/group-based data separation
* Role-based collaboration model
* CSRF-aware security configuration for cookie-based flows

### Financial forecasting domain

* Account-based financial tracking
* Recurring transactions as the core forecasting unit
* Actual transactions linked to recurring transaction expectations
* Scenario simulation through simulation groups
* Bucket support for logical allocation of liquidity
* Credit card support with account-balance and SerenityLine-liquidity distinction
* Category and financial-priority support
* Business-day adjustment support for recurring payments
* Structure designed to preserve historical consistency of financial data

### Reliability and security

* Backend input validation
* DTO-based request/response flow
* Protection against cross-user and cross-group data access
* Error handling with appropriate HTTP responses
* Login attempt protection
* Email outbox pattern to reduce the risk of losing email actions in case of provider failure
* Encrypted email outbox content
* Token hashing for sensitive token persistence
* Account deletion workflow
* Data export workflow
* Production-oriented configuration for sensitive personal data
* OpenAPI/Swagger support for controlled development and review environments

OpenAPI/Swagger is intentionally not publicly exposed in production.

## Tech stack

### Backend

* Java 25
* Spring Boot 4.0.6
* Spring Web MVC
* Spring Data JPA
* Spring Security
* Spring Validation / Bean Validation
* Spring Actuator
* JWT with HS256 signing
* Maven / Maven Wrapper
* PostgreSQL
* Flyway
* OpenAPI / Swagger
* JUnit
* Mockito

### Additional backend libraries

* BCrypt for password hashing
* zxcvbn for password strength evaluation
* OpenGamma Strata for business-day calendar support
* Caffeine for in-memory rate limiting/cache-style use cases
* Resend Java SDK for email delivery

### External services and deployment

* Railway for backend deployment
* Railway PostgreSQL for database hosting
* Resend for email delivery
* Netlify for frontend deployment

## Testing

The backend includes more than **1,700 automated tests**.

The test suite was created to support confidence in the core business logic, authentication flows, persistence behavior, validation, authorization rules, and API behavior.

Typical test command:

```bash
mvn test
```

If the project is run through the Maven wrapper, use:

```bash
./mvnw test
```

## Requirements

This project is configured for:

* Java 25
* Maven or Maven Wrapper
* PostgreSQL

The exact Java and Spring Boot versions are defined in `pom.xml`.

## Local setup

1. Clone the repository:

```bash
git clone https://github.com/Samuel-Valentini/serenityline-api.git
cd serenityline-api
```

2. Configure the required environment variables.

Production secrets and environment-specific values are intentionally not committed to the repository.

Typical required configuration includes:

| Area                    | Examples                                                                                                          |
| ----------------------- | ----------------------------------------------------------------------------------------------------------------- |
| Spring profile          | `SPRING_PROFILES_ACTIVE`                                                                                          |
| Server                  | `SERVER_PORT`                                                                                                     |
| Database                | `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`                                                                            |
| JWT/Auth                | `JWT_SECRET`, `JWT_ISSUER`, `JWT_ACCESS_TOKEN_TTL`                                                                |
| Refresh tokens          | `REFRESH_TOKEN_TTL`, `REFRESH_TOKEN_COOKIE_NAME`, `REFRESH_TOKEN_COOKIE_SECURE`, `REFRESH_TOKEN_COOKIE_SAME_SITE` |
| Token hashing           | `TOKEN_HASHING_SECRET`                                                                                            |
| Sensitive hashing       | `SENSITIVE_HASH_KEY_BASE64`                                                                                       |
| Frontend integration    | `FRONTEND_BASE_URL`                                                                                               |
| CORS                    | allowed origins are configured per environment profile                                                            |
| Email provider          | `EMAIL_PROVIDER`, `EMAIL_FROM`, `RESEND_API_KEY`                                                                  |
| Email outbox encryption | `EMAIL_OUTBOX_ENCRYPTION_KEY_ID`, `EMAIL_OUTBOX_ENCRYPTION_KEY_BASE64`                                            |
| Support/contact flow    | `SUPPORT_CONTACT_RECIPIENT_EMAIL`                                                                                 |
| Worker configuration    | email outbox, reminder, cleanup, and deletion worker settings                                                     |
| Data export             | export limits and concurrency settings                                                                            |

3. Run the application:

```bash
mvn spring-boot:run
```

Or, if using the Maven wrapper:

```bash
./mvnw spring-boot:run
```

4. Run tests:

```bash
mvn test
```

Or:

```bash
./mvnw test
```

## API documentation

OpenAPI/Swagger support is available for development and review environments.

For security reasons, Swagger UI and OpenAPI docs are disabled in production.

## Frontend

The frontend web application is available here:

[serenityline-web](https://github.com/Samuel-Valentini/serenityline-web)

## Notes for reviewers

This project is publicly visible as part of my developer portfolio.

The live application is deployed and usable, but it requires registration. A demo video is available inside the landing page under [How it works](https://serenityline.me/come-funziona#tutorial-serenityline).

For security reasons, production API documentation is not publicly exposed.

## License / Copyright

Copyright (c) 2026 Samuel Valentini. All rights reserved.

This project is proprietary and publicly visible only as part of the author's portfolio.

Recruiters, hiring managers, instructors, examiners, and authorized reviewers may view, clone, download, run, and test this software solely for professional recruitment evaluation, academic evaluation, or portfolio review, as described in `LICENSE.md`.

No permission is granted to copy, modify, distribute, publish, sublicense, or use this code for any other purpose without prior written permission.

See `LICENSE.md` for details.
