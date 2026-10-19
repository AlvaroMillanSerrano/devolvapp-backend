package es.devolvapp.backend.service;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.Map;
@Service
@RequiredArgsConstructor
public class ParameterService {
    private final JdbcTemplate jdbc;
    public Map<String, String> all() {
        return jdbc.query("SELECT param_key, param_value FROM app_parameter",
                (rs, row) -> Map.entry(rs.getString(1), rs.getString(2)))
                .stream().collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
    public BigDecimal decimal(String key, String fallback) {
        return new BigDecimal(all().getOrDefault(key, fallback));
    }
    public int integer(String key, int fallback) {
        return Integer.parseInt(all().getOrDefault(key, Integer.toString(fallback)));
    }
    public String get(String key, String fallback) {
        return all().getOrDefault(key, fallback);
    }
}
