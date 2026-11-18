# OmniBase: Backend

API REST para cadastro e acompanhamento de pessoas atendidas e de qualquer outro tipo de registro que a instituição precise manter, com **controle de acesso granular por usuário, categoria e coluna**, **linha do tempo auditável** e **documentos com nível de sigilo**.

Foi pensada para serviços de atendimento como uma Secretaria da Mulher, onde diferentes profissionais (recepção, assistência social, psicologia, coordenação, instrutoras de cursos) trabalham sobre os mesmos cadastros, mas cada uma só pode ver e alterar o que é necessário para a sua função.

---

## Sumário

- [Intuito e conceitos](#intuito-e-conceitos)
- [Tecnologias](#tecnologias)
- [Como rodar](#como-rodar)
- [Configuração (`.env`)](#configuração-env)
- [Estrutura do projeto](#estrutura-do-projeto)
- [Modelo de dados](#modelo-de-dados)
- [Autenticação](#autenticação)
- [Controle de acesso](#controle-de-acesso)
- [Rotas](#rotas)
  - [Auth](#auth)
  - [Usuários](#usuários)
  - [Categorias](#categorias)
  - [Campos personalizados](#campos-personalizados-de-categorias-dinâmicas)
  - [Tipos de registro da linha do tempo](#tipos-de-registro-da-linha-do-tempo)
  - [Permissões](#permissões)
  - [Clientes](#clientes)
  - [Entidades dinâmicas](#entidades-dinâmicas)
  - [Busca com filtros](#busca-com-filtros)
  - [Linha do tempo, documentos e visão 360°](#linha-do-tempo-documentos-e-visão-360)
  - [Arquivos](#arquivos)
- [Erros](#erros)
- [Dados de demonstração](#dados-de-demonstração)
- [Fluxo típico de uso](#fluxo-típico-de-uso)

---

## Intuito e conceitos

| Conceito | O que é |
|---|---|
| **Usuário** | Funcionária ou funcionário que acessa o sistema. Pode ser **administrador global**, com acesso total, ou usuário comum, que depende das permissões. |
| **Categoria** | Um tipo de cadastro. Pode ser **nativa** (`native`), que aponta para uma tabela própria do sistema, hoje `clients`, ou **dinâmica** (`dynamic`), cujos registros ficam em `dynamic_entities` e têm campos definidos pelo administrador. |
| **Cliente** | Pessoa atendida. Tabela nativa com `nome`, `data_nascimento`, `telefone` e `sob_risco`. |
| **Entidade dinâmica** | Registro de uma categoria dinâmica (ex.: abrigos, cursos, municípios parceiros). Os valores ficam em JSONB e são validados contra os **campos personalizados** da categoria. |
| **Permissão** | Liga um usuário a uma categoria: o que ele pode fazer (ler, criar, editar, excluir), **quais colunas** pode ler e escrever e o **nível máximo de sigilo** de documentos que pode acessar. |
| **Linha do tempo** | Histórico imutável de cada registro (`activities_ledger`). Guarda ações automáticas de auditoria (criação, edição com valor antigo e novo, documentos) e atendimentos registrados manualmente. |
| **Tipo de registro** | Os tipos de registro manual da linha do tempo, configurados pelo administrador em cada categoria (ex.: em "Mulheres atendidas", *Triagem, Atendimento psicológico, Visita domiciliar*; em "Cursos", *Matrícula, Presença, Certificado*). A permissão pode limitar quais tipos cada perfil registra. |
| **Documento** | Arquivo anexado a um registro, com nível de sigilo `INTERNAL`, `RESTRICTED` ou `CONFIDENTIAL`. |

Princípios do projeto:

- **A autorização fica no backend.** Toda regra de acesso é aplicada nos services, via `AccessControlService`, e vale igual para qualquer front ou integração.
- **Nada vaza.** Colunas sem permissão de leitura voltam `null` ou são removidas e listadas em `hiddenFields`. O histórico também oculta valores de colunas e nomes de documentos que o usuário não pode ver. Filtrar ou ordenar por campo oculto é proibido.
- **Tudo é auditado.** Criação, edição, exclusão, upload, mudança de sigilo e download de documentos restritos ou sigilosos ficam na linha do tempo, pensando na LGPD.

---

## Tecnologias

- **Java 27** e **Spring Boot 4.1** (Web MVC, Data JPA, Validation, Security, OAuth2 Resource Server)
- **PostgreSQL**: JSONB para os campos dinâmicos, permissões por coluna e histórico de alterações
- **JWT HS256** (Nimbus) para autenticação stateless e **BCrypt** para senhas
- **Lombok**
- **Maven**: use o wrapper `./mvnw`, não é preciso instalar o Maven

---

## Como rodar

### Pré-requisitos

- JDK 27
- PostgreSQL acessível (padrão: `localhost:5432`)

### 1. Criar o banco e o schema

O schema **não** é criado pelo Hibernate (`ddl-auto=validate`). Ele vem do script `src/main/resources/db/init.sql`:

```bash
createdb -U postgres omnibase
psql -U postgres -d omnibase -f src/main/resources/db/init.sql
```

O script é idempotente (`CREATE ... IF NOT EXISTS`) e habilita a extensão `pgcrypto`, usada por `gen_random_uuid()`.

**Atualizando um banco existente:** rode o mesmo `init.sql` de novo. Ele acrescenta as tabelas e colunas novas (`ADD COLUMN IF NOT EXISTS`) e migra os dados, então pode ser executado quantas vezes for preciso. Sem isso, a aplicação não sobe, porque o Hibernate confere se o schema bate com as entidades. A migração dos tipos de registro:
- cria os quatro tipos padrão (`triagem`, `atendimento`, `acompanhamento`, `anotacao`) em cada categoria que ainda não tem nenhum tipo;
- converte as atividades manuais antigas (`TRIAGE`, `SERVICE`, `FOLLOW_UP`, `NOTE`) para `MANUAL`, apontando para o tipo correspondente da categoria do registro. As que não têm categoria identificável, como as de um registro já excluído, continuam como estão e seguem legíveis.

### 2. Criar o `.env`

Na raiz do `backend/`. Veja a [tabela completa](#configuração-env).

```properties
DB_URL=jdbc:postgresql://localhost:5432/omnibase
DB_USERNAME=postgres
DB_PASSWORD=sua_senha
JWT_SECRET=uma_chave_com_pelo_menos_32_caracteres
ADMIN_EMAIL=admin@omnibase.local
ADMIN_PASSWORD=Admin@2026
```

O `.env` é carregado automaticamente (`spring.config.import=optional:file:.env[.properties]`) e está no `.gitignore`. **Nunca versione esse arquivo.** Variáveis de ambiente do sistema têm prioridade sobre ele.

### 3. Subir a aplicação

```bash
./mvnw spring-boot:run                                      # API em http://localhost:8080
./mvnw spring-boot:run -Dspring-boot.run.profiles=seed      # com dados de demonstração
./mvnw test                                                 # testes
./mvnw clean package && java -jar target/demo-*.jar         # build para produção
```

No primeiro início, se o banco não tiver nenhum usuário, o administrador definido em `ADMIN_EMAIL`/`ADMIN_PASSWORD` é criado automaticamente (`AdminBootstrap`).

---

## Configuração (`.env`)

| Variável | Padrão | Descrição |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/omnibase` | URL JDBC do PostgreSQL |
| `DB_USERNAME` | `postgres` | Usuário do banco |
| `DB_PASSWORD` | *(vazio)* | Senha do banco |
| `JWT_SECRET` | *(vazio)* | Segredo do JWT, com **no mínimo 32 bytes**. Sem ele, uma chave aleatória é gerada a cada início e todos os tokens expiram ao reiniciar (só serve para desenvolvimento). |
| `JWT_EXPIRATION` | `8h` | Validade do token (`30m`, `8h`, `1d`...) |
| `ADMIN_NAME` | `Administrador` | Nome do primeiro administrador |
| `ADMIN_EMAIL` | *(vazio)* | E-mail do primeiro administrador; só é usado com o banco sem usuários |
| `ADMIN_PASSWORD` | *(vazio)* | Senha do primeiro administrador |
| `STORAGE_PATH` | `./storage` | Pasta onde os documentos enviados são gravados (no `.gitignore`) |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173,http://localhost:3000` | Origens do front autorizadas, separadas por vírgula |
| `SEED_PASSWORD` | `Demo@2026` | Senha dos usuários de demonstração (profile `seed`) |

Limite de upload: **20 MB** por arquivo.

---

## Estrutura do projeto

```
src/main/java/com/secmulher/
├── Application.java
└── modules/
    ├── core/                       # Tudo que é geral e compartilhado entre os setores
    │   ├── controllers/            # Rotas REST + GlobalExceptionHandler (formato dos erros)
    │   ├── domain/
    │   │   ├── access/             # AccessControlService e CategoryAccess: regras de autorização
    │   │   ├── dtos/               # Records de request/response
    │   │   ├── enums/              # RecordType, FilterOperator
    │   │   ├── exceptions/         # NotFound, Forbidden, Conflict, Business, FieldValidation...
    │   │   └── services/           # Regras de negócio (um service por recurso)
    │   └── infra/
    │       ├── config/             # AdminBootstrap (primeiro administrador)
    │       ├── entities/           # Entidades JPA e enums (FieldType, ActivityType, ConfidentialityLevel)
    │       ├── repositories/       # Spring Data + DynamicEntitySearchRepository (filtros sobre JSONB)
    │       ├── security/           # SecurityConfig, TokenService, CurrentUserProvider
    │       ├── seed/               # DemoDataSeeder (profile "seed")
    │       └── storage/            # FileStorage e LocalFileStorage
    └── finance/                    # Setor financeiro (esqueleto, ainda sem rotas)
        ├── controllers/
        ├── domain/
        └── infra/
src/main/resources/
├── application.properties
└── db/init.sql                     # Schema do banco (fora de static/, não é publicado via HTTP)
```

### Como criar um módulo de setor

1. Crie `modules/<setor>/` com as mesmas pastas do core: `controllers/`, `domain/` (`dtos/`, `enums/`, `services/`) e `infra/` (`entities/`, `repositories/`).
2. O setor pode usar tudo do core (exceções, `AccessControlService`, `CurrentUserProvider`, `FileStorage`, entidades). O core **não** importa nada de um setor, e um setor não importa de outro.
3. Use um prefixo próprio nas rotas (ex.: `/finance/...`) para não colidir com as rotas do core.
4. Tabelas novas entram no `db/init.sql`, depois das tabelas do core (o Hibernate roda com `ddl-auto=validate`).

Não é preciso registrar nada: como `Application` fica em `com.secmulher`, o Spring encontra sozinho os componentes, entidades e repositórios de qualquer módulo.

---

## Modelo de dados

| Tabela | Conteúdo |
|---|---|
| `users` | Usuários (`is_global_admin`, `is_active`, senha em BCrypt) |
| `categories` | Categorias: `entity_type` (`native`/`dynamic`) e `table_reference` (ex.: `clients`) |
| `permissions` | Matriz usuário × categoria: `can_read/create/update/delete`, `allowed_columns` (JSONB), `max_confidentiality` e `allowed_activity_types` (JSONB, keys dos tipos de registro liberados; nulo = todos) |
| `clients` | Pessoas atendidas (tabela nativa) |
| `category_fields` | Definição dos campos de cada categoria dinâmica (o "formulário") |
| `dynamic_entities` | Registros das categorias dinâmicas; valores em `custom_fields` (JSONB, com índice GIN) |
| `activity_types` | Tipos de registro manual de cada categoria: `key` (única por categoria, imutável), `label`, `description`, `color`, `position`, `is_active` |
| `activities_ledger` | Linha do tempo e auditoria de cada registro (polimórfica: `entity_type` + `entity_id`). Registros manuais têm `action_type = 'MANUAL'`, `activity_type_id` e `activity_type_label` (o nome do tipo **no momento do registro**) |
| `files` | Metadados dos documentos (o arquivo físico fica em `STORAGE_PATH`, com nome UUID) |

> Uma categoria não pode ser excluída (**409**) enquanto tiver registros em `dynamic_entities` ou se algum de seus tipos de registro já tiver sido usado na linha do tempo. Nesse caso, desative-a com `isActive: false`.

---

## Autenticação

1. `POST /auth/login` com e-mail e senha devolve um **JWT**.
2. Todas as outras rotas exigem o cabeçalho:
   ```
   Authorization: Bearer <token>
   ```
3. Sem token, ou com token inválido ou expirado, a resposta é **401**.

Detalhes:

- **Login errado:** e-mail inexistente, senha errada e usuário inativo devolvem a mesma resposta (401), com tempo de resposta equivalente, para não revelar quais e-mails existem.
- **Sem estado:** não há sessão nem CSRF. O token carrega o id do usuário (`sub`) e o nome.
- **CORS:** expõe os cabeçalhos `Location`, usado nas respostas 201, e `Content-Disposition`, usado nos downloads.

---

## Controle de acesso

### Administrador global vs. usuário comum

- **Administrador global** (`isGlobalAdmin: true`): acesso total a tudo. É o único que gerencia usuários, categorias, campos e permissões.
- **Usuário comum**: só acessa as categorias em que tem permissão, dentro dos limites dela.

### Permissão por categoria

```json
{
  "userId": "…",
  "categoryId": "…",
  "canRead": true,
  "canCreate": false,
  "canUpdate": true,
  "canDelete": false,
  "allowedColumns": { "read": ["nome", "telefone"], "write": ["telefone"] },
  "maxConfidentiality": "RESTRICTED",
  "allowedActivityTypes": ["triagem", "atendimento", "visita_domiciliar"]
}
```

| Regra | Comportamento |
|---|---|
| Sem permissão na categoria | 403 em qualquer operação com os registros dela |
| `allowedColumns.read` | Lista das colunas legíveis. **Sem a chave, todas são legíveis.** As demais voltam `null` (clientes) ou são removidas (dinâmicas) e aparecem em `hiddenFields`. |
| `allowedColumns.write` | Lista das colunas que podem ser escritas. **Sem a chave, todas podem.** |
| Criar preenchendo coluna sem permissão de escrita | 403 `Sem permissão para preencher: …` |
| Editar alterando coluna sem permissão de escrita | 403 `Sem permissão para alterar: …`. Reenviar o **valor atual** é aceito, então o front pode mandar o objeto inteiro no `PUT`. |
| Colunas ocultas na edição | São preservadas com o valor atual |
| Filtrar ou ordenar por coluna oculta | 403, porque o resultado revelaria o valor |
| Documentos | Só aparecem e podem ser baixados se o sigilo for ≤ `maxConfidentiality` (`INTERNAL` < `RESTRICTED` < `CONFIDENTIAL`) |
| Histórico | Valores de colunas ocultas e eventos de documentos acima do nível são ocultados (`redacted: true`) |
| `allowedActivityTypes` | Keys dos [tipos de registro](#tipos-de-registro-da-linha-do-tempo) que o usuário pode registrar na categoria. **Ausente (`null`), todos são permitidos.** Registrar um tipo fora da lista: 403. As keys precisam existir na categoria (senão, 400). |

### Categoria nativa de clientes

Os registros de `clients` pertencem à categoria cujo `tableReference` é `"clients"`. **Crie essa categoria** (`entityType: "native"`) para poder dar permissões sobre clientes a usuários comuns. Sem ela, só administradores acessam clientes.

### Regras de proteção

- O administrador não pode remover o próprio acesso de administrador, se desativar nem excluir o próprio usuário (422).
- Atendimentos manuais na linha do tempo exigem permissão de **edição** no registro e que o tipo esteja liberado em `allowedActivityTypes`.

---

## Rotas

Convenções:

- **Formato:** JSON (`Content-Type: application/json`), exceto o upload, que usa `multipart/form-data`.
- **IDs:** UUID.
- **Datas:** `AAAA-MM-DD`; data e hora, ISO-8601.
- **Respostas:** criação devolve **201** com o cabeçalho `Location`; exclusão e troca de senha, **204**.
- **Coluna "Acesso"** nas tabelas abaixo: `público` = sem token; `autenticado` = qualquer usuário logado; `admin` = só administrador global; `permissão` = depende da matriz de permissões.

### Auth

| Método | Rota | Acesso | Descrição |
|---|---|---|---|
| `POST` | `/auth/login` | público | Login |
| `GET` | `/auth/me` | autenticado | Usuário logado e acesso efetivo por categoria |
| `PUT` | `/auth/me/password` | autenticado | Troca a própria senha |

**`POST /auth/login`**
```json
// request
{ "email": "admin@omnibase.local", "password": "Admin@2026" }
// 200
{
  "token": "eyJhbGciOiJIUzI1NiJ9…",
  "tokenType": "Bearer",
  "expiresAt": "2026-11-17T21:44:49Z",
  "user": { "id": "…", "name": "Administrador", "email": "admin@omnibase.local",
            "isGlobalAdmin": true, "isActive": true, "createdAt": "…", "updatedAt": "…" }
}
```

**`GET /auth/me`**: feito para o front montar menus e telas sem chamadas extras.
```json
{
  "user": { "id": "…", "name": "Operadora", "isGlobalAdmin": false, "…": "…" },
  "access": [
    {
      "categoryId": "…", "categoryName": "Mulheres atendidas",
      "entityType": "native", "tableReference": "clients",
      "canRead": true, "canCreate": false, "canUpdate": true, "canDelete": false,
      "allowedColumns": { "read": ["nome", "telefone"], "write": ["telefone"] },
      "maxConfidentiality": "INTERNAL",
      "activityTypes": [
        { "id": "…", "key": "triagem", "label": "Triagem", "color": "#5b2a86" },
        { "id": "…", "key": "visita_domiciliar", "label": "Visita domiciliar", "color": "#15803d" }
      ]
    }
  ]
}
```

`activityTypes` lista os tipos ativos que o usuário **pode registrar** naquela categoria: já considera `allowedActivityTypes` e vem vazio quando ele não tem `canUpdate`. O front usa essa lista no seletor de tipo ao registrar um atendimento.

**`PUT /auth/me/password`**: `{ "currentPassword": "…", "newPassword": "…" }` (mínimo de 6 caracteres). Responde 204; senha atual errada dá 422.

### Usuários

| Método | Rota | Acesso | Descrição |
|---|---|---|---|
| `GET` | `/users` | admin | Lista usuários |
| `GET` | `/users/{id}` | admin ou o próprio | Busca usuário |
| `GET` | `/users/{id}/activities` | admin ou o próprio | Ações realizadas pelo usuário (auditoria) |
| `POST` | `/users` | admin | Cria usuário |
| `PUT` | `/users/{id}` | admin | Atualiza usuário |
| `DELETE` | `/users/{id}` | admin | Exclui usuário |

```json
// POST/PUT /users
{
  "name": "Fernanda Lima",
  "email": "fernanda@exemplo.org",
  "password": "Senha@123",   // obrigatório na criação; no PUT, se omitido, mantém a atual
  "isGlobalAdmin": false,
  "isActive": true
}
```
E-mail duplicado dá **409**.

### Categorias

| Método | Rota | Acesso | Descrição |
|---|---|---|---|
| `GET` | `/categories` | autenticado | Lista categorias (o usuário comum só vê as que pode ler) |
| `GET` | `/categories/{id}` | autenticado | Busca categoria |
| `POST` | `/categories` | admin | Cria categoria |
| `PUT` | `/categories/{id}` | admin | Atualiza categoria |
| `DELETE` | `/categories/{id}` | admin | Exclui categoria (409 se ainda houver entidades dinâmicas ou se seus tipos já foram usados na linha do tempo) |

```json
// POST/PUT /categories
{
  "name": "Abrigos",
  "entityType": "dynamic",        // "native" ou "dynamic"
  "tableReference": "abrigos",    // para clientes: "clients"
  "isActive": true                // categorias inativas não recebem novos registros
}
```
Toda categoria nova já nasce com os quatro [tipos de registro](#tipos-de-registro-da-linha-do-tempo) padrão: Triagem, Atendimento, Acompanhamento e Anotação.

### Campos personalizados (de categorias dinâmicas)

São os campos do "formulário" de uma categoria dinâmica. A `key` é o nome usado em `customFields` e em `allowedColumns`.

| Método | Rota | Acesso | Descrição |
|---|---|---|---|
| `GET` | `/categories/{categoryId}/fields` | permissão de leitura | Lista campos (o usuário só vê a definição dos campos que pode ler) |
| `POST` | `/categories/{categoryId}/fields` | admin | Cria campo |
| `PUT` | `/categories/{categoryId}/fields/{fieldId}` | admin | Atualiza campo |
| `DELETE` | `/categories/{categoryId}/fields/{fieldId}?purgeData=false` | admin | Remove campo |

```json
// POST/PUT
{
  "key": "regiao",              // minúsculas, números e "_", começando por letra; não pode mudar depois
  "label": "Região",
  "fieldType": "LIST",          // TEXT | NUMBER | BOOLEAN | LIST | DATE
  "isRequired": false,
  "options": ["Norte", "Sul", "Centro"],   // obrigatório (e só usado) em LIST
  "position": 3                 // ordem de exibição
}
// resposta
{
  "id": "…", "categoryId": "…", "key": "regiao", "label": "Região", "fieldType": "LIST",
  "isRequired": false, "options": ["Norte", "Sul", "Centro"], "position": 3,
  "filterOperators": ["EQUALS", "NOT_EQUALS", "IN", "IS_EMPTY", "NOT_EMPTY"],
  "createdAt": "…"
}
```

**Tipos de campo**

| Tipo | Valor aceito | Exemplo |
|---|---|---|
| `TEXT` | Texto livre, sem formatação específica | `"Casa Rosa"` |
| `NUMBER` | Número (inteiro ou decimal) | `20`, `12.5` |
| `BOOLEAN` | `true` / `false` | `true` |
| `LIST` | Uma das `options` do campo | `"Sul"` |
| `DATE` | Data `AAAA-MM-DD` | `"2020-03-01"` |

`filterOperators` informa ao front quais operadores de [busca](#busca-com-filtros) aquele campo aceita.

**Regras de integridade:**

- **Chave duplicada:** 409.
- **Chave:** não pode ser alterada (422). Crie um campo novo.
- **Tipo:** trocar o tipo de um campo que **já tem dados** dá 409.
- **Opções de LIST:** remover uma opção que **ainda é usada** por algum registro dá 409, e a mensagem lista as opções em uso. Adicionar opções e remover opções sem uso é permitido.
- **Exclusão:** excluir um campo com dados dá 409. Com `?purgeData=true`, o campo é removido e o valor é apagado de todos os registros.

### Tipos de registro da linha do tempo

Cada categoria (nativa ou dinâmica) tem os seus tipos de registro manual. Exemplos: em "Mulheres atendidas", *Triagem, Atendimento psicológico, Atendimento jurídico, Visita domiciliar*; em "Cursos", *Matrícula, Presença, Certificado*.

| Método | Rota | Acesso | Descrição |
|---|---|---|---|
| `GET` | `/categories/{categoryId}/activity-types?includeInactive=false` | permissão de leitura | Lista os tipos ativos, ordenados por `position`. Só admin pode pedir `includeInactive=true` (senão, 403). |
| `POST` | `/categories/{categoryId}/activity-types` | admin | Cria tipo |
| `PUT` | `/categories/{categoryId}/activity-types/{typeId}` | admin | Atualiza tipo (renomear, recolorir, reordenar, desativar) |
| `DELETE` | `/categories/{categoryId}/activity-types/{typeId}` | admin | Exclui tipo |

```json
// POST/PUT
{
  "key": "atendimento_psicologico",   // minúsculas, números e "_", começando por letra; não pode mudar depois
  "label": "Atendimento psicológico",
  "description": "Sessão com a psicóloga",   // opcional
  "color": "#0e7490",                        // opcional; hexadecimal (#rgb, #rrggbb ou #rrggbbaa)
  "position": 2,                             // ordem de exibição; padrão 0
  "isActive": true                           // padrão true
}
// resposta
{
  "id": "…", "categoryId": "…", "key": "atendimento_psicologico", "label": "Atendimento psicológico",
  "description": "Sessão com a psicóloga", "color": "#0e7490", "position": 2, "isActive": true, "createdAt": "…"
}
```

**Regras:**

- **Chave:** `key` repetida na categoria dá **409**; tentar alterar a `key` dá **422**.
- **Renomear:** mudar o `label` **não altera o histórico**, porque cada registro guarda o nome do tipo no momento em que foi feito. Já a `color` exibida na linha do tempo é sempre a atual.
- **Excluir:** um tipo **já usado** na linha do tempo não pode ser excluído (**409**); desative-o com `isActive: false`. Tipos inativos não aparecem no `GET` padrão nem podem ser usados em novos registros, mas continuam no histórico. Ao excluir um tipo sem uso, a key dele sai também das permissões que o listavam.
- **Tipos padrão:** toda categoria nova nasce com `triagem`, `atendimento`, `acompanhamento` e `anotacao`, que podem ser renomeados, desativados ou excluídos como os demais.

### Permissões

| Método | Rota | Acesso | Descrição |
|---|---|---|---|
| `GET` | `/permissions?userId={id}` | admin | Lista permissões (filtro por usuário opcional) |
| `GET` | `/permissions/{id}` | admin | Busca permissão |
| `POST` | `/permissions` | admin | Cria permissão |
| `PUT` | `/permissions/{id}` | admin | Atualiza permissão |
| `DELETE` | `/permissions/{id}` | admin | Remove permissão |

O corpo está descrito em [Controle de acesso](#permissão-por-categoria). `maxConfidentiality`, se omitido, vale `INTERNAL`. Cada usuário tem **uma** permissão por categoria; uma segunda para o mesmo par usuário/categoria dá **409**, então use o `PUT` para alterar.

**`allowedActivityTypes` no `PUT`:** segue a convenção dos outros campos.

| Valor enviado | Efeito |
|---|---|
| ausente ou `null` | mantém o valor atual |
| `[]` | remove a restrição: todos os tipos ficam liberados, inclusive os criados depois |
| lista de keys | passa a valer só essa lista |

Na criação (`POST`), ausente ou `[]` significa todos os tipos.

### Clientes

| Método | Rota | Acesso | Descrição |
|---|---|---|---|
| `GET` | `/clients` | permissão (leitura) | Lista clientes |
| `GET` | `/clients/{id}` | permissão (leitura) | Busca cliente |
| `POST` | `/clients` | permissão (criação) | Cria cliente |
| `PUT` | `/clients/{id}` | permissão (edição) | Atualiza cliente |
| `DELETE` | `/clients/{id}` | permissão (exclusão) | Exclui cliente e seus documentos (o histórico permanece) |

```json
// POST/PUT
{ "nome": "Maria Silva", "dataNascimento": "1990-05-10", "telefone": "11999990000", "sobRisco": true }
// resposta (usuário que só pode ler nome e telefone)
{
  "id": "…", "nome": "Maria Silva", "dataNascimento": null, "telefone": "11999990000", "sobRisco": null,
  "createdBy": "…", "createdAt": "…", "updatedAt": "…",
  "hiddenFields": ["data_nascimento", "sob_risco"]
}
```

| Campo da API | Coluna (`allowedColumns`) | Regra |
|---|---|---|
| `nome` | `nome` | obrigatório, até 255 caracteres |
| `dataNascimento` | `data_nascimento` | data no passado |
| `telefone` | `telefone` | até 50 caracteres |
| `sobRisco` | `sob_risco` | booleano; padrão `false` |

### Entidades dinâmicas

| Método | Rota | Acesso | Descrição |
|---|---|---|---|
| `GET` | `/dynamic-entities?categoryId={id}` | permissão (leitura) | Lista registros. Sem `categoryId`, devolve os de todas as categorias que o usuário pode ler. |
| `GET` | `/dynamic-entities/{id}` | permissão (leitura) | Busca registro |
| `POST` | `/dynamic-entities/search` | permissão (leitura) | [Busca com filtros](#busca-com-filtros) |
| `POST` | `/dynamic-entities` | permissão (criação) | Cria registro |
| `PUT` | `/dynamic-entities/{id}` | permissão (edição) | Atualiza registro |
| `DELETE` | `/dynamic-entities/{id}` | permissão (exclusão) | Exclui registro e seus documentos (o histórico permanece) |

```json
// POST/PUT
{
  "categoryId": "…",
  "customFields": { "nome": "Casa Rosa", "vagas": 20, "ativo": true, "regiao": "Norte", "inaugurado": "2020-03-01" }
}
// resposta (campos sem permissão de leitura são removidos)
{
  "id": "…", "categoryId": "…",
  "customFields": { "nome": "Casa Rosa", "vagas": 20, "regiao": "Norte" },
  "createdBy": "…", "createdAt": "…", "updatedAt": "…",
  "hiddenFields": ["ativo", "inaugurado"]
}
```

- **Validação:** os valores são validados contra os campos da categoria. Campo inexistente, obrigatório ausente, tipo errado ou opção fora da lista dão **400**, com o erro por campo em `errors`.
- **Categoria:** precisa ser `dynamic` e estar ativa (senão, 422).
- **Mover de categoria:** é possível trocando o `categoryId` no `PUT`, mas exige permissão de criação na categoria de destino.

### Busca com filtros

`POST /dynamic-entities/search`: pensada para as telas de filtro do front.

```json
{
  "categoryId": "…",
  "filters": [
    { "field": "nome",       "operator": "CONTAINS", "value": "casa" },
    { "field": "vagas",      "operator": "BETWEEN",  "value": [5, 20] },
    { "field": "regiao",     "operator": "IN",       "value": ["Norte", "Sul"] },
    { "field": "ativo",      "operator": "EQUALS",   "value": true },
    { "field": "inaugurado", "operator": "GTE",      "value": "2020-01-01" },
    { "field": "telefone",   "operator": "IS_EMPTY" }
  ],
  "sort": { "field": "vagas", "direction": "asc" },
  "fields": { "regiao": "Sul" }
}
```

- **`filters`:** combinados com **E**, no máximo 30.
- **`fields`** (opcional): atalho de igualdade exata. Cada par equivale a um filtro `EQUALS`.
- **`sort`** (opcional): qualquer campo da categoria, ou `createdAt`/`updatedAt`. A direção `asc`/`desc` vale `desc` por padrão. Sem `sort`, a ordem é `createdAt` decrescente. Registros sem o campo vão para o fim.
- **Resposta:** lista de entidades no mesmo formato de `GET /dynamic-entities/{id}`.

**Operadores por tipo**

| Operador | `value` | TEXT | NUMBER | DATE | LIST | BOOLEAN |
|---|---|:-:|:-:|:-:|:-:|:-:|
| `EQUALS` | valor | ✔ | ✔ | ✔ | ✔ | ✔ |
| `NOT_EQUALS` | valor (inclui registros sem o campo) | ✔ | ✔ | ✔ | ✔ | |
| `CONTAINS` | texto, sem diferenciar maiúsculas | ✔ | | | | |
| `STARTS_WITH` | texto, sem diferenciar maiúsculas | ✔ | | | | |
| `IN` | lista de 1 a 100 valores | ✔ | | | ✔ | |
| `GT` `GTE` `LT` `LTE` | valor | | ✔ | ✔ | | |
| `BETWEEN` | `[mínimo, máximo]`, inclusivo | | ✔ | ✔ | | |
| `IS_EMPTY` / `NOT_EMPTY` | não usa | ✔ | ✔ | ✔ | ✔ | ✔ |

- **Formato dos valores:** `NUMBER` aceita número ou texto numérico (`"5"`), `DATE` usa `AAAA-MM-DD` e `BOOLEAN` aceita `true`/`false`. Em `LIST`, os valores precisam estar entre as opções do campo.
- **Erros de filtro:** voltam todos juntos (400), indicando qual filtro está errado:
  ```json
  { "status": 400, "detail": "Filtros de busca inválidos",
    "errors": { "filters[0]": "operador CONTAINS não se aplica a campos BOOLEAN; use um de [EQUALS, IS_EMPTY, NOT_EMPTY]" } }
  ```
- **Campo oculto:** filtrar ou ordenar por um campo sem permissão de leitura dá **403**.
- **Segurança:** os valores e os nomes dos campos são enviados como parâmetros da consulta, sem concatenação de SQL.

> A busca devolve todos os resultados de uma vez, sem paginação.

### Linha do tempo, documentos e visão 360°

Rotas compartilhadas por clientes e entidades dinâmicas: `{tipo}` é `clients` ou `dynamic-entities`.

| Método | Rota | Acesso | Descrição |
|---|---|---|---|
| `GET` | `/{tipo}/{id}/overview` | leitura | Visão 360°: registro + linha do tempo + documentos em uma resposta |
| `GET` | `/{tipo}/{id}/activities` | leitura | Linha do tempo do registro |
| `POST` | `/{tipo}/{id}/activities` | edição | Registra um atendimento manual |
| `GET` | `/{tipo}/{id}/files` | leitura | Documentos do registro (só os de sigilo permitido) |
| `POST` | `/{tipo}/{id}/files` | edição | Envia documento (`multipart/form-data`) |

**Atendimento manual**
```json
{
  "activityTypeId": "…",                    // um dos tipos ativos da categoria do registro
  "description": "Atendimento psicológico inicial",
  "responsibleEmployee": "Fernanda Lima",   // opcional
  "occurredAt": "2026-11-17T10:00:00"       // opcional, não pode ser no futuro; padrão: agora
}
```

| Situação | Resposta |
|---|---|
| Tipo não pertence à categoria do registro, não existe ou está inativo | **422** |
| Cliente sem categoria nativa `clients` (não há tipos) | **422** |
| Tipo fora do `allowedActivityTypes` do usuário, ou sem `canUpdate` | **403** |
| Nenhum tipo informado, ou `activityTypeId` e `actionType` juntos | **400** |

> **Compatibilidade:** o formato antigo, `"actionType": "TRIAGE" | "SERVICE" | "FOLLOW_UP" | "NOTE"` sem `activityTypeId`, ainda é aceito e equivale ao tipo padrão correspondente da categoria (`triagem`, `atendimento`, `acompanhamento`, `anotacao`). Se a categoria não tiver esse tipo, a resposta é 422. Prefira `activityTypeId`.

**Resposta das atividades** (em `/activities`, `/overview` e `/users/{id}/activities`):
```json
{
  "id": "…", "entityType": "clients", "entityId": "…", "userId": "…", "userName": "Fernanda Lima",
  "actionType": "MANUAL",
  "activityType": { "id": "…", "key": "atendimento_psicologico", "label": "Atendimento psicológico", "color": "#0e7490" },
  "description": "…", "changes": null, "responsibleEmployee": "Fernanda Lima",
  "occurredAt": "…", "createdAt": "…", "redacted": false
}
```

- **`activityType.label`:** é o nome gravado **no momento do registro**. A `color` é a atual do tipo.
- **Eventos automáticos:** vêm com `activityType: null`.

**`actionType`**

| Valor | Quando |
|---|---|
| `MANUAL` | Registro manual. O tipo de fato está em `activityType` |
| `CREATED`, `UPDATED`, `DELETED`, `FILE_UPLOADED`, `FILE_UPDATED`, `FILE_REMOVED`, `FILE_DOWNLOADED` | Eventos automáticos de auditoria; não podem ser criados pela API (422) |
| `TRIAGE`, `SERVICE`, `FOLLOW_UP`, `NOTE` | Registros antigos que não puderam ser migrados (ex.: de um registro já excluído). Só aparecem no histórico, com `activityType: null` |

Cada atividade traz `changes` (em `UPDATED`: `{"campo": {"antigo": …, "novo": …}}`) e `redacted: true` quando parte do conteúdo foi ocultada para o usuário.

**Upload de documento**
```bash
curl -X POST http://localhost:8080/clients/{id}/files \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@laudo.pdf" \
  -F "confidentialityLevel=CONFIDENTIAL"     # INTERNAL | RESTRICTED (padrão) | CONFIDENTIAL
```
O usuário só pode enviar documentos com sigilo até o seu `maxConfidentiality`.

### Arquivos

| Método | Rota | Acesso | Descrição |
|---|---|---|---|
| `GET` | `/files/{id}` | leitura + sigilo | Metadados do documento |
| `GET` | `/files/{id}/download` | leitura + sigilo | Baixa o arquivo (sempre como anexo) |
| `PATCH` | `/files/{id}` | edição + sigilo | Altera o nível de sigilo: `{ "confidentialityLevel": "RESTRICTED" }` |
| `DELETE` | `/files/{id}` | exclusão + sigilo | Remove o documento |

- **Download:** sempre vem com `Content-Disposition: attachment`, para o navegador não renderizar conteúdo enviado por terceiros. Downloads de documentos `RESTRICTED` ou `CONFIDENTIAL` são registrados na linha do tempo (`FILE_DOWNLOADED`).
- **Mudança de sigilo:** exige que o usuário tenha acesso tanto ao nível antigo quanto ao novo.
- **Armazenamento:** o arquivo físico é gravado com nome UUID; o nome original é só metadado.

---

## Erros

Todos os erros seguem o formato **Problem Details** (RFC 9457):

```json
{
  "title": "Bad Request",
  "status": 400,
  "detail": "Dados inválidos",
  "instance": "/clients",
  "errors": { "dataNascimento": "deve ser uma data passada" }
}
```

| Status | Quando |
|---|---|
| **400** | JSON malformado, validação de campos (`errors` por campo), valores de campos dinâmicos ou filtros inválidos, enum desconhecido (a mensagem lista os valores válidos) |
| **401** | Sem token, token inválido ou expirado, login inválido |
| **403** | Autenticado, mas sem permissão (rota de admin, categoria, coluna, nível de sigilo ou tipo de registro não liberado) |
| **404** | Recurso não encontrado |
| **409** | Conflito: e-mail ou chave duplicados, categoria com registros ou com histórico manual, campo com dados, opção em uso, tipo de registro já usado |
| **413** | Arquivo acima de 20 MB |
| **422** | Regra de negócio: categoria não dinâmica ou inativa, tipo de registro inválido ou inativo para o registro, tipo de atividade automático, senha atual incorreta, chave de campo ou de tipo alterada, administrador tentando se bloquear |

---

## Dados de demonstração

Com o profile `seed`, a aplicação cria dados fictícios para apresentação e testes do front:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=seed
```

- **Funcionárias:** cinco perfis, todos com a senha `SEED_PASSWORD` (padrão `Demo@2026`):

  | E-mail | Perfil |
  |---|---|
  | `coordenacao@secmulher.demo` | Coordenação (administradora) |
  | `psicologia@secmulher.demo` | Psicóloga (única com acesso a documentos sigilosos) |
  | `assistencia@secmulher.demo` | Assistente social |
  | `recepcao@secmulher.demo` | Recepção (não vê situação de risco nem laudos) |
  | `cursos@secmulher.demo` | Instrutora de cursos (só vê contato das atendidas) |

- **Atendidas:** mulheres com histórico completo (chegada, triagem, atendimentos, cursos) e PDFs simulando documentos.
- **Tipos de registro:**
  - **"Mulheres Atendidas":** os quatro padrão + *Atendimento psicológico*, *Atendimento jurídico*, *Visita domiciliar* e *Atividade de curso*, cada um com cor.
  - **"Inscrições em Cursos":** os padrão + *Matrícula*, *Presença* e *Certificado*.
  - **Restrição por perfil:** só a psicóloga registra *Atendimento psicológico*; a assistente social pode registrar todos os outros.
- **Execução:** o seed não roda de novo se já tiver sido aplicado.

---

## Fluxo típico de uso

1. **Login como administrador**: `POST /auth/login`.
2. **Criar a categoria de clientes**: `POST /categories` com `{"name": "Mulheres atendidas", "entityType": "native", "tableReference": "clients"}`.
3. **Criar categorias dinâmicas** e seus campos: `POST /categories` com `entityType: "dynamic"` e depois `POST /categories/{id}/fields` para cada campo.
4. **Configurar os tipos de registro** de cada categoria: ajustar os quatro padrão e criar os específicos com `POST /categories/{id}/activity-types` (ex.: *Atendimento psicológico*, *Matrícula*).
5. **Cadastrar as funcionárias**: `POST /users`.
6. **Dar permissões**: `POST /permissions` para cada funcionária e categoria, definindo colunas, nível de sigilo e, se for o caso, os tipos de registro liberados (`allowedActivityTypes`).
7. **No front**, após o login de cada funcionária:
   - `GET /auth/me` para montar menus e telas conforme o acesso, incluindo os tipos que ela pode registrar (`activityTypes`);
   - `GET /categories/{id}/fields` para montar formulários e filtros dinâmicos (`fieldType`, `options`, `filterOperators`);
   - `GET /clients` / `POST /dynamic-entities/search` para as listagens com filtros;
   - `GET /{tipo}/{id}/overview` para a ficha completa do registro;
   - `POST /{tipo}/{id}/activities` com `activityTypeId` para registrar um atendimento.
