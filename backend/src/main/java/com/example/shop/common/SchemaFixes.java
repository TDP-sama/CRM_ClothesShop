package com.example.shop.common;

import java.util.List;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Schema changes {@code ddl-auto=update} cannot make. Hibernate puts a CHECK constraint listing the enum values on
 * enum columns when it creates a table, but never updates it, so values added later would be rejected by
 * PostgreSQL. Dropping a stale check is safe: those columns are still written only from the Java enum.
 * When adding a value to a persisted enum, add its table/column and the new value here.
 */
@Component
public class SchemaFixes implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(SchemaFixes.class);

    /** table, column, newest enum value (a check that does not mention it is stale). */
    private static final List<String[]> ENUM_COLUMNS = List.of(
            new String[] {"app_users", "role", "STAFF"},
            new String[] {"user_permissions", "section", "REPORTS"});

    private final JdbcTemplate jdbc;
    private final DataSource dataSource;

    public SchemaFixes(DataSource dataSource) {
        this.dataSource = dataSource;
        this.jdbc = new JdbcTemplate(dataSource);
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        String product;
        try (var connection = dataSource.getConnection()) {
            product = connection.getMetaData().getDatabaseProductName();
        }
        if (!"PostgreSQL".equalsIgnoreCase(product)) {
            return; // H2 test databases are created fresh with the current enum values
        }
        for (String[] column : ENUM_COLUMNS) {
            dropStaleCheck(column[0], column[1], column[2]);
        }
    }

    private void dropStaleCheck(String table, String column, String newestValue) {
        List<String> stale = jdbc.queryForList("""
                select conname from pg_constraint
                where conrelid = to_regclass(?) and contype = 'c'
                  and pg_get_constraintdef(oid) like ? and pg_get_constraintdef(oid) not like ?
                """, String.class, table, "%" + column + "%", "%" + newestValue + "%");
        for (String name : stale) {
            jdbc.execute("alter table " + table + " drop constraint \"" + name.replace("\"", "") + "\"");
            log.info("Dropped outdated check constraint {} on {}.{}", name, table, column);
        }
    }
}
