package com.janmeterai.service.janmeteraiservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.janmeterai.service.janmeteraiservice.entity.JanmeterDistrict;
import com.janmeterai.service.janmeteraiservice.entity.JanmeterState;
import com.janmeterai.service.janmeteraiservice.entity.JanmeterTaluka;
import com.janmeterai.service.janmeteraiservice.entity.JanmeterVillageCity;
import com.janmeterai.service.janmeteraiservice.entity.JanmeterWard;
import com.janmeterai.service.janmeteraiservice.repository.JanmeterDistrictRepository;
import com.janmeterai.service.janmeteraiservice.repository.JanmeterStateRepository;
import com.janmeterai.service.janmeteraiservice.repository.JanmeterTalukaRepository;
import com.janmeterai.service.janmeteraiservice.repository.JanmeterVillageCityRepository;
import com.janmeterai.service.janmeteraiservice.repository.JanmeterWardRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class VillageCityProcessingService {

    private static final Logger logger = LoggerFactory.getLogger(VillageCityProcessingService.class);

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final JanmeterStateRepository stateRepository;
    private final JanmeterDistrictRepository districtRepository;
    private final JanmeterTalukaRepository talukaRepository;
    private final JanmeterVillageCityRepository villageCityRepository;
    private final JanmeterWardRepository wardRepository;
    private final Executor processingExecutor;
    private final AtomicBoolean processing = new AtomicBoolean();
    private volatile ProcessingProgress currentProgress;

    public VillageCityProcessingService(
            ChatClient.Builder chatClientBuilder,
            JanmeterStateRepository stateRepository,
            JanmeterDistrictRepository districtRepository,
            JanmeterTalukaRepository talukaRepository,
            JanmeterVillageCityRepository villageCityRepository,
            JanmeterWardRepository wardRepository,
            @Qualifier("villageCityProcessingExecutor") Executor processingExecutor) {
        this.chatClient = chatClientBuilder.build();
        this.stateRepository = stateRepository;
        this.districtRepository = districtRepository;
        this.talukaRepository = talukaRepository;
        this.villageCityRepository = villageCityRepository;
        this.wardRepository = wardRepository;
        this.processingExecutor = processingExecutor;
    }

    public SseEmitter startDistrictProcessing() {
        return startProcessing(this::runDistrictProcessing);
    }

    public SseEmitter startTalukaProcessing() {
        return startProcessing(this::runTalukaProcessing);
    }

    public SseEmitter startVillageCityProcessing() {
        return startProcessing(this::runVillageCityProcessing);
    }

    private SseEmitter startProcessing(java.util.function.Consumer<SseEmitter> processingTask) {
        if (!processing.compareAndSet(false, true)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Geographic processing is already running");
        }

        SseEmitter emitter = new SseEmitter(0L);
        try {
            processingExecutor.execute(() -> processingTask.accept(emitter));
        } catch (RuntimeException exception) {
            processing.set(false);
            throw exception;
        }
        return emitter;
    }

    private void runDistrictProcessing(SseEmitter emitter) {
        try {
            currentProgress = null;
            List<JanmeterState> states = stateRepository.findAllByOrderByIdAsc();
            if (states.isEmpty()) {
                throw new IllegalStateException("No states are available to process");
            }

            sendEvent(emitter, "started", new ProcessingProgress(
                    "started", states.get(0).getStateName(), null, null, null, null, null, 0, states.size(),
                    "Loaded " + states.size() + " states from the database"));

            for (int stateIndex = 0; stateIndex < states.size(); stateIndex++) {
                JanmeterState state = states.get(stateIndex);
                int percentage = stateIndex * 100 / states.size();
                sendProgress(emitter, "selected_state", state, null, null, null, null,
                        percentage, states.size(), "Selected state " + (stateIndex + 1) + " of "
                                + states.size() + ": " + state.getStateName());
                processState(emitter, state, percentage, states.size());
                int completedPercentage = (stateIndex + 1) * 100 / states.size();
                sendProgress(emitter, "state_completed", state, null, null, null, null,
                        completedPercentage, states.size(), "Finished state " + state.getStateName());
            }

            sendEvent(emitter, "complete", new ProcessingProgress(
                    "complete", null, null, null, null, null, null, 100, states.size(),
                    "Finished processing districts for all states"));
            emitter.complete();
        } catch (Exception exception) {
            handleFailure(emitter, exception);
        } finally {
            processing.set(false);
        }
    }

    private void runTalukaProcessing(SseEmitter emitter) {
        try {
            currentProgress = null;
            List<JanmeterState> states = stateRepository.findAllByOrderByIdAsc();
            if (states.isEmpty()) {
                throw new IllegalStateException("No states are available to process");
            }

            sendEvent(emitter, "started", new ProcessingProgress(
                    "started", states.get(0).getStateName(), null, null, null, null, null, 0, states.size(),
                    "Loaded " + states.size() + " states from the database"));

            for (int stateIndex = 0; stateIndex < states.size(); stateIndex++) {
                JanmeterState state = states.get(stateIndex);
                int percentage = stateIndex * 100 / states.size();
                sendProgress(emitter, "selected_state", state, null, null, null, null,
                        percentage, states.size(), "Selected state " + (stateIndex + 1) + " of "
                                + states.size() + ": " + state.getStateName());
                List<JanmeterDistrict> districts = districtRepository.findAllByStateId(state.getId());
                for (int districtIndex = 0; districtIndex < districts.size(); districtIndex++) {
                    JanmeterDistrict district = districts.get(districtIndex);
                    sendProgress(emitter, "selected_district", state, district, null, null, null,
                            percentage, states.size(), "Selected district " + (districtIndex + 1) + " of "
                                    + districts.size() + ": " + district.getDistrictName());
                    processDistrict(emitter, state, district, percentage, states.size());
                }
                int completedPercentage = (stateIndex + 1) * 100 / states.size();
                sendProgress(emitter, "state_completed", state, null, null, null, null,
                        completedPercentage, states.size(), "Finished taluka processing for "
                                + state.getStateName());
            }

            sendEvent(emitter, "complete", new ProcessingProgress(
                    "complete", null, null, null, null, null, null, 100, states.size(),
                    "Finished processing talukas for all stored districts"));
            emitter.complete();
        } catch (Exception exception) {
            handleFailure(emitter, exception);
        } finally {
            processing.set(false);
        }
    }

    private void runVillageCityProcessing(SseEmitter emitter) {
        try {
            currentProgress = null;
            List<JanmeterState> states = stateRepository.findAllByOrderByIdAsc();
            if (states.isEmpty()) {
                throw new IllegalStateException("No states are available to process");
            }

            sendEvent(emitter, "started", new ProcessingProgress(
                    "started", states.get(0).getStateName(), null, null, null, null, null, 0, states.size(),
                    "Loaded " + states.size() + " states from the database"));

            for (int stateIndex = 0; stateIndex < states.size(); stateIndex++) {
                JanmeterState state = states.get(stateIndex);
                int percentage = stateIndex * 100 / states.size();
                sendProgress(emitter, "selected_state", state, null, null, null, null,
                        percentage, states.size(), "Selected state " + (stateIndex + 1) + " of "
                                + states.size() + ": " + state.getStateName());
                for (JanmeterDistrict district : districtRepository.findAllByStateId(state.getId())) {
                    sendProgress(emitter, "selected_district", state, district, null, null, null,
                            percentage, states.size(), "Selected district " + district.getDistrictName());
                    for (JanmeterTaluka taluka : talukaRepository.findAllByDistrictId(district.getId())) {
                        sendProgress(emitter, "selected_taluka", state, district, taluka, null, null,
                                percentage, states.size(), "Selected taluka " + taluka.getTalukaName());
                        processTaluka(emitter, state, district, taluka, percentage, states.size());
                    }
                }
                int completedPercentage = (stateIndex + 1) * 100 / states.size();
                sendProgress(emitter, "state_completed", state, null, null, null, null,
                        completedPercentage, states.size(), "Finished village/city processing for "
                                + state.getStateName());
            }

            sendEvent(emitter, "complete", new ProcessingProgress(
                    "complete", null, null, null, null, null, null, 100, states.size(),
                    "Finished processing villages, PIN codes, and city wards for all states"));
            emitter.complete();
        } catch (Exception exception) {
            handleFailure(emitter, exception);
        } finally {
            processing.set(false);
        }
    }

    private void handleFailure(SseEmitter emitter, Exception exception) {
        ProcessingProgress lastProgress = currentProgress;
        String errorMessage = describeFailure(exception, lastProgress);
        if (hasQuotaFailure(exception)) {
            logger.warn("{}", errorMessage);
        } else {
            logger.error("{}", errorMessage, exception);
        }
        try {
            sendEvent(emitter, "error", new ProcessingProgress(
                    "error",
                    lastProgress == null ? null : lastProgress.stateName(),
                    lastProgress == null ? null : lastProgress.districtName(),
                    lastProgress == null ? null : lastProgress.talukaName(),
                    lastProgress == null ? null : lastProgress.villageCityName(),
                    lastProgress == null ? null : lastProgress.wardName(),
                    lastProgress == null ? null : lastProgress.pincode(),
                    lastProgress == null ? null : lastProgress.percentage(),
                    lastProgress == null ? null : lastProgress.totalStates(),
                    errorMessage));
        } catch (RuntimeException sendException) {
            logger.warn("Unable to send processing error to client", sendException);
        }
        emitter.complete();
    }

    private void processState(SseEmitter emitter, JanmeterState state, int percentage, int totalStates)
            throws IOException {
        sendProgress(emitter, "fetching_districts", state, null, null, null, null,
                percentage, totalStates, "Fetching district data for " + state.getStateName());
        List<GeneratedName> districts = generate(
                "districts", "districts in the Indian state or union territory \"" + state.getStateName() + "\"",
                "{\"name\":\"district name\"}",
                GeneratedName[].class);
        sendProgress(emitter, "districts_fetched", state, null, null, null, null,
                percentage, totalStates, "Fetched " + districts.size() + " districts for " + state.getStateName());

        Map<String, JanmeterDistrict> knownDistricts = new HashMap<>();
        districtRepository.findAllByStateId(state.getId()).forEach(
                existing -> knownDistricts.put(normalize(existing.getDistrictName()), existing));

        for (int districtIndex = 0; districtIndex < districts.size(); districtIndex++) {
            GeneratedName generatedDistrict = districts.get(districtIndex);
            requireName(generatedDistrict == null ? null : generatedDistrict.name(), "district");
            String districtKey = normalize(generatedDistrict.name());
            JanmeterDistrict district = knownDistricts.get(districtKey);
            if (district == null) {
                sendProgress(emitter, "creating_district", state, null, null, null, null,
                        percentage, totalStates, "Creating district " + (districtIndex + 1) + " of "
                                + districts.size() + ": " + generatedDistrict.name());
                district = new JanmeterDistrict();
                district.setDistrictName(generatedDistrict.name().trim());
                district.setState(state);
                setTimestamps(district);
                district = districtRepository.save(district);
                knownDistricts.put(districtKey, district);
                sendProgress(emitter, "district_created", state, district, null, null, null,
                        percentage, totalStates, "Created district " + (districtIndex + 1) + " of "
                                + districts.size() + ": " + district.getDistrictName());
            } else {
                sendProgress(emitter, "district_exists", state, district, null, null, null,
                        percentage, totalStates, "District already exists; using "
                                + district.getDistrictName());
            }

            sendProgress(emitter, "selected_district", state, district, null, null, null,
                    percentage, totalStates, "Selected district " + (districtIndex + 1) + " of "
                            + districts.size() + ": " + district.getDistrictName());
        }
    }

    private void processDistrict(SseEmitter emitter, JanmeterState state, JanmeterDistrict district,
                                 int percentage, int totalStates) throws IOException {
        sendProgress(emitter, "fetching_talukas", state, district, null, null, null,
                percentage, totalStates, "Fetching taluka data for " + district.getDistrictName());
        List<GeneratedName> talukas = generate(
                "talukas", "talukas or sub-districts in " + district.getDistrictName()
                        + ", " + state.getStateName(),
                "{\"name\":\"taluka name\"}",
                GeneratedName[].class);
        sendProgress(emitter, "talukas_fetched", state, district, null, null, null,
                percentage, totalStates, "Fetched " + talukas.size() + " talukas for "
                        + district.getDistrictName());

        Map<String, JanmeterTaluka> knownTalukas = new HashMap<>();
        talukaRepository.findAllByDistrictId(district.getId()).forEach(
                existing -> knownTalukas.put(normalize(existing.getTalukaName()), existing));

        for (int talukaIndex = 0; talukaIndex < talukas.size(); talukaIndex++) {
            GeneratedName generatedTaluka = talukas.get(talukaIndex);
            requireName(generatedTaluka == null ? null : generatedTaluka.name(), "taluka");
            String talukaKey = normalize(generatedTaluka.name());
            JanmeterTaluka taluka = knownTalukas.get(talukaKey);
            if (taluka == null) {
                sendProgress(emitter, "creating_taluka", state, district, null, null, null,
                        percentage, totalStates, "Creating taluka " + (talukaIndex + 1) + " of "
                                + talukas.size() + ": " + generatedTaluka.name());
                taluka = new JanmeterTaluka();
                taluka.setTalukaName(generatedTaluka.name().trim());
                taluka.setDistrict(district);
                setTimestamps(taluka);
                taluka = talukaRepository.save(taluka);
                knownTalukas.put(talukaKey, taluka);
                sendProgress(emitter, "taluka_created", state, district, taluka, null, null,
                        percentage, totalStates, "Created taluka " + (talukaIndex + 1) + " of "
                                + talukas.size() + ": " + taluka.getTalukaName());
            } else {
                sendProgress(emitter, "taluka_exists", state, district, taluka, null, null,
                        percentage, totalStates, "Taluka already exists; using " + taluka.getTalukaName());
            }

        }
    }

    private void processTaluka(SseEmitter emitter, JanmeterState state, JanmeterDistrict district,
                               JanmeterTaluka taluka, int percentage, int totalStates) throws IOException {
        sendProgress(emitter, "fetching_village_cities", state, district, taluka, null, null,
                percentage, totalStates, "Fetching village and city data for " + taluka.getTalukaName());
        List<GeneratedVillageCity> places = generate(
                "village/city names, city flags, and PIN codes",
                "villages and cities in " + taluka.getTalukaName() + ", "
                        + district.getDistrictName() + ", " + state.getStateName()
                        + ". For each item, set isCity to true only for a city and false for a village. "
                        + "Include the six-digit Indian postal PIN code when known, otherwise use null.",
                "{\"name\":\"place name\",\"isCity\":true,\"pincode\":\"123456\"}",
                GeneratedVillageCity[].class);
        sendProgress(emitter, "village_cities_fetched", state, district, taluka, null, null,
                percentage, totalStates, "Fetched " + places.size() + " villages/cities for "
                        + taluka.getTalukaName());

        Map<String, JanmeterVillageCity> knownPlaces = new HashMap<>();
        villageCityRepository.findAllByTalukaId(taluka.getId()).forEach(
                existing -> knownPlaces.put(normalize(existing.getName()), existing));

        for (int placeIndex = 0; placeIndex < places.size(); placeIndex++) {
            GeneratedVillageCity generatedPlace = places.get(placeIndex);
            requireName(generatedPlace == null ? null : generatedPlace.name(), "village/city");
            if (generatedPlace.isCity() == null) {
                throw new IllegalStateException("OpenAI omitted the isCity flag for " + generatedPlace.name());
            }
            String pincode = generatedPlace.pincode();
            if (pincode != null && !pincode.isBlank() && !pincode.matches("\\d{6}")) {
                throw new IllegalStateException("OpenAI returned an invalid PIN code for " + generatedPlace.name());
            }

            String placeKey = normalize(generatedPlace.name());
            JanmeterVillageCity villageCity = knownPlaces.get(placeKey);
            if (villageCity == null) {
                sendProgress(emitter, "creating_village_city", state, district, taluka, null, null,
                        percentage, totalStates, "Creating " + (generatedPlace.isCity() ? "city " : "village ")
                                + (placeIndex + 1) + " of " + places.size() + ": " + generatedPlace.name());
                villageCity = new JanmeterVillageCity();
                villageCity.setName(generatedPlace.name().trim());
                villageCity.setCity(generatedPlace.isCity());
                villageCity.setPincode(pincode);
                villageCity.setTaluka(taluka);
                setTimestamps(villageCity);
                villageCity = villageCityRepository.save(villageCity);
                knownPlaces.put(placeKey, villageCity);
                sendProgress(emitter, "village_city_created", state, district, taluka, villageCity, null,
                        percentage, totalStates, "Created "
                                + (villageCity.isCity() ? "city " : "village ")
                                + (placeIndex + 1) + " of " + places.size() + ": " + villageCity.getName());
            } else {
                sendProgress(emitter, "village_city_exists", state, district, taluka, villageCity, null,
                        percentage, totalStates, "Village/city already exists; using " + villageCity.getName());
                if ((villageCity.getPincode() == null || villageCity.getPincode().isBlank())
                        && pincode != null && !pincode.isBlank()) {
                    villageCity.setPincode(pincode);
                    villageCity.setUpdatedDateTime(LocalDateTime.now());
                    villageCityRepository.save(villageCity);
                }
            }

            if (villageCity.isCity()) {
                processCityWards(emitter, state, district, taluka, villageCity, percentage, totalStates);
            }
        }
    }

    private void processCityWards(SseEmitter emitter, JanmeterState state, JanmeterDistrict district,
                                  JanmeterTaluka taluka, JanmeterVillageCity city,
                                  int percentage, int totalStates) throws IOException {
        sendProgress(emitter, "fetching_wards", state, district, taluka, city, null,
                percentage, totalStates, "Fetching ward data for city " + city.getName());
        List<GeneratedWard> wards = generate(
                "ward names and numbers",
                "all municipal wards in the city " + city.getName() + ", "
                        + taluka.getTalukaName() + ", " + district.getDistrictName() + ", "
                        + state.getStateName() + ". Return each ward's official name and integer ward number.",
                "{\"name\":\"ward name\",\"wardNumber\":1}",
                GeneratedWard[].class);
        sendProgress(emitter, "wards_fetched", state, district, taluka, city, null,
                percentage, totalStates, "Fetched " + wards.size() + " wards for city " + city.getName());

        Set<String> knownWards = new HashSet<>();
        for (JanmeterWard ward : wardRepository.findAllByVillageCityId(city.getId())) {
            knownWards.add(normalize(ward.getName()) + ":" + ward.getWardNumber());
        }

        for (int wardIndex = 0; wardIndex < wards.size(); wardIndex++) {
            GeneratedWard generatedWard = wards.get(wardIndex);
            requireName(generatedWard == null ? null : generatedWard.name(), "ward");
            if (generatedWard.wardNumber() == null) {
                throw new IllegalStateException("OpenAI omitted a ward number for " + generatedWard.name());
            }
            if (!knownWards.add(normalize(generatedWard.name()) + ":" + generatedWard.wardNumber())) {
                continue;
            }

            JanmeterWard ward = new JanmeterWard();
            ward.setName(generatedWard.name().trim());
            ward.setWardNumber(generatedWard.wardNumber());
            ward.setVillageCity(city);
            setTimestamps(ward);
            sendProgress(emitter, "creating_ward", state, district, taluka, city, ward,
                    percentage, totalStates, "Creating ward " + (wardIndex + 1) + " of "
                            + wards.size() + ": " + generatedWard.name());
            wardRepository.save(ward);
            sendProgress(emitter, "ward_created", state, district, taluka, city, ward,
                    percentage, totalStates, "Created ward " + (wardIndex + 1) + " of "
                            + wards.size() + ": " + ward.getName() + " (number "
                            + ward.getWardNumber() + ")");
        }
    }

    private <T> List<T> generate(String subject, String location, String itemShape, Class<T[]> responseType) {
        logger.info("OPENAI REQUEST STARTED: generating {} for {}. This is AI-generated, not fetched from a verified data source.",
                subject, location);
        String content;
        try {
            content = chatClient.prompt()
                    .system("Generate a best-effort, non-authoritative list using your knowledge of India. "
                            + "Be exhaustive, but never invent entries when uncertain. Return only a valid JSON array "
                            + "with no markdown. Each item must match the requested JSON fields.")
                    .user("Return all " + subject + " for " + location
                            + ". Return a JSON array of objects matching this item shape: " + itemShape
                            + ". If there are no entries, return [].")
                    .call()
                    .content();
        } catch (RuntimeException exception) {
            if (hasQuotaFailure(exception)) {
                logger.warn("OPENAI QUOTA EXCEEDED: stopped generating {} for {}", subject, location);
            } else {
                logger.error("OPENAI REQUEST FAILED: could not generate {} for {}", subject, location, exception);
            }
            throw new IllegalStateException("OpenAI request failed while generating " + subject + " for " + location,
                    exception);
        }

        if (content == null || content.isBlank()) {
            logger.error("OPENAI RESPONSE EMPTY: no {} returned for {}", subject, location);
            throw new IllegalStateException("OpenAI returned an empty list for " + subject + " in " + location);
        }

        String json = removeCodeFence(content.trim());
        try {
            T[] results = objectMapper.readValue(json, responseType);
            if (results == null) {
                logger.error("OPENAI RESPONSE INVALID: returned null instead of a list of {} for {}", subject, location);
                throw new IllegalStateException("OpenAI returned null instead of a JSON array for " + subject);
            }
            logger.info("OPENAI RESPONSE PARSED: generated {} {} for {}",
                    results.length, subject, location);
            return Arrays.asList(results);
        } catch (JsonProcessingException exception) {
            logger.error("OPENAI RESPONSE INVALID: could not parse {} for {} as JSON: {}",
                    subject, location, content, exception);
            throw new IllegalStateException("OpenAI returned invalid JSON for " + subject + " in " + location, exception);
        }
    }

    private void sendProgress(SseEmitter emitter, String status, JanmeterState state,
                              JanmeterDistrict district, JanmeterTaluka taluka,
                              JanmeterVillageCity villageCity, JanmeterWard ward,
                              int percentage, int totalStates, String message) {
        sendEvent(emitter, "progress", new ProcessingProgress(
                status,
                state == null ? null : state.getStateName(),
                district == null ? null : district.getDistrictName(),
                taluka == null ? null : taluka.getTalukaName(),
                villageCity == null ? null : villageCity.getName(),
                ward == null ? null : ward.getName(),
                villageCity == null ? null : villageCity.getPincode(),
                percentage,
                totalStates,
                message));
    }

    private void sendEvent(SseEmitter emitter, String eventName, ProcessingProgress data) {
        try {
            currentProgress = data;
            logger.info("Hierarchy processing [{}] {} (state={}, district={}, taluka={}, place={}, ward={})",
                    data.percentage() == null ? "n/a" : data.percentage() + "%",
                    data.message(),
                    data.stateName(),
                    data.districtName(),
                    data.talukaName(),
                    data.villageCityName(),
                    data.wardName());
            emitter.send(SseEmitter.event().name(eventName).data(data));
        } catch (IOException exception) {
            throw new IllegalStateException("Client disconnected while sending processing progress", exception);
        }
    }

    private static boolean hasQuotaFailure(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            String message = cause.getMessage();
            if (message != null) {
                String normalized = message.toLowerCase(Locale.ROOT);
                if (normalized.contains("quota exceeded")
                        || normalized.contains("exceeded your current quota")
                        || normalized.contains("429")) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String describeFailure(Throwable exception, ProcessingProgress progress) {
        String location = progress == null ? "" : " while processing "
                + String.join(", ", java.util.stream.Stream.of(
                                progress.stateName(),
                                progress.districtName(),
                                progress.talukaName(),
                                progress.villageCityName(),
                                progress.wardName())
                        .filter(value -> value != null && !value.isBlank())
                        .toList());

        if (hasQuotaFailure(exception)) {
            return "OpenAI API quota exceeded" + location
                    + ". Processing stopped. Records saved before this point remain in the database. "
                    + "Resume after the quota resets or enable billing/increase the OpenAI API quota.";
        }

        String detail = exception.getMessage();
        return "Geographic hierarchy processing failed" + location
                + (detail == null || detail.isBlank() ? "." : ": " + detail);
    }

    private static void setTimestamps(JanmeterDistrict entity) {
        LocalDateTime now = LocalDateTime.now();
        entity.setCreatedDateTime(now);
        entity.setUpdatedDateTime(now);
    }

    private static void setTimestamps(JanmeterTaluka entity) {
        LocalDateTime now = LocalDateTime.now();
        entity.setCreatedDateTime(now);
        entity.setUpdatedDateTime(now);
    }

    private static void setTimestamps(JanmeterVillageCity entity) {
        LocalDateTime now = LocalDateTime.now();
        entity.setCreatedDateTime(now);
        entity.setUpdatedDateTime(now);
    }

    private static void setTimestamps(JanmeterWard entity) {
        LocalDateTime now = LocalDateTime.now();
        entity.setCreatedDateTime(now);
        entity.setUpdatedDateTime(now);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static void requireName(String name, String type) {
        if (name == null || name.isBlank()) {
            throw new IllegalStateException("OpenAI returned a " + type + " with no name");
        }
    }

    private static String removeCodeFence(String content) {
        if (!content.startsWith("```")) {
            return content;
        }
        int firstLineBreak = content.indexOf('\n');
        int lastFence = content.lastIndexOf("```");
        if (firstLineBreak < 0 || lastFence <= firstLineBreak) {
            return content;
        }
        return content.substring(firstLineBreak + 1, lastFence).trim();
    }

    public record ProcessingProgress(
            String status,
            String stateName,
            String districtName,
            String talukaName,
            String villageCityName,
            String wardName,
            String pincode,
            Integer percentage,
            Integer totalStates,
            String message) {
    }

    public record GeneratedName(String name) {
    }

    public record GeneratedVillageCity(String name, Boolean isCity, String pincode) {
    }

    public record GeneratedWard(String name, Integer wardNumber) {
    }
}
