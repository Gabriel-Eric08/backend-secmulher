package com.secmulher.modules.amendments.controllers;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
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

import com.secmulher.modules.amendments.domain.dtos.AmendmentDtos.ActivityResponse;
import com.secmulher.modules.amendments.domain.dtos.AmendmentDtos.AmendmentFilter;
import com.secmulher.modules.amendments.domain.dtos.AmendmentDtos.AmendmentOptionsResponse;
import com.secmulher.modules.amendments.domain.dtos.AmendmentDtos.AmendmentRequest;
import com.secmulher.modules.amendments.domain.dtos.AmendmentDtos.AmendmentResponse;
import com.secmulher.modules.amendments.domain.dtos.AmendmentDtos.AmendmentSort;
import com.secmulher.modules.amendments.domain.dtos.AmendmentDtos.AmendmentSummaryResponse;
import com.secmulher.modules.amendments.domain.dtos.AmendmentDtos.CompleteRequest;
import com.secmulher.modules.amendments.domain.dtos.AmendmentDtos.NoteRequest;
import com.secmulher.modules.amendments.domain.dtos.AmendmentDtos.PendencyRequest;
import com.secmulher.modules.amendments.domain.dtos.AmendmentDtos.PendencyResponse;
import com.secmulher.modules.amendments.domain.dtos.AmendmentDtos.ReasonRequest;
import com.secmulher.modules.amendments.domain.dtos.AmendmentDtos.ResolvePendencyRequest;
import com.secmulher.modules.amendments.domain.dtos.AmendmentSectorDtos.AccessResponse;
import com.secmulher.modules.amendments.domain.services.AmendmentSectorService;
import com.secmulher.modules.amendments.domain.services.AmendmentService;
import com.secmulher.modules.amendments.domain.services.AmendmentTrackingService;
import com.secmulher.modules.amendments.infra.entities.enums.AmendmentStatus;
import com.secmulher.modules.amendments.infra.entities.enums.PendencyKind;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/amendments")
public class AmendmentController {
    final AmendmentService amendmentService;
    final AmendmentTrackingService trackingService;
    final AmendmentSectorService sectorService;

    public AmendmentController(AmendmentService amendmentService, AmendmentTrackingService trackingService,
            AmendmentSectorService sectorService) {
        this.amendmentService = amendmentService;
        this.trackingService = trackingService;
        this.sectorService = sectorService;
    }

    // ---------------------------------------------------------------- consultas

    // Filtros opcionais, combinados com "e". status aceita vários (?status=WAITING&status=BLOCKED)
    @GetMapping
    public List<AmendmentResponse> search(
            @RequestParam(required = false) List<AmendmentStatus> status,
            @RequestParam(required = false) Boolean open,
            @RequestParam(required = false) Boolean pending,
            @RequestParam(required = false) PendencyKind pendencyKind,
            @RequestParam(required = false) Integer idleDays,
            @RequestParam(required = false) Integer deadlineWithinDays,
            @RequestParam(required = false) Boolean expired,
            @RequestParam(required = false) UUID sectorId,
            @RequestParam(required = false) UUID responsibleUserId,
            @RequestParam(required = false) Boolean mine,
            @RequestParam(required = false) Integer exerciseYear,
            @RequestParam(required = false) String cycle,
            @RequestParam(required = false) String parliamentarian,
            @RequestParam(required = false) String municipality,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) AmendmentSort sort) {
        AmendmentFilter filter = new AmendmentFilter(status, open, pending, pendencyKind, idleDays, deadlineWithinDays,
            expired, sectorId, responsibleUserId, mine, exerciseYear, cycle, parliamentarian, municipality, q);
        return amendmentService.search(filter, sort);
    }

    // Contadores para o painel da aba
    @GetMapping("/summary")
    public AmendmentSummaryResponse summary(
            @RequestParam(required = false) Integer exerciseYear,
            @RequestParam(required = false) UUID sectorId,
            @RequestParam(required = false) UUID responsibleUserId,
            @RequestParam(required = false) Boolean mine,
            @RequestParam(required = false) Integer idleDays) {
        return amendmentService.summary(exerciseYear, sectorId, responsibleUserId, mine, idleDays);
    }

    // Valores já usados (anos, ciclos, parlamentares...), para montar os filtros
    @GetMapping("/options")
    public AmendmentOptionsResponse options() {
        return amendmentService.options();
    }

    // Permissões do usuário logado no módulo e o setor ao qual está restrito
    @GetMapping("/access")
    public AccessResponse access() {
        return sectorService.myAccess();
    }

    @GetMapping("/{id}")
    public AmendmentResponse findById(@PathVariable UUID id) {
        return amendmentService.findById(id);
    }

    @GetMapping("/{id}/timeline")
    public List<ActivityResponse> timeline(@PathVariable UUID id) {
        return trackingService.timeline(id);
    }

    @GetMapping("/{id}/pendencies")
    public List<PendencyResponse> pendencies(@PathVariable UUID id, @RequestParam(required = false) Boolean open) {
        return trackingService.pendencies(id, open);
    }

    // ---------------------------------------------------------------- cadastro

    @PostMapping
    public ResponseEntity<AmendmentResponse> create(@RequestBody @Valid AmendmentRequest request) {
        AmendmentResponse created = amendmentService.create(request);
        return ResponseEntity.created(URI.create("/amendments/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    public AmendmentResponse update(@PathVariable UUID id, @RequestBody @Valid AmendmentRequest request) {
        return amendmentService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        amendmentService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // ---------------------------------------------------------------- acompanhamento

    @PostMapping("/{id}/notes")
    public AmendmentResponse addNote(@PathVariable UUID id, @RequestBody @Valid NoteRequest request) {
        return trackingService.addNote(id, request);
    }

    @PostMapping("/{id}/pendencies")
    public ResponseEntity<PendencyResponse> openPendency(@PathVariable UUID id,
            @RequestBody @Valid PendencyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(trackingService.openPendency(id, request));
    }

    @PostMapping("/{id}/pendencies/{pendencyId}/resolve")
    public PendencyResponse resolvePendency(@PathVariable UUID id, @PathVariable UUID pendencyId,
            @RequestBody @Valid ResolvePendencyRequest request) {
        return trackingService.resolvePendency(id, pendencyId, request);
    }

    @PostMapping("/{id}/complete")
    public AmendmentResponse complete(@PathVariable UUID id, @RequestBody @Valid CompleteRequest request) {
        return trackingService.complete(id, request);
    }

    @PostMapping("/{id}/cancel")
    public AmendmentResponse cancel(@PathVariable UUID id, @RequestBody @Valid ReasonRequest request) {
        return trackingService.cancel(id, request);
    }

    @PostMapping("/{id}/reopen")
    public AmendmentResponse reopen(@PathVariable UUID id, @RequestBody @Valid ReasonRequest request) {
        return trackingService.reopen(id, request);
    }
}
