-- ==============================================================================
-- INICIALIZAÇÃO E EXTENSÕES
-- ==============================================================================

-- Garante que a função gen_random_uuid() esteja disponível no banco de dados
CREATE EXTENSION IF NOT EXISTS pgcrypto;


-- ==============================================================================
-- 1. MÓDULO DE AUTENTICAÇÃO E PERMISSÕES (ACL)
-- ==============================================================================

-- Tabela Base de Usuários
CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    is_global_admin BOOLEAN DEFAULT FALSE,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Tabela Central de Categorias (Lista oficial do que existe no sistema)
CREATE TABLE IF NOT EXISTS categories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) UNIQUE NOT NULL,       -- Ex: 'Mulher', 'Município'
    entity_type VARCHAR(50) NOT NULL,        -- Define se é 'native' ou 'dynamic'
    table_reference VARCHAR(100) NOT NULL,   -- Tabela no banco (ex: 'clients' ou 'dynamic_entities')
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Matriz de Permissões (Controle ultra-granular por usuário e por coluna)
CREATE TABLE IF NOT EXISTS permissions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    category_id UUID NOT NULL REFERENCES categories(id) ON DELETE CASCADE,
    can_read BOOLEAN DEFAULT FALSE,
    can_create BOOLEAN DEFAULT FALSE,
    can_update BOOLEAN DEFAULT FALSE,
    can_delete BOOLEAN DEFAULT FALSE,
    -- JSONB para dizer quais colunas o usuário pode ver/editar. 
    -- Ex: {"read": ["nome", "telefone", "sob_risco"], "write": ["telefone"]}
    -- Sem a chave "read"/"write", todas as colunas são liberadas para aquela operação.
    allowed_columns JSONB DEFAULT '{}'::jsonb,
    -- Nível máximo de sigilo de documento acessível: INTERNAL, RESTRICTED ou CONFIDENTIAL
    max_confidentiality VARCHAR(20) NOT NULL DEFAULT 'INTERNAL',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id, category_id) -- Impede permissões duplicadas para o mesmo usuário na mesma categoria
);


-- ==============================================================================
-- 2. MÓDULO DE ENTIDADES (O CORAÇÃO MODULAR)
-- ==============================================================================

-- Tabela de Categoria Nativa: Clientes (Atendidas / Mulheres)
-- (Alta performance, regras de negócio rígidas)
CREATE TABLE IF NOT EXISTS clients (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nome VARCHAR(255) NOT NULL,
    data_nascimento DATE,
    telefone VARCHAR(50),
    sob_risco BOOLEAN DEFAULT FALSE,
    created_by UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Tabela de Categorias Genéricas (Dinâmicas)
CREATE TABLE IF NOT EXISTS dynamic_entities (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    category_id UUID NOT NULL REFERENCES categories(id) ON DELETE RESTRICT,
    custom_fields JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_by UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Índices para listagem por categoria e buscas por conteúdo (operador @>)
CREATE INDEX IF NOT EXISTS idx_dynamic_entities_category ON dynamic_entities(category_id);
CREATE INDEX IF NOT EXISTS idx_dynamic_entities_custom_fields ON dynamic_entities USING GIN (custom_fields);

-- Definição dos campos customizáveis de cada categoria dinâmica (o "formulário")
CREATE TABLE IF NOT EXISTS category_fields (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    category_id UUID NOT NULL REFERENCES categories(id) ON DELETE CASCADE,
    field_key VARCHAR(100) NOT NULL,         -- Chave usada no JSONB (ex: 'bairro')
    label VARCHAR(255) NOT NULL,             -- Rótulo exibido (ex: 'Bairro')
    field_type VARCHAR(20) NOT NULL,         -- TEXT, NUMBER, DATE, BOOLEAN, LIST
    is_required BOOLEAN DEFAULT FALSE,
    options JSONB,                           -- Opções válidas quando field_type = 'LIST'
    position INTEGER DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(category_id, field_key)
);


-- ==============================================================================
-- 3. MÓDULO DE HISTÓRICO / LEDGER
-- ==============================================================================

-- Diário Universal e Imutável de Atividades
CREATE TABLE IF NOT EXISTS activities_ledger (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    -- Polimorfismo: Qual tabela? (Ex: 'clients' ou 'dynamic_entities')
    entity_type VARCHAR(100) NOT NULL, 
    -- Polimorfismo: Qual registro específico dentro da tabela?
    entity_id UUID NOT NULL, 
    user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    
    -- Ex: 'MANUAL_ENTRY' (Atendimento anotado) ou 'AUTO_UPDATE' (Sistema auditando alteração)
    action_type VARCHAR(50) NOT NULL, 
    
    -- Texto livre do registro da atividade
    description TEXT, 
    
    -- O "Ledger" para auditoria de segurança (O que mudou)
    -- Ex: {"campo": "telefone", "antigo": "1111", "novo": "2222"}
    changes JSONB,

    -- Funcionário que realizou o atendimento (pode não ser usuário do sistema)
    responsible_employee VARCHAR(255),

    -- Quando a atividade aconteceu (pode ser anterior ao registro no sistema)
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Índices de performance cruciais para tabelas polimórficas gigantes
CREATE INDEX IF NOT EXISTS idx_ledger_polymorphic ON activities_ledger(entity_type, entity_id);
CREATE INDEX IF NOT EXISTS idx_ledger_user ON activities_ledger(user_id);

-- Tipos de registro manual configuráveis por categoria (ex: 'Atendimento psicológico', 'Matrícula')
CREATE TABLE IF NOT EXISTS activity_types (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    category_id UUID NOT NULL REFERENCES categories(id) ON DELETE CASCADE,
    key VARCHAR(100) NOT NULL,               -- Identificador estável (^[a-z][a-z0-9_]*$), não muda depois de criado
    label VARCHAR(255) NOT NULL,             -- Nome exibido
    description TEXT,
    color VARCHAR(20),                       -- Ex: '#5b2a86'
    position INTEGER DEFAULT 0,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(category_id, key)
);

-- Registros manuais apontam para o tipo e guardam o nome dele no momento do registro (auditoria imutável)
ALTER TABLE activities_ledger
    ADD COLUMN IF NOT EXISTS activity_type_id UUID REFERENCES activity_types(id) ON DELETE RESTRICT;
ALTER TABLE activities_ledger ADD COLUMN IF NOT EXISTS activity_type_label VARCHAR(255);
CREATE INDEX IF NOT EXISTS idx_ledger_activity_type ON activities_ledger(activity_type_id);

-- Tipos que o usuário pode registrar na categoria (lista de keys); NULL = todos
ALTER TABLE permissions ADD COLUMN IF NOT EXISTS allowed_activity_types JSONB;

-- Migração (idempotente): os quatro tipos padrão em cada categoria que ainda não tem nenhum tipo
INSERT INTO activity_types (category_id, key, label, position)
SELECT c.id, d.key, d.label, d.position
FROM categories c
CROSS JOIN (VALUES ('triagem', 'Triagem', 0), ('atendimento', 'Atendimento', 1),
                   ('acompanhamento', 'Acompanhamento', 2), ('anotacao', 'Anotação', 3)) AS d(key, label, position)
WHERE NOT EXISTS (SELECT 1 FROM activity_types t WHERE t.category_id = c.id);

-- Migração (idempotente): registros manuais antigos (enum fixo) passam a apontar para o tipo da categoria
-- do registro. Os que não têm categoria identificável (ex: registro já excluído) continuam como estão.
UPDATE activities_ledger l
SET action_type = 'MANUAL', activity_type_id = t.id, activity_type_label = t.label
FROM activity_types t
WHERE l.action_type IN ('TRIAGE', 'SERVICE', 'FOLLOW_UP', 'NOTE')
  AND t.key = CASE l.action_type
                  WHEN 'TRIAGE' THEN 'triagem'
                  WHEN 'SERVICE' THEN 'atendimento'
                  WHEN 'FOLLOW_UP' THEN 'acompanhamento'
                  ELSE 'anotacao'
              END
  AND t.category_id = CASE l.entity_type
                          WHEN 'clients' THEN (SELECT c.id FROM categories c
                                               WHERE c.table_reference = 'clients' ORDER BY c.created_at LIMIT 1)
                          WHEN 'dynamic_entities' THEN (SELECT e.category_id FROM dynamic_entities e
                                                        WHERE e.id = l.entity_id)
                      END;


-- ==============================================================================
-- 4. MÓDULO DE ARQUIVOS CENTRALIZADO
-- ==============================================================================

-- Repositório Único de Documentos e Anexos
CREATE TABLE IF NOT EXISTS files (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    -- Polimorfismo (Idêntico ao do Ledger)
    entity_type VARCHAR(100) NOT NULL, 
    entity_id UUID NOT NULL, 
    
    uploaded_by UUID REFERENCES users(id) ON DELETE SET NULL,
    
    -- Metadados do arquivo
    original_name VARCHAR(255) NOT NULL,
    storage_path VARCHAR(500) NOT NULL, -- Caminho no S3, GCP, ou disco local
    file_size_bytes BIGINT,
    content_type VARCHAR(255),

    -- Nível de sigilo: INTERNAL (público interno), RESTRICTED (restrito), CONFIDENTIAL (sigiloso)
    confidentiality_level VARCHAR(20) NOT NULL DEFAULT 'RESTRICTED',

    uploaded_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Índice para buscas rápidas ao abrir a pasta de anexos de um registro
CREATE INDEX IF NOT EXISTS idx_files_polymorphic ON files(entity_type, entity_id);


-- ==============================================================================
-- 5. MÓDULO FINANCEIRO (modules/finance)
-- ==============================================================================
-- Acesso pela categoria nativa com table_reference = 'finance_actions'.
-- Os saldos gravados em finance_actions.total_amount e finance_sub_actions.balance são sempre
-- a soma dos lançamentos (finance_entries); somar os lançamentos até uma data dá o saldo naquele dia.

-- Ações: valor total = valor distribuído nas subações + saldo a distribuir
CREATE TABLE IF NOT EXISTS finance_actions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(50) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    total_amount NUMERIC(15,2) NOT NULL DEFAULT 0 CHECK (total_amount >= 0),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_finance_actions_code ON finance_actions (LOWER(code));

-- Subações: código único em todo o sistema
CREATE TABLE IF NOT EXISTS finance_sub_actions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    action_id UUID NOT NULL REFERENCES finance_actions(id) ON DELETE CASCADE,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    balance NUMERIC(15,2) NOT NULL DEFAULT 0 CHECK (balance >= 0),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_finance_sub_actions_code ON finance_sub_actions (LOWER(code));
CREATE INDEX IF NOT EXISTS idx_finance_sub_actions_action ON finance_sub_actions(action_id);

-- Operações (imutáveis): OPENING, TRANSFER_BETWEEN_SUB_ACTIONS (Método A),
-- TRANSFER_BETWEEN_ACTIONS (Método B), TAC (Método C) e DISTRIBUTION
CREATE TABLE IF NOT EXISTS finance_operations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    number BIGSERIAL UNIQUE,                 -- Número sequencial da operação (ordem do histórico)
    method VARCHAR(40) NOT NULL,
    amount NUMERIC(15,2) NOT NULL CHECK (amount > 0),
    description TEXT,
    performed_by UUID REFERENCES users(id) ON DELETE SET NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_finance_operations_occurred_at ON finance_operations(occurred_at);

-- Lançamentos (imutáveis), com sinal. sub_action_id nulo = mexe no valor total da ação;
-- preenchido = mexe no saldo da subação
CREATE TABLE IF NOT EXISTS finance_entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    operation_id UUID NOT NULL REFERENCES finance_operations(id) ON DELETE RESTRICT,
    action_id UUID NOT NULL REFERENCES finance_actions(id) ON DELETE RESTRICT,
    sub_action_id UUID REFERENCES finance_sub_actions(id) ON DELETE RESTRICT,
    amount NUMERIC(15,2) NOT NULL CHECK (amount <> 0)
);
CREATE INDEX IF NOT EXISTS idx_finance_entries_operation ON finance_entries(operation_id);
CREATE INDEX IF NOT EXISTS idx_finance_entries_action ON finance_entries(action_id);
CREATE INDEX IF NOT EXISTS idx_finance_entries_sub_action ON finance_entries(sub_action_id);

-- ------------------------------------------------------------------------------
-- 5.1 Execução da despesa: empenho -> liquidação -> pagamento (e anulação de empenho)
-- ------------------------------------------------------------------------------

-- Tipo do lançamento: BUDGET (dotação), COMMITTED (empenhado), LIQUIDATED (liquidado), PAID (pago)
ALTER TABLE finance_entries ADD COLUMN IF NOT EXISTS kind VARCHAR(20) NOT NULL DEFAULT 'BUDGET';

-- Execução acumulada na subação. Disponível = balance (dotação) - committed_amount
ALTER TABLE finance_sub_actions ADD COLUMN IF NOT EXISTS committed_amount NUMERIC(15,2) NOT NULL DEFAULT 0;
ALTER TABLE finance_sub_actions ADD COLUMN IF NOT EXISTS liquidated_amount NUMERIC(15,2) NOT NULL DEFAULT 0;
ALTER TABLE finance_sub_actions ADD COLUMN IF NOT EXISTS paid_amount NUMERIC(15,2) NOT NULL DEFAULT 0;
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_finance_sub_actions_execution') THEN
        ALTER TABLE finance_sub_actions ADD CONSTRAINT ck_finance_sub_actions_execution
            CHECK (paid_amount >= 0 AND paid_amount <= liquidated_amount
                   AND liquidated_amount <= committed_amount AND committed_amount <= balance);
    END IF;
END $$;

-- Empenhos (notas de empenho)
CREATE TABLE IF NOT EXISTS finance_commitments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    number VARCHAR(50) NOT NULL,             -- Ex: '2026NE000123'
    sub_action_id UUID NOT NULL REFERENCES finance_sub_actions(id) ON DELETE RESTRICT,
    action_id UUID NOT NULL REFERENCES finance_actions(id) ON DELETE RESTRICT,
    creditor VARCHAR(255) NOT NULL,          -- Favorecido
    description TEXT,
    amount NUMERIC(15,2) NOT NULL CHECK (amount > 0),        -- Valor original
    cancelled_amount NUMERIC(15,2) NOT NULL DEFAULT 0,       -- Anulado
    liquidated_amount NUMERIC(15,2) NOT NULL DEFAULT 0,
    paid_amount NUMERIC(15,2) NOT NULL DEFAULT 0,
    created_by UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_finance_commitments_execution
        CHECK (cancelled_amount >= 0 AND paid_amount >= 0 AND paid_amount <= liquidated_amount
               AND liquidated_amount <= amount - cancelled_amount)
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_finance_commitments_number ON finance_commitments (LOWER(number));
CREATE INDEX IF NOT EXISTS idx_finance_commitments_sub_action ON finance_commitments(sub_action_id);
CREATE INDEX IF NOT EXISTS idx_finance_commitments_action ON finance_commitments(action_id);

-- Operações de empenho, anulação, liquidação e pagamento apontam para o empenho
ALTER TABLE finance_operations
    ADD COLUMN IF NOT EXISTS commitment_id UUID REFERENCES finance_commitments(id) ON DELETE RESTRICT;
CREATE INDEX IF NOT EXISTS idx_finance_operations_commitment ON finance_operations(commitment_id);

-- ------------------------------------------------------------------------------
-- 5.2 Data real das operações (ex.: data do empenho, do pagamento)
-- ------------------------------------------------------------------------------
-- operation_date define os saldos em cada dia e a ordem do histórico; occurred_at continua sendo
-- o momento do registro. Linhas antigas recebem a data do registro (migração idempotente).
ALTER TABLE finance_operations ADD COLUMN IF NOT EXISTS operation_date DATE;
UPDATE finance_operations SET operation_date = occurred_at::date WHERE operation_date IS NULL;
ALTER TABLE finance_operations ALTER COLUMN operation_date SET DEFAULT CURRENT_DATE;
ALTER TABLE finance_operations ALTER COLUMN operation_date SET NOT NULL;
CREATE INDEX IF NOT EXISTS idx_finance_operations_operation_date ON finance_operations(operation_date);

ALTER TABLE finance_commitments ADD COLUMN IF NOT EXISTS commitment_date DATE;
UPDATE finance_commitments SET commitment_date = created_at::date WHERE commitment_date IS NULL;
ALTER TABLE finance_commitments ALTER COLUMN commitment_date SET DEFAULT CURRENT_DATE;
ALTER TABLE finance_commitments ALTER COLUMN commitment_date SET NOT NULL;


-- ==============================================================================
-- 6. MÓDULO DE FROTAS (modules/fleet)
-- ==============================================================================
-- Acesso pela categoria nativa com table_reference = 'fleet_trips'.

-- Motoristas (CNH única; um usuário do sistema por motorista, opcional)
CREATE TABLE IF NOT EXISTS fleet_drivers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    phone VARCHAR(50),
    license_number VARCHAR(20) NOT NULL UNIQUE,      -- Número da CNH
    license_category VARCHAR(5) NOT NULL,            -- A, B, C, D, E, AB, AC, AD, AE
    license_expiry DATE NOT NULL,                    -- Validade da CNH
    user_id UUID UNIQUE REFERENCES users(id) ON DELETE SET NULL,
    notes TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Veículos (placa única, sem hífen e em maiúsculas; capacity = passageiros, sem o motorista)
CREATE TABLE IF NOT EXISTS fleet_vehicles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    plate VARCHAR(10) NOT NULL UNIQUE,
    brand VARCHAR(100),
    model VARCHAR(100) NOT NULL,
    year INTEGER,
    color VARCHAR(50),
    capacity INTEGER NOT NULL CHECK (capacity > 0),
    odometer_km INTEGER NOT NULL DEFAULT 0 CHECK (odometer_km >= 0),
    notes TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Viagens: SCHEDULED -> IN_PROGRESS -> COMPLETED, ou SCHEDULED -> CANCELLED
CREATE TABLE IF NOT EXISTS fleet_trips (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    number BIGSERIAL UNIQUE,                         -- Número sequencial da viagem
    status VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
    purpose VARCHAR(255) NOT NULL,                   -- Motivo / título
    description TEXT,
    departure_location VARCHAR(255) NOT NULL,        -- Local de partida
    destination VARCHAR(255) NOT NULL,
    departure_at TIMESTAMP WITH TIME ZONE NOT NULL,  -- Ida prevista
    return_at TIMESTAMP WITH TIME ZONE NOT NULL,     -- Volta prevista
    driver_id UUID NOT NULL REFERENCES fleet_drivers(id) ON DELETE RESTRICT,
    vehicle_id UUID NOT NULL REFERENCES fleet_vehicles(id) ON DELETE RESTRICT,
    actual_departure_at TIMESTAMP WITH TIME ZONE,    -- Saída real (ao iniciar)
    actual_return_at TIMESTAMP WITH TIME ZONE,       -- Volta real (ao concluir)
    start_odometer_km INTEGER CHECK (start_odometer_km >= 0),
    end_odometer_km INTEGER,
    completion_notes TEXT,
    cancellation_reason TEXT,
    cancelled_at TIMESTAMP WITH TIME ZONE,
    cancelled_by UUID REFERENCES users(id) ON DELETE SET NULL,
    created_by UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_fleet_trips_period CHECK (return_at > departure_at),
    CONSTRAINT ck_fleet_trips_odometer CHECK (end_odometer_km IS NULL OR end_odometer_km >= start_odometer_km)
);
CREATE INDEX IF NOT EXISTS idx_fleet_trips_driver_period ON fleet_trips(driver_id, departure_at, return_at);
CREATE INDEX IF NOT EXISTS idx_fleet_trips_vehicle_period ON fleet_trips(vehicle_id, departure_at, return_at);
CREATE INDEX IF NOT EXISTS idx_fleet_trips_departure ON fleet_trips(departure_at);

-- Participantes: usuário do sistema (USER), atendida (CLIENT) ou pessoa externa (EXTERNAL).
-- name guarda o nome no momento do cadastro (continua lá se o usuário ou a atendida for excluído)
CREATE TABLE IF NOT EXISTS fleet_trip_participants (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    trip_id UUID NOT NULL REFERENCES fleet_trips(id) ON DELETE CASCADE,
    participant_type VARCHAR(20) NOT NULL,
    user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    client_id UUID REFERENCES clients(id) ON DELETE SET NULL,
    name VARCHAR(255) NOT NULL,
    phone VARCHAR(50),
    document VARCHAR(50),
    notes TEXT
);
CREATE INDEX IF NOT EXISTS idx_fleet_trip_participants_trip ON fleet_trip_participants(trip_id);
CREATE INDEX IF NOT EXISTS idx_fleet_trip_participants_user ON fleet_trip_participants(user_id);
CREATE INDEX IF NOT EXISTS idx_fleet_trip_participants_client ON fleet_trip_participants(client_id);

-- ==============================================================================
-- 7. MÓDULO DE EMENDAS (modules/amendments)
-- ==============================================================================
-- Acesso pela categoria nativa com table_reference = 'amendments'.
-- Cada emenda é uma linha da planilha de emendas parlamentares (LOA). As colunas de status da planilha
-- são texto livre; a situação usada nos filtros é a coluna status, controlada pelo sistema.

-- Setores responsáveis por emendas
CREATE TABLE IF NOT EXISTS amendment_sectors (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    description TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_amendment_sectors_name ON amendment_sectors (LOWER(name));

-- Emendas. status: IN_PROGRESS, WAITING (espera aberta), BLOCKED (impedimento aberto), COMPLETED, CANCELLED
CREATE TABLE IF NOT EXISTS amendments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    -- Identificação (planilha)
    exercise_year INTEGER NOT NULL,                  -- Ano da LOA (ex: 2026)
    cycle VARCHAR(50),                               -- CICLO (ex: '4º CICLO')
    parliamentarian VARCHAR(255) NOT NULL,           -- PARLAMENTAR
    category VARCHAR(100),                           -- CATEGORIA
    alepe_code VARCHAR(50),                          -- COD ALEPE
    code VARCHAR(50),                                -- COD (chave da importação, único no ano)
    action_code VARCHAR(50),                         -- AÇÃO_COD
    action_name VARCHAR(255),                        -- AÇÃO_NOME
    sub_action VARCHAR(255),                         -- SUBAÇÃO
    expense_group VARCHAR(20),                       -- GD (grupo de despesa)
    application_modality VARCHAR(20),                -- MOD (modalidade de aplicação)
    beneficiary VARCHAR(255),                        -- BENEFICIÁRIO
    municipality VARCHAR(150),                       -- MUNICÍPIO
    object TEXT,                                     -- OBJETO
    type VARCHAR(100),                               -- TIPO
    under_change VARCHAR(255),                       -- EM ALTERAÇÃO
    current_amount NUMERIC(15,2) CHECK (current_amount >= 0),  -- VALOR ATUAL
    -- Monitoramento (planilha, texto livre)
    financial_status VARCHAR(255),                   -- STATUS FINANCEIRO
    seplag_impediment TEXT,                          -- IMPEDIMENTO TÉCNICO - SEPLAG
    documental_status VARCHAR(255),                  -- STATUS DOCUMENTAL/FÍSICO
    details TEXT,                                    -- DETALHAMENTO
    notes TEXT,                                      -- OBSERVAÇÕES
    -- Responsáveis
    technical_area VARCHAR(255),                     -- ÁREA TÉCNICA (texto da planilha)
    sector_id UUID REFERENCES amendment_sectors(id) ON DELETE SET NULL,
    responsible_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    responsible_name VARCHAR(255),                   -- TÉCNICA RESPONSÁVEL (nome)
    -- Processo e Termo de Fomento
    sei_number VARCHAR(50),                          -- Nº SEI
    technical_opinion TEXT,                          -- PARECER TÉCNICO
    term_number VARCHAR(50),                         -- Nº TERMO FOMENTO (TF)
    term_end_date DATE,                              -- VIGÊNCIA (TF): prazo limite para concluir
    -- Execução financeira (SEFAZ)
    pf_number VARCHAR(50),                           -- PF Nº
    pf_date DATE,                                    -- DATA PF
    sefaz_pf VARCHAR(100),                           -- SEFAZ_PF (DT./AUT.)
    commitment_number VARCHAR(50),                   -- Nº EMPENHO
    dh_number VARCHAR(50),                           -- DH
    le_number VARCHAR(50),                           -- LE
    pd VARCHAR(100),                                 -- PD (Nº/DT)
    ob_number VARCHAR(50),                           -- Nº OB
    payment_date DATE,                               -- DATA PAGTO
    -- Sistema GTV
    call_number VARCHAR(50),                         -- Nº CHAMAMENTO (DP)
    proposal_number VARCHAR(50),                     -- Nº PROPOSTA
    situation VARCHAR(255),                          -- SITUAÇÃO (texto da planilha)
    -- Controle do sistema
    status VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS',
    last_activity_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP WITH TIME ZONE,
    completed_by UUID REFERENCES users(id) ON DELETE SET NULL,
    cancelled_at TIMESTAMP WITH TIME ZONE,
    cancelled_by UUID REFERENCES users(id) ON DELETE SET NULL,
    created_by UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_amendments_year_code ON amendments (exercise_year, LOWER(code))
    WHERE code IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_amendments_status ON amendments(status);
CREATE INDEX IF NOT EXISTS idx_amendments_term_end_date ON amendments(term_end_date);
CREATE INDEX IF NOT EXISTS idx_amendments_last_activity ON amendments(last_activity_at);
CREATE INDEX IF NOT EXISTS idx_amendments_responsible ON amendments(responsible_user_id);
CREATE INDEX IF NOT EXISTS idx_amendments_sector ON amendments(sector_id);

-- Pendências: impedimento (IMPEDIMENT) ou espera (WAITING). Abertas enquanto resolved_at for nulo
CREATE TABLE IF NOT EXISTS amendment_pendencies (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    amendment_id UUID NOT NULL REFERENCES amendments(id) ON DELETE CASCADE,
    kind VARCHAR(20) NOT NULL,
    description TEXT NOT NULL,
    waiting_on VARCHAR(255),                         -- De quem / de quê (ex: 'SEPLAG', 'Beneficiário')
    opened_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    opened_by UUID REFERENCES users(id) ON DELETE SET NULL,
    resolved_at TIMESTAMP WITH TIME ZONE,
    resolved_by UUID REFERENCES users(id) ON DELETE SET NULL,
    resolution TEXT,
    CONSTRAINT ck_amendment_pendencies_resolution CHECK (resolved_at IS NULL OR resolved_at >= opened_at)
);
CREATE INDEX IF NOT EXISTS idx_amendment_pendencies_amendment ON amendment_pendencies(amendment_id);
CREATE INDEX IF NOT EXISTS idx_amendment_pendencies_open ON amendment_pendencies(amendment_id)
    WHERE resolved_at IS NULL;

-- Linha do tempo da emenda (imutável)
CREATE TABLE IF NOT EXISTS amendment_activities (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    amendment_id UUID NOT NULL REFERENCES amendments(id) ON DELETE CASCADE,
    kind VARCHAR(30) NOT NULL,
    description TEXT,
    -- Ex: {"financial_status": {"antigo": "Aguardando PF", "novo": "PF autorizada"}}
    changes JSONB,
    pendency_id UUID REFERENCES amendment_pendencies(id) ON DELETE SET NULL,
    user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_amendment_activities_amendment ON amendment_activities(amendment_id, occurred_at);
CREATE INDEX IF NOT EXISTS idx_amendment_activities_user ON amendment_activities(user_id);
