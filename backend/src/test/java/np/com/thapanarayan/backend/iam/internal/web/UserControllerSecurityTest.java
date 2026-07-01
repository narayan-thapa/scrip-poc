package np.com.thapanarayan.backend.iam.internal.web;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.util.UUID;
import np.com.thapanarayan.backend.iam.internal.UserService;
import np.com.thapanarayan.backend.iam.internal.config.SecurityConfig;
import np.com.thapanarayan.backend.iam.internal.config.SecurityProperties;
import np.com.thapanarayan.backend.iam.internal.domain.AppUser;
import np.com.thapanarayan.backend.iam.internal.domain.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Verifies the JWT security chain on a protected endpoint without Docker: anonymous requests are
 * rejected (401) and a valid access-token JWT is accepted. Uses Spring Security's {@code jwt()}
 * post-processor (a real {@code @WithMockUser} would 401 against a resource server — see project memory).
 */
@WebMvcTest(controllers = UserController.class)
@Import({SecurityConfig.class, UserControllerSecurityTest.TestBeans.class})
@EnableConfigurationProperties(SecurityProperties.class)
class UserControllerSecurityTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    UserService userService;

    @Test
    void anonymousIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/users/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void validJwtCanReadOwnProfile() throws Exception {
        UUID id = UUID.randomUUID();
        AppUser user = new AppUser("a@b.com", "x", Role.USER);
        when(userService.require(id)).thenReturn(user);

        mockMvc.perform(get("/api/v1/users/me")
                        .with(jwt().jwt(j -> j.subject(id.toString()).claim("roles", java.util.List.of("USER")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("a@b.com"))
                .andExpect(jsonPath("$.role").value("USER"));
    }

    /** The platform exception advice needs a Clock; supply a system one for the slice. */
    @TestConfiguration
    static class TestBeans {
        @Bean
        Clock nptClock() {
            return Clock.systemUTC();
        }
    }
}
