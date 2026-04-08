# ARCÁDIA OS v1.0

Sistema Operacional de Decisão Autônoma - Máximo ganho financeiro com carga mental mínima.

## Propriedade e dependências

**ARCÁDIA OS** — interface, Parlamento, Orquestrador, Supabase e rotas `/api` (chat e padrões embutidos com **Google Gemini** no servidor) — faz parte do **fin_search** em **`tools/arcadia-web`**. **Não** é obrigatório instalar o CLI Fabric (upstream) nem serviço na porta 18080.

## 🏛️ Visão Geral

O ARCÁDIA OS é um sistema inteligente que transforma suas finanças e produtividade em uma infraestrutura de execução contínua, regida por uma constituição ética auto-adaptativa.

### Características Principais

- **Parlamento Cognitivo**: 5 agentes especializados debatem cada decisão financeira
- **Constituição Financeira**: Princípios e regras personalizáveis que guiam todas as decisões
- **Persistência Automática**: Todas as decisões são salvas e podem ser auditadas
- **Dashboard Inteligente**: KPIs financeiros e métricas de performance em tempo real
- **Onboarding Interativo**: Introdução completa ao sistema para novos usuários

## 🚀 Instalação e Configuração

### 1. Pré-requisitos

- Node.js 18+
- Projeto Supabase
- Chave **ARCADIA_GEMINI_API_KEY** (ou `GEMINI_API_KEY`) no `.env` do servidor

### 2. Configuração do Supabase

1. Crie um projeto no [Supabase](https://supabase.com)
2. Execute as migrações SQL na pasta `supabase/migrations/`
3. Configure as variáveis de ambiente

### 3. Configuração do Frontend

```bash
# Instalar dependências
npm install

# Configurar ambiente
copy env.example .env
# Edite .env: Supabase + ARCADIA_GEMINI_API_KEY

# Executar em desenvolvimento
npm run dev
```

### 4. Iniciar o servidor de desenvolvimento

Na raiz do repositório:

```powershell
.\tools\start_fabric_local_app.ps1
```

Ou, nesta pasta: `npm run dev`. O chat usa `POST /api/chat` (Gemini); não é necessário backend separado.

## 📋 Funcionalidades Implementadas

### ✅ Sistema de Autenticação
- Login com Magic Link (principal)
- Login com email/senha (fallback)
- Proteção de rotas automática
- Redirecionamento inteligente

### ✅ Constituição Financeira
- Carregamento automático após login
- Página dedicada para edição
- Versionamento automático
- Integração com o sistema de decisões

### ✅ Dashboard Principal
- KPIs financeiros (impacto líquido, taxa de sucesso, confiança)
- Status do sistema (execuções, precisão, riscos)
- Últimas decisões executadas
- Auto-refresh a cada 30 segundos

### ✅ Parlamento Cognitivo
- **Dr. Prudência**: Analista Conservador (foco em segurança)
- **Alex Crescimento**: Estrategista Agressivo (foco em retorno)
- **Dra. Psique**: Especialista Comportamental (foco no bem-estar)
- **Prof. Números**: Analista Quantitativo (foco em dados)
- **Juíza Ética**: Conselheira Ética (foco nos valores)

### ✅ Orquestrador Inteligente
- Detecta automaticamente quando acionar o Parlamento
- Integra decisões parlamentares com o modelo de IA (stream via `/api/chat`)
- Persistência automática de todas as decisões

### ✅ Histórico de Decisões
- Listagem completa com filtros (status, tipo, busca)
- Detalhes completos de cada decisão
- Paginação e ordenação
- Visualização de riscos e planos de execução

### ✅ Onboarding Interativo
- Introdução ao conceito do ARCÁDIA OS
- Explicação do Parlamento Cognitivo
- Apresentação da Constituição Financeira
- Tutorial de uso do sistema

## 🎯 Como Usar

### 1. Primeiro Acesso
1. Faça login com email ou Magic Link
2. Complete o onboarding interativo
3. Sua constituição será criada automaticamente

### 2. Fazendo Perguntas Financeiras
```
Exemplo: "Devo investir R$ 10.000 em ações ou deixar na poupança?"
```

O sistema automaticamente:
- Detecta que é uma decisão financeira importante
- Aciona o Parlamento Cognitivo
- Cada agente apresenta sua perspectiva
- Gera uma decisão fundamentada
- Salva tudo no histórico

### 3. Monitoramento
- Acesse o Dashboard para métricas em tempo real
- Consulte o Histórico para revisar decisões passadas
- Edite sua Constituição para ajustar comportamentos

## 🏗️ Arquitetura

### Frontend (SvelteKit)
- **Rotas Protegidas**: Sistema de autenticação integrado
- **Stores Reativos**: Estado global gerenciado com Svelte stores
- **Componentes Modulares**: UI consistente com Skeleton UI + Tailwind

### Backend (Supabase)
- **Autenticação**: Magic Link + email/senha
- **Banco de Dados**: PostgreSQL com RLS (Row Level Security)
- **Funções**: Queries otimizadas para dashboard e métricas
- **Triggers**: Automação de criação de dados padrão

### API de IA (SvelteKit)
- **Rotas `/api`**: `POST /api/chat` (Gemini, SSE), `GET /api/patterns/*` (padrões embutidos)
- **Streaming**: respostas em tempo real para o cliente
- **Chave**: só no servidor (`ARCADIA_GEMINI_API_KEY`)

## 📊 Schema do Banco de Dados

### Tabelas Principais
- `users`: Perfis de usuário vinculados ao auth.users
- `constitutions`: Constituições financeiras dos usuários
- `strategies`: Estratégias disponíveis no marketplace
- `sessions`: Sessões de chat/interação
- `decision_history`: Histórico completo de decisões
- `performance_metrics`: Métricas de performance das decisões

### Funções Otimizadas
- `dashboard_financial_kpis()`: KPIs financeiros principais
- `dashboard_system_status()`: Status atual do sistema
- `dashboard_latest_executed_decisions()`: Últimas decisões
- `seed_default_arcadia_data()`: Criação automática de dados padrão

## 🔧 Desenvolvimento

### Estrutura de Pastas
```
src/
├── lib/
│   ├── components/     # Componentes UI
│   ├── services/       # Lógica de negócio
│   ├── store/         # Stores Svelte
│   └── config/        # Configurações
├── routes/
│   ├── (auth)/        # Rotas protegidas
│   └── login/         # Página de login
└── supabase/
    └── migrations/    # Migrações SQL
```

### Principais Serviços
- `Parliament.ts`: Sistema de debate multi-agente
- `Orchestrator.ts`: Coordenação de fluxos de decisão
- `ParliamentPersistenceService.ts`: Persistência no Supabase
- `ConstitutionService.ts`: Gerenciamento de constituições
- `SupabaseClient.ts`: Cliente Supabase singleton

## 🎨 UI/UX

### Design System
- **Cores**: Gradientes primary/secondary/tertiary
- **Tipografia**: Hierarquia clara com títulos e metadados
- **Componentes**: Cards com glassmorphism e bordas sutis
- **Responsividade**: Mobile-first com breakpoints MD/LG

### Experiência do Usuário
- **Onboarding**: Introdução passo-a-passo
- **Feedback**: Loading states e mensagens de sucesso/erro
- **Navegação**: Breadcrumbs e botões de ação claros
- **Performance**: Auto-refresh e otimizações de query

## 🔒 Segurança

### Autenticação
- Magic Link como método principal
- Fallback para email/senha
- Sessões gerenciadas pelo Supabase Auth

### Autorização
- Row Level Security (RLS) em todas as tabelas
- Políticas específicas por usuário
- Validação de permissões no frontend e backend

### Dados
- Criptografia em trânsito (HTTPS)
- Isolamento por usuário
- Backup automático (Supabase)

## 📈 Métricas e Monitoramento

### KPIs Financeiros
- Impacto Líquido (últimos 30 dias)
- Impacto Esperado (projeção mensal)
- Taxa de Sucesso das Execuções
- Confiança Média das Decisões

### Métricas do Sistema
- Execuções (24h/7d)
- Score de Precisão
- Sessões Ativas do Autopilot
- Eventos de Risco Abertos

## 🚧 Próximos Passos

### Melhorias Planejadas
- [ ] Integração com APIs bancárias (Open Finance)
- [ ] Marketplace de Estratégias com Prova de Valor
- [ ] Notificações push para decisões importantes
- [ ] Exportação de relatórios (PDF/Excel)
- [ ] Integração com assistentes de voz
- [ ] App mobile (React Native)

### Otimizações
- [ ] Cache Redis para queries frequentes
- [ ] Compressão de assets estáticos
- [ ] Lazy loading de componentes
- [ ] Service Workers para offline

## 📝 Licença

**ARCÁDIA OS / fin_search:** segue a licença do repositório raiz ([LICENSE](../../../LICENSE) na raiz do projeto, quando aplicável).

## 🤝 Contribuição

Para contribuir com o ARCÁDIA OS (fin_search):

1. Fork o repositório
2. Crie uma branch para sua feature
3. Implemente seguindo os padrões estabelecidos
4. Teste thoroughly
5. Abra um Pull Request

## 📞 Suporte

Para suporte e dúvidas sobre **ARCÁDIA OS**:
- Documentação neste repositório (`ARCADIA_README.md`, `README.md` na raiz)
- Issues no repositório **fin_search**

---

**ARCÁDIA OS v1.0** - Sistema Operacional de Decisão Autônoma
*Máximo ganho financeiro com carga mental mínima*