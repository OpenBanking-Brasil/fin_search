# fin_search — Open Finance + Assistente Financeiro IA

Plataforma que combina leitura de dados abertos do **Open Finance Brasil** com um **Assistente Financeiro baseado em IA** (Fabric + Gemini) para análise, categorização e recomendações sobre eventos financeiros pessoais.

---

## Arquitetura

```
┌─────────────────────────────────────────────────────────────┐
│  Frontend (SvelteKit + Tailwind)  —  tools/Fabric/web       │
│  AI Autopilot + Workflows + Métricas  —  porta 5199         │
└────────────────────┬────────────────────────────────────────┘
                     │ HTTP (proxy /api/chat)
┌────────────────────▼────────────────────────────────────────┐
│  Fabric Go (backend de IA)  —  porta 18080                  │
│  Patterns: summarize, extract_wisdom, explain_code, …       │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│  Spring Boot 4 (Java 21)  —  porta 8081                     │
│  Open Data + Assistente Financeiro REST API                 │
└────────────────────┬────────────────────────────────────────┘
                     │ JDBC
┌────────────────────▼────────────────────────────────────────┐
│  PostgreSQL 16  —  porta 5432                               │
└─────────────────────────────────────────────────────────────┘
```

---

## Stack

| Camada | Tecnologia |
|--------|-----------|
| Backend API | Spring Boot 4.0, Java 21, Virtual Threads |
| Persistência | PostgreSQL 16, Spring Data JPA, Hibernate |
| Segurança | Spring Security (HTTP Basic), AES-GCM (dados) |
| Documentação | springdoc-openapi (Swagger UI em `/swagger-ui.html`) |
| Frontend | SvelteKit 2, Svelte 5, Tailwind CSS, Skeleton UI |
| IA | Fabric (Go) + Gemini 2.5 Flash |
| Infra | Docker, Docker Compose, GitHub Actions CI |

---

## Pré-requisitos

- Java 21+
- Maven (ou use `./mvnw`)
- Docker + Docker Compose
- [Fabric](https://github.com/danielmiessler/Fabric) instalado (winget install Fabric)
- Node.js 20+ (para o frontend)
- Chave de API Gemini (obter em https://aistudio.google.com)

---

## Início rápido

### 1. Subir infraestrutura (PostgreSQL + pgAdmin)

```bash
docker compose up -d postgres pgadmin
```

pgAdmin disponível em http://localhost:8080 (admin@exemplo.com / admin123)

### 2. Configurar chave Gemini

```powershell
# Windows (PowerShell)
.\tools\fabric_gemini_setup.ps1
```

### 3. Subir backend Spring Boot

```bash
./mvnw spring-boot:run
```

API disponível em http://localhost:8081  
Swagger UI: http://localhost:8081/swagger-ui.html  
Health check: http://localhost:8081/healthcheck

### 4. Subir o Assistente IA (Fabric + UI)

```powershell
# Abre backend Fabric (18080) + frontend (5199) + janela app no Edge
.\tools\start_fabric_local_app.ps1
```

Ou como atalho no Desktop: **"Fabric Local App"**

---

## Subir tudo com Docker

```bash
# Sobe PostgreSQL + pgAdmin + app Spring Boot
docker compose up -d

# Ver logs da app
docker compose logs -f app
```

Variáveis de ambiente opcionais:

```bash
FINANCIAL_ASSISTANT_ENCRYPTION_KEY=sua-chave-forte-32-bytes-minimo
APP_API_USER=admin
APP_API_PASSWORD=senha-segura
```

---

## Endpoints da API REST

### Open Finance — Dados Abertos

| Método | Path | Descrição |
|--------|------|-----------|
| GET | `/api/v1/opendata/directory/institutions` | Lista instituições do diretório Open Finance |

### Assistente Financeiro

#### Ingestão de Eventos

| Método | Path | Body |
|--------|------|------|
| POST | `/api/v1/financial-assistant/ingest/email` | `List<EmailMessageInput>` |
| POST | `/api/v1/financial-assistant/ingest/csv` | `List<CsvTransactionInput>` |
| POST | `/api/v1/financial-assistant/ingest/ocr` | `List<OcrReceiptInput>` |
| POST | `/api/v1/financial-assistant/ingest/manual` | `List<ManualEventInput>` |
| POST | `/api/v1/financial-assistant/ingest/messages` | `List<MessageTransactionInput>` |
| POST | `/api/v1/financial-assistant/ingest/open-finance` | `List<BankTransactionInput>` |

#### Análise

| Método | Path | Descrição |
|--------|------|-----------|
| POST | `/api/v1/financial-assistant/analysis/classify` | Classifica eventos sem categoria |
| GET | `/api/v1/financial-assistant/analysis/patterns` | Detecta padrões de gastos recorrentes |
| GET | `/api/v1/financial-assistant/analysis/forecast` | Previsão de fluxo de caixa (30 dias) |

#### Recomendações

| Método | Path | Descrição |
|--------|------|-----------|
| POST | `/api/v1/financial-assistant/advisor/recommendations/generate` | Gera recomendações |
| GET | `/api/v1/financial-assistant/advisor/recommendations/pending` | Lista recomendações pendentes |
| POST | `/api/v1/financial-assistant/advisor/recommendations/{id}/decision` | Aprovar ou rejeitar |
| POST | `/api/v1/financial-assistant/advisor/recommendations/{id}/execute` | Executar aprovada |

#### Consentimento e Segurança

| Método | Path | Descrição |
|--------|------|-----------|
| POST | `/api/v1/financial-assistant/security/consent/grant` | Conceder consentimento |
| POST | `/api/v1/financial-assistant/security/consent/revoke` | Revogar consentimento |
| GET | `/api/v1/financial-assistant/security/consent/check` | Verificar consentimento |
| POST | `/api/v1/financial-assistant/security/encrypt` | Criptografar valor (AES-GCM) |
| POST | `/api/v1/financial-assistant/security/decrypt` | Descriptografar valor |

**Autenticação:** HTTP Basic. Usuário e senha configurados via `APP_API_USER` / `APP_API_PASSWORD` (default: `admin` / `change-me-in-production`).

---

## Configuração (`application.yaml`)

```yaml
server:
  port: 8081

spring:
  threads:
    virtual:
      enabled: true        # Java 21 Virtual Threads
  datasource:
    url: jdbc:postgresql://localhost:5432/opendata
  security:
    user:
      name: ${APP_API_USER:admin}
      password: ${APP_API_PASSWORD:change-me-in-production}

open-finance:
  directory:
    participants-url: https://data.directory.openbankingbrasil.org.br/participants
  opendata:
    institution-include: []  # vazio = todas as instituições

financial-assistant:
  security:
    encryption-key: ${FINANCIAL_ASSISTANT_ENCRYPTION_KEY:change-this-key-32-bytes-minimum!}
```

---

## Módulo Assistente Financeiro — Pipeline

```
Fonte (email/CSV/OCR/manual/mensagem/Open Finance)
    └─► EventNormalizerService   (normaliza + gera fingerprint SHA-256)
        └─► EventIngestionService    (deduplicação, persistência em batch)
            └─► CategoryClassificationService  (classifica por keywords)
                └─► PatternEngineService        (recorrência + spike de gastos)
                    └─► RecommendationEngineService  (gera recomendações)
                        └─► ApprovalWorkflowService  (decide + executa)
```

---

## Frontend IA — Funcionalidades

- **AI Autopilot:** seleciona automaticamente o pattern e estratégia com base no texto (heurísticas + `suggest_pattern` via backend)
- **Workflows:** atalhos pré-configurados (Resumir, Extrair Insights, Explicar Código, Revisar Código, Comparar, Melhorar Texto, Criar Plano)
- **ResponseRefiner:** refina respostas de baixa confiança com `improve_writing`
- **Métricas de sessão:** painel com taxa de sucesso, fallback, tempo médio e confiança do autopilot

---

## CI/CD

GitHub Actions em `.github/workflows/ci.yml`:
- Build Maven + testes (com PostgreSQL via service container)
- Build Docker image
- Ativado em push para `main`, `master`, `develop`, branches `2026-*` e PRs

---

## Estrutura de pacotes Java

```
br.aof.read_opendata_apis/
├── application/
│   ├── controller/    OpenDataDirectoryController
│   ├── dto/           OpenDataDTO, ErrorOpenDataDTO, ParticipantDTO, ApiLoansDTO
│   ├── exception/     GlobalExceptionHandler, ApplicationException (hierarquia)
│   ├── repository/    OpendataRepository, ErrorOpendataRepository
│   └── service/       CentralDirectoryIntegration, ReadBankDataService, ReadOpenDataEndpoints
├── domain/
│   ├── event/         RawAccountEvent
│   └── exception/     DomainException (hierarquia)
├── financialassistant/
│   ├── application/   controller, dto (15 records), repository (4), service (16)
│   ├── domain/model/  ConsentRecord, FinanceEvent, RecommendationItem, SecurityAuditEntry + enums
│   └── infrastructure/config/ FinancialAssistantSecurityProperties
├── infrastructure/config/
│   ├── OpenFinanceProperties
│   └── SecurityConfig
└── scheduler/
    └── DataPumpScheduler
```

---

## Licença

Veja [LICENSE](LICENSE).
