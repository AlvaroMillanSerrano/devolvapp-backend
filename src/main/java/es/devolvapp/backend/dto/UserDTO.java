package es.devolvapp.backend.dto;

import lombok.Data;

import java.util.Set;
import es.devolvapp.backend.entity.Role;

@Data
public class UserDTO {
    private long id;
    private String firstName;
    private String lastName;
    private String idCard;
    private String phoneNumber;
    private String email;
    private String status;
    private Set<Role> roles;
}
