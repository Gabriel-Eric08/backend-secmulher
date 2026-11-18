package com.secmulher.modules.finance.domain.access;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.secmulher.modules.core.domain.access.AccessControlService;
import com.secmulher.modules.core.domain.access.CategoryAccess;
import com.secmulher.modules.core.infra.entities.User;

// Acesso ao módulo financeiro pela matriz de permissões do core: vale a permissão do usuário
// na categoria nativa cuja table_reference é 'finance_actions' (administradores têm acesso total).
//   ler     -> dashboard, saldos em uma data, históricos e operações
//   criar   -> cadastrar ações e subações
//   editar  -> alterar ações e subações e realizar operações (transferências, TAC, distribuição)
//   excluir -> excluir ações e subações sem movimentação
@Service
public class FinanceAccessService {
    public static final String TABLE_REFERENCE = "finance_actions";

    final AccessControlService accessControl;

    public FinanceAccessService(AccessControlService accessControl) {
        this.accessControl = accessControl;
    }

    @Transactional(readOnly = true)
    public CategoryAccess access() {
        return accessControl.forNativeTable(TABLE_REFERENCE);
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
