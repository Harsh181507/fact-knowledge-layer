package com.superjoin.fact_knowledge_layer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "llm.api")
public class LlmProperties {
    /** Get a free key (no credit card) at https://aistudio.google.com/apikey */
    private String key;
    /** Base URL, e.g. https://generativelanguage.googleapis.com/v1beta/models */
    private String url;
    /** e.g. gemini-2.0-flash */
    private String model;
    private int maxTokens;

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public int getMaxTokens() { return maxTokens; }
    public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }
}
