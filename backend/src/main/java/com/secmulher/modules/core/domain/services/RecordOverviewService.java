package com.secmulher.modules.core.domain.services;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.secmulher.modules.core.domain.dtos.RecordOverviewDto;
import com.secmulher.modules.core.domain.enums.RecordType;

@Service
public class RecordOverviewService {
    final ClientService clientService;
    final DynamicEntityService dynamicEntityService;
    final ActivityService activityService;
    final FileService fileService;

    public RecordOverviewService(ClientService clientService, DynamicEntityService dynamicEntityService,
            ActivityService activityService, FileService fileService) {
        this.clientService = clientService;
        this.dynamicEntityService = dynamicEntityService;
        this.activityService = activityService;
        this.fileService = fileService;
    }

    @Transactional(readOnly = true)
    public RecordOverviewDto getOverview(RecordType type, UUID recordId) {
        Object record = switch (type) {
            case CLIENT -> clientService.findById(recordId);
            case DYNAMIC_ENTITY -> dynamicEntityService.findById(recordId);
        };

        return new RecordOverviewDto(
            type.getPath(),
            record,
            activityService.getTimeline(type, recordId),
            fileService.findAll(type, recordId)
        );
    }
}
