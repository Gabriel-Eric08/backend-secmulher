-- ==============================================================================
-- Remove os dados de TESTE do módulo de emendas (criados por emendas-inserir.sql)
-- ==============================================================================
-- Pendências e linha do tempo caem em cascata com as emendas; vínculos com setores caem com os
-- setores; permissões caem com as técnicas. A categoria "Emendas" é mantida.
--
-- Rodar: psql -U <usuario> -d omnibase -f scripts/teste/emendas-remover.sql

BEGIN;
DELETE FROM amendments WHERE code LIKE 'TESTE-%' AND cycle = 'TESTE';
DELETE FROM amendment_sectors WHERE name LIKE '[TESTE] %';
DELETE FROM users WHERE email LIKE 'emendas.%@secmulher.demo';
COMMIT;
