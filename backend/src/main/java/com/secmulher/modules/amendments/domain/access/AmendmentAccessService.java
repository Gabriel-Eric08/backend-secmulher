package com.secmulher.modules.amendments.domain.access;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.secmulher.modules.amendments.infra.entities.Amendment;
import com.secmulher.modules.amendments.infra.entities.AmendmentSectorMember;
import com.secmulher.modules.amendments.infra.repositories.AmendmentSectorMemberRepository;
import com.secmulher.modules.core.domain.access.AccessControlService;
import com.secmulher.modules.core.domain.access.CategoryAccess;
import com.secmulher.modules.core.domain.exceptions.ForbiddenException;
import com.secmulher.modules.core.infra.entities.User;

// Acesso ao módulo de emendas pela matriz de permissões do core: vale a permissão do usuário na
// categoria nativa cuja table_reference é 'amendments' (administradores têm acesso total).
//   ler     -> emendas, pendências, linha do tempo e setores
//   criar   -> cadastrar e importar emendas; cadastrar setores
//   editar  -> alterar campos, registrar atividades e pendências, concluir, reabrir e cancelar
//   excluir -> excluir emendas e setores
// Escopo por setor: a técnica vinculada a um setor (amendment_sector_members) só alcança as emendas
// do próprio setor e as que estão sob sua responsabilidade. Administradores e usuários sem setor
// alcançam todas.
@Service
public class AmendmentAccessService {
    public static final String TABLE_REFERENCE = "amendments";

    final AccessControlService accessControl;
    final AmendmentSectorMemberRepository memberRepository;

    public AmendmentAccessService(AccessControlService accessControl, AmendmentSectorMemberRepository memberRepository) {
        this.accessControl = accessControl;
        this.memberRepository = memberRepository;
    }

    @Transactional(readOnly = true)
    public CategoryAccess access() {
        return accessControl.forNativeTable(TABLE_REFERENCE);
    }

    public User requireUser() {
        return accessControl.requireUser();
    }

    public void requireRead() {
        access().requireRead();
    }

    public User requireCreate() {
        access().requireCreate();
        return accessControl.requireUser();
    }

    public User requireUpdate() {
        access().requireUpdate();
        return accessControl.requireUser();
    }

    public void requireDelete() {
        access().requireDelete();
    }

    // Setor da técnica atual (vazio para administradores, que nunca são restritos)
    @Transactional(readOnly = true)
    public AmendmentScope scope() {
        User user = accessControl.requireUser();
        if (accessControl.isAdmin()) {
            return new AmendmentScope(user.getId(), null);
        }
        return new AmendmentScope(user.getId(),
            memberRepository.findByUserId(user.getId()).map(AmendmentSectorMember::getSector).orElse(null));
    }

    public void requireInScope(Amendment amendment) {
        requireInScope(scope(), amendment);
    }

    public static void requireInScope(AmendmentScope scope, Amendment amendment) {
        if (!scope.covers(amendment)) {
            throw new ForbiddenException("Esta emenda é de outro setor. Você só acessa as emendas do setor "
                + scope.sector().getName() + " e as que estão sob sua responsabilidade");
        }
    }

    // Gerenciar as técnicas dos setores é da coordenação: exige edição e não estar vinculado a um setor
    public User requireSectorManagement() {
        User user = requireUpdate();
        if (scope().restricted()) {
            throw new ForbiddenException("Só a coordenação (usuários sem setor) gerencia as técnicas dos setores");
        }
        return user;
    }
}
