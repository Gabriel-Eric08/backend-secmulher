package com.secmulher.modules.core.domain.services;

import static com.secmulher.modules.core.domain.dtos.ClientDtos.DATA_NASCIMENTO;
import static com.secmulher.modules.core.domain.dtos.ClientDtos.NOME;
import static com.secmulher.modules.core.domain.dtos.ClientDtos.SOB_RISCO;
import static com.secmulher.modules.core.domain.dtos.ClientDtos.TELEFONE;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.secmulher.modules.core.domain.access.AccessControlService;
import com.secmulher.modules.core.domain.access.CategoryAccess;
import com.secmulher.modules.core.domain.dtos.ClientDtos.ClientRequest;
import com.secmulher.modules.core.domain.dtos.ClientDtos.ClientResponse;
import com.secmulher.modules.core.domain.enums.RecordType;
import com.secmulher.modules.core.domain.exceptions.FieldValidationException;
import com.secmulher.modules.core.domain.exceptions.ForbiddenException;
import com.secmulher.modules.core.domain.exceptions.NotFoundException;
import com.secmulher.modules.core.infra.entities.Client;
import com.secmulher.modules.core.infra.entities.enums.ActivityType;
import com.secmulher.modules.core.infra.repositories.ClientRepository;

// Regras de coluna: na criação, colunas sem permissão de escrita não podem ser enviadas;
// na edição, elas são preservadas com o valor atual, independentemente do que vier.
@Service
public class ClientService {
    final ClientRepository clientRepository;
    final ActivityService activityService;
    final FileService fileService;
    final AccessControlService accessControl;

    public ClientService(ClientRepository clientRepository, ActivityService activityService, FileService fileService,
            AccessControlService accessControl) {
        this.clientRepository = clientRepository;
        this.activityService = activityService;
        this.fileService = fileService;
        this.accessControl = accessControl;
    }

    private CategoryAccess access() {
        return accessControl.forNativeTable(RecordType.CLIENT.getTableName());
    }

    private Client getClientById(UUID id) {
        return clientRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Cliente não encontrada: " + id));
    }

    private static void requireNome(String nome) {
        if (nome == null || nome.isBlank()) {
            throw new FieldValidationException(Map.of(NOME, "campo obrigatório"));
        }
    }

    @Transactional(readOnly = true)
    public List<ClientResponse> findAll() {
        CategoryAccess access = access().requireRead();
        return clientRepository.findAll().stream().map(client -> ClientResponse.from(client, access)).toList();
    }

    @Transactional(readOnly = true)
    public ClientResponse findById(UUID id) {
        Client client = getClientById(id);
        return ClientResponse.from(client, access().requireRead());
    }

    @Transactional
    public ClientResponse create(ClientRequest request) {
        CategoryAccess access = access().requireCreate();

        Map<String, Object> sent = new LinkedHashMap<>();
        sent.put(NOME, request.nome());
        sent.put(DATA_NASCIMENTO, request.dataNascimento());
        sent.put(TELEFONE, request.telefone());
        sent.put(SOB_RISCO, request.sobRisco());
        List<String> forbidden = sent.entrySet().stream()
            .filter(entry -> entry.getValue() != null && !access.canWriteColumn(entry.getKey()))
            .map(Map.Entry::getKey)
            .toList();
        if (!forbidden.isEmpty()) {
            throw new ForbiddenException("Sem permissão para preencher: " + String.join(", ", forbidden));
        }
        if (!access.canWriteColumn(NOME)) {
            throw new ForbiddenException("Sem permissão para preencher 'nome', obrigatório para criar o cadastro");
        }
        requireNome(request.nome());

        Client client = clientRepository.saveAndFlush(Client.builder()
            .nome(request.nome())
            .dataNascimento(request.dataNascimento())
            .telefone(request.telefone())
            .sobRisco(Objects.requireNonNullElse(request.sobRisco(), false))
            .createdBy(accessControl.requireUser())
            .build());

        activityService.record(RecordType.CLIENT, client.getId(), ActivityType.CREATED, "Cadastro criado", null);
        return ClientResponse.from(client, access);
    }

    @Transactional
    public ClientResponse update(UUID id, ClientRequest request) {
        Client client = getClientById(id);
        CategoryAccess access = access().requireUpdate();

        // Alterar coluna sem permissão de escrita é recusado; reenviar o valor atual é aceito
        Map<String, Object[]> sent = new LinkedHashMap<>();
        sent.put(NOME, new Object[] { request.nome(), client.getNome() });
        sent.put(DATA_NASCIMENTO, new Object[] { request.dataNascimento(), client.getDataNascimento() });
        sent.put(TELEFONE, new Object[] { request.telefone(), client.getTelefone() });
        sent.put(SOB_RISCO, new Object[] { request.sobRisco(), client.getSobRisco() });
        List<String> forbidden = sent.entrySet().stream()
            .filter(entry -> !access.canWriteColumn(entry.getKey()))
            .filter(entry -> entry.getValue()[0] != null && !Objects.equals(entry.getValue()[0], entry.getValue()[1]))
            .map(Map.Entry::getKey)
            .toList();
        if (!forbidden.isEmpty()) {
            throw new ForbiddenException("Sem permissão para alterar: " + String.join(", ", forbidden));
        }

        String nome =access.canWriteColumn(NOME) ? request.nome() : client.getNome();
        var dataNascimento = access.canWriteColumn(DATA_NASCIMENTO) ? request.dataNascimento() : client.getDataNascimento();
        String telefone = access.canWriteColumn(TELEFONE) ? request.telefone() : client.getTelefone();
        Boolean sobRisco = access.canWriteColumn(SOB_RISCO)
            ? Objects.requireNonNullElse(request.sobRisco(), client.getSobRisco())
            : client.getSobRisco();
        requireNome(nome);

        Map<String, Object> changes = new LinkedHashMap<>();
        ActivityService.trackChange(changes, NOME, client.getNome(), nome);
        ActivityService.trackChange(changes, DATA_NASCIMENTO, client.getDataNascimento(), dataNascimento);
        ActivityService.trackChange(changes, TELEFONE, client.getTelefone(), telefone);
        ActivityService.trackChange(changes, SOB_RISCO, client.getSobRisco(), sobRisco);

        client.setNome(nome);
        client.setDataNascimento(dataNascimento);
        client.setTelefone(telefone);
        client.setSobRisco(sobRisco);

        if (!changes.isEmpty()) {
            activityService.record(RecordType.CLIENT, id, ActivityType.UPDATED, "Cadastro atualizado", changes);
        }
        return ClientResponse.from(clientRepository.saveAndFlush(client), access);
    }

    // O histórico permanece no ledger mesmo após a exclusão; os documentos são removidos
    @Transactional
    public void delete(UUID id) {
        Client client = getClientById(id);
        access().requireDelete();
        fileService.deleteAllFor(RecordType.CLIENT, id);
        activityService.record(RecordType.CLIENT, id, ActivityType.DELETED, "Cadastro excluído", null);
        clientRepository.delete(client);
    }
}
