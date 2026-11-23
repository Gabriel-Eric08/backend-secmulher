package com.secmulher.modules.amendments.domain.services;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.secmulher.modules.amendments.domain.access.AmendmentAccessService;
import com.secmulher.modules.amendments.domain.dtos.AmendmentSectorDtos.SectorRequest;
import com.secmulher.modules.amendments.domain.dtos.AmendmentSectorDtos.SectorResponse;
import com.secmulher.modules.amendments.infra.entities.AmendmentSector;
import com.secmulher.modules.amendments.infra.repositories.AmendmentRepository;
import com.secmulher.modules.amendments.infra.repositories.AmendmentSectorRepository;
import com.secmulher.modules.core.domain.exceptions.ConflictException;
import com.secmulher.modules.core.domain.exceptions.NotFoundException;

// Setores responsáveis por emendas. Nome único sem diferenciar maiúsculas
@Service
public class AmendmentSectorService {
    final AmendmentSectorRepository sectorRepository;
    final AmendmentRepository amendmentRepository;
    final AmendmentAccessService access;

    public AmendmentSectorService(AmendmentSectorRepository sectorRepository, AmendmentRepository amendmentRepository,
            AmendmentAccessService access) {
        this.sectorRepository = sectorRepository;
        this.amendmentRepository = amendmentRepository;
        this.access = access;
    }

    AmendmentSector getSectorById(UUID id) {
        return sectorRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Setor não encontrado: " + id));
    }

    @Transactional(readOnly = true)
    public List<SectorResponse> findAll() {
        access.requireRead();
        return sectorRepository.findAllByOrderByNameAsc().stream().map(AmendmentMapper::sector).toList();
    }

    @Transactional(readOnly = true)
    public SectorResponse findById(UUID id) {
        access.requireRead();
        return AmendmentMapper.sector(getSectorById(id));
    }

    @Transactional
    public SectorResponse create(SectorRequest request) {
        access.requireCreate();
        String name = request.name().trim();
        if (sectorRepository.existsByNameIgnoreCase(name)) {
            throw new ConflictException("Já existe um setor chamado " + name);
        }
        AmendmentSector sector = AmendmentSector.builder()
            .name(name)
            .description(AmendmentMapper.normalize(request.description()))
            .isActive(request.isActive() == null || request.isActive())
            .build();
        return AmendmentMapper.sector(sectorRepository.saveAndFlush(sector));
    }

    @Transactional
    public SectorResponse update(UUID id, SectorRequest request) {
        access.requireUpdate();
        AmendmentSector sector = getSectorById(id);
        String name = request.name().trim();
        if (sectorRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new ConflictException("Já existe um setor chamado " + name);
        }
        sector.setName(name);
        sector.setDescription(AmendmentMapper.normalize(request.description()));
        if (request.isActive() != null) {
            sector.setIsActive(request.isActive());
        }
        return AmendmentMapper.sector(sectorRepository.saveAndFlush(sector));
    }

    @Transactional
    public void delete(UUID id) {
        access.requireDelete();
        AmendmentSector sector = getSectorById(id);
        if (amendmentRepository.existsBySectorId(id)) {
            throw new ConflictException("O setor " + sector.getName() + " tem emendas e não pode ser excluído. "
                + "Desative-o com isActive: false");
        }
        sectorRepository.delete(sector);
    }
}
