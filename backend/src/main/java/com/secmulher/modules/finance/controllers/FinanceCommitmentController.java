package com.secmulher.modules.finance.controllers;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.secmulher.modules.finance.domain.dtos.FinanceCommitmentDtos.CommitmentCreateRequest;
import com.secmulher.modules.finance.domain.dtos.FinanceCommitmentDtos.CommitmentDetailResponse;
import com.secmulher.modules.finance.domain.dtos.FinanceCommitmentDtos.CommitmentMovementRequest;
import com.secmulher.modules.finance.domain.dtos.FinanceCommitmentDtos.CommitmentResponse;
import com.secmulher.modules.finance.domain.dtos.FinanceCommitmentDtos.CommitmentStatus;
import com.secmulher.modules.finance.domain.dtos.FinanceCommitmentDtos.CommitmentUpdateRequest;
import com.secmulher.modules.finance.domain.services.FinanceCommitmentService;

import jakarta.validation.Valid;

// Empenhos e a execução da despesa (liquidação, pagamento e anulação)
@RestController
@RequestMapping("/finance/commitments")
public class FinanceCommitmentController {
    final FinanceCommitmentService commitmentService;

    public FinanceCommitmentController(FinanceCommitmentService commitmentService) {
        this.commitmentService = commitmentService;
    }

    @GetMapping
    public List<CommitmentResponse> findAll(
            @RequestParam(required = false) UUID actionId,
            @RequestParam(required = false) UUID subActionId,
            @RequestParam(required = false) String creditor,
            @RequestParam(required = false) CommitmentStatus status) {
        return commitmentService.findAll(actionId, subActionId, creditor, status);
    }

    @GetMapping("/{id}")
    public CommitmentDetailResponse findById(@PathVariable UUID id) {
        return commitmentService.findById(id);
    }

    @PostMapping
    public ResponseEntity<CommitmentDetailResponse> create(@RequestBody @Valid CommitmentCreateRequest request) {
        CommitmentDetailResponse created = commitmentService.create(request);
        return ResponseEntity.created(URI.create("/finance/commitments/" + created.commitment().id())).body(created);
    }

    @PutMapping("/{id}")
    public CommitmentResponse update(@PathVariable UUID id, @RequestBody @Valid CommitmentUpdateRequest request) {
        return commitmentService.update(id, request);
    }

    @PostMapping("/{id}/liquidations")
    public CommitmentDetailResponse liquidate(@PathVariable UUID id, @RequestBody @Valid CommitmentMovementRequest request) {
        return commitmentService.liquidate(id, request);
    }

    @PostMapping("/{id}/payments")
    public CommitmentDetailResponse pay(@PathVariable UUID id, @RequestBody @Valid CommitmentMovementRequest request) {
        return commitmentService.pay(id, request);
    }

    @PostMapping("/{id}/cancellations")
    public CommitmentDetailResponse cancel(@PathVariable UUID id, @RequestBody @Valid CommitmentMovementRequest request) {
        return commitmentService.cancel(id, request);
    }
}
