package es.devolvapp.backend.repository;

import es.devolvapp.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    boolean existsByEmail(String email);
    boolean existsByPhoneNumber(String phoneNumber);
    boolean existsByIdCard(String idCard);

    Optional<User> findByEmail(String email);
    Optional<User> findByEmailIgnoreCaseOrIdCardIgnoreCase(String email, String idCard);

}
