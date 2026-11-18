package com.secmulher.modules.finance.domain.services;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.secmulher.modules.core.domain.exceptions.BusinessException;
import com.secmulher.modules.core.domain.exceptions.ConflictException;
import com.secmulher.modules.core.domain.exceptions.NotFoundException;
import com.secmulher.modules.core.infra.entities.User;
import com.secmulher.modules.finance.domain.access.FinanceAccessService;
import com.secmulher.modules.finance.domain.dtos.FinanceSubActionDtos.FinanceSubActionRequest;
import com.secmulher.modules.finance.domain.dtos.FinanceSubActionDtos.FinanceSubActionResponse;
import com.secmulher.modules.finance.infra.entities.FinanceAction;
import com.secmulher.modules.finance.infra.entities.FinanceSubAction;
import com.secmulher.modules.finance.infra.repositories.FinanceActionRepository;
import com.secmulher.modules.finance.infra.repositories.FinanceEntryRepository;
import com.secmulher.modules.finance.infra.repositories.FinanceSubActionRepository;

// Cadastro de subações. O código é único em todo o sistema; o saldo só muda por operações
@Service
public class FinanceSubActionService {
    final FinanceActionRepository actionRepository;
    final FinanceSubActionRepository subActionRepository;
    final FinanceEntryRepository entryRepository;
    final FinanceAccessService access;
    final FinanceMapper mapper;

    public FinanceSubActionService(FinanceActionRepository actionRepository,
            FinanceSubActionRepository subActionRepository, FinanceEntryRepository entryRepository,
            FinanceAccessService access, FinanceMapper mapper) {
        this.actionRepository = actionRepository;
        this.subActionRepository = subActionRepository;
        this.entryRepository = entryRepository;
        this.access = access;
        this.mapper = mapper;
    }

    private FinanceSubAction getSubActionById(UUID id) {
        return subActionRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Subação não encontrada: " + id));
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    @Transactional
    public FinanceSubActionResponse create(UUID actionId, FinanceSubActionRequest request) {
        User user = access.requireCreate();
        FinanceAction action = actionRepository.findById(actionId)
            .orElseThrow(() -> new NotFoundException("Ação não encontrada: " + actionId));
        if (!Boolean.TRUE.equals(action.getIsActive())) {
            throw new BusinessException("A ação " + action.getCode() + " está inativa");
        }
        String code = request.code().trim();
        if (subActionRepository.existsByCodeIgnoreCase(code)) {
            throw new ConflictException("Já existe uma subação com o código: " + code);
        }

        FinanceSubAction sub = subActionRepository.saveAndFlush(FinanceSubAction.builder()
            .action(action)
            .code(code)
            .name(request.name().trim())
            .description(normalize(request.description()))
            .balance(FinanceMapper.ZERO)
            .committedAmount(FinanceMapper.ZERO)
            .liquidatedAmount(FinanceMapper.ZERO)
            .paidAmount(FinanceMapper.ZERO)
            .isActive(request.isActive() == null || request.isActive())
            .createdBy(user)
            .build());
        return mapper.subAction(sub, action, FinanceMapper.SubValues.current(sub));
    }

    @Transactional
    public FinanceSubActionResponse update(UUID id, FinanceSubActionRequest request) {
        access.requireUpdate();
        FinanceSubAction sub = getSubActionById(id);
        String code = request.code().trim();
        if (subActionRepository.existsByCodeIgnoreCaseAndIdNot(code, id)) {
            throw new ConflictException("Já existe uma subação com o código: " + code);
        }

        sub.setCode(code);
        sub.setName(request.name().trim());
        sub.setDescription(normalize(request.description()));
        if (request.isActive() != null) {
            sub.setIsActive(request.isActive());
        }
        subActionRepository.saveAndFlush(sub);
        return mapper.currentSubAction(sub);
    }

    @Transactional
    public void delete(UUID id) {
        access.requireDelete();
        FinanceSubAction sub = getSubActionById(id);
        if (entryRepository.existsBySubActionId(id)) {
            throw new ConflictException("A subação " + sub.getCode() + " tem operações no histórico e não pode ser "
                + "excluída. Desative-a com isActive: false");
        }
        subActionRepository.delete(sub);
    }
}
