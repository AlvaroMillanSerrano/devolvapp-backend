package es.devolvapp.backend.service;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
@Service
@RequiredArgsConstructor
public class CodeGenerator {
    private final JdbcTemplate jdbc;
    public String next(String sequence, String prefix, int radix, int minWidth) {
        Long value = jdbc.queryForObject("SELECT nextval('" + sequence + "')", Long.class);
        return prefix + String.format("%" + minWidth + "s", Long.toString(value, radix).toUpperCase())
                .replace(' ', '0');
    }
    public String remittance() {
        return "REM-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
    }
}
