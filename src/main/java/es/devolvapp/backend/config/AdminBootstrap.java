package es.devolvapp.backend.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class AdminBootstrap implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    @Value("$" + "{app.bootstrap.admin.email:}")
    private String email;
    @Value("$" + "{app.bootstrap.admin.password:}")
    private String password;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (email.isBlank() || password.isBlank()) return;
        Integer exists = jdbc.queryForObject("SELECT COUNT(*) FROM d_user WHERE lower(email)=lower(?)",
                Integer.class, email);
        if (exists != null && exists > 0) return;
        Long id = jdbc.queryForObject("""
                INSERT INTO d_user (first_name, last_name, email, password_hash)
                VALUES ('Administración', 'DevolvApp', ?, ?) RETURNING id
                """, Long.class, email, passwordEncoder.encode(password));
        jdbc.update("INSERT INTO user_role (user_id, role) VALUES (?, 'ADMIN')", id);
    }
}
