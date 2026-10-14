package es.devolvapp.backend.mappers;

import es.devolvapp.backend.dto.UserDTO;
import es.devolvapp.backend.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserMapper {
    UserDTO toUserDTO(User user);
}
