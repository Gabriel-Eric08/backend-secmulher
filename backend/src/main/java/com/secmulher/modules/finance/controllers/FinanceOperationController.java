package com.secmulher.modules.finance.controllers;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.secmulher.modules.finance.domain.dtos.FinanceOperationDtos.DistributionRequest;
import com.secmulher.modules.finance.domain.dtos.FinanceOperationDtos.OperationResponse;
import com.secmulher.modules.finance.domain.dtos.FinanceOperationDtos.TacRequest;
import com.secmulher.modules.finance.domain.dtos.FinanceOperationDtos.TransferRequest;
import com.secmulher.modules.finance.domain.services.FinanceOperationService;
import com.secmulher.modules.finance.infra.entities.enums.FinanceOperationMethod;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/finance/operations")
public class FinanceOperationController {
    final FinanceOperationService operationService;

    public FinanceOperationController(FinanceOperationService operationService) {
        this.operationService = operationService;
    }

    // Histórico geral, mais recente primeiro; todos os filtros são opcionais
    @GetMapping
    public List<OperationResponse> findAll(
            @RequestParam(required = false) UUID actionId,
            @RequestParam(required = false) UUID subActionId,
            @RequestParam(required = false) UUID commitmentId,
            @RequestParam(required = false) FinanceOperationMethod method,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate to) {
        return operationService.findAll(actionId, subActionId, commitmentId, method, from, to);
    }

    @GetMapping("/{id}")
    public OperationResponse findById(@PathVariable UUID id) {
        return operationService.findById(id);
    }

    // Método A
    @PostMapping("/sub-action-transfers")
    public ResponseEntity<OperationResponse> transferBetweenSubActions(@RequestBody @Valid TransferRequest request) {
        return created(operationService.transferBetweenSubActions(request));
    }

    // Método B
    @PostMapping("/action-transfers")
    public ResponseEntity<OperationResponse> transferBetweenActions(@RequestBody @Valid TransferRequest request) {
        return created(operationService.transferBetweenActions(request));
    }

    // Método C
    @PostMapping("/tac")
    public ResponseEntity<OperationResponse> tac(@RequestBody @Valid TacRequest request) {
        return created(operationService.tac(request));
    }

    @PostMapping("/distributions")
    public ResponseEntity<OperationResponse> distribute(@RequestBody @Valid DistributionRequest request) {
        return created(operationService.distribute(request));
    }

    private static ResponseEntity<OperationResponse> created(OperationResponse operation) {
        return ResponseEntity.created(URI.create("/finance/operations/" + operation.id())).body(operation);
    }
}
