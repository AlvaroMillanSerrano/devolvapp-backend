package es.devolvapp.backend.service;

import es.devolvapp.backend.entity.User;
import es.devolvapp.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;

    public List<User> readUsers(){
        return userRepository.findAll();
    }
}
