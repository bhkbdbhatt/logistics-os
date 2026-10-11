package io.logisticsos.mdm;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.h2.jdbcx.JdbcDataSource;

/** H2-in-PostgreSQL-mode datasource + migration bootstrap for MDM persistence tests. */
final class H2Support {

    private static final AtomicInteger COUNTER = new AtomicInteger();

    private H2Support() {
    }

    static DataSource h2() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:mdm_" + COUNTER.incrementAndGet()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        return dataSource;
    }

    static void apply(DataSource dataSource, String... resources) {
        try (Connection connection = dataSource.getConnection()) {
            for (String resource : resources) {
                for (String statementText : read(resource).split(";")) {
                    String sql = statementText.trim();
                    if (sql.isEmpty()) {
                        continue;
                    }
                    try (Statement statement = connection.createStatement()) {
                        statement.execute(sql);
                    }
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("schema bootstrap failed", e);
        }
    }

    private static String read(String resource) {
        try (InputStream in = H2Support.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("missing classpath resource: " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("failed to read " + resource, e);
        }
    }
}
