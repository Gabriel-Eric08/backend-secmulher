-- ==============================================================================
-- Dados de TESTE do módulo de emendas
-- ==============================================================================
-- Cria (em uma transação):
--   - a categoria nativa "Emendas" (table_reference = 'amendments'), se ainda não existir;
--   - 4 setores '[TESTE] ...' (um desativado);
--   - 4 técnicas com login (e-mails emendas.*@secmulher.demo, senha Demo@2026), cada uma vinculada a
--     um setor e com permissão de leitura, criação e edição em "Emendas" (sem exclusão);
--   - 10 emendas (códigos TESTE-001 a TESTE-010, ciclo 'TESTE') cobrindo todas as situações,
--     com pendências e linha do tempo.
-- Depende dos usuários do profile "seed" (Administrador e coordenação) para autoria; sem eles,
-- a autoria fica vazia.
--
-- Rodar:   psql -U <usuario> -d omnibase -f scripts/teste/emendas-inserir.sql
-- Remover: psql -U <usuario> -d omnibase -f scripts/teste/emendas-remover.sql
-- Recusa rodar se os dados de teste já existirem.

DO $$
DECLARE
  pwd TEXT := crypt('Demo@2026', gen_salt('bf', 10));
  u_admin UUID := (SELECT id FROM users WHERE name = 'Administrador' ORDER BY created_at LIMIT 1);
  u_coord UUID := (SELECT id FROM users WHERE email = 'coordenacao@secmulher.demo');
  t_aline UUID; t_bianca UUID; t_cecilia UUID; t_denise UUID;
  s_conv UUID; s_fin UUID; s_jur UUID; s_old UUID;
  cat UUID;
  a UUID; p UUID;
  now_ TIMESTAMPTZ := date_trunc('minute', now());
BEGIN
  IF EXISTS (SELECT 1 FROM amendments WHERE code LIKE 'TESTE-%' AND cycle = 'TESTE')
     OR EXISTS (SELECT 1 FROM users WHERE email LIKE 'emendas.%@secmulher.demo') THEN
    RAISE EXCEPTION 'Dados de teste de emendas já existem; rode scripts/teste/emendas-remover.sql antes';
  END IF;
  u_admin := COALESCE(u_admin, u_coord);
  u_coord := COALESCE(u_coord, u_admin);

  -- Categoria de acesso do módulo
  SELECT id INTO cat FROM categories WHERE table_reference = 'amendments' ORDER BY created_at LIMIT 1;
  IF cat IS NULL THEN
    INSERT INTO categories (name, entity_type, table_reference) VALUES ('Emendas', 'native', 'amendments') RETURNING id INTO cat;
  END IF;

  -- Setores
  INSERT INTO amendment_sectors (name, description) VALUES ('[TESTE] Convênios', 'Análise e formalização dos Termos de Fomento') RETURNING id INTO s_conv;
  INSERT INTO amendment_sectors (name, description) VALUES ('[TESTE] Financeiro', 'Empenho, liquidação e pagamento') RETURNING id INTO s_fin;
  INSERT INTO amendment_sectors (name, description) VALUES ('[TESTE] Jurídico', 'Pareceres jurídicos') RETURNING id INTO s_jur;
  INSERT INTO amendment_sectors (name, description, is_active) VALUES ('[TESTE] Setor desativado', 'Não recebe novas emendas', FALSE) RETURNING id INTO s_old;

  -- Técnicas (login com a senha Demo@2026)
  INSERT INTO users (name, email, password_hash) VALUES ('Aline Moura (Técnica de Emendas - Convênios)', 'emendas.convenios1@secmulher.demo', pwd) RETURNING id INTO t_aline;
  INSERT INTO users (name, email, password_hash) VALUES ('Bianca Torres (Técnica de Emendas - Convênios)', 'emendas.convenios2@secmulher.demo', pwd) RETURNING id INTO t_bianca;
  INSERT INTO users (name, email, password_hash) VALUES ('Cecília Nunes (Técnica de Emendas - Financeiro)', 'emendas.financeiro@secmulher.demo', pwd) RETURNING id INTO t_cecilia;
  INSERT INTO users (name, email, password_hash) VALUES ('Denise Prado (Técnica de Emendas - Jurídico)', 'emendas.juridico@secmulher.demo', pwd) RETURNING id INTO t_denise;

  INSERT INTO permissions (user_id, category_id, can_read, can_create, can_update, can_delete) VALUES
    (t_aline, cat, TRUE, TRUE, TRUE, FALSE),
    (t_bianca, cat, TRUE, TRUE, TRUE, FALSE),
    (t_cecilia, cat, TRUE, TRUE, TRUE, FALSE),
    (t_denise, cat, TRUE, TRUE, TRUE, FALSE);

  INSERT INTO amendment_sector_members (sector_id, user_id, created_by) VALUES
    (s_conv, t_aline, u_coord),
    (s_conv, t_bianca, u_coord),
    (s_fin, t_cecilia, u_coord),
    (s_jur, t_denise, u_coord);

  -- 1) Convênios, em andamento, recente, prazo OK
  INSERT INTO amendments (exercise_year, cycle, parliamentarian, category, alepe_code, code, action_code, action_name, sub_action,
    expense_group, application_modality, beneficiary, municipality, object, type, current_amount, financial_status, documental_status,
    technical_area, sector_id, responsible_user_id, responsible_name, sei_number, term_number, term_end_date, call_number, proposal_number,
    situation, status, last_activity_at, created_by, created_at)
  VALUES (2026, 'TESTE', 'Dep. Ana Teste', 'Individual', 'AL-0001', 'TESTE-001', '4321', 'Enfrentamento à violência contra a mulher', 'Apoio a projetos',
    '3', '50', 'Instituto Mulheres Fortes', 'Recife', 'Oficinas de capacitação profissional para mulheres em situação de vulnerabilidade', 'Custeio',
    150000.00, 'Aguardando PF', 'Documentação completa', 'Convênios', s_conv, t_aline, 'Aline Moura', '0001.2026.000101/2026-11', 'TF 010/2026',
    DATE '2027-06-30', 'DP 01/2026', 'PROP-1001', 'Em análise', 'IN_PROGRESS', now_ - interval '2 days', u_admin, now_ - interval '40 days')
  RETURNING id INTO a;
  INSERT INTO amendment_activities (amendment_id, kind, description, changes, user_id, occurred_at) VALUES
    (a, 'CREATED', 'Emenda cadastrada', NULL, u_admin, now_ - interval '40 days'),
    (a, 'FIELD_UPDATE', 'Documentação conferida', '{"documentalStatus": {"antigo": "Pendente", "novo": "Documentação completa"}}', t_aline, now_ - interval '10 days'),
    (a, 'NOTE', 'Beneficiário confirmou agenda das oficinas por telefone.', NULL, t_aline, now_ - interval '2 days');

  -- 2) Convênios, aguardando (espera aberta), prazo perto (NEAR)
  INSERT INTO amendments (exercise_year, cycle, parliamentarian, category, code, action_name, beneficiary, municipality, object, type,
    current_amount, financial_status, documental_status, sector_id, responsible_user_id, responsible_name, sei_number, term_number, term_end_date,
    status, last_activity_at, created_by, created_at)
  VALUES (2026, 'TESTE', 'Dep. Bruno Exemplo', 'Bancada', 'TESTE-002', 'Autonomia econômica das mulheres', 'Associação Rede Solidária', 'Olinda',
    'Aquisição de equipamentos para cozinha comunitária', 'Investimento', 85000.00, 'Empenhado', 'Aguardando certidões',
    s_conv, t_bianca, 'Bianca Torres', '0001.2026.000102/2026-11', 'TF 011/2026', (now_ + interval '15 days')::date,
    'WAITING', now_ - interval '5 days', u_admin, now_ - interval '60 days')
  RETURNING id INTO a;
  INSERT INTO amendment_pendencies (amendment_id, kind, description, waiting_on, opened_at, opened_by)
    VALUES (a, 'WAITING', 'Certidão negativa de débitos vencida', 'Beneficiário', now_ - interval '5 days', t_bianca) RETURNING id INTO p;
  INSERT INTO amendment_activities (amendment_id, kind, description, changes, pendency_id, user_id, occurred_at) VALUES
    (a, 'CREATED', 'Emenda cadastrada', NULL, NULL, u_admin, now_ - interval '60 days'),
    (a, 'PENDENCY_OPENED', 'Espera: Certidão negativa de débitos vencida (Beneficiário)', '{"status": {"antigo": "IN_PROGRESS", "novo": "WAITING"}}', p, t_bianca, now_ - interval '5 days');

  -- 3) Jurídico, com impedimento (BLOCKED) + espera aberta, parada há 45 dias
  INSERT INTO amendments (exercise_year, cycle, parliamentarian, category, code, action_name, beneficiary, municipality, object, type,
    current_amount, financial_status, seplag_impediment, sector_id, responsible_user_id, responsible_name, sei_number, term_end_date,
    status, last_activity_at, created_by, created_at)
  VALUES (2026, 'TESTE', 'Dep. Carla Modelo', 'Individual', 'TESTE-003', 'Casa da Mulher', 'Prefeitura de Caruaru', 'Caruaru',
    'Reforma do centro de referência da mulher', 'Investimento', 320000.00, 'Bloqueado', 'Plano de trabalho incompatível com a ação orçamentária',
    s_jur, t_denise, 'Denise Prado', '0001.2026.000103/2026-11', DATE '2027-03-31',
    'BLOCKED', now_ - interval '45 days', u_admin, now_ - interval '90 days')
  RETURNING id INTO a;
  INSERT INTO amendment_pendencies (amendment_id, kind, description, waiting_on, opened_at, opened_by)
    VALUES (a, 'IMPEDIMENT', 'Impedimento técnico da SEPLAG: plano de trabalho incompatível', 'SEPLAG', now_ - interval '50 days', t_denise) RETURNING id INTO p;
  INSERT INTO amendment_activities (amendment_id, kind, description, changes, pendency_id, user_id, occurred_at) VALUES
    (a, 'CREATED', 'Emenda cadastrada', NULL, NULL, u_admin, now_ - interval '90 days'),
    (a, 'PENDENCY_OPENED', 'Impedimento: Impedimento técnico da SEPLAG: plano de trabalho incompatível (SEPLAG)', '{"status": {"antigo": "IN_PROGRESS", "novo": "BLOCKED"}}', p, t_denise, now_ - interval '50 days');
  INSERT INTO amendment_pendencies (amendment_id, kind, description, waiting_on, opened_at, opened_by)
    VALUES (a, 'WAITING', 'Novo plano de trabalho', 'Prefeitura de Caruaru', now_ - interval '45 days', t_denise) RETURNING id INTO p;
  INSERT INTO amendment_activities (amendment_id, kind, description, pendency_id, user_id, occurred_at) VALUES
    (a, 'PENDENCY_OPENED', 'Espera: Novo plano de trabalho (Prefeitura de Caruaru)', p, t_denise, now_ - interval '45 days');

  -- 4) Financeiro, concluída (pendência resolvida antes)
  INSERT INTO amendments (exercise_year, cycle, parliamentarian, category, code, action_name, beneficiary, municipality, object, type,
    current_amount, financial_status, documental_status, sector_id, responsible_user_id, responsible_name, sei_number, term_number, term_end_date,
    pf_number, pf_date, commitment_number, ob_number, payment_date,
    status, last_activity_at, completed_at, completed_by, created_by, created_at)
  VALUES (2026, 'TESTE', 'Dep. Ana Teste', 'Individual', 'TESTE-004', 'Enfrentamento à violência contra a mulher', 'ONG Viver Bem', 'Jaboatão dos Guararapes',
    'Campanha de conscientização em escolas', 'Custeio', 45000.00, 'Pago', 'Prestação de contas entregue',
    s_fin, t_cecilia, 'Cecília Nunes', '0001.2026.000104/2026-11', 'TF 004/2026', DATE '2026-12-31',
    'PF 2026/0456', DATE '2026-08-10', '2026NE000789', '2026OB001234', DATE '2026-09-02',
    'COMPLETED', now_ - interval '20 days', now_ - interval '20 days', t_cecilia, u_admin, now_ - interval '150 days')
  RETURNING id INTO a;
  INSERT INTO amendment_pendencies (amendment_id, kind, description, waiting_on, opened_at, opened_by, resolved_at, resolved_by, resolution)
    VALUES (a, 'WAITING', 'Assinatura do Termo de Fomento', 'Beneficiário', now_ - interval '120 days', t_cecilia, now_ - interval '110 days', t_cecilia, 'Termo assinado e publicado') RETURNING id INTO p;
  INSERT INTO amendment_activities (amendment_id, kind, description, changes, pendency_id, user_id, occurred_at) VALUES
    (a, 'CREATED', 'Emenda cadastrada', NULL, NULL, u_admin, now_ - interval '150 days'),
    (a, 'PENDENCY_OPENED', 'Espera: Assinatura do Termo de Fomento (Beneficiário)', '{"status": {"antigo": "IN_PROGRESS", "novo": "WAITING"}}', p, t_cecilia, now_ - interval '120 days'),
    (a, 'PENDENCY_RESOLVED', 'Pendência resolvida (espera): Termo assinado e publicado', '{"status": {"antigo": "WAITING", "novo": "IN_PROGRESS"}}', p, t_cecilia, now_ - interval '110 days'),
    (a, 'FIELD_UPDATE', 'Pagamento registrado', '{"financialStatus": {"antigo": "Empenhado", "novo": "Pago"}}', NULL, t_cecilia, now_ - interval '80 days'),
    (a, 'COMPLETED', 'Objeto executado e prestação de contas entregue', '{"status": {"antigo": "IN_PROGRESS", "novo": "COMPLETED"}}', NULL, t_cecilia, now_ - interval '20 days');

  -- 5) Convênios, cancelada pela coordenação (pendência encerrada no cancelamento)
  INSERT INTO amendments (exercise_year, cycle, parliamentarian, category, code, action_name, beneficiary, municipality, object, type,
    current_amount, sector_id, responsible_user_id, responsible_name, term_end_date,
    status, last_activity_at, cancelled_at, cancelled_by, created_by, created_at)
  VALUES (2026, 'TESTE', 'Dep. Bruno Exemplo', 'Individual', 'TESTE-005', 'Autonomia econômica das mulheres', 'Cooperativa Mãos Unidas', 'Petrolina',
    'Curso de corte e costura', 'Custeio', 30000.00, s_conv, t_aline, 'Aline Moura', DATE '2027-01-31',
    'CANCELLED', now_ - interval '12 days', now_ - interval '12 days', u_coord, u_admin, now_ - interval '70 days')
  RETURNING id INTO a;
  INSERT INTO amendment_pendencies (amendment_id, kind, description, waiting_on, opened_at, opened_by, resolved_at, resolved_by, resolution)
    VALUES (a, 'IMPEDIMENT', 'Beneficiário sem CNPJ ativo', 'Beneficiário', now_ - interval '30 days', t_aline, now_ - interval '12 days', u_coord, 'Resolvida no cancelamento da emenda: Parlamentar remanejou o recurso') RETURNING id INTO p;
  INSERT INTO amendment_activities (amendment_id, kind, description, changes, pendency_id, user_id, occurred_at) VALUES
    (a, 'CREATED', 'Emenda cadastrada', NULL, NULL, u_admin, now_ - interval '70 days'),
    (a, 'PENDENCY_OPENED', 'Impedimento: Beneficiário sem CNPJ ativo (Beneficiário)', '{"status": {"antigo": "IN_PROGRESS", "novo": "BLOCKED"}}', p, t_aline, now_ - interval '30 days'),
    (a, 'CANCELLED', 'Parlamentar remanejou o recurso (1 pendência(s) aberta(s) encerrada(s) junto)', '{"status": {"antigo": "BLOCKED", "novo": "CANCELLED"}}', NULL, u_coord, now_ - interval '12 days');

  -- 6) Financeiro, vigência vencida e ainda aberta (EXPIRED), parada há 35 dias
  INSERT INTO amendments (exercise_year, cycle, parliamentarian, category, code, action_name, beneficiary, municipality, object, type,
    current_amount, financial_status, sector_id, responsible_user_id, responsible_name, term_number, term_end_date,
    status, last_activity_at, created_by, created_at)
  VALUES (2025, 'TESTE', 'Dep. Carla Modelo', 'Bancada', 'TESTE-006', 'Casa da Mulher', 'Prefeitura de Garanhuns', 'Garanhuns',
    'Aquisição de veículo para a patrulha Maria da Penha', 'Investimento', 210000.00, 'Empenhado',
    s_fin, t_cecilia, 'Cecília Nunes', 'TF 031/2025', (now_ - interval '10 days')::date,
    'IN_PROGRESS', now_ - interval '35 days', u_admin, now_ - interval '300 days')
  RETURNING id INTO a;
  INSERT INTO amendment_activities (amendment_id, kind, description, user_id, occurred_at) VALUES
    (a, 'CREATED', 'Emenda cadastrada', u_admin, now_ - interval '300 days'),
    (a, 'NOTE', 'Licitação do veículo deserta; prefeitura vai republicar.', t_cecilia, now_ - interval '35 days');

  -- 7) Sem setor, sem responsável e sem vigência, recém-cadastrada (só a coordenação vê)
  INSERT INTO amendments (exercise_year, cycle, parliamentarian, code, beneficiary, municipality, object, current_amount,
    technical_area, status, last_activity_at, created_by, created_at)
  VALUES (2026, 'TESTE', 'Dep. Daniel Fictício', 'TESTE-007', 'Instituto Sementes', 'Serra Talhada',
    'Atendimento psicossocial itinerante', 60000.00, 'A definir', 'IN_PROGRESS', now_ - interval '1 day', u_admin, now_ - interval '1 day')
  RETURNING id INTO a;
  INSERT INTO amendment_activities (amendment_id, kind, description, user_id, occurred_at) VALUES
    (a, 'CREATED', 'Emenda cadastrada', u_admin, now_ - interval '1 day');

  -- 8) Financeiro, parada há 60 dias, prazo perto (NEAR), responsável = Administrador
  INSERT INTO amendments (exercise_year, cycle, parliamentarian, category, code, action_name, beneficiary, municipality, object, type,
    current_amount, financial_status, sector_id, responsible_user_id, responsible_name, sei_number, term_number, term_end_date,
    status, last_activity_at, created_by, created_at)
  VALUES (2026, 'TESTE', 'Dep. Daniel Fictício', 'Individual', 'TESTE-008', 'Enfrentamento à violência contra a mulher', 'Centro Comunitário Esperança', 'Recife',
    'Grupo de apoio para mulheres vítimas de violência', 'Custeio', 75500.50, 'Aguardando empenho',
    s_fin, u_admin, 'Administrador', '0001.2026.000108/2026-11', 'TF 018/2026', (now_ + interval '25 days')::date,
    'IN_PROGRESS', now_ - interval '60 days', u_admin, now_ - interval '100 days')
  RETURNING id INTO a;
  INSERT INTO amendment_activities (amendment_id, kind, description, user_id, occurred_at) VALUES
    (a, 'CREATED', 'Emenda cadastrada', u_admin, now_ - interval '100 days'),
    (a, 'NOTE', 'Solicitado empenho ao financeiro.', u_admin, now_ - interval '60 days');

  -- 9) Convênios, reaberta depois de concluída
  INSERT INTO amendments (exercise_year, cycle, parliamentarian, category, code, action_name, beneficiary, municipality, object, type,
    current_amount, sector_id, responsible_user_id, responsible_name, term_end_date,
    status, last_activity_at, created_by, created_at)
  VALUES (2026, 'TESTE', 'Dep. Ana Teste', 'Individual', 'TESTE-009', 'Autonomia econômica das mulheres', 'Associação Flor do Sertão', 'Arcoverde',
    'Feira de economia solidária', 'Custeio', 25000.00, s_conv, t_bianca, 'Bianca Torres', DATE '2027-02-28',
    'IN_PROGRESS', now_ - interval '3 days', u_admin, now_ - interval '80 days')
  RETURNING id INTO a;
  INSERT INTO amendment_activities (amendment_id, kind, description, changes, user_id, occurred_at) VALUES
    (a, 'CREATED', 'Emenda cadastrada', NULL, u_admin, now_ - interval '80 days'),
    (a, 'COMPLETED', 'Feira realizada', '{"status": {"antigo": "IN_PROGRESS", "novo": "COMPLETED"}}', t_bianca, now_ - interval '15 days'),
    (a, 'REOPENED', 'Faltou anexar a prestação de contas', '{"status": {"antigo": "COMPLETED", "novo": "IN_PROGRESS"}}', u_coord, now_ - interval '3 days');

  -- 10) Convênios, outro ano, aguardando SEPLAG, valor alto
  INSERT INTO amendments (exercise_year, cycle, parliamentarian, category, code, action_name, beneficiary, municipality, object, type,
    current_amount, financial_status, sector_id, responsible_user_id, responsible_name, term_end_date,
    status, last_activity_at, created_by, created_at)
  VALUES (2025, 'TESTE', 'Dep. Bruno Exemplo', 'Bancada', 'TESTE-010', 'Casa da Mulher', 'Prefeitura de Petrolina', 'Petrolina',
    'Construção de abrigo para mulheres em situação de risco', 'Investimento', 980000.00, 'Aguardando liberação',
    s_conv, t_aline, 'Aline Moura', DATE '2027-12-31',
    'WAITING', now_ - interval '8 days', u_admin, now_ - interval '200 days')
  RETURNING id INTO a;
  INSERT INTO amendment_pendencies (amendment_id, kind, description, waiting_on, opened_at, opened_by)
    VALUES (a, 'WAITING', 'Liberação de cota financeira', 'SEPLAG', now_ - interval '8 days', t_aline) RETURNING id INTO p;
  INSERT INTO amendment_activities (amendment_id, kind, description, changes, pendency_id, user_id, occurred_at) VALUES
    (a, 'CREATED', 'Emenda cadastrada', NULL, NULL, u_admin, now_ - interval '200 days'),
    (a, 'PENDENCY_OPENED', 'Espera: Liberação de cota financeira (SEPLAG)', '{"status": {"antigo": "IN_PROGRESS", "novo": "WAITING"}}', p, t_aline, now_ - interval '8 days');
END $$;
