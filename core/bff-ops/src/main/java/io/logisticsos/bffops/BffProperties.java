package io.logisticsos.bffops;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "bff")
public class BffProperties {

    private String readModelBaseUrl = "http://localhost:8085";
    private String towerBaseUrl = "http://localhost:8087";
    private String mdmBaseUrl = "http://localhost:8086";

    public String getReadModelBaseUrl() {
        return readModelBaseUrl;
    }

    public void setReadModelBaseUrl(String readModelBaseUrl) {
        this.readModelBaseUrl = readModelBaseUrl;
    }

    public String getTowerBaseUrl() {
        return towerBaseUrl;
    }

    public void setTowerBaseUrl(String towerBaseUrl) {
        this.towerBaseUrl = towerBaseUrl;
    }

    public String getMdmBaseUrl() {
        return mdmBaseUrl;
    }

    public void setMdmBaseUrl(String mdmBaseUrl) {
        this.mdmBaseUrl = mdmBaseUrl;
    }
}