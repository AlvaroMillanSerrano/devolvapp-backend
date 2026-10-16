package es.devolvapp.backend.controller;

import es.devolvapp.backend.dto.LoginDTO;
import es.devolvapp.backend.dto.RegisterUserDTO;
import es.devolvapp.backend.dto.UserDTO;
import es.devolvapp.backend.entity.User;
import es.devolvapp.backend.mappers.UserMapper;
import es.devolvapp.backend.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final UserMapper userMapper;
    /*public void asdasd(){
        userMapper.toUserDTO(new User());
    }*/

    @GetMapping("/usuarios")
    public ResponseEntity getUser(){
        var usuariosDto = userService.readUsers()
                .stream()
                .map(userMapper::toUserDTO)
                .toList();

        return ResponseEntity.ok(usuariosDto);
    }

    @PostMapping("/registro")
    public ResponseEntity registerUser(@Valid @RequestBody RegisterUserDTO  registerUserDTO){

        User user = userMapper.toUser(registerUserDTO);

        User registeredUser = userService.registerUser(user);

        UserDTO response = userMapper.toUserDTO(registeredUser);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);


    }

    @PostMapping("/login")
    public ResponseEntity<UserDTO> login(@Valid @RequestBody LoginDTO loginDTO) {
        User user = userService.login(loginDTO.getEmail(), loginDTO.getPassword());
        return ResponseEntity.ok(userMapper.toUserDTO(user));
    }

}
