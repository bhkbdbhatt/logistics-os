package io.logisticsos.mdm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Seeds a minimal reference-data working set on start (roadmap Phase 1: "MDM golden records
 * (seed)") so ACLs, read models and the control tower have locations/customers to resolve.
 */
@Component
public class MdmSeedData {

    private static final Logger log = LoggerFactory.getLogger(MdmSeedData.class);

    private final MdmService service;
    private final MdmProperties properties;

    public MdmSeedData(MdmService service, MdmProperties properties) {
        this.service = service;
        this.properties = properties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seed() {
        if (!properties.isSeedEnabled()) {
            return;
        }
        String tenant = properties.getDefaultTenant();
        try {
            service.upsert(MdmEntities.LOCATION, "LOC-WH-AMS", location(tenant, "LOC-WH-AMS",
                    "Amsterdam DC", "DC", "NL"), tenant, "mdm-seed");
            service.upsert(MdmEntities.LOCATION, "LOC-WH-FFM", location(tenant, "LOC-WH-FFM",
                    "Frankfurt Hub", "WAREHOUSE", "DE"), tenant, "mdm-seed");
            service.upsert(MdmEntities.LOCATION, "LOC-STORE-BER", location(tenant, "LOC-STORE-BER",
                    "Berlin Store", "STORE", "DE"), tenant, "mdm-seed");
            service.upsert(MdmEntities.CUSTOMER, "CUST-ACME", customer(tenant, "CUST-ACME", "ACME Retail"),
                    tenant, "mdm-seed");
            log.info("MDM golden-record seed applied ({} records)", 4);
        } catch (Exception ex) {
            log.warn("MDM seed skipped: {}", ex.getMessage());
        }
    }

    private String location(String tenant, String id, String name, String type, String country) {
        return """
                {
                  "locationId": "%s",
                  "tenantId": "%s",
                  "name": "%s",
                  "type": "%s",
                  "address": {"line1": "1 Logistics Way", "city": "%s", "postalCode": "0001", "country": "%s"},
                  "timezone": "Europe/Amsterdam",
                  "status": "ACTIVE"
                }""".formatted(id, tenant, name, type, name, country);
    }

    private String customer(String tenant, String id, String name) {
        return """
                {
                  "customerId": "%s",
                  "tenantId": "%s",
                  "name": "%s",
                  "type": "RETAIL",
                  "status": "ACTIVE",
                  "roles": ["SHIPPER"],
                  "billingAddress": {"line1": "1 Market St", "city": "Amsterdam", "postalCode": "1000", "country": "NL"},
                  "contacts": [{"name": "Ops Desk", "email": "ops@example.com"}],
                  "paymentTerms": "NET_30",
                  "updatedAt": 0
                }""".formatted(id, tenant, name);
    }
}