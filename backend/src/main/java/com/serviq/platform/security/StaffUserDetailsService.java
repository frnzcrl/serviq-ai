package com.serviq.platform.security;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class StaffUserDetailsService implements UserDetailsService {
    private final NamedParameterJdbcTemplate jdbc;
    public StaffUserDetailsService(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public UserDetails loadUserByUsername(String suppliedEmail) {
        String email = suppliedEmail == null ? "" : suppliedEmail.trim().toLowerCase(Locale.ROOT);
        var args = new MapSqlParameterSource("email", email);
        List<Staff> users = jdbc.query("""
            SELECT user_id, email_address, password_hash, enabled
            FROM serviq_staff_user WHERE lower(btrim(email_address)) = :email
            """, args, (rs, n) -> new Staff(
                rs.getObject("user_id", UUID.class), rs.getString("email_address"),
                rs.getString("password_hash"), rs.getBoolean("enabled")));
        if (users.isEmpty()) throw new UsernameNotFoundException("Invalid credentials");
        Staff staff = users.getFirst();
        var roles = jdbc.query("SELECT role_code FROM serviq_staff_user_role WHERE user_id = :id",
            new MapSqlParameterSource("id", staff.id()),
            (rs, n) -> new SimpleGrantedAuthority("ROLE_" + rs.getString("role_code")));
        return User.withUsername(staff.email()).password(staff.passwordHash())
            .disabled(!staff.enabled()).authorities(roles).build();
    }
    private record Staff(UUID id, String email, String passwordHash, boolean enabled) { }
}
