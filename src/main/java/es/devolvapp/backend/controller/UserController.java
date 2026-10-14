package es.devolvapp.backend.controller;

import es.devolvapp.backend.dto.UserDTO;
import es.devolvapp.backend.entity.User;
import es.devolvapp.backend.mappers.UserMapper;
import es.devolvapp.backend.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
    public ResponseEntity getUsuarios(){
        var usuariosDto = userService.readUsers()
                .stream().map(userMapper::toUserDTO).toList();

        return ResponseEntity.ok(usuariosDto);
    }

}
