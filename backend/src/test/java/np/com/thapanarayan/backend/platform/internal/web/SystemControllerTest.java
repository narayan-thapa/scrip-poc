package np.com.thapanarayan.backend.platform.internal.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/**
 * No-Docker web-slice test for the ping contract. Scoped to {@link SystemController} and with
 * security filters disabled — this verifies controller mapping + serialization, not the security
 * chain (that's covered by the IAM tests).
 */
@WebMvcTest(controllers = SystemController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(SystemControllerTest.FixedClockConfig.class)
class SystemControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void pingReturnsServiceStatusAndNptZone() throws Exception {
        mockMvc.perform(get("/api/v1/system/ping"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("backend"))
                .andExpect(jsonPath("$.zone").value("Asia/Kathmandu"))
                .andExpect(jsonPath("$.time").exists());
    }

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        Clock nptClock() {
            return Clock.fixed(Instant.parse("2026-06-30T09:16:00Z"), ZoneId.of("Asia/Kathmandu"));
        }
    }
}
