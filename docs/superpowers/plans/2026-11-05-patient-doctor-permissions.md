# Patient and Doctor Permissions Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans inline, with a fresh final review. Steps use checkbox syntax.

**Goal:** Implement the approved two-role read scope, patient demographics, doctor assignment and local account maintenance.

**Architecture:** Database roles and centralized read authorization; owned writes remain unchanged. Add nullable indexed patient columns, compatibility backfill, read-time demographics, and a paginated doctor directory. Keep existing personal sync semantics.

**Tech Stack:** Existing Java 21, Spring Boot 3.5.16, JDBC, Flyway, MySQL 8 and H2.

**Spec:** docs/superpowers/specs/2026-11-05-patient-doctor-permissions-design.md (approved by user “可以”).

## Global Constraints

- Do not install new environment versions or dependencies.
- PATIENT reads owned data; HOSPITAL_STAFF reads all patients and their resources, cross-owner writes prohibited.
- Preserve existing business data, V1 migration, personal sync scope, and Swagger split layout.
- Do not create real privileged accounts or print secrets.

## Review Focus

- Revoked/downgraded doctors and signed download tickets must not retain cross-owner access.
- Parent deletion, direct child IDs, file references, and uploads must not bypass ownership.
- Scope switching, page tokens, doctor directory search and patient names must not leak or break paging.
- Nullable patch fields, birthDate compatibility, month/day age boundaries, and legacy data must remain consistent.
- Provisioning cannot overwrite existing credentials or promote an existing patient.

## Task 1: Patient fields and migration

Files: V2 SQL migration, V3 Java backfill, PatientDetails component, ResourceService, Records, runtime contract, demographic and migration tests.
Interfaces: PatientDetails.normalize(data,patch), present(data), persist columns via Records; Clock in Asia/Shanghai.
- [ ] Write behavior tests and observe failure for new demographics, invalid values and legacy migration.
- [ ] Add additive columns and backfill without changing revisions; validate, normalize and derive age.
- [ ] Verify targeted tests, then existing suite.

## Task 2: Role reads and doctor directory

Files: AccessPolicy, AuthService, ResourceService, UploadService, ApiController, contract and permission integration tests.
Interfaces: AccessPolicy.read(kind,user,id), isDoctor(user), require current ACTIVE role; owned write APIs unchanged.
- [ ] Observe failing HTTP tests for global doctor reads, scope filtering, directory and signed downloads.
- [ ] Implement database role, centralized reads, SQL pagination and doctor selection.
- [ ] Verify cross-owner writes, ordinary patient isolation, revoked/downgraded tickets and personal sync.

## Task 3: Local account maintenance

Files: Bootstrap/provision runner, AuthService, scripts/create-account.ps1, account behavior tests.
- [ ] Observe tests failing for role-bearing creation and duplicate-account protection.
- [ ] Add non-web local provisioning mode and masked input wrapper, never overwrite/promote existing users.
- [ ] Verify runner exit and account authentication without creating a real privileged account.

## Task 4: Documentation, verification and local upgrade

Files: Swagger examples, frontend field/openapi/handoff artifacts, role guide, README and regression tests.
- [ ] Update Swagger/contract counts and role-aware examples and frontend docs.
- [ ] Run complete H2 suite, Node navigation tests and MySQL disposable-schema tests.
- [ ] Fresh final review; fix material findings with reproductions and rerun checks.
- [ ] Back up project database, stop script-owned server, package, start with migrations and verify health/schema.

Implementation record: target/patient-doctor-progress.md. Existing workspace contains the complete uncommitted project and running local installation; apply authorized changes in place, without discarding changes, creating commits, or moving private runtime data.

## Completion

All four tasks completed 2026-11-05. Backend33/33, disposable MySQL25/25, Node3/3. Fresh review issue reproduced and fixed; business database backup and V1→V3 migration verified without row loss or account promotion. Packaged non-web account maintenance and Windows PowerShell5 parsing verified. Backend running with API1.1.0. No actual doctor account automatically created; user chooses its credentials through Create-Account.cmd.
