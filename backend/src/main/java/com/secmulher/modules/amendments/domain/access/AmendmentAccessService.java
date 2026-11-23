package com.secmulher.modules.amendments.domain.access;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.secmulher.modules.core.domain.access.AccessControlService;
import com.secmulher.modules.core.domain.access.CategoryAccess;
import com.secmulher.modules.core.infra.entities.User;

// Acesso ao módulo de emendas pela matriz de permissões do core: vale a permissão do usuário na
// categoria nativa cuja table_reference é 'amendments' (administradores têm acesso total).
//   ler     -> emendas, pendências, linha do tempo e setores
//   criar   -> cadastrar e importar emendas; cadastrar setores
//   editar  -> alterar campos, registrar atividades e pendências, concluir, reabrir e cancelar
//   excluir -> excluir emendas e setores
@Service
public class AmendmentAccessService {
    public static final String TABLE_REFERENCE = "amendments";

    final AccessControlService accessControl;

    public AmendmentAccessService(AccessControlService accessControl) {
        this.accessControl = accessControl;
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
}
