package com.secmulher.modules.amendments.domain.services;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.secmulher.modules.amendments.domain.access.AmendmentAccessService;
import com.secmulher.modules.amendments.domain.access.AmendmentScope;
import com.secmulher.modules.amendments.domain.dtos.AmendmentSectorDtos.AccessResponse;
import com.secmulher.modules.amendments.domain.dtos.AmendmentSectorDtos.MemberResponse;
import com.secmulher.modules.amendments.domain.dtos.AmendmentSectorDtos.SectorRequest;
import com.secmulher.modules.amendments.domain.dtos.AmendmentSectorDtos.SectorResponse;
import com.secmulher.modules.amendments.domain.dtos.AmendmentSectorDtos.SectorSummary;
import com.secmulher.modules.amendments.infra.entities.AmendmentSector;
import com.secmulher.modules.amendments.infra.entities.AmendmentSectorMember;
import com.secmulher.modules.amendments.infra.repositories.AmendmentRepository;
import com.secmulher.modules.amendments.infra.repositories.AmendmentSectorMemberRepository;
import com.secmulher.modules.amendments.infra.repositories.AmendmentSectorRepository;
import com.secmulher.modules.core.domain.access.CategoryAccess;
import com.secmulher.modules.core.domain.exceptions.ConflictException;
import com.secmulher.modules.core.domain.exceptions.NotFoundException;
import com.secmulher.modules.core.infra.entities.User;
import com.secmulher.modules.core.infra.repositories.UserRepository;

// Setores responsáveis por emendas (nome único sem diferenciar maiúsculas) e suas técnicas.
// Cada usuário pertence a no máximo um setor: vincular a outro setor move a técnica.
// Vincular e desvincular técnicas é da coordenação (edição e sem setor próprio).
@Service
public class AmendmentSectorService {
    final AmendmentSectorRepository sectorRepository;
    final AmendmentSectorMemberRepository memberRepository;
    final AmendmentRepository amendmentRepository;
    final UserRepository userRepository;
    final AmendmentAccessService access;

    public AmendmentSectorService(AmendmentSectorRepository sectorRepository,
            AmendmentSectorMemberRepository memberRepository, AmendmentRepository amendmentRepository,
            UserRepository userRepository, AmendmentAccessService access) {
        this.sectorRepository = sectorRepository;
        this.memberRepository = memberRepository;
        this.amendmentRepository = amendmentRepository;
        this.userRepository = userRepository;
        this.access = access;
    }

    AmendmentSector getSectorById(UUID id) {
        return sectorRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Setor não encontrado: " + id));
    }

    // Permissões do usuário logado e o setor ao qual ele está restrito (para o front montar a tela)
    @Transactional(readOnly = true)
    public AccessResponse myAccess() {
        CategoryAccess categoryAccess = access.access();
        AmendmentScope scope = access.scope();
        SectorSummary sector = scope.restricted()
            ? new SectorSummary(scope.sector().getId(), scope.sector().getName())
            : null;
        return new AccessResponse(categoryAccess.canRead(), categoryAccess.canCreate(), categoryAccess.canUpdate(),
            categoryAccess.canDelete(), scope.restricted(), sector, categoryAccess.canUpdate() && !scope.restricted());
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

    // Excluir o setor desvincula as técnicas (em cascata)
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

    // ---------------------------------------------------------------- técnicas do setor

    @Transactional(readOnly = true)
    public List<MemberResponse> members(UUID sectorId) {
        access.requireRead();
        getSectorById(sectorId);
        return memberRepository.findBySectorId(sectorId).stream().map(AmendmentSectorService::member).toList();
    }

    // Vincula a técnica ao setor; se ela já estava em outro setor, passa para este
    @Transactional
    public MemberResponse addMember(UUID sectorId, UUID userId) {
        User manager = access.requireSectorManagement();
        AmendmentSector sector = getSectorById(sectorId);
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new NotFoundException("Usuário não encontrado: " + userId));
        AmendmentSectorMember member = memberRepository.findByUserId(userId)
            .orElseGet(() -> AmendmentSectorMember.builder().user(user).build());
        member.setSector(sector);
        member.setCreatedBy(manager);
        return member(memberRepository.saveAndFlush(member));
    }

    @Transactional
    public void removeMember(UUID sectorId, UUID userId) {
        access.requireSectorManagement();
        getSectorById(sectorId);
        AmendmentSectorMember member = memberRepository.findByUserId(userId)
            .filter(m -> m.getSector().getId().equals(sectorId))
            .orElseThrow(() -> new NotFoundException("O usuário " + userId + " não é técnica deste setor"));
        memberRepository.delete(member);
    }

    private static MemberResponse member(AmendmentSectorMember member) {
        User user = member.getUser();
        return new MemberResponse(user.getId(), user.getName(), user.getEmail(), user.getIsActive(),
            member.getCreatedAt());
    }
}
