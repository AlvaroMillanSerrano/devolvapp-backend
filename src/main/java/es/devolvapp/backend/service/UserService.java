package es.devolvapp.backend.service;

import es.devolvapp.backend.entity.Role;
import es.devolvapp.backend.entity.User;
import es.devolvapp.backend.exception.UserException;
import es.devolvapp.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {


    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    public List<User> readUsers(){

        return userRepository.findAll();
    }


    public User registerUser(User user) {
        if (userRepository.existsByEmail(user.getEmail())) {
            throw new UserException("El correo electrónico ya está registrado");
        }

        if (userRepository.existsByPhoneNumber(user.getPhoneNumber())) {
            throw new UserException("El teléfono ya está registrado");
        }

        user.setRol(Role.ciudadano);
        user.setActive(true);
        user.setCreatedAt(LocalDateTime.now());
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        return userRepository.save(user);
    }

    public User login(String email, String password) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserException("Credenciales incorrectas"));

        if (!user.isActive() || !passwordEncoder.matches(password, user.getPassword())) {
            throw new UserException("Credenciales incorrectas");
        }

        return user;
    }
}
