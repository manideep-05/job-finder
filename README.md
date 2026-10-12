# ⚡ JobFinder India — Autonomous SDE 1 & SDE 2 Job Discovery & Referral Engine

A high-performance, automated Spring Boot application that continuously scans public career boards (Greenhouse, Lever, Ashby, Workday, Amazon Jobs) for high-compensation **SDE 1 & SDE 2** openings in **India**, evaluates them with an intelligent scoring system, and enables **1-click alumni referral outreach** (NIT Bhopal / MANIT).

---

## 🎯 Target Role & Candidate Profile
* **Target Roles:** Software Development Engineer I & II (SDE 1, SDE 2, MTS 1, MTS 2, Backend Engineer).
* **Target Experience:** 1 to 5 years (sweet spot for ~3 years experience; excludes 6+ yrs / Staff / Principal roles).
* **Locations:** India tech hubs (Bengaluru, Hyderabad, Pune, Delhi/NCR, Chennai, Mumbai) and Remote in India.
* **Target Compensation:** ₹33L – ₹75L+ CTC.
* **Core Tech Stack:** Java, Spring Boot, PostgreSQL, Oracle, Redis, AWS (ECS, SQS, RDS), Microservices, Distributed Systems, Kafka, REST APIs.

---

## 🚀 Key Features

### 1. Multi-Platform ATS & Career Portal Integration (Live APIs)
The application avoids brittle web scraping by connecting directly to public applicant tracking system (ATS) APIs:
* **Greenhouse Adapter:** Queries `boards-api.greenhouse.io` for Stripe, Razorpay, PhonePe, Postman, Swiggy, Rippling, Databricks, Uber, UiPath, Cloudflare, Confluent, MongoDB, etc.
* **Lever Adapter:** Queries `api.lever.co` for Atlassian, Meesho, BrowserStack, Chargebee, Clevertap, Acceldata, HighLevel, Palantir, etc.
* **Ashby Adapter:** Queries `api.ashbyhq.com/posting-api` for Zepto, Retool, Ramp, Notion, Zapier, with clean compensation parsing.
* **Workday CXS Adapter:** Queries tenant Workday JSON endpoints for Salesforce, Walmart Global Tech, Adobe, Mastercard, Intuit, Workday, and ServiceNow.
* **Amazon Jobs Live Feed:** Direct programmatic query to Amazon's live India SDE endpoint (`amazon.jobs/en/search.json`).
* **Microsoft Careers API:** Direct query to Microsoft Global Careers Search (`gcsservices.careers.microsoft.com`).
* **Google Careers API:** Direct query to Google Careers Search API (`careers.google.com/api/v3/search`).
* **Apple Careers API:** Direct query to Apple Role Search API (`jobs.apple.com/api/role/search`) with location filter for India.
* **Java 21 Virtual Threads Concurrent Fetching:** All adapters and individual company boards run concurrently via `Executors.newVirtualThreadPerTaskExecutor()`, accelerating scans from 90s to under 6s.
* **Workday Deep JD Resolution:** Automatically fetches the full job description text for candidate postings to ensure comprehensive 50-point skill evaluation.

### 2. Industry Standard 5-Pillar Scoring Engine (100 Pts Max)
Calibrated directly from real top tech JDs (Amazon, Uber, Atlassian, Stripe, JP Morgan) for SDE 1 & SDE 2 hiring:
1. **Generic Tech Skill Clusters (Max 50 pts — Heavyweight):** 5 core clusters (10 pts each):
   * *Languages & Core OOP (10 pts):* Java, Python, Multithreading, Concurrency, JVM, DSA, OOP, Clean Code, Separation of Concerns, Abstractions.
   * *Frameworks & API Architecture (10 pts):* Spring Boot, Spring, Microservices, REST APIs, Domain-Driven Design (DDD), Hibernate, JPA, gRPC, Low-Level Design (LLD).
   * *Databases & Caching (10 pts):* PostgreSQL, Postgres, Oracle, MySQL, SQL, Transact SQL, Redis, Caching, DynamoDB, Cassandra, MongoDB, Elasticsearch, Data Modeling, ACID.
   * *Distributed Systems & Messaging (10 pts):* Distributed Systems, System Design, Scalability, Kafka, RabbitMQ, Distributed Messaging, SQS, Event-Driven, Reliability, Latency.
   * *Cloud, DevOps & Engineering Excellence (10 pts):* AWS, GCP, Azure, Docker, Kubernetes, CI/CD, Git, Unit/Integration Testing, JUnit, Mockito, Observability (Datadog/Splunk), AI-assisted tools (Copilot).
2. **Role Match (Max 15 pts):** Exact SDE 1, SDE 2, MTS 1, MTS 2, Backend Engineer (+15 pts); General Software Engineer (+12 pts); excludes Senior (5+ yrs), Staff, Principal, Director, or Interns.
3. **Experience Match (Max 15 pts):** Sweet spot for ~3 years (1.5–4.5 years required = +15 pts prime sweet spot; 0.5–1.5 yrs = +13 pts; supports decimal JDs like 2.6+ yrs; $\ge 6$ yrs = excluded).
4. **Strict Location Match (Max 10 pts):** Verified Indian tech hubs (Bengaluru, Hyderabad, Pune, Delhi/NCR, Mumbai, Chennai) or verified India Remote (+10 pts; non-India = excluded).
5. **Freshness Recency (Max 10 pts):** Posted $\le 3$ days ago (+10 pts hot opening), $\le 7$ days (+8 pts), $\le 14$ days (+6 pts).
* **Cutoff:** Strictly $\ge 50$ points required to persist into PostgreSQL (`npcil.job_postings`).

### 3. 🎓 High-Conversion NIT Bhopal Alumni Referral Hub
Cold portal applications yield < 2% interview callbacks. JobFinder converts cold jobs into warm referral leads:
* **1-Click Alumni Referral:** Generates direct LinkedIn search queries linking `{Company} + "NIT Bhopal" + "software"` to connect with college seniors.
* **Talent Acquisition Search:** Instant LinkedIn & Google searches for technical recruiters and hiring managers.

### 4. Modern, Responsive Frontend Dashboard
* **Application Tracking Categories:** Organize and track jobs across 3 dedicated workflow tabs: **Pending**, **Applied**, and **Not Relevant**, with instant 1-click status updating.
* **Score-Wise Sorted:** Openings are ordered by `matchScore DESC` by default.
* **Interactive Live Search:** Real-time search by title, company, tech stack, or city.
* **City Filter Chips:** One-click filtering for *All India, Bengaluru, Hyderabad, Pune, Delhi/NCR, Remote*.
* **Interactive Sort Options:** Sort by Score (High to Low, Low to High) or Company name (A-Z).
* **Visual Score Badges:** Emerald (`Top Match 80+`), Blue (`Great Match 60-79`), and Amber (`Match 50-59`).

### 5. Automated Background Scans & Email Alerts
* **Scheduled Cron:** Automatically scans every 2 hours via `@Scheduled(cron = "0 0 */2 * * *")`.
* **HTML Email Reports:** Delivers formatted reports to the address configured in `JOB_REPORT_EMAIL` with matched skills, compensation status, and 1-click referral search links.

### 6. Persistent Database Error Logs & 4-Day Auto-Purge
* **PostgreSQL Storage:** API failures, HTTP 403 blocks, or schema mismatches are stored in table `npcil.source_error_logs`.
* **Frontend Diagnostics:** Visible directly under the *Database Error Logs* panel on the dashboard with timestamps (IST) and root cause error messages.
* **4-Day TTL / Retention:** Automatically deletes error logs older than 4 days via `@Scheduled(cron = "0 0 2 * * *")` daily cleanup.

---

## 🛠️ Technology Stack
* **Backend:** Java 21, Spring Boot 3.5.x
* **Persistence:** Spring Data JPA, Hibernate, PostgreSQL
* **Email:** Spring Mail (JavaMailSender via Gmail SMTP & TLS)
* **Web & Parsing:** Spring MVC, RestClient, Jackson, Jsoup
* **UI:** Thymeleaf, Vanilla CSS, Google Fonts (*Plus Jakarta Sans*)

---

## ⚙️ Configuration

### Secrets via `.env` (required)
All credentials (database + SMTP) are kept out of source control. They are loaded
from a local `.env` file at startup by [spring-dotenv](https://github.com/paulschwarz/spring-dotenv),
which registers them as Spring properties. `application.properties` only references
these keys (e.g. `${DB_PASSWORD}`) and holds no real values.

**Setup:**
1. Copy the template:
   ```powershell
   Copy-Item .env.example .env
   ```
2. Open `.env` and fill in your real values:
   ```dotenv
   # Database
   DB_URL=jdbc:postgresql://localhost:5432/engdb
   DB_USERNAME=your_db_user
   DB_PASSWORD=your_db_password

   # SMTP (Gmail App Password — Google Account > Security > App passwords)
   SMTP_HOST=smtp.gmail.com
   SMTP_PORT=587
   SMTP_USERNAME=you@example.com
   SMTP_PASSWORD=your16charapppassword

   # Email report routing
   JOB_REPORT_EMAIL=you@example.com
   EMAIL_FROM=you@example.com
   ```

> ⚠️ `.env` is listed in `.gitignore` and must never be committed. Only `.env.example`
> (placeholders) is tracked. If a credential is ever exposed, rotate it immediately —
> for Gmail, delete and regenerate the App Password.

The app fails fast on startup if a required secret is missing, so a leaked default
can never be silently used.

### Non-secret configuration (`application.properties`)
Filter thresholds, schema, and cron schedule live here (no secrets):
```properties
spring.jpa.properties.hibernate.default_schema=npcil
jobfinder.target-ctc-lpa=33
jobfinder.max-age-days=30
jobfinder.min-score=50
jobfinder.scan.cron=0 0 */2 * * *
```

### Company ATS Boards (`application.yml`)
Configured with 30+ top product companies categorized under `greenhouse`, `lever`, `ashby`, `workday`, and `career-pages`.

---

## 🏃 Getting Started

### Prerequisites
* **Java 21** or later
* **Maven 3.9+**
* **PostgreSQL** running on port 5432 with database `engdb`

### Running the Application

1. Open PowerShell in the project directory:
   ```powershell
   cd d:\projects\job-finder-springboot
   ```

2. Create your `.env` file from the template and fill in secrets (see [Configuration](#️-configuration)):
   ```powershell
   Copy-Item .env.example .env
   ```

3. Start the Spring Boot application:
   ```powershell
   mvn spring-boot:run
   ```

3. Open your browser and navigate to:
   ```
   http://localhost:8080/
   ```

4. Click **▶ Run Job Scan** to trigger an immediate scan, or let the background timer run automatically.

---

## 🌐 Application Endpoints
| Endpoint | Method | Description |
|---|---|---|
| `/` | `GET` | Main interactive dashboard with score-sorted jobs and filters |
| `/scan` | `POST` | Manually triggers a full job scan across all ATS boards |
| `/scan` | `GET` | Safe redirect back to `/` |
| `/api/status` | `GET` | Returns scan diagnostics, total jobs, qualifying jobs, and errors in JSON |

---

## 🏢 Target Companies Directory

### Primary Big Tech & Global Enterprise
* **Amazon** (Direct SDE API)
* **Salesforce, Walmart Global Tech, Adobe, Mastercard, Intuit, Workday, ServiceNow** (Workday CXS)
* **Atlassian** (Lever)
* **Uber** (Greenhouse)
* **Microsoft, Google, JPMorgan Chase, Visa, Oracle, SAP** (Career Pages)

### High-Growth Product & FinTech Unicorns
* **Stripe, Razorpay, PhonePe, Swiggy, UiPath, Automation Anywhere** (Greenhouse)
* **Meesho, BrowserStack, Chargebee, Clevertap** (Lever)
* **Zepto, Retool, Ramp** (Ashby)
* **Flipkart, Zoho, Freshworks** (Career Pages)

### Tier-1 Distributed Systems & AI Infrastructure
* **Databricks, Rippling, Confluent (Kafka), MongoDB, Cloudflare, Elastic, Twilio, Harness, CRED, Groww** (Greenhouse)
