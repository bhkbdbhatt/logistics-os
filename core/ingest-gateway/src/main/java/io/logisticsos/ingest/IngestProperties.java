package io.logisticsos.ingest;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Ingest gateway configuration. Topics follow the {@code <layer>.<domain>.vN} convention
 * (see deployments in CI/compose) and default to the canonical observation topics.
 */
@ConfigurationProperties(prefix = "ingest")
public class IngestProperties {

    private boolean autoCreateTopics = true;

    public boolean isAutoCreateTopics() {
        return autoCreateTopics;
    }

    public void setAutoCreateTopics(boolean autoCreateTopics) {
        this.autoCreateTopics = autoCreateTopics;
    }
}