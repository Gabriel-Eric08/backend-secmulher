package com.secmulher.modules.finance.controllers;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.secmulher.modules.finance.domain.dtos.FinanceOperationDtos.SubActionHistoryItem;
import com.secmulher.modules.finance.domain.dtos.FinanceSubActionDtos.FinanceSubActionRequest;
import com.secmulher.modules.finance.domain.dtos.FinanceSubActionDtos.FinanceSubActionResponse;
import com.secmulher.modules.finance.domain.services.FinanceQueryService;
import com.secmulher.modules.finance.domain.services.FinanceSubActionService;

import jakarta.validation.Valid;

// Subações são criadas em POST /finance/actions/{id}/sub-actions
@RestController
@RequestMapping("/finance/sub-actions")
public class FinanceSubActionController {
    final FinanceSubActionService subActionService;
    final FinanceQueryService queryService;

    public FinanceSubActionController(FinanceSubActionService subActionService, FinanceQueryService queryService) {
        this.subActionService = subActionService;
        this.queryService = queryService;
    }

    @GetMapping("/{id}")
    public FinanceSubActionResponse findById(@PathVariable UUID id,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate date) {
        return queryService.subAction(id, date);
    }

    @GetMapping("/{id}/history")
    public List<SubActionHistoryItem> history(@PathVariable UUID id,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate to) {
        return queryService.subActionHistory(id, from, to);
    }

    @PutMapping("/{id}")
    public FinanceSubActionResponse update(@PathVariable UUID id, @RequestBody @Valid FinanceSubActionRequest request) {
        return subActionService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        subActionService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
