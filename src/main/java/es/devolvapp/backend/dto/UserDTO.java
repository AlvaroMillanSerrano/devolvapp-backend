package es.devolvapp.backend.dto;

import lombok.Data;

@Data
public class UserDTO {
    private long id;
    private String name;
    private String surname;
    private String idCard;
    private String phoneNumber;
    private String email;
    private String rol;
    private float wallet;
    private String city;
}
