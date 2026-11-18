package com.secmulher.modules.finance.controllers;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.secmulher.modules.finance.domain.dtos.FinanceActionDtos.FinanceActionCreateRequest;
import com.secmulher.modules.finance.domain.dtos.FinanceActionDtos.FinanceActionResponse;
import com.secmulher.modules.finance.domain.dtos.FinanceActionDtos.FinanceActionUpdateRequest;
import com.secmulher.modules.finance.domain.dtos.FinanceOperationDtos.ActionHistoryItem;
import com.secmulher.modules.finance.domain.dtos.FinanceSubActionDtos.FinanceSubActionRequest;
import com.secmulher.modules.finance.domain.dtos.FinanceSubActionDtos.FinanceSubActionResponse;
import com.secmulher.modules.finance.domain.services.FinanceActionService;
import com.secmulher.modules.finance.domain.services.FinanceQueryService;
import com.secmulher.modules.finance.domain.services.FinanceSubActionService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/finance/actions")
public class FinanceActionController {
    final FinanceActionService actionService;
    final FinanceSubActionService subActionService;
    final FinanceQueryService queryService;

    public FinanceActionController(FinanceActionService actionService, FinanceSubActionService subActionService,
            FinanceQueryService queryService) {
        this.actionService = actionService;
        this.subActionService = subActionService;
        this.queryService = queryService;
    }

    @GetMapping
    public List<FinanceActionResponse> findAll(@RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate date) {
        return queryService.dashboard(date).actions();
    }

    @GetMapping("/{id}")
    public FinanceActionResponse findById(@PathVariable UUID id,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate date) {
        return queryService.action(id, date);
    }

    @GetMapping("/{id}/history")
    public List<ActionHistoryItem> history(@PathVariable UUID id,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate to) {
        return queryService.actionHistory(id, from, to);
    }

    @PostMapping
    public ResponseEntity<FinanceActionResponse> create(@RequestBody @Valid FinanceActionCreateRequest request) {
        FinanceActionResponse created = actionService.create(request);
        return ResponseEntity.created(URI.create("/finance/actions/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    public FinanceActionResponse update(@PathVariable UUID id, @RequestBody @Valid FinanceActionUpdateRequest request) {
        return actionService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        actionService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/sub-actions")
    public ResponseEntity<FinanceSubActionResponse> createSubAction(@PathVariable UUID id,
            @RequestBody @Valid FinanceSubActionRequest request) {
        FinanceSubActionResponse created = subActionService.create(id, request);
        return ResponseEntity.created(URI.create("/finance/sub-actions/" + created.id())).body(created);
    }
}
