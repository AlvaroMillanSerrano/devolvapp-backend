package es.devolvapp.backend.service;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
@Service
@RequiredArgsConstructor
public class WalletService {
    private final JdbcTemplate jdbc;
    public BigDecimal balance(Long citizenId) {
        return jdbc.queryForObject("""
                SELECT COALESCE((SELECT SUM(s.citizen_share) FROM settlement s
                                 JOIN collection c ON c.id=s.collection_id
                                 WHERE c.citizen_id=?), 0)
                     - COALESCE((SELECT SUM(w.amount) FROM withdrawal w
                                 WHERE w.citizen_id=? AND w.status <> 'FALLIDA'), 0)
                """, BigDecimal.class, citizenId, citizenId);
    }
}
