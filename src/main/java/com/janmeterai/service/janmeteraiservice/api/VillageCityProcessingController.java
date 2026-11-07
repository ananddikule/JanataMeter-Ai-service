package com.janmeterai.service.janmeteraiservice.api;

import com.janmeterai.service.janmeteraiservice.service.VillageCityProcessingService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/ai")
public class VillageCityProcessingController {

    private final VillageCityProcessingService processingService;

    public VillageCityProcessingController(VillageCityProcessingService processingService) {
        this.processingService = processingService;
    }

    @PostMapping(path = "/process-talukas", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter processTalukas() {
        return processingService.startTalukaProcessing();
    }

    @PostMapping(path = {"/process-villages-pincodes", "/process-village-cities"},
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter processVillageCities() {
        return processingService.startVillageCityProcessing();
    }
}
