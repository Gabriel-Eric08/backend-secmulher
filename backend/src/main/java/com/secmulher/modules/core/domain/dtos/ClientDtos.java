package com.secmulher.modules.core.domain.dtos;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.secmulher.modules.core.domain.access.CategoryAccess;
import com.secmulher.modules.core.infra.entities.Client;

import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

public final class ClientDtos {
    private ClientDtos() {}

    // Nomes das colunas usados em allowed_columns e no histórico de alterações
    public static final String NOME = "nome";
    public static final String DATA_NASCIMENTO = "data_nascimento";
    public static final String TELEFONE = "telefone";
    public static final String SOB_RISCO = "sob_risco";
    public static final List<String> COLUMNS = List.of(NOME, DATA_NASCIMENTO, TELEFONE, SOB_RISCO);

    // nome só é obrigatório para quem pode escrevê-lo; a regra fica no service
    public record ClientRequest(
        @Size(max = 255) String nome,
        @Past LocalDate dataNascimento,
        @Size(max = 50) String telefone,
        Boolean sobRisco
    ) {}

    // Colunas sem permissão de leitura voltam nulas e são listadas em hiddenFields
    public record ClientResponse(
        UUID id,
        String nome,
        LocalDate dataNascimento,
        String telefone,
        Boolean sobRisco,
        UUID createdBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<String> hiddenFields
    ) {
        public static ClientResponse from(Client client, CategoryAccess access) {
            return new ClientResponse(
                client.getId(),
                access.canReadColumn(NOME) ? client.getNome() : null,
                access.canReadColumn(DATA_NASCIMENTO) ? client.getDataNascimento() : null,
                access.canReadColumn(TELEFONE) ? client.getTelefone() : null,
                access.canReadColumn(SOB_RISCO) ? client.getSobRisco() : null,
                client.getCreatedBy() != null ? client.getCreatedBy().getId() : null,
                client.getCreatedAt(),
                client.getUpdatedAt(),
                access.hiddenColumns(COLUMNS)
            );
        }
    }
}
