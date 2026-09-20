package blog.code.codeblog.repository;

import blog.code.codeblog.model.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;


import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("Deve encontrar usuário por login")
    void findByLogin() {
        User user = new User();
        user.setLogin("usuario_teste");
        user.setPassword("senhaTeste");
        user.setName("Usuário de Teste");
        userRepository.save(user);

        Optional<User> resultado = Optional.ofNullable(userRepository.findByLogin("usuario_teste"));

        assertTrue(resultado.isPresent());
        assertEquals("usuario_teste", resultado.get().getLogin());
        assertEquals("Usuário de Teste", resultado.get().getName());
    }

    @Test
    @DisplayName("Não deve encontrar usuário por login")
    void notfindByLogin() {
        Optional<User> resultado = Optional.ofNullable(userRepository.findByLogin("usuario_teste"));
        assertTrue(resultado.isEmpty());
    }

    @Test
    @DisplayName("Deve persistir e buscar usuário por ID")
    void persistAndFindById() {
        User user = new User();
        user.setLogin("usuario_id");
        user.setPassword("senhaId");
        user.setName("Usuário ID");
        User saved = userRepository.save(user);
        assertNotNull(saved.getId());
        User found = userRepository.findById(saved.getId()).orElse(null);
        assertNotNull(found);
        assertEquals("usuario_id", found.getLogin());
    }
}