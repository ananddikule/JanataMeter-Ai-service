package com.janmeterai.service.janmeteraiservice.service;

import org.springframework.stereotype.Service;

@Service
public class PoliticianResearchService {
    private final OpenAiWebResearchService openAi;

    public PoliticianResearchService(OpenAiWebResearchService openAi) {
        this.openAi = openAi;
    }

    public PoliticianResearchProvider.ResearchResult research(String prompt, String subject) {
        return openAi.research(prompt, subject);
    }
}
