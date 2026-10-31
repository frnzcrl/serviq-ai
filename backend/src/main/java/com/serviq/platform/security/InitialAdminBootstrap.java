package com.serviq.platform.security;

import java.util.Locale;
import java.util.UUID;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class InitialAdminBootstrap implements ApplicationRunner {
    private final NamedParameterJdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final Environment environment;

    public InitialAdminBootstrap(NamedParameterJdbcTemplate jdbc, PasswordEncoder encoder, Environment environment) {
        this.jdbc = jdbc; this.encoder = encoder; this.environment = environment;
    }

    @Override @Transactional
    public void run(ApplicationArguments arguments) {
        Integer admins = jdbc.getJdbcTemplate().queryForObject("""
            SELECT COUNT(*) FROM serviq_staff_user u
            JOIN serviq_staff_user_role r ON r.user_id = u.user_id
            WHERE r.role_code = 'ADMIN'
            """, Integer.class);
        if (admins != null && admins > 0) return;
        String email = environment.getProperty("SERVIQ_BOOTSTRAP_ADMIN_EMAIL", "").trim().toLowerCase(Locale.ROOT);
        String password = environment.getProperty("SERVIQ_BOOTSTRAP_ADMIN_PASSWORD", "");
        if (email.isBlank() || password.length() < 12) {
            throw new IllegalStateException("No admin exists. Set SERVIQ_BOOTSTRAP_ADMIN_EMAIL and a 12+ character SERVIQ_BOOTSTRAP_ADMIN_PASSWORD before startup.");
        }
        UUID id = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO serviq_staff_user (user_id, email_address, display_name, password_hash)
            VALUES (:id, :email, :name, :hash)
            """, new MapSqlParameterSource().addValue("id", id).addValue("email", email)
            .addValue("name", "Serviq Administrator").addValue("hash", encoder.encode(password)));
        jdbc.update("INSERT INTO serviq_staff_user_role (user_id, role_code) VALUES (:id, 'ADMIN')",
            new MapSqlParameterSource("id", id));
        jdbc.update("""
            INSERT INTO serviq_staff_security_audit (audit_id, actor_user_id, action, target_user_id, details)
            VALUES (:audit, :id, 'INITIAL_ADMIN_CREATED', :id, 'Initial administrator bootstrapped')
            """, new MapSqlParameterSource().addValue("audit", UUID.randomUUID()).addValue("id", id));
    }
}
