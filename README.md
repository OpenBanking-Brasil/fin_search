# fin_search — Open Finance + ARCÁDIA OS

Plataforma que combina leitura de dados abertos do **Open Finance Brasil** com o **ARCÁDIA OS** - Sistema Operacional de Decisão Autônoma que oferece máximo ganho financeiro com carga mental mínima através de um Parlamento Cognitivo inteligente.

**Propriedade:** este repositório (**fin_search**) é o projeto principal; o **ARCÁDIA OS** (UI, Parlamento, Orquestrador, Supabase, rotas `/api` com Gemini) vive em `tools/Fabric/web`. O código histórico mantém a pasta `Fabric` no caminho; **não** é necessário o binário Fabric nem a porta 18080.

---

## Arquitetura

```
┌─────────────────────────────────────────────────────────────┐
│  🏛️ ARCÁDIA OS (SvelteKit)  —  Vite (ex.: 5199)             │
│  Parliament + Orchestrator + /api/chat (Gemini no servidor)  │
└────────────────────┬────────────────────────────────────────┘
                     │ HTTPS API
┌────────────────────▼────────────────────────────────────────┐
│  📊 Supabase (PostgreSQL + Auth + API)                      │
│  RLS + Triggers + Functions + Real-time                     │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│  ☕ Spring Boot 4 (Java 21)  —  porta 8081                  │
│  Open Finance + Assistente Financeiro REST API             │
└────────────────────┬────────────────────────────────────────┘
                     │ JDBC
┌────────────────────▼────────────────────────────────────────┐
│  🐘 PostgreSQL 16  —  porta 5432                            │
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
| IA | ARCÁDIA OS (rotas SvelteKit) + Google Gemini |
| Database | Supabase (PostgreSQL + Auth + Real-time) |
| Infra | Docker, Docker Compose, GitHub Actions CI |

---

## Pré-requisitos

- Java 21+
- Maven (ou use `./mvnw`)
- Docker + Docker Compose
- Node.js 20+ (para o frontend ARCÁDIA)
- Chave de API Gemini no `.env` do web (`ARCADIA_GEMINI_API_KEY`; ver `tools/Fabric/web/env.example`)
- Conta Supabase (para o ARCÁDIA OS)

---

## Início rápido

### 1. Subir infraestrutura (PostgreSQL + pgAdmin)

```bash
docker compose up -d postgres pgadmin
```

pgAdmin disponível em http://localhost:8080 (admin@exemplo.com / admin123)

### 2. Subir backend Spring Boot

```bash
./mvnw spring-boot:run
```

API disponível em http://localhost:8081  
Swagger UI: http://localhost:8081/swagger-ui.html  
Health check: http://localhost:8081/healthcheck

### 3. Configurar e iniciar ARCÁDIA OS

```powershell
# Setup (Supabase + npm; copiar env.example → .env e ARCADIA_GEMINI_API_KEY)
.\tools\setup_arcadia_os.ps1 -SupabaseUrl "https://xxx.supabase.co" -SupabaseKey "your-key"

# Ou inicie o Vite (API em /api no mesmo processo):
.\tools\start_fabric_local_app.ps1
# cd tools\Fabric\web && npm run dev
```

**ARCÁDIA OS** — URL mostrada pelo Vite (por defeito `http://127.0.0.1:5199`).

Aplique as migrações SQL em `supabase/migrations/` no projeto Supabase (inclui **Planeamento** + fluxo de caixa + KPIs). Depois do login: **`/planning`** (objetivos, revisões, importação CSV) e **`POST /api/finance/ingest`** (opcional, com service role no servidor).

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

## 🏛️ ARCÁDIA OS — Sistema Operacional de Decisão Autônoma

### Visão Geral

O **ARCÁDIA OS** é um sistema inteligente que transforma suas finanças em uma infraestrutura de execução contínua, oferecendo **máximo ganho financeiro com carga mental mínima** através de decisões automatizadas e fundamentadas.

### Funcionalidades Principais

#### 🗣️ Parlamento Cognitivo
Sistema de debate multi-perspectiva com 5 agentes especializados:
- **Dr. Prudência** (Conservador): Foca na segurança e gestão de riscos
- **Alex Crescimento** (Agressivo): Busca oportunidades de alto retorno  
- **Dra. Psique** (Comportamental): Analisa o impacto psicológico
- **Prof. Números** (Quantitativo): Decisões baseadas em dados
- **Juíza Ética** (Ética): Garante alinhamento com valores pessoais

#### 📜 Constituição Financeira
- Princípios e regras personalizáveis que guiam todas as decisões
- Versionamento automático das alterações
- Interface de edição intuitiva
- Integração completa com o sistema de decisões

#### 📊 Dashboard Inteligente
- **KPIs Financeiros**: Impacto líquido, taxa de sucesso, confiança média
- **Status do Sistema**: Execuções, precisão, sessões ativas, riscos
- **Últimas Decisões**: Histórico com detalhes completos
- Auto-refresh em tempo real

#### 🤖 Orquestrador Inteligente
- Detecta automaticamente quando acionar o Parlamento
- Integração híbrida: Parliament + Fabric
- Persistência automática de todas as decisões

#### 📈 Histórico Completo
- Todas as decisões com filtros e busca
- Detalhes expandidos (riscos, planos, metadados)
- Auditoria completa do processo de decisão

#### 🚀 Onboarding Interativo
- Introdução passo-a-passo ao sistema
- Tutorial do Parlamento Cognitivo
- Guia de uso prático

### Como Usar o ARCÁDIA OS

1. **Acesse**: http://localhost:5173
2. **Faça login** com Magic Link ou email/senha
3. **Complete o onboarding** interativo
4. **Faça perguntas financeiras** no chat:
   ```
   "Devo investir R$ 10.000 em ações ou deixar na poupança?"
   ```
5. **O sistema automaticamente**:
   - Aciona o Parlamento Cognitivo
   - Analisa com base na sua constituição
   - Apresenta decisão fundamentada
   - Salva tudo no histórico

### Documentação Completa

Consulte `tools/Fabric/web/ARCADIA_README.md` para documentação detalhada incluindo:
- Arquitetura completa
- Schema do banco de dados
- Guias de desenvolvimento
- Scripts de automação

---

## Frontend IA — Funcionalidades

- **AI Autopilot:** seleciona pattern e estratégia (heurísticas + `suggest_pattern` via API)
- **Workflows:** atalhos pré-configurados (Resumir, Extrair Insights, Explicar Código, Revisar Código, Comparar, Melhorar Texto, Criar Plano)
- **ResponseRefiner:** refina respostas de baixa confiança com `improve_writing`
- **Métricas de sessão:** painel com taxa de sucesso, fallback, tempo médio e confiança do autopilot

---

## 🛠️ Scripts de Automação

### ARCÁDIA OS
- `setup_arcadia_os.ps1`: npm install + `.env` (Supabase; lembrete `ARCADIA_GEMINI_API_KEY`)
- `start_fabric_local_app.ps1`: inicia só o Vite/SvelteKit (API `/api` no mesmo processo)

### Opcional (CLI Fabric a montante, não necessário para o ARCÁDIA OS)
- `fabric_aliases.ps1`, `fabric_update.ps1`, `fabric_gemini_setup.ps1`: úteis se usar o projeto Fabric em separado

### Uso rápido
```powershell
.\tools\setup_arcadia_os.ps1 -SupabaseUrl "https://xxx.supabase.co" -SupabaseKey "your-key"
.\tools\start_fabric_local_app.ps1
```

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
