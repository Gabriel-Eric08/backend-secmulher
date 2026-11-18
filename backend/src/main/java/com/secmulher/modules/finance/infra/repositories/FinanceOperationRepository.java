package com.secmulher.modules.finance.infra.repositories;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.secmulher.modules.finance.infra.entities.FinanceOperation;

public interface FinanceOperationRepository
        extends JpaRepository<FinanceOperation, UUID>, JpaSpecificationExecutor<FinanceOperation> {
}
