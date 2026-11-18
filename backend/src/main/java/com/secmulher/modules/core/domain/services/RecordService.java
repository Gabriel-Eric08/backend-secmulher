package com.secmulher.modules.core.domain.services;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.secmulher.modules.core.domain.enums.RecordType;
import com.secmulher.modules.core.domain.exceptions.NotFoundException;
import com.secmulher.modules.core.infra.repositories.ClientRepository;
import com.secmulher.modules.core.infra.repositories.DynamicEntityRepository;

// Valida referências polimórficas (entity_type + entity_id) usadas pelo ledger e pelos arquivos
@Service
public class RecordService {
    final ClientRepository clientRepository;
    final DynamicEntityRepository dynamicEntityRepository;

    public RecordService(ClientRepository clientRepository, DynamicEntityRepository dynamicEntityRepository) {
        this.clientRepository = clientRepository;
        this.dynamicEntityRepository = dynamicEntityRepository;
    }

    public void ensureExists(RecordType type, UUID recordId) {
        boolean exists = switch (type) {
            case CLIENT -> clientRepository.existsById(recordId);
            case DYNAMIC_ENTITY -> dynamicEntityRepository.existsById(recordId);
        };
        if (!exists) {
            throw new NotFoundException("Registro não encontrado: " + type.getPath() + "/" + recordId);
        }
    }
}
