package io.logisticsos.readmodel;

import io.logisticsos.readmodel.projection.ReadModelTopology;
import java.util.Map;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafkaStreams;
import org.springframework.kafka.annotation.KafkaStreamsDefaultConfiguration;
import org.springframework.kafka.config.KafkaStreamsConfiguration;

@Configuration(proxyBeanMethods = false)
@EnableKafkaStreams
@EnableConfigurationProperties(RmbProperties.class)
public class StreamsConfig {

    @Bean(name = KafkaStreamsDefaultConfiguration.DEFAULT_STREAMS_CONFIG_BEAN_NAME)
    public KafkaStreamsConfiguration streamsConfiguration(KafkaProperties properties) {
        Map<String, Object> props = properties.buildStreamsProperties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "read-model-builder");
        return new KafkaStreamsConfiguration(props);
    }

    @Bean
    public Topology readModelTopology() {
        return ReadModelTopology.build();
    }
}