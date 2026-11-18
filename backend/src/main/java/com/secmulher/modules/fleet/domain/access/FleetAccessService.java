package com.secmulher.modules.fleet.domain.access;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.secmulher.modules.core.domain.access.AccessControlService;
import com.secmulher.modules.core.domain.access.CategoryAccess;
import com.secmulher.modules.core.domain.dtos.ClientDtos;
import com.secmulher.modules.core.domain.enums.RecordType;
import com.secmulher.modules.core.infra.entities.User;

// Acesso ao módulo de frotas pela matriz de permissões do core: vale a permissão do usuário na
// categoria nativa cuja table_reference é 'fleet_trips' (administradores têm acesso total).
//   ler     -> viagens, agenda, disponibilidade, motoristas e veículos
//   criar   -> cadastrar viagens, motoristas e veículos
//   editar  -> alterar, iniciar, concluir e cancelar viagens; alterar motoristas e veículos
//   excluir -> excluir motoristas e veículos sem viagens
// O motorista vinculado a um usuário vê a própria agenda sem precisar de permissão (/fleet/trips/mine).
@Service
public class FleetAccessService {
    public static final String TABLE_REFERENCE = "fleet_trips";

    final AccessControlService accessControl;

    public FleetAccessService(AccessControlService accessControl) {
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

    // Atendidas são dados sensíveis: o nome de uma atendida participante só aparece para quem
    // pode ler a coluna 'nome' na categoria de clientes
    @Transactional(readOnly = true)
    public boolean canSeeClientNames() {
        CategoryAccess clients = accessControl.forNativeTable(RecordType.CLIENT.getTableName());
        return clients.canRead() && clients.canReadColumn(ClientDtos.NOME);
    }
}
