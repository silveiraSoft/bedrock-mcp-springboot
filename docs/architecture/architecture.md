# Architecture — bedrock-mcp-springboot

> Status: **Target architecture v2 — decisions accepted by the owner** (ADR-0001…0009). Decisions are recorded in [`docs/adr`](../adr).
> Editable diagram: [`architecture.drawio`](architecture.drawio) (open in draw.io / diagrams.net → *File → Export as* PNG/SVG/PDF).

## 1. What the system does

1. A **frontend** (web, mobile, partner) sends a natural-language request: *"Do you have a 2-person tent under $400? Order one."*
2. **API Gateway** authenticates (Cognito JWT), throttles per client (usage plans) and filters attacks (WAF).
3. The **Spring Boot service** on ECS Fargate:
   1. screens the prompt with **Amazon Bedrock Guardrails** (`ApplyGuardrail`, source = INPUT),
   2. calls the **LLM** with Bedrock **Converse** + tool use (Claude Haiku 4.5 via cross-region inference profile),
   3. executes the **tools** the model chooses — each tool is a typed call to the **REST API**, with the end user's token,
   4. screens the answer with **Guardrails** (source = OUTPUT: PII masking, toxicity, denied topics),
   5. returns a stable JSON contract.
4. The same tools are published as an **MCP server** (`/mcp`, Streamable HTTP, *stateless*). External AI agents reach it through **Amazon Bedrock AgentCore Gateway** (OAuth, tool Policy, rate limits) — ADR-0009.
5. Release 1 is **read-only**: no write tools exist.

## 2. Target AWS architecture (QA / PROD)

```mermaid
flowchart LR
  subgraph Clients
    FE["Web / Mobile / Partner frontends<br/>(Cognito users, ES/EN)"]
    AG["External AI agents<br/>(MCP clients, M2M)"]
  end

  subgraph Edge["Edge & Identity"]
    R53["Route 53"]
    WAF["AWS WAF<br/>managed rules + rate limit"]
    APIGW["API Gateway REST (Regional)<br/>Cognito authorizer · usage plans"]
    COG["Amazon Cognito<br/>user pool + M2M clients"]
    ACG["Bedrock AgentCore Gateway<br/>MCP front door · OAuth inbound<br/>Policy (read-only tools) · rate limits"]
  end

  subgraph VPC["VPC (us-east-1) — private subnets"]
    VPCL["VPC Link v2"]
    LAT["VPC Lattice<br/>(managed private endpoint)"]
    ALB["Internal ALB"]
    subgraph ECS["ECS Fargate ARM64"]
      SVC["bedrock-mcp-springboot<br/>/api/v1/chat (in-process tools)<br/>/mcp (stateless MCP server)"]
      API["Sample catalog REST API<br/>(read-only; real API later)"]
    end
    CACHE[("ElastiCache Serverless Valkey<br/>tool cache + chat memory 24h")]
    VPCE["VPC endpoints<br/>bedrock-runtime · secretsmanager · ecr · logs"]
  end

  subgraph AI["Amazon Bedrock"]
    BR["Converse API<br/>Claude Haiku 4.5 · prompt caching"]
    GR["Guardrails (Standard tier)<br/>ApplyGuardrail in/out · ES+EN"]
  end

  subgraph Platform["Security · Observability · Delivery"]
    SM["Secrets Manager + KMS"]
    CW["CloudWatch · X-Ray"]
    ECR["ECR"]
    GHA["GitHub Actions (OIDC) · Terraform"]
  end

  FE --> R53 --> WAF --> APIGW
  APIGW -. JWT .-> COG
  APIGW --> VPCL --> ALB
  AG -- "MCP + OAuth token" --> ACG
  ACG -. JWT .-> COG
  ACG -- "MCP server target<br/>OAuth client-credentials" --> LAT --> ALB
  ALB --> SVC
  SVC -- "HTTP + user JWT (GET only)" --> API
  SVC <--> CACHE
  SVC --> VPCE
  VPCE --> BR
  VPCE --> GR
  VPCE --> SM
  SVC -.-> CW
  GHA --> ECR -.-> ECS
```

## 3. Request sequence (chat)

```mermaid
sequenceDiagram
  autonumber
  participant FE as Frontend
  participant GW as API Gateway (+WAF, Cognito)
  participant APP as Spring Boot (ECS Fargate)
  participant G as Bedrock Guardrails
  participant LLM as Bedrock Converse (Claude)
  participant C as Valkey cache
  participant API as REST API

  FE->>GW: POST /api/v1/chat {message} + JWT
  GW->>APP: forward (VPC Link → ALB)
  APP->>G: ApplyGuardrail(INPUT)
  alt blocked (prompt attack, denied topic)
    G-->>APP: GUARDRAIL_INTERVENED
    APP-->>FE: 200 {answer: blocked message, guardrail.input=BLOCKED}
  else allowed / PII masked
    APP->>LLM: Converse(system + history + tools)
    LLM-->>APP: toolUse search_products(query="tent")
    APP->>C: GET product-search
    alt cache miss
      APP->>API: GET /api/v1/products?query=tent (user JWT)
      API-->>APP: 200 [products]
      APP->>C: SET (TTL 60s)
    end
    APP->>LLM: toolResult
    LLM-->>APP: final answer
    APP->>G: ApplyGuardrail(OUTPUT)
    G-->>APP: NONE / masked text
    APP-->>FE: 200 {conversationId, answer, guardrail, usage}
  end
```

## 4. Local environment

```mermaid
flowchart LR
  DEV["IntelliJ / docker compose<br/>profile: local"] --> APPL["bedrock-mcp-springboot<br/>:8080"]
  APPL -- "Secrets Manager / SSM" --> FLOCI["Floci :4566<br/>(AWS emulator)"]
  APPL -- cache --> VK[("Valkey :6379")]
  APPL -- "Converse + ApplyGuardrail<br/>IAM Identity Center profile" --> AWSB["Real Amazon Bedrock<br/>(dev account)"]
  APPL -. "profile local-offline" .-> OLL["Ollama :11434"]
  APPL -. "profile floci-bedrock (stub)" .-> FLOCI
```

| Need | Local | QA / PROD |
|---|---|---|
| Secrets | Floci Secrets Manager (seeded by `floci-init`) | Secrets Manager → ECS `secrets` → env vars |
| Cache | Valkey container | ElastiCache Serverless for Valkey |
| LLM | Real Bedrock via SSO **or** Ollama offline **or** Floci stub | Bedrock via VPC endpoint |
| Guardrails | `LOCAL` (regex/denylist) or real Bedrock Guardrail | Bedrock Guardrail, numbered version |
| Identity | `aws sso login` (IAM Identity Center) | ECS task role (no secrets) |
| Auth | disabled | Cognito JWT at API Gateway **and** in the app |

## 5. Compute decision summary (see ADR-0002)

| Option | Verdict | Why |
|---|---|---|
| **ECS Fargate (ARM64)** | ✅ **Chosen** | Long-running LLM/tool loops (5–60 s), streaming-friendly, no cold starts for the JVM, private networking, simple ops, Graviton ≈ 20 % cheaper. |
| AWS Lambda (+SnapStart) | ❌ for this service | 29 s default API GW integration timeout, cold starts, MCP Streamable HTTP and tool loops fit poorly; good only for spiky, short calls. |
| App Runner | ❌ | Closed to new customers; AWS recommends ECS Express Mode instead. |
| ECS Express Mode | ⚠️ QA/sandbox only | Fast path (Fargate + ALB in one call) but it provisions its own public ALB; we need API Gateway + private ALB control. |
| EKS | ❌ (for now) | Operational overhead not justified for 1–3 services. |
| Bedrock AgentCore Runtime | ❌ | Built for agent code, not a REST/chat service. |
| Bedrock AgentCore **Gateway** | ✅ **Used** (ADR-0009) | Managed MCP front door for external agents; targets the Spring `/mcp`. Not used for `/chat`. |
