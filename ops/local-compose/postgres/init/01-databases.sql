-- Local dev bootstrap: one PostgreSQL instance, one logical database per service (ADR-0021).
-- Only MDM uses a system-of-record database in 0.3.0; additional services get their own DB here later.
CREATE DATABASE mdm;
