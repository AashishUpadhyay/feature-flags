package com.featureflags.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class FeatureFlagValidator {
    private Set<String> registeredFeatureFlags;

    @PostConstruct
    public void init() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        List<Map<String, String>> featureFlags = mapper.readValue(
                new ClassPathResource("feature-flags.json").getInputStream(),
                new TypeReference<List<Map<String, String>>>() {
                });

        registeredFeatureFlags = new HashSet<>();
        for (Map<String, String> flag : featureFlags) {
            registeredFeatureFlags.add(flag.get("name"));
        }
    }

    public boolean isFeatureFlagRegistered(String featureFlagName) {
        return registeredFeatureFlags.contains(featureFlagName);
    }
}
