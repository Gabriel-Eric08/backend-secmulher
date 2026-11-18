package com.secmulher.modules.finance.controllers;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.secmulher.modules.finance.domain.dtos.FinanceDashboardDtos.DashboardResponse;
import com.secmulher.modules.finance.domain.services.FinanceQueryService;

@RestController
@RequestMapping("/finance")
public class FinanceDashboardController {
    final FinanceQueryService queryService;

    public FinanceDashboardController(FinanceQueryService queryService) {
        this.queryService = queryService;
    }

    // ?date=2026-11-10 devolve como tudo estava no fim daquele dia
    @GetMapping("/dashboard")
    public DashboardResponse dashboard(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return queryService.dashboard(date);
    }
}
