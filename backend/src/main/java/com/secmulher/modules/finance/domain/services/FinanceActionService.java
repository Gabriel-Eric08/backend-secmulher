package com.secmulher.modules.finance.domain.services;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.secmulher.modules.core.domain.exceptions.ConflictException;
import com.secmulher.modules.core.domain.exceptions.NotFoundException;
import com.secmulher.modules.core.infra.entities.User;
import com.secmulher.modules.finance.domain.access.FinanceAccessService;
import com.secmulher.modules.finance.domain.dtos.FinanceActionDtos.FinanceActionCreateRequest;
import com.secmulher.modules.finance.domain.dtos.FinanceActionDtos.FinanceActionResponse;
import com.secmulher.modules.finance.domain.dtos.FinanceActionDtos.FinanceActionUpdateRequest;
import com.secmulher.modules.finance.infra.entities.FinanceAction;
import com.secmulher.modules.finance.infra.repositories.FinanceActionRepository;
import com.secmulher.modules.finance.infra.repositories.FinanceEntryRepository;
import com.secmulher.modules.finance.infra.repositories.FinanceSubActionRepository;

// Cadastro de ações. O valor da ação só muda por operações (FinanceOperationService)
@Service
public class FinanceActionService {
    final FinanceActionRepository actionRepository;
    final FinanceSubActionRepository subActionRepository;
    final FinanceEntryRepository entryRepository;
    final FinanceOperationService operationService;
    final FinanceAccessService access;
    final FinanceMapper mapper;

    public FinanceActionService(FinanceActionRepository actionRepository, FinanceSubActionRepository subActionRepository,
            FinanceEntryRepository entryRepository, FinanceOperationService operationService,
            FinanceAccessService access, FinanceMapper mapper) {
        this.actionRepository = actionRepository;
        this.subActionRepository = subActionRepository;
        this.entryRepository = entryRepository;
        this.operationService = operationService;
        this.access = access;
        this.mapper = mapper;
    }

    FinanceAction getActionById(UUID id) {
        return actionRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Ação não encontrada: " + id));
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    @Transactional
    public FinanceActionResponse create(FinanceActionCreateRequest request) {
        User user = access.requireCreate();
        String code = request.code().trim();
        if (actionRepository.existsByCodeIgnoreCase(code)) {
            throw new ConflictException("Já existe uma ação com o código: " + code);
        }

        FinanceAction action = actionRepository.saveAndFlush(FinanceAction.builder()
            .code(code)
            .name(request.name().trim())
            .description(normalize(request.description()))
            .totalAmount(FinanceMapper.ZERO)
            .isActive(true)
            .createdBy(user)
            .build());

        if (request.initialAmount() != null && request.initialAmount().compareTo(BigDecimal.ZERO) > 0) {
            operationService.recordOpening(action, request.initialAmount(), request.initialDate(), user);
        }
        return mapper.currentAction(action, subActionRepository.findByActionIdOrderByCodeAsc(action.getId()));
    }

    @Transactional
    public FinanceActionResponse update(UUID id, FinanceActionUpdateRequest request) {
        access.requireUpdate();
        FinanceAction action = getActionById(id);
        String code = request.code().trim();
        if (actionRepository.existsByCodeIgnoreCaseAndIdNot(code, id)) {
            throw new ConflictException("Já existe uma ação com o código: " + code);
        }

        action.setCode(code);
        action.setName(request.name().trim());
        action.setDescription(normalize(request.description()));
        if (request.isActive() != null) {
            action.setIsActive(request.isActive());
        }
        actionRepository.saveAndFlush(action);
        return mapper.currentAction(action, subActionRepository.findByActionIdOrderByCodeAsc(id));
    }

    // Só sai do banco se nunca foi movimentada; caso contrário o histórico precisa dela
    @Transactional
    public void delete(UUID id) {
        access.requireDelete();
        FinanceAction action = getActionById(id);
        if (entryRepository.existsByActionId(id)) {
            throw new ConflictException("A ação " + action.getCode() + " tem operações no histórico e não pode ser "
                + "excluída. Desative-a com isActive: false");
        }
        subActionRepository.deleteAll(subActionRepository.findByActionIdOrderByCodeAsc(id));
        actionRepository.delete(action);
    }
}
