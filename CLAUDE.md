# Regras do projeto OmniBase

## Dicionário da aplicação (obrigatório)

`backend/DICIONARIO.md` descreve tudo o que existe na aplicação: tabelas e colunas, enums, rotas e termos do negócio.

**Toda entrega que mudar qualquer um destes itens atualiza o dicionário na mesma entrega:**

- tabela ou coluna nova, alterada ou removida (`backend/src/main/resources/db/init.sql`);
- valor de enum novo ou alterado;
- rota nova, alterada ou removida, inclusive parâmetros e campos de request/response;
- regra de negócio relevante (ex.: validações de saldo, permissões).

Uma tarefa só está concluída quando o dicionário está atualizado.

## Estrutura

- O backend é organizado em módulos: `backend/src/main/java/com/secmulher/modules/<modulo>/` com `controllers/`, `domain/` e `infra/`.
- `core` tem o que é geral; módulos de setor (ex.: `finance`) podem usar o `core`, mas o `core` nunca importa de um setor e um setor não importa de outro.
- Rotas de um setor usam prefixo próprio (ex.: `/finance/...`).

## Banco de dados

- O schema vive em `backend/src/main/resources/db/init.sql` e precisa continuar **idempotente** (`CREATE ... IF NOT EXISTS`, `ADD COLUMN IF NOT EXISTS`, migrações que podem rodar de novo).
- O Hibernate roda com `ddl-auto=validate`: toda entidade nova ou alterada precisa da mudança correspondente no `init.sql`.

## Documentação

- O `backend/README.md` explica como usar; o `backend/DICIONARIO.md` descreve o que cada coisa é. Os dois são atualizados juntos.
