package com.internlink.core.support;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;

import java.util.UUID;

/**
 * Tạo một database PostgreSQL riêng cho mỗi lớp test chạy migration Flyway.
 *
 * <p>Không dùng schema riêng trong cùng database vì extension (vector, pgcrypto) chỉ cài một
 * lần cho cả database: lớp test chạy sau sẽ không thấy kiểu {@code vector} nằm trong schema
 * của lớp chạy trước.
 */
public final class PostgresTestDatabase {
    public static final String URL_ENV = "INTERNLINK_TEST_PG_URL";
    public static final String USER = "internlink_test";

    private PostgresTestDatabase() {}

    /** Tạo database mới và trỏ datasource của Spring vào đó. */
    public static void register(DynamicPropertyRegistry properties, String prefix) {
        String url = create(prefix);
        properties.add("spring.datasource.url", () -> url);
        properties.add("spring.datasource.username", () -> USER);
        properties.add("spring.datasource.password", () -> "");
        properties.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    /** Tạo database mới, trả về JDBC URL của nó. */
    public static String create(String prefix) {
        String base = System.getenv(URL_ENV);
        String name = prefix + "_" + UUID.randomUUID().toString().replace("-", "");
        new JdbcTemplate(new DriverManagerDataSource(base, USER, "")).execute("CREATE DATABASE " + name);
        int query = base.indexOf('?');
        String path = query < 0 ? base : base.substring(0, query);
        String params = query < 0 ? "" : base.substring(query);
        return path.substring(0, path.lastIndexOf('/') + 1) + name + params;
    }
}
