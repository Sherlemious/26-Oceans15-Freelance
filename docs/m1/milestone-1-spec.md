Milestone 1

1 Overview

This milestone is worth 15% of your final grade. You will build a Freelance Marketplace consisting of 5 independently runnable Spring Boot services organized as a Maven multi-module project. All 5 services connect to a single shared PostgreSQL database and are orchestrated via a single `docker-compose.yaml`. In future milestones, these services will be refactored into true microservices with separate databases, inter-service communication (OpenFeign, RabbitMQ, etc.), and Kubernetes deployment. For now, they are independent applications sharing one database.

**Technologies:** Spring Boot, PostgreSQL, Spring Data JPA, Docker, Docker Compose, Maven multi-module, Git & GitHub.

**Services:**

- a) User Service (port 8081) - Freelancer/client accounts, profiles, skills
    
- b) Job Service (port 8082) - Job postings, categories, requirements
    
- c) Proposal Service (port 8083) - Proposals, bidding, milestones
    
- d) Contract Service (port 8084) - Contracts, progress tracking, deliverables
    
- e) Wallet Service (port 8085) - Payment processing, refunds, revenue
    

**Deliverables:** 45 features (9 per service), full CRUD for all entities, Dockerized multi-service application, Git history with personalized feature branches and PRs.

---

2 Personalization & Team Roster

Every team member's work must be individually identifiable in Git history. The auto-grader cross-references the `team.json` roster against `git log` to determine who built what. Violations result in zero credit for the affected member.

2.1 team.json (Required)

Create a file named `team.json` in the project root directory (next to the parent `pom.xml`). It contains a JSON array with one entry per team member:

JSON

```
[{"studentId": "55-8078","name": "Ahmed Ali", "githubUsername":"ahmed-ali-dev", "service": "user-service"}, ...] 
```

**Fields per entry:**

- `studentId`: University ID in the format XX-XXXX
    
- `name`: Full name
    
- `githubUsername`: The exact GitHub username that will author commits
    
- `service`: Which service module this member belongs to (one of: `user-service`, `job-service`, `proposal-service`, `contract-service`, `wallet-service`)
    

The auto-grader reads this file and then runs specific tests to find each member's commits. It matches those commits against branch names to determine which features each member implemented. Do not self-declare features the grader discovers them automatically.

2.2 Branch Naming (Mandatory)

All feature branches must include the implementing member's student ID: `feat/<service>/<feature-name>/<studentId>` Example: student 55-8078 implementing S1-F1 (Service 1, Feature 1) creates: `feat/user/S1-F1/55-8078`

2.3 Commit Message Format (Mandatory)

All commits must follow this convention: `feat (<service name>): <description> (ID)` Examples: `feat (user-service): add User entity model (55-8078)`, `fix (proposal-service): fix null handling in search query (55-8078)`.

The auto-grader matches each commit's Git author (from `team.json`) against the student ID in the message. Mismatched or missing IDs will result in failures during grading. So even if you named the commit message wrong or the branch name was wrong and did follow the criteria, you know how to fix it using the commands that you took in the Git Lab.

---

3 Project Structure Multi-Module Maven

The project is organized as a Maven multi-module project. The root directory contains a parent POM with `<packaging>pom</packaging>` that declares all 5 services as modules. Each service is a fully independent Spring Boot application with its own POM, source tree, and `@SpringBootApplication` entry point. Section 4 provides full step-by-step instructions for creating this structure.

3.1 Port & Database Configuration

All services connect to the same PostgreSQL database (`freelancedb`). Each runs on a different port:

|**Service**|**Internal Port**|**Docker Host Port**|
|---|---|---|
|`user-service`|8080|8081|
|`job-service`|8080|8082|
|`proposal-service`|8080|8083|
|`contract-service`|8080|8084|
|`wallet-service`|8080|8085|
||||

Each service's `application.properties` sets `server.port=8080`. The port differentiation happens in `docker-compose.yaml` via host-port mapping (e.g., `8081:8080` for `user-service`). When running services locally without Docker during development, each developer works on their own service and can use port 8080 since they only run one service at a time. Each service configures: `spring.datasource.url=jdbc:postgresql://localhost:5432/freelancedb` (or `postgres:5432` inside Docker), `ddl-auto update`, and `show-sql=true`.

---

4 Step-by-Step: Creating the Multi-Module Project

This section walks you through creating the entire project structure from scratch. Two approaches are provided: Option A using IntelliJ IDEA (recommended), and Option B using Spring Initializr for students not using IntelliJ. Both produce the same result.

Important architectural note: The root project acts as an aggregator only. It does not contain source code and does not act as a Maven parent for the child modules. Each service module keeps its own generated Spring Boot parent. The root `pom.xml` simply lists the modules so you can build them all with a single command.

4.1 Using IntelliJ IDEA (Recommended)

#### 4.1.1 A1.

Create the Parent Project

- a) Open IntelliJ IDEA File → New Project.
    
- b) On the left panel, select Java.
    
- c) On the right, set Build system to Maven.
    
- d) Set the JDK to 25.
    
- e) Uncheck Add sample code.
    
- f) Expand Advanced Settings (at the bottom of the dialog).
    
- g) Fill in:
    
    - Name: `freelance`
        
    - GroupId: `com.teamXX.freelance` (replace XX with your team number)
        
    - ArtifactId: `freelance`
        
- h) Click Create.
    
- i) IntelliJ creates a Maven project with a `pom.xml`. Open this `pom.xml` and manually add the following line inside the `<project>` block (after `<version>`): `<packaging>pom</packaging>`
    
- j) If IntelliJ created a `src/` folder in the root project, delete it the root aggregator project has no source code.
    
- k) Reload Maven: Click the Maven reload icon (circular arrows) in the Maven tool window, or right-click the `pom.xml` Maven Reload project.
    

#### 4.1.2 A2.

Add Each Service as a Module

Repeat the following for each of the 5 services (`user-service`, `job-service`, `proposal-service`, `contract-service`, `wallet-service`):

- a) Right-click the project root (`freelance`) in the Project panel → New → Module.
    
- b) Select Spring Boot on the left.
    
- c) Configure the service information:
    
    - Name / Artifact: the service name (e.g., `user-service`)
        
    - Group: `com.teamXX.freelance`
        
    - Package name: Use the service domain name without hyphens: `com.teamXX.freelance.user` , `com.teamXX.freelance.job` , `com.teamXX.freelance.proposal` , `com.teamXX.freelance.contract` , `com.teamXX.freelance.wallet`
        
    - Type Build system: Maven
        
    - Java: 25
        
    - Packaging: Jar
        
    - Warning: The `artifactId` can use hyphens (e.g., `user-service`), but Java package names cannot contain hyphens. Use `user`, not `user-service`, in the package name.
        
- d) Click Next to continue to the dependencies page.
    
- e) Select these dependencies: Spring Web, Spring Boot DevTools, Spring Data JPA, PostgreSQL Driver, Rest Repositories.
    
- f) Click Create.
    

#### 4.1.3 A3.

What IntelliJ Does (and Does Not Do) Automatically

After creating each service module, IntelliJ will usually:

- Create the service folder with its own `pom.xml` and `src/` tree.
    
- Generate the `@SpringBootApplication` main class. However, IntelliJ may not automatically add the `<module>` entry to the root `pom.xml`. You must verify this yourself after each module creation.
    

#### 4.1.4 A4.

Verify and Complete the Root POM

After creating all 5 service modules:

- a) Open the root `freelance/pom.xml`.
    
- b) Confirm that `<packaging>pom</packaging>` exists.
    
- c) Check whether all 5 services appear inside a `<modules>` block. If any are missing, add them manually:
    

XML

```
<modules>
    <module>user-service</module>
    <module>job-service</module>
    <module>proposal-service</module>
    <module>contract-service</module>
    <module>wallet-service</module>
</modules>
```

- d) Save the file.
    
- e) Reload Maven to pick up the changes.
    
- f) Confirm there are no Maven errors in the tool window.
    

#### 4.1.5 A5.

Important: Do NOT Modify Child Service POMS

Each generated service already has its own `<parent>` block pointing to Spring Boot. Do not replace or modify this parent. The root `freelance` POM acts only as an aggregator (it lists the modules so you can build them all at once). It is not a Maven parent for the child modules. In other words:

- Do not add a `<parent>` block pointing to `freelance` inside any child POM.
    
- Do not remove the existing Spring Boot `<parent>` from any child POM. The only connection between the root POM and the children is the `<modules>` block in the root. You are done with Option A. Skip to Shared Steps below.
    

4.1.6 Add jackson-databind Dependency

In each service's `pom.xml`, add the following dependency inside the `<dependencies>` block (required for Hibernate JSONB support):

XML

```
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
</dependency>
```

After adding, reload Maven.

4.1.7 Configure application.properties per Service

Inside each service's `src/main/resources/application.properties`, add:

Properties

```
server.port=8080
spring.datasource.url=jdbc:postgresql://localhost:5432/freelancedb
spring.datasource.username=postgres
spring.datasource.password=postgres
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect
```

All services use port 8080 internally. The port differentiation to 8081-8085 happens in `docker-compose.yaml` (covered in the Dockerization section).

4.1.8 Create Sub-Packages

Inside each service's main package (e.g., `src/main/java/com/teamXX/freelance/user/`), create these sub-packages: `model/`, `repository/`, `service/`, `controller/`, `dto/`.

4.1.9 Add a Health Endpoint to Each Service

In each service's `controller/` package, create a simple health controller that exposes a `GET` endpoint returning `"OK"` (200):

- User Service: `GET /api/users/health`
    
- Job Service: `GET /api/jobs/health`
    
- Proposal Service: `GET /api/proposals/health`
    
- Contract Service: `GET /api/contracts/health`
    
- Wallet Service: `GET /api/payouts/health`
    

4.1.10 Create team.json

In the project root (next to the root `pom.xml`), create `team.json` as described in Section 2.

4.1.11 Verify the Build

Start PostgreSQL via Docker: `docker compose up -d` (This requires the `docker-compose.yaml` with just the PostgreSQL service see Section 6.) Then build all services from the project root: `mvn clean package -DskipTests` If the build succeeds, you should see `BUILD SUCCESS` and a JAR in each service's `target/` directory. To test a single service locally: `cd user-service` `mvn spring-boot:run` Then visit `http://localhost:8080/api/users/health` to confirm it responds with "OK".

4.2 Final Project Structure

After completing all steps, your project should look like this:

```
freelance/
+-- pom.xml                (root aggregator POM, packaging-pom)
+-- team.json              (team roster)
+-- docker-compose.yaml    (PostgreSQL, later: all services)
+-- .gitignore
+-- user-service/
    +-- pom.xml            (own Spring Boot parent, NOT freelance)
    +-- Dockerfile         (added in Phase D)
    +-- src/main/java/com/teamXX/freelance/user/
        +-- UserApplication.java (@SpringBootApplication)
        +-- model/
        +-- repository/
        +-- service/
        +-- controller/
        +-- dto/
+-- job-service/
    +-- (same structure)
+-- proposal-service/
    +-- (same structure)
+-- contract-service/
    +-- (same structure)
+-- wallet-service/
    +-- (same structure)
```

---

5 Git Workflow

**Initial Setup**

- a) Initialize a Git repository in the project root.
    
- b) Create a `.gitignore` excluding: `target/`, `.idea/`, `*.iml`, `.env`, `*.class`, `.DS_Store`.
    
- c) Create the parent POM, all 5 child POM files, the package structure, and the `team.json` file.
    
- d) Make an initial commit: `init: project setup by (Name_ID)` (e.g: `init: project setup by (Mohamed_Ayman_52_8078)`) (note: this is supposed to be the only commit message that will not follow the commit message rule that is stated in this description).
    
- e) Create a private GitHub repository named `TeamNumber-TeamName-Freelance` (e.g: `40-Random-Freelance`).
    
- f) Push to remote and rename the default branch to `main`.
    
- g) Branch protection: Enable "Require a pull request before merging" on `main`. (The rule may not be enforced on the free version of git so you have to enforce it manually as the grader will check the generated PRs).
    
- h) Add `Scalable-Submissions` as a collaborator.
    
- i) Each member must contribute to the project through Git using their own GitHub account. The auto-grader verifies all GitHub usernames from `team.json` appear in history of the repo. If someone didn't contribute to the project (no-commits for him/her), will be considered as if they didn't work at all in the milestone and will receive a ZERO as a result in the milestone grade.
    

---

6 Database Setup (Docker Compose)

The `docker-compose.yaml` in the project root contains a PostgreSQL service with:

- Container name: `freelance-db`
    
- Port mapping: `5432:5432`
    
- Environment variables: user `postgres`, password `postgres`, database `freelancedb`
    
- Named volume `pgdata` for data persistence
    

In Phase D (Dockerization), you will add all 5 application services to this same file. Verify: Run `docker compose up -d` (PostgreSQL only at first), start any service locally, and confirm it connects to the database.

---

7 Entity Models

All entities use auto-generated Long IDs. Some entities include one JSONB column implemented as a `Map<String, Object>` using the Hibernate JSONB annotations learned in Lab 4. Since all services share one database, tables from different services coexist in the same database schema. Each service defines JPA relationships (`@OneToMany`, `@ManyToOne`, join entities) between entities within the same service only. References to entities in other services are stored as plain Long foreign key columns (not JPA-managed relationships), and cross-service data access uses native SQL JOINs on the shared database. Remember to use `@JsonIgnore` on bidirectional relationships to prevent infinite recursion during JSON serialization.

7.1 User Service Entities (user-service)

7.1.1 User Entity

Table: `users`

|**Field**|**Type**|**Constraints**|**Notes**|
|---|---|---|---|
|`id`|`Long`|PK, auto-generated||
|`name`|`String`|not null||
|`email`|`String`|not null, unique||
|`password`|`String`|not null||
|`phone`|`String`|not null, unique||
|`role`|`ENUM`|not null|"FREELANCER" or "CLIENT" or "ADMIN"|
|`status`|`ENUM`|not null, default ACTIVE|ACTIVE / DEACTIVATED|
|`preferences`|`Map<String, Object>`|JSONB|See below|
|`createdAt`|`LocalDateTime`|not null|Set on creation|
|`userSkills`|`List<UserSkill>`||Relational Attribute|
|||||

_JSONB preferences:_ Language, notification settings (email/sms booleans), timezone, profile visibility, hourly rate range. _Relationships:_ One `User` can have Many `UserSkills` and `User` is the inverse side and `UserSkill` is the owner side.

7.1.2 UserSkill Entity

Table: `user_skills`

|**Field**|**Type**|**Constraints**|**Notes**|
|---|---|---|---|
|`id`|`Long`|PK, auto-generated||
|`skillName`|`String`|not null|e.g., "Java", "Graphic Design"|
|`category`|`String`|not null|e.g., "DEVELOPMENT", "DESIGN"|
|`yearsOfExperience`|`Integer`|not null||
|`proficiencyLevel`|`ENUM`|not null|BEGINNER / INTERMEDIATE / EXPERT|
|`isPrimary`|`Boolean`|default false||
|`metadata`|`Map<String, Object>`|JSONB|See below|
|`createdAt`|`LocalDateTime`|not null||
|`user`|`User`||Relational Attribute|
|||||

_JSONB metadata:_ Certifications list, portfolio URLs, endorsement count, last used date, tools/frameworks. _Relationships:_ Many `UserSkills` can belong to One `User` and `User` is the inverse side and `UserSkill` is the owner side.

7.2 Job Service Entities (job-service)

7.2.1 Job Entity

Table: `jobs`

|**Field**|**Type**|**Constraints**|**Notes**|
|---|---|---|---|
|`id`|`Long`|PK, auto-generated||
|`clientId`|`Long`|not null|FK reference to users (not JPA-managed)|
|`title`|`String`|not null||
|`description`|`String`|not null||
|`category`|`ENUM`|not null|e.g., WEB_DEV, MOBILE, DESIGN, WRITING|
|`status`|`ENUM`|not null|OPEN / IN PROGRESS / CLOSED|
|`budgetMin`|`Double`|not null|Minimum budget|
|`budgetMax`|`Double`|not null|Maximum budget|
|`rating`|`Double`|default 0.0|Client rating for this job|
|`totalRatings`|`Integer`|default 0|Count for average calculation|
|`requirements`|`Map<String, Object>`|JSONB|See below|
|`createdAt`|`LocalDateTime`|not null||
|`jobAttachments`|`List<JobAttachment>`||Relational Attribute|
|||||

_JSONB requirements:_ Required skills list, experience level (JUNIOR, MID, SENIOR), estimated duration (weeks), remote allowed (boolean), preferred timezone. _Relationships:_ One `Job` has Many `JobAttachments` and `Job` is the inverse side and `JobAttachment` is the owner side.

7.2.2 JobAttachment Entity

Table: `job_attachments`

|**Field**|**Type**|**Constraints**|**Notes**|
|---|---|---|---|
|`id`|`Long`|PK, auto-generated||
|`type`|`ENUM`|not null|BRIEF / MOCKUP / REFERENCE / CONTRACT_TEMPLATE|
|`fileUrl`|`String`|not null|URL or path to file|
|`expiryDate`|`LocalDate`|not null||
|`verified`|`Boolean`|default false||
|`metadata`|`Map<String, Object>`|JSONB|See below|
|`uploadedAt`|`LocalDateTime`|not null||
|`job`|`Job`||Relational Attribute|
|||||

_JSONB metadata:_ File size (KB), file format, version number, verification notes, rejection reason. _Relationships:_ Many `JobAttachments` belong to One `Job` and `Job` is the inverse side and `JobAttachment` is the owner side.

7.3 Proposal Service Entities (proposal-service)

7.3.1 Proposal Entity

Table: `proposals`

|**Field**|**Type**|**Constraints**|**Notes**|
|---|---|---|---|
|`id`|`Long`|PK, auto-generated||
|`jobId`|`Long`|not null|FK reference (not JPA-managed)|
|`freelancerId`|`Long`|not null|FK reference (not JPA-managed)|
|`coverLetter`|`String`|not null||
|`bidAmount`|`Double`|not null|Proposed price|
|`estimatedDays`|`Integer`|not null|Proposed delivery time|
|`status`|`ENUM`|not null|See values below|
|`metadata`|`Map<String, Object>`|JSONB|See below|
|`submittedAt`|`LocalDateTime`|not null||
|`acceptedAt`|`LocalDateTime`|nullable|Set when accepted|
|`proposalMilestones`|`List<ProposalMilestone>`||Relational Attribute|
|||||

_Status values:_ SUBMITTED, SHORTLISTED, ACCEPTED, REJECTED, WITHDRAWN. _JSONB metadata:_ Approach summary, relevant experience, tools/technologies proposed, availability start date, portfolio links. _Note:_ `jobId` and `freelancerId` are plain Long columns, not JPA `@ManyToOne` relationships (Job and User live in different services). _Relationships:_ One `Proposal` can have Many `ProposalMilestones` and `Proposal` is the inverse side while `ProposalMilestone` is the owner side.

7.3.2 Proposal Milestone Entity

Table: `proposal_milestones` A proposal can have multiple milestones (e.g., design phase, development phase, testing). Each milestone has an order within the proposal.

|**Field**|**Type**|**Constraints**|**Notes**|
|---|---|---|---|
|`id`|`Long`|PK, auto-generated||
|`milestoneOrder`|`Integer`|not null|1, 2, 3...|
|`title`|`String`|not null||
|`description`|`String`|not null||
|`amount`|`Double`|not null|Payment for this milestone|
|`status`|`ENUM`|not null|PENDING / IN PROGRESS / COMPLETED / APPROVED|
|`metadata`|`Map<String, Object>`|JSONB|See below|
|`proposal`|`Proposal`||Relational Attribute|
|||||

_JSONB metadata:_ Deliverables list, acceptance criteria, estimated days, actual completion date, revision count. _Relationships:_ Many `ProposalMilestones` belong to One `Proposal` and `Proposal` is the inverse side while `ProposalMilestone` is the owner side.

7.4 Contract Service Entities (contract-service)

7.4.1 Contract Entity

Table: `contracts`

|**Field**|**Type**|**Constraints**|**Notes**|
|---|---|---|---|
|`id`|`Long`|PK, auto-generated||
|`jobId`|`Long`|not null|FK reference (not JPA-managed)|
|`freelancerId`|`Long`|not null|FK reference (not JPA-managed)|
|`clientId`|`Long`|not null|FK reference (not JPA-managed)|
|`proposalId`|`Long`|not null|FK reference (not JPA-managed)|
|`agreedAmount`|`Double`|not null||
|`status`|`ENUM`|not null|ACTIVE / COMPLETED / TERMINATED / DISPUTED|
|`startDate`|`LocalDateTime`|not null||
|`endDate`|`LocalDateTime`|nullable||
|`metadata`|`Map<String, Object>`|JSONB|See below|
|`createdAt`|`LocalDateTime`|not null||
|||||

_JSONB metadata:_ Payment terms (MILESTONE, HOURLY, FIXED), revision limit, NDA signed (boolean), weekly hours expected, progress percentage, last activity date. _Relationships:_ None within this service. `jobId`, `freelancerId`, `clientId`, and `proposalId` are plain Long references to other services tables in the shared database via native SQL.

7.5 Wallet Service Entities (wallet-service)

This service demonstrates the join entity pattern for many-to-many relationships with extra columns (like the OrderItem pattern from Lab 4).

7.5.1 Payout Entity

Table: `payouts`

|**Field**|**Type**|**Constraints**|**Notes**|
|---|---|---|---|
|`id`|`Long`|PK, auto-generated||
|`contractId`|`Long`|not null|FK reference (not JPA-managed)|
|`freelancerId`|`Long`|not null|FK reference (not JPA-managed)|
|`amount`|`Double`|not null||
|`method`|`ENUM`|not null|BANK_TRANSFER / PAYPAL / CRYPTO|
|`status`|`ENUM`|not null|See below|
|`transactionDetails`|`Map<String, Object>`|JSONB|See below|
|`createdAt`|`LocalDateTime`|not null||
|`payoutPromos`|`List<PayoutPromo>`||Relational Attribute|
|||||

_Status values:_ PENDING, COMPLETED, FAILED, REFUNDED. _JSONB transactionDetails:_ Gateway response, account last four digits, receipt URL, failure reason. _Relationships:_ One `Payout` can reference Many `PayoutPromos` and `Payout` is the inverse side while `PayoutPromo` is the owner side.

7.5.2 PromoCode Entity

Table: `promo_codes`

|**Field**|**Type**|**Constraints**|**Notes**|
|---|---|---|---|
|`id`|`Long`|PK, auto-generated||
|`code`|`String`|not null, unique|e.g., "FIRSTJOB20"|
|`discountType`|`ENUM`|not null|PERCENTAGE or FIXED|
|`discountValue`|`Double`|not null|e.g., 20.0|
|`maxUses`|`Integer`|not null||
|`currentUses`|`Integer`|default 0||
|`expiryDate`|`LocalDateTime`|not null||
|`active`|`Boolean`|default true||
|`metadata`|`Map<String, Object>`|JSONB|See below|
|`payoutPromos`|`List<PayoutPromo>`||Relational Attribute|
|||||

_JSONB metadata:_ Eligible job categories, minimum contract amount, terms and conditions, applicable regions. _Relationships:_ One `PromoCode` can reference Many `PayoutPromos` and `PromoCode` is the inverse side while `PayoutPromo` is the owner side.

7.5.3 Payout Promo Entity (Join Entity)

Table: `payout_promos` This is a join entity linking `Payout` and `PromoCode` in a many-to-many relationship with extra columns the same pattern as OrderItem from Lab 4. A payout can use multiple promo codes, and a promo code can be used on multiple payouts.

|**Field**|**Type**|**Constraints**|**Notes**|
|---|---|---|---|
|`id`|`Long`|PK, auto-generated||
|`discountApplied`|`Double`|not null|Actual amount deducted|
|`appliedAt`|`LocalDateTime`|not null|When promo code was applied|
|`payout`|`Payout`||Relational Attribute|
|`promoCode`|`PromoCode`||Relational Attribute|
|||||

_Relationships:_

- Many `PayoutPromos` can belong to One `Payout` and `Payout` is the inverse side while `PayoutPromo` is the owner side.
    
- Many `PayoutPromos` can belong to One `PromoCode` and `PromoCode` is the inverse side while `PayoutPromo` is the owner side.
    

7.6 Relationship Summary

|**Service**|**Relationship**|**Pattern Tested**|
|---|---|---|
|User|User - UserSkill|Bidirectional|
|Job|Job - JobAttachment|Bidirectional|
|Proposal|Proposal - ProposalMilestone|Bidirectional ordering|
|Contract|(no JPA relations)|Cross-service via native SQL|
|Wallet|Payout - PromoCode|Join entity (PayoutPromo)|
||||

7.7 Important: Cross-Service Data Access

Since all services share one PostgreSQL database, any service can query any table using native SQL. For example, the Proposal Service can JOIN proposals with jobs or users in a native `@Query`. However, each service only defines JPA entity classes and JPA relationships for entities within its own module. Cross-table access is always via native SQL, never via JPA relationships. This pattern will be replaced by Micro-services Communications in Milestone 3.

---

8 Repository Layer

Each service defines a `JpaRepository` for each of its entities. For services with multiple entities, you need multiple repository interfaces. Make sure that you make use of the Repository Layer when interacting with the database and not have the queries at the service layer as the auto grader will test having proper layering logic in your code. Repositories include:

- Naming-convention methods for simple lookups.
    
- Custom `@Query` methods with native SQL for complex queries, including JOINs against other services tables in the shared database.
    
- Modifying + Transactional for UPDATE/DELETE queries.
    

---

9 Features

CRUD operations (create, read by ID, read all, update, delete) are the baseline for every entity in every service and are NOT counted as features. This means: User Service needs CRUD for both User and UserSkill, Job Service for both Job and JobAttachment, Proposal Service for both Proposal and ProposalMilestone, Contract Service for Contract, and Wallet Service for Payout, PromoCode, and PayoutPromo. Implement all CRUD first before starting features. _Important Note:_ Do not forget to apply the Layered architecture (having repository, service and controller) as the test case will test that each feature is implemented according to the layered architecture as explained to you in the Labs.

_(Note: The full detailed feature lists S1-F1 through S5-F9 and appendices are documented in the PDF. They follow the structure: Feature ID, Branch, Endpoint, Request/Response, Behavior, and Test Scenario.)_

---

10 Git Workflow Feature Development

Every team member follows this workflow for every feature:

- a) Start from main: `git checkout main && git pull origin main`
    
- b) Create feature branch: `git checkout -b feat/<service>/<feature-ID>/<YOUR-STUDENT-ID>`
    
- c) Implement: repository methods -> service logic -> controller endpoint -> test via Postman.
    
- d) Commit: `git commit -m "feat(<service-name>): <description> (<YOUR-STUDENT-ID>)"` (except only the first commit which is the initialization of the project)
    
- e) Push and create a Pull Request on GitHub.
    
- f) At least 1 teammate reviews and approves.
    
- g) Merge into main using a regular merge commit ("Create a merge commit" on GitHub). Do NOT use squash merge. A regular merge preserves the branch name in the merge commit message (e.g., `Merge pull request #12 from feat/user/S1-F1/55-8078`), which the auto-grader uses to verify who implemented which feature.
    
- h) Do NOT delete the feature branch after merging. The auto-grader will verify that each feature has a correctly-named branch containing the student ID. If you delete the branch, the grader will not be able to verify it and this may result in deductions in your grade.
    

10.1 Merge Order (Checkpoint-Gated)

- a) Phase A (Week 1): Parent POM + all 5 modules with all entities... repositories, CRUD for every entity, health endpoints + `team.json`. → Run checkpoint A.
    
- b) Phase B (Week 2): Features F1-F3 per service (15 total). → Run checkpoint B.
    
- c) Phase C (Week 3): Features F4-F6 per service (15 total). → Run checkpoint C.
    
- d) Phase D (Week 4): Features F7-F9 per service (15 total) + Dockerfiles + final `docker-compose.yaml`. → Run checkpoint D.
    

---

11 Dockerization (Phase D)

- a) Create a `Dockerfile` inside each service module using `eclipse-temurin:25.0.2_10-jdk`. Copy that service's JAR and expose port 8080.
    
- b) Update the root `docker-compose.yaml` to include all 5 services alongside PostgreSQL. Each service:
    
    - Builds from its own Dockerfile (e.g., `build: ./user-service`)
        
    - Maps its host port to container port 8080 (e.g., `8081:8080`)
        
    - Overrides datasource URL to `jdbc:postgresql://postgres:5432/freelancedb`
        
    - Depends on the PostgreSQL service
        
- c) Build: `mvn clean package -DskipTests` from the root.
    
- d) Run: `docker compose up --build`
    
- e) Verify all services respond on their respective host ports (`localhost:8081` through `8085`).
    
- f) Git: branch `feat/docker/<ID>`, commit with ID, PR, merge.
    

---

12 Work Distribution (Recommended)

3 members per service x 5 services = 15 members. Each member implements 3 features. Phase A (all entities + CRUD for every entity) is done collectively by the 3 members of each sub-team.

---

13 Submission & Auto-Grader

- a) Repository must be private.
    
- b) `Scalable-Submissions` must be added as a collaborator.
    
- c) `team.json` must be present and valid at the project root.
    
- d) `docker compose up` must start PostgreSQL + all 5 services.
    
- e) Services must respond at `localhost:8081` through `localhost:8085`.
    
- f) Git history must show feature branches merged via PRs with student IDs.
    
- g) Commits must follow `feat (<service>): <desc> (<ID>)` format.
    
- h) Details about the usage of the auto grader and the testing will be sent later.
    

**Important:** Grading is per member. A feature only counts for the member whose GitHub username authored the commits on a branch containing their student ID. Features with mismatched or missing IDs receive zero credit. (The zero will affect both, the team member and the whole team). It is the responsibility of the scrum master to make sure that everyone is working and the whole requirements are being delivered.

**Deadline:** The Milestone deadline is Saturday 11/04/2026 at 11:59 PM. **Submission:** Submission form will be sent later to the scrum masters.German University in Cairo

Department of Computer Science

Assoc. Prof. Mervat Abu El-Kheir

Architecture of Massively Scalable Applications, Spring 2026

Milestone 1: Freelance Marketplace - Core Services Foundation

**Deadline is Saturday 11/04/2026 at 11:59 PM**

---

Contents

|**Section**|**Title**|**Page**|
|---|---|---|
|1|Overview|5|
|2|Personalization & Team Roster|5|
|2.1|team.json (Required)|5|
|2.2|Branch Naming (Mandatory)|6|
|2.3|Commit Message Format (Mandatory)|6|
|3|Multi-Module Maven Project Structure|6|
|3.1|Port & Database Configuration|6|
|4|Step-by-Step: Creating the Multi-Module Project|6|
|4.1|Using IntelliJ IDEA (Recommended)|7|
|4.1.1|A1. Create the Parent Project|7|
|4.1.2|A2. Add Each Service as a Module|7|
|4.1.3|A3. What IntelliJ Does (and Does Not Do) Automatically|8|
|4.1.4|A4. Verify and Complete the Root POM|8|
|4.1.5|A5. Important: Do NOT Modify Child Service POMS|9|
|4.1.6|Add jackson-databind Dependency|9|
|4.1.7|Configure application.properties per Service|9|
|4.1.8|Create Sub-Packages|9|
|4.1.9|Add a Health Endpoint to Each Service|9|
|4.1.10|Create team.json|10|
|4.1.11|Verify the Build|10|
|4.2|Final Project Structure|10|
|5|Git Workflow Initial Setup|11|
|6|Database Setup (Docker Compose)|11|
|7|Entity Models|11|
|7.1|User Service Entities (user-service)|12|
|7.1.1|User Entity|12|
|7.1.2|UserSkill Entity|12|
|7.2|Job Service Entities (job-service)|12|
|7.2.1|Job Entity|12|
|7.2.2|JobAttachment Entity|13|
|7.3|Proposal Service Entities (proposal-service)|13|
|7.3.1|Proposal Entity|13|
|7.3.2|Proposal Milestone Entity|14|
|7.4|Contract Service Entities (contract-service)|14|
|7.4.1|Contract Entity|14|
|7.5|Wallet Service Entities (wallet-service)|15|
|7.5.1|Payout Entity|15|
|7.5.2|PromoCode Entity|15|
|7.5.3|Payout Promo Entity (Join Entity)|16|
|7.6|Relationship Summary|16|
|7.7|Important: Cross-Service Data Access|17|
|8|Repository Layer|17|
|9|Features|17|
|9.1|User Service Features (port 8081)|17|
|9.2|Job Service Features (port 8082)|20|
|9.3|Proposal Service Features (port 8083)|23|
|9.4|Contract Service Features (port 8084)|27|
|9.5|Wallet Service Features (port 8085)|30|
|10|Git Workflow Feature Development|36|
|10.1|Merge Order (Checkpoint-Gated)|36|
|11|Dockerization (Phase D)|36|
|12|Work Distribution (Recommended)|37|
|13|Submission & Auto-Grader|37|
|14|Appendix: JSONB Sample Data|37|
|15|Appendix: Example Database Tables|39|

---

1 Overview

This milestone is worth 15% of your final grade. You will build a Freelance Marketplace consisting of 5 independently runnable Spring Boot services organized as a Maven multi-module project. All 5 services connect to a single shared PostgreSQL database and are orchestrated via a single `docker-compose.yaml`. In future milestones, these services will be refactored into true microservices with separate databases, inter-service communication (OpenFeign, RabbitMQ, etc.), and Kubernetes deployment. For now, they are independent applications sharing one database.

**Technologies:** Spring Boot, PostgreSQL, Spring Data JPA, Docker, Docker Compose, Maven multi-module, Git & GitHub.

**Services:**

- a) User Service (port 8081) - Freelancer/client accounts, profiles, skills
    
- b) Job Service (port 8082) - Job postings, categories, requirements
    
- c) Proposal Service (port 8083) - Proposals, bidding, milestones
    
- d) Contract Service (port 8084) - Contracts, progress tracking, deliverables
    
- e) Wallet Service (port 8085) - Payment processing, refunds, revenue
    

**Deliverables:** 45 features (9 per service), full CRUD for all entities, Dockerized multi-service application, Git history with personalized feature branches and PRs.

---

2 Personalization & Team Roster

Every team member's work must be individually identifiable in Git history. The auto-grader cross-references the `team.json` roster against `git log` to determine who built what. Violations result in zero credit for the affected member.

2.1 team.json (Required)

Create a file named `team.json` in the project root directory (next to the parent `pom.xml`). It contains a JSON array with one entry per team member:

JSON

```
[{"studentId": "55-8078","name": "Ahmed Ali", "githubUsername":"ahmed-ali-dev", "service": "user-service"}, ...] 
```

**Fields per entry:**

- `studentId`: University ID in the format XX-XXXX
    
- `name`: Full name
    
- `githubUsername`: The exact GitHub username that will author commits
    
- `service`: Which service module this member belongs to (one of: `user-service`, `job-service`, `proposal-service`, `contract-service`, `wallet-service`)
    

The auto-grader reads this file and then runs specific tests to find each member's commits. It matches those commits against branch names to determine which features each member implemented. Do not self-declare features the grader discovers them automatically.

2.2 Branch Naming (Mandatory)

All feature branches must include the implementing member's student ID: `feat/<service>/<feature-name>/<studentId>` Example: student 55-8078 implementing S1-F1 (Service 1, Feature 1) creates: `feat/user/S1-F1/55-8078`

2.3 Commit Message Format (Mandatory)

All commits must follow this convention: `feat (<service name>): <description> (ID)` Examples: `feat (user-service): add User entity model (55-8078)`, `fix (proposal-service): fix null handling in search query (55-8078)`.

The auto-grader matches each commit's Git author (from `team.json`) against the student ID in the message. Mismatched or missing IDs will result in failures during grading. So even if you named the commit message wrong or the branch name was wrong and did follow the criteria, you know how to fix it using the commands that you took in the Git Lab.

---

3 Project Structure Multi-Module Maven

The project is organized as a Maven multi-module project. The root directory contains a parent POM with `<packaging>pom</packaging>` that declares all 5 services as modules. Each service is a fully independent Spring Boot application with its own POM, source tree, and `@SpringBootApplication` entry point. Section 4 provides full step-by-step instructions for creating this structure.

3.1 Port & Database Configuration

All services connect to the same PostgreSQL database (`freelancedb`). Each runs on a different port:

|**Service**|**Internal Port**|**Docker Host Port**|
|---|---|---|
|`user-service`|8080|8081|
|`job-service`|8080|8082|
|`proposal-service`|8080|8083|
|`contract-service`|8080|8084|
|`wallet-service`|8080|8085|
||||

Each service's `application.properties` sets `server.port=8080`. The port differentiation happens in `docker-compose.yaml` via host-port mapping (e.g., `8081:8080` for `user-service`). When running services locally without Docker during development, each developer works on their own service and can use port 8080 since they only run one service at a time. Each service configures: `spring.datasource.url=jdbc:postgresql://localhost:5432/freelancedb` (or `postgres:5432` inside Docker), `ddl-auto update`, and `show-sql=true`.

---

4 Step-by-Step: Creating the Multi-Module Project

This section walks you through creating the entire project structure from scratch. Two approaches are provided: Option A using IntelliJ IDEA (recommended), and Option B using Spring Initializr for students not using IntelliJ. Both produce the same result.

Important architectural note: The root project acts as an aggregator only. It does not contain source code and does not act as a Maven parent for the child modules. Each service module keeps its own generated Spring Boot parent. The root `pom.xml` simply lists the modules so you can build them all with a single command.

4.1 Using IntelliJ IDEA (Recommended)

#### 4.1.1 A1.

Create the Parent Project

- a) Open IntelliJ IDEA File → New Project.
    
- b) On the left panel, select Java.
    
- c) On the right, set Build system to Maven.
    
- d) Set the JDK to 25.
    
- e) Uncheck Add sample code.
    
- f) Expand Advanced Settings (at the bottom of the dialog).
    
- g) Fill in:
    
    - Name: `freelance`
        
    - GroupId: `com.teamXX.freelance` (replace XX with your team number)
        
    - ArtifactId: `freelance`
        
- h) Click Create.
    
- i) IntelliJ creates a Maven project with a `pom.xml`. Open this `pom.xml` and manually add the following line inside the `<project>` block (after `<version>`): `<packaging>pom</packaging>`
    
- j) If IntelliJ created a `src/` folder in the root project, delete it the root aggregator project has no source code.
    
- k) Reload Maven: Click the Maven reload icon (circular arrows) in the Maven tool window, or right-click the `pom.xml` Maven Reload project.
    

#### 4.1.2 A2.

Add Each Service as a Module

Repeat the following for each of the 5 services (`user-service`, `job-service`, `proposal-service`, `contract-service`, `wallet-service`):

- a) Right-click the project root (`freelance`) in the Project panel → New → Module.
    
- b) Select Spring Boot on the left.
    
- c) Configure the service information:
    
    - Name / Artifact: the service name (e.g., `user-service`)
        
    - Group: `com.teamXX.freelance`
        
    - Package name: Use the service domain name without hyphens: `com.teamXX.freelance.user` , `com.teamXX.freelance.job` , `com.teamXX.freelance.proposal` , `com.teamXX.freelance.contract` , `com.teamXX.freelance.wallet`
        
    - Type Build system: Maven
        
    - Java: 25
        
    - Packaging: Jar
        
    - Warning: The `artifactId` can use hyphens (e.g., `user-service`), but Java package names cannot contain hyphens. Use `user`, not `user-service`, in the package name.
        
- d) Click Next to continue to the dependencies page.
    
- e) Select these dependencies: Spring Web, Spring Boot DevTools, Spring Data JPA, PostgreSQL Driver, Rest Repositories.
    
- f) Click Create.
    

#### 4.1.3 A3.

What IntelliJ Does (and Does Not Do) Automatically

After creating each service module, IntelliJ will usually:

- Create the service folder with its own `pom.xml` and `src/` tree.
    
- Generate the `@SpringBootApplication` main class. However, IntelliJ may not automatically add the `<module>` entry to the root `pom.xml`. You must verify this yourself after each module creation.
    

#### 4.1.4 A4.

Verify and Complete the Root POM

After creating all 5 service modules:

- a) Open the root `freelance/pom.xml`.
    
- b) Confirm that `<packaging>pom</packaging>` exists.
    
- c) Check whether all 5 services appear inside a `<modules>` block. If any are missing, add them manually:
    

XML

```
<modules>
    <module>user-service</module>
    <module>job-service</module>
    <module>proposal-service</module>
    <module>contract-service</module>
    <module>wallet-service</module>
</modules>
```

- d) Save the file.
    
- e) Reload Maven to pick up the changes.
    
- f) Confirm there are no Maven errors in the tool window.
    

#### 4.1.5 A5.

Important: Do NOT Modify Child Service POMS

Each generated service already has its own `<parent>` block pointing to Spring Boot. Do not replace or modify this parent. The root `freelance` POM acts only as an aggregator (it lists the modules so you can build them all at once). It is not a Maven parent for the child modules. In other words:

- Do not add a `<parent>` block pointing to `freelance` inside any child POM.
    
- Do not remove the existing Spring Boot `<parent>` from any child POM. The only connection between the root POM and the children is the `<modules>` block in the root. You are done with Option A. Skip to Shared Steps below.
    

4.1.6 Add jackson-databind Dependency

In each service's `pom.xml`, add the following dependency inside the `<dependencies>` block (required for Hibernate JSONB support):

XML

```
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
</dependency>
```

After adding, reload Maven.

4.1.7 Configure application.properties per Service

Inside each service's `src/main/resources/application.properties`, add:

Properties

```
server.port=8080
spring.datasource.url=jdbc:postgresql://localhost:5432/freelancedb
spring.datasource.username=postgres
spring.datasource.password=postgres
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect
```

All services use port 8080 internally. The port differentiation to 8081-8085 happens in `docker-compose.yaml` (covered in the Dockerization section).

4.1.8 Create Sub-Packages

Inside each service's main package (e.g., `src/main/java/com/teamXX/freelance/user/`), create these sub-packages: `model/`, `repository/`, `service/`, `controller/`, `dto/`.

4.1.9 Add a Health Endpoint to Each Service

In each service's `controller/` package, create a simple health controller that exposes a `GET` endpoint returning `"OK"` (200):

- User Service: `GET /api/users/health`
    
- Job Service: `GET /api/jobs/health`
    
- Proposal Service: `GET /api/proposals/health`
    
- Contract Service: `GET /api/contracts/health`
    
- Wallet Service: `GET /api/payouts/health`
    

4.1.10 Create team.json

In the project root (next to the root `pom.xml`), create `team.json` as described in Section 2.

4.1.11 Verify the Build

Start PostgreSQL via Docker: `docker compose up -d` (This requires the `docker-compose.yaml` with just the PostgreSQL service see Section 6.) Then build all services from the project root: `mvn clean package -DskipTests` If the build succeeds, you should see `BUILD SUCCESS` and a JAR in each service's `target/` directory. To test a single service locally: `cd user-service` `mvn spring-boot:run` Then visit `http://localhost:8080/api/users/health` to confirm it responds with "OK".

4.2 Final Project Structure

After completing all steps, your project should look like this:

```
freelance/
+-- pom.xml                (root aggregator POM, packaging-pom)
+-- team.json              (team roster)
+-- docker-compose.yaml    (PostgreSQL, later: all services)
+-- .gitignore
+-- user-service/
    +-- pom.xml            (own Spring Boot parent, NOT freelance)
    +-- Dockerfile         (added in Phase D)
    +-- src/main/java/com/teamXX/freelance/user/
        +-- UserApplication.java (@SpringBootApplication)
        +-- model/
        +-- repository/
        +-- service/
        +-- controller/
        +-- dto/
+-- job-service/
    +-- (same structure)
+-- proposal-service/
    +-- (same structure)
+-- contract-service/
    +-- (same structure)
+-- wallet-service/
    +-- (same structure)
```

---

5 Git Workflow

**Initial Setup**

- a) Initialize a Git repository in the project root.
    
- b) Create a `.gitignore` excluding: `target/`, `.idea/`, `*.iml`, `.env`, `*.class`, `.DS_Store`.
    
- c) Create the parent POM, all 5 child POM files, the package structure, and the `team.json` file.
    
- d) Make an initial commit: `init: project setup by (Name_ID)` (e.g: `init: project setup by (Mohamed_Ayman_52_8078)`) (note: this is supposed to be the only commit message that will not follow the commit message rule that is stated in this description).
    
- e) Create a private GitHub repository named `TeamNumber-TeamName-Freelance` (e.g: `40-Random-Freelance`).
    
- f) Push to remote and rename the default branch to `main`.
    
- g) Branch protection: Enable "Require a pull request before merging" on `main`. (The rule may not be enforced on the free version of git so you have to enforce it manually as the grader will check the generated PRs).
    
- h) Add `Scalable-Submissions` as a collaborator.
    
- i) Each member must contribute to the project through Git using their own GitHub account. The auto-grader verifies all GitHub usernames from `team.json` appear in history of the repo. If someone didn't contribute to the project (no-commits for him/her), will be considered as if they didn't work at all in the milestone and will receive a ZERO as a result in the milestone grade.
    

---

6 Database Setup (Docker Compose)

The `docker-compose.yaml` in the project root contains a PostgreSQL service with:

- Container name: `freelance-db`
    
- Port mapping: `5432:5432`
    
- Environment variables: user `postgres`, password `postgres`, database `freelancedb`
    
- Named volume `pgdata` for data persistence
    

In Phase D (Dockerization), you will add all 5 application services to this same file. Verify: Run `docker compose up -d` (PostgreSQL only at first), start any service locally, and confirm it connects to the database.

---

7 Entity Models

All entities use auto-generated Long IDs. Some entities include one JSONB column implemented as a `Map<String, Object>` using the Hibernate JSONB annotations learned in Lab 4. Since all services share one database, tables from different services coexist in the same database schema. Each service defines JPA relationships (`@OneToMany`, `@ManyToOne`, join entities) between entities within the same service only. References to entities in other services are stored as plain Long foreign key columns (not JPA-managed relationships), and cross-service data access uses native SQL JOINs on the shared database. Remember to use `@JsonIgnore` on bidirectional relationships to prevent infinite recursion during JSON serialization.

7.1 User Service Entities (user-service)

7.1.1 User Entity

Table: `users`

|**Field**|**Type**|**Constraints**|**Notes**|
|---|---|---|---|
|`id`|`Long`|PK, auto-generated||
|`name`|`String`|not null||
|`email`|`String`|not null, unique||
|`password`|`String`|not null||
|`phone`|`String`|not null, unique||
|`role`|`ENUM`|not null|"FREELANCER" or "CLIENT" or "ADMIN"|
|`status`|`ENUM`|not null, default ACTIVE|ACTIVE / DEACTIVATED|
|`preferences`|`Map<String, Object>`|JSONB|See below|
|`createdAt`|`LocalDateTime`|not null|Set on creation|
|`userSkills`|`List<UserSkill>`||Relational Attribute|
|||||

_JSONB preferences:_ Language, notification settings (email/sms booleans), timezone, profile visibility, hourly rate range. _Relationships:_ One `User` can have Many `UserSkills` and `User` is the inverse side and `UserSkill` is the owner side.

7.1.2 UserSkill Entity

Table: `user_skills`

|**Field**|**Type**|**Constraints**|**Notes**|
|---|---|---|---|
|`id`|`Long`|PK, auto-generated||
|`skillName`|`String`|not null|e.g., "Java", "Graphic Design"|
|`category`|`String`|not null|e.g., "DEVELOPMENT", "DESIGN"|
|`yearsOfExperience`|`Integer`|not null||
|`proficiencyLevel`|`ENUM`|not null|BEGINNER / INTERMEDIATE / EXPERT|
|`isPrimary`|`Boolean`|default false||
|`metadata`|`Map<String, Object>`|JSONB|See below|
|`createdAt`|`LocalDateTime`|not null||
|`user`|`User`||Relational Attribute|
|||||

_JSONB metadata:_ Certifications list, portfolio URLs, endorsement count, last used date, tools/frameworks. _Relationships:_ Many `UserSkills` can belong to One `User` and `User` is the inverse side and `UserSkill` is the owner side.

7.2 Job Service Entities (job-service)

7.2.1 Job Entity

Table: `jobs`

|**Field**|**Type**|**Constraints**|**Notes**|
|---|---|---|---|
|`id`|`Long`|PK, auto-generated||
|`clientId`|`Long`|not null|FK reference to users (not JPA-managed)|
|`title`|`String`|not null||
|`description`|`String`|not null||
|`category`|`ENUM`|not null|e.g., WEB_DEV, MOBILE, DESIGN, WRITING|
|`status`|`ENUM`|not null|OPEN / IN PROGRESS / CLOSED|
|`budgetMin`|`Double`|not null|Minimum budget|
|`budgetMax`|`Double`|not null|Maximum budget|
|`rating`|`Double`|default 0.0|Client rating for this job|
|`totalRatings`|`Integer`|default 0|Count for average calculation|
|`requirements`|`Map<String, Object>`|JSONB|See below|
|`createdAt`|`LocalDateTime`|not null||
|`jobAttachments`|`List<JobAttachment>`||Relational Attribute|
|||||

_JSONB requirements:_ Required skills list, experience level (JUNIOR, MID, SENIOR), estimated duration (weeks), remote allowed (boolean), preferred timezone. _Relationships:_ One `Job` has Many `JobAttachments` and `Job` is the inverse side and `JobAttachment` is the owner side.

7.2.2 JobAttachment Entity

Table: `job_attachments`

|**Field**|**Type**|**Constraints**|**Notes**|
|---|---|---|---|
|`id`|`Long`|PK, auto-generated||
|`type`|`ENUM`|not null|BRIEF / MOCKUP / REFERENCE / CONTRACT_TEMPLATE|
|`fileUrl`|`String`|not null|URL or path to file|
|`expiryDate`|`LocalDate`|not null||
|`verified`|`Boolean`|default false||
|`metadata`|`Map<String, Object>`|JSONB|See below|
|`uploadedAt`|`LocalDateTime`|not null||
|`job`|`Job`||Relational Attribute|
|||||

_JSONB metadata:_ File size (KB), file format, version number, verification notes, rejection reason. _Relationships:_ Many `JobAttachments` belong to One `Job` and `Job` is the inverse side and `JobAttachment` is the owner side.

7.3 Proposal Service Entities (proposal-service)

7.3.1 Proposal Entity

Table: `proposals`

|**Field**|**Type**|**Constraints**|**Notes**|
|---|---|---|---|
|`id`|`Long`|PK, auto-generated||
|`jobId`|`Long`|not null|FK reference (not JPA-managed)|
|`freelancerId`|`Long`|not null|FK reference (not JPA-managed)|
|`coverLetter`|`String`|not null||
|`bidAmount`|`Double`|not null|Proposed price|
|`estimatedDays`|`Integer`|not null|Proposed delivery time|
|`status`|`ENUM`|not null|See values below|
|`metadata`|`Map<String, Object>`|JSONB|See below|
|`submittedAt`|`LocalDateTime`|not null||
|`acceptedAt`|`LocalDateTime`|nullable|Set when accepted|
|`proposalMilestones`|`List<ProposalMilestone>`||Relational Attribute|
|||||

_Status values:_ SUBMITTED, SHORTLISTED, ACCEPTED, REJECTED, WITHDRAWN. _JSONB metadata:_ Approach summary, relevant experience, tools/technologies proposed, availability start date, portfolio links. _Note:_ `jobId` and `freelancerId` are plain Long columns, not JPA `@ManyToOne` relationships (Job and User live in different services). _Relationships:_ One `Proposal` can have Many `ProposalMilestones` and `Proposal` is the inverse side while `ProposalMilestone` is the owner side.

7.3.2 Proposal Milestone Entity

Table: `proposal_milestones` A proposal can have multiple milestones (e.g., design phase, development phase, testing). Each milestone has an order within the proposal.

|**Field**|**Type**|**Constraints**|**Notes**|
|---|---|---|---|
|`id`|`Long`|PK, auto-generated||
|`milestoneOrder`|`Integer`|not null|1, 2, 3...|
|`title`|`String`|not null||
|`description`|`String`|not null||
|`amount`|`Double`|not null|Payment for this milestone|
|`status`|`ENUM`|not null|PENDING / IN PROGRESS / COMPLETED / APPROVED|
|`metadata`|`Map<String, Object>`|JSONB|See below|
|`proposal`|`Proposal`||Relational Attribute|
|||||

_JSONB metadata:_ Deliverables list, acceptance criteria, estimated days, actual completion date, revision count. _Relationships:_ Many `ProposalMilestones` belong to One `Proposal` and `Proposal` is the inverse side while `ProposalMilestone` is the owner side.

7.4 Contract Service Entities (contract-service)

7.4.1 Contract Entity

Table: `contracts`

|**Field**|**Type**|**Constraints**|**Notes**|
|---|---|---|---|
|`id`|`Long`|PK, auto-generated||
|`jobId`|`Long`|not null|FK reference (not JPA-managed)|
|`freelancerId`|`Long`|not null|FK reference (not JPA-managed)|
|`clientId`|`Long`|not null|FK reference (not JPA-managed)|
|`proposalId`|`Long`|not null|FK reference (not JPA-managed)|
|`agreedAmount`|`Double`|not null||
|`status`|`ENUM`|not null|ACTIVE / COMPLETED / TERMINATED / DISPUTED|
|`startDate`|`LocalDateTime`|not null||
|`endDate`|`LocalDateTime`|nullable||
|`metadata`|`Map<String, Object>`|JSONB|See below|
|`createdAt`|`LocalDateTime`|not null||
|||||

_JSONB metadata:_ Payment terms (MILESTONE, HOURLY, FIXED), revision limit, NDA signed (boolean), weekly hours expected, progress percentage, last activity date. _Relationships:_ None within this service. `jobId`, `freelancerId`, `clientId`, and `proposalId` are plain Long references to other services tables in the shared database via native SQL.

7.5 Wallet Service Entities (wallet-service)

This service demonstrates the join entity pattern for many-to-many relationships with extra columns (like the OrderItem pattern from Lab 4).

7.5.1 Payout Entity

Table: `payouts`

|**Field**|**Type**|**Constraints**|**Notes**|
|---|---|---|---|
|`id`|`Long`|PK, auto-generated||
|`contractId`|`Long`|not null|FK reference (not JPA-managed)|
|`freelancerId`|`Long`|not null|FK reference (not JPA-managed)|
|`amount`|`Double`|not null||
|`method`|`ENUM`|not null|BANK_TRANSFER / PAYPAL / CRYPTO|
|`status`|`ENUM`|not null|See below|
|`transactionDetails`|`Map<String, Object>`|JSONB|See below|
|`createdAt`|`LocalDateTime`|not null||
|`payoutPromos`|`List<PayoutPromo>`||Relational Attribute|
|||||

_Status values:_ PENDING, COMPLETED, FAILED, REFUNDED. _JSONB transactionDetails:_ Gateway response, account last four digits, receipt URL, failure reason. _Relationships:_ One `Payout` can reference Many `PayoutPromos` and `Payout` is the inverse side while `PayoutPromo` is the owner side.

7.5.2 PromoCode Entity

Table: `promo_codes`

|**Field**|**Type**|**Constraints**|**Notes**|
|---|---|---|---|
|`id`|`Long`|PK, auto-generated||
|`code`|`String`|not null, unique|e.g., "FIRSTJOB20"|
|`discountType`|`ENUM`|not null|PERCENTAGE or FIXED|
|`discountValue`|`Double`|not null|e.g., 20.0|
|`maxUses`|`Integer`|not null||
|`currentUses`|`Integer`|default 0||
|`expiryDate`|`LocalDateTime`|not null||
|`active`|`Boolean`|default true||
|`metadata`|`Map<String, Object>`|JSONB|See below|
|`payoutPromos`|`List<PayoutPromo>`||Relational Attribute|
|||||

_JSONB metadata:_ Eligible job categories, minimum contract amount, terms and conditions, applicable regions. _Relationships:_ One `PromoCode` can reference Many `PayoutPromos` and `PromoCode` is the inverse side while `PayoutPromo` is the owner side.

7.5.3 Payout Promo Entity (Join Entity)

Table: `payout_promos` This is a join entity linking `Payout` and `PromoCode` in a many-to-many relationship with extra columns the same pattern as OrderItem from Lab 4. A payout can use multiple promo codes, and a promo code can be used on multiple payouts.

|**Field**|**Type**|**Constraints**|**Notes**|
|---|---|---|---|
|`id`|`Long`|PK, auto-generated||
|`discountApplied`|`Double`|not null|Actual amount deducted|
|`appliedAt`|`LocalDateTime`|not null|When promo code was applied|
|`payout`|`Payout`||Relational Attribute|
|`promoCode`|`PromoCode`||Relational Attribute|
|||||

_Relationships:_

- Many `PayoutPromos` can belong to One `Payout` and `Payout` is the inverse side while `PayoutPromo` is the owner side.
    
- Many `PayoutPromos` can belong to One `PromoCode` and `PromoCode` is the inverse side while `PayoutPromo` is the owner side.
    

7.6 Relationship Summary

|**Service**|**Relationship**|**Pattern Tested**|
|---|---|---|
|User|User - UserSkill|Bidirectional|
|Job|Job - JobAttachment|Bidirectional|
|Proposal|Proposal - ProposalMilestone|Bidirectional ordering|
|Contract|(no JPA relations)|Cross-service via native SQL|
|Wallet|Payout - PromoCode|Join entity (PayoutPromo)|
||||

7.7 Important: Cross-Service Data Access

Since all services share one PostgreSQL database, any service can query any table using native SQL. For example, the Proposal Service can JOIN proposals with jobs or users in a native `@Query`. However, each service only defines JPA entity classes and JPA relationships for entities within its own module. Cross-table access is always via native SQL, never via JPA relationships. This pattern will be replaced by Micro-services Communications in Milestone 3.

---

8 Repository Layer

Each service defines a `JpaRepository` for each of its entities. For services with multiple entities, you need multiple repository interfaces. Make sure that you make use of the Repository Layer when interacting with the database and not have the queries at the service layer as the auto grader will test having proper layering logic in your code. Repositories include:

- Naming-convention methods for simple lookups.
    
- Custom `@Query` methods with native SQL for complex queries, including JOINs against other services tables in the shared database.
    
- Modifying + Transactional for UPDATE/DELETE queries.
    

---

9 Features

CRUD operations (create, read by ID, read all, update, delete) are the baseline for every entity in every service and are NOT counted as features. This means: User Service needs CRUD for both User and UserSkill, Job Service for both Job and JobAttachment, Proposal Service for both Proposal and ProposalMilestone, Contract Service for Contract, and Wallet Service for Payout, PromoCode, and PayoutPromo. Implement all CRUD first before starting features. _Important Note:_ Do not forget to apply the Layered architecture (having repository, service and controller) as the test case will test that each feature is implemented according to the layered architecture as explained to you in the Labs.

_(Note: The full detailed feature lists S1-F1 through S5-F9 and appendices are documented in the PDF. They follow the structure: Feature ID, Branch, Endpoint, Request/Response, Behavior, and Test Scenario.)_

---

10 Git Workflow Feature Development

Every team member follows this workflow for every feature:

- a) Start from main: `git checkout main && git pull origin main`
    
- b) Create feature branch: `git checkout -b feat/<service>/<feature-ID>/<YOUR-STUDENT-ID>`
    
- c) Implement: repository methods -> service logic -> controller endpoint -> test via Postman.
    
- d) Commit: `git commit -m "feat(<service-name>): <description> (<YOUR-STUDENT-ID>)"` (except only the first commit which is the initialization of the project)
    
- e) Push and create a Pull Request on GitHub.
    
- f) At least 1 teammate reviews and approves.
    
- g) Merge into main using a regular merge commit ("Create a merge commit" on GitHub). Do NOT use squash merge. A regular merge preserves the branch name in the merge commit message (e.g., `Merge pull request #12 from feat/user/S1-F1/55-8078`), which the auto-grader uses to verify who implemented which feature.
    
- h) Do NOT delete the feature branch after merging. The auto-grader will verify that each feature has a correctly-named branch containing the student ID. If you delete the branch, the grader will not be able to verify it and this may result in deductions in your grade.
    

10.1 Merge Order (Checkpoint-Gated)

- a) Phase A (Week 1): Parent POM + all 5 modules with all entities... repositories, CRUD for every entity, health endpoints + `team.json`. → Run checkpoint A.
    
- b) Phase B (Week 2): Features F1-F3 per service (15 total). → Run checkpoint B.
    
- c) Phase C (Week 3): Features F4-F6 per service (15 total). → Run checkpoint C.
    
- d) Phase D (Week 4): Features F7-F9 per service (15 total) + Dockerfiles + final `docker-compose.yaml`. → Run checkpoint D.
    

---

11 Dockerization (Phase D)

- a) Create a `Dockerfile` inside each service module using `eclipse-temurin:25.0.2_10-jdk`. Copy that service's JAR and expose port 8080.
    
- b) Update the root `docker-compose.yaml` to include all 5 services alongside PostgreSQL. Each service:
    
    - Builds from its own Dockerfile (e.g., `build: ./user-service`)
        
    - Maps its host port to container port 8080 (e.g., `8081:8080`)
        
    - Overrides datasource URL to `jdbc:postgresql://postgres:5432/freelancedb`
        
    - Depends on the PostgreSQL service
        
- c) Build: `mvn clean package -DskipTests` from the root.
    
- d) Run: `docker compose up --build`
    
- e) Verify all services respond on their respective host ports (`localhost:8081` through `8085`).
    
- f) Git: branch `feat/docker/<ID>`, commit with ID, PR, merge.
    

---

12 Work Distribution (Recommended)

3 members per service x 5 services = 15 members. Each member implements 3 features. Phase A (all entities + CRUD for every entity) is done collectively by the 3 members of each sub-team.

---

13 Submission & Auto-Grader

- a) Repository must be private.
    
- b) `Scalable-Submissions` must be added as a collaborator.
    
- c) `team.json` must be present and valid at the project root.
    
- d) `docker compose up` must start PostgreSQL + all 5 services.
    
- e) Services must respond at `localhost:8081` through `localhost:8085`.
    
- f) Git history must show feature branches merged via PRs with student IDs.
    
- g) Commits must follow `feat (<service>): <desc> (<ID>)` format.
    
- h) Details about the usage of the auto grader and the testing will be sent later.
    

**Important:** Grading is per member. A feature only counts for the member whose GitHub username authored the commits on a branch containing their student ID. Features with mismatched or missing IDs receive zero credit. (The zero will affect both, the team member and the whole team). It is the responsibility of the scrum master to make sure that everyone is working and the whole requirements are being delivered.

**Deadline:** The Milestone deadline is Saturday 11/04/2026 at 11:59 PM. **Submission:** Submission form will be sent later to the scrum masters.