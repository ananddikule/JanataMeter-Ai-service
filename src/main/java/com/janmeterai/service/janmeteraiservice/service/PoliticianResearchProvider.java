package com.janmeterai.service.janmeteraiservice.service;

import java.util.List;

public interface PoliticianResearchProvider {
    ResearchResult research(String prompt, String subject);

    record ResearchResult(String text, List<String> sourceUrls) {}
}
