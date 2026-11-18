package com.secmulher.modules.finance.domain.dtos;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.secmulher.modules.finance.domain.dtos.FinanceActionDtos.FinanceActionResponse;

public final class FinanceDashboardDtos {
    private FinanceDashboardDtos() {}

    // date nulo = situação atual; com data, os valores são os do fim daquele dia
    public record DashboardResponse(
        LocalDate date,
        BigDecimal totalAmount,
        BigDecimal distributedAmount,
        BigDecimal undistributedAmount,
        BigDecimal committedAmount,
        BigDecimal liquidatedAmount,
        BigDecimal paidAmount,
        BigDecimal availableAmount,
        List<FinanceActionResponse> actions
    ) {}
}
