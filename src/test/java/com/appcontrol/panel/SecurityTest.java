package com.appcontrol.panel;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
@SpringBootTest(properties="panel.firebase.enabled=false")
@AutoConfigureMockMvc
class SecurityTest {
    @Autowired MockMvc mvc;
    @Test void anonymousCannotReadUsersOrPostTelemetry() throws Exception {
        mvc.perform(get("/api/admin/users")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/mobile/events").contentType("application/json").content("{}")).andExpect(status().isUnauthorized());
    }
    @Test void regularUserCannotChangePolicyOrSendPush() throws Exception {
        mvc.perform(put("/api/admin/settings").with(user("mobile").roles("USER"))).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/notifications").with(user("mobile").roles("USER"))).andExpect(status().isForbidden());
    }
    @Test void missingConfigurationFailsClosed() throws Exception {
        mvc.perform(get("/api/admin/stats").with(user("admin").roles("ADMIN"))).andExpect(status().isServiceUnavailable());
        mvc.perform(get("/api/mobile/config?platform=ios&build=1")).andExpect(status().isServiceUnavailable());
        mvc.perform(get("/api/admin/stats").header("Authorization","Bearer invalid")).andExpect(status().isServiceUnavailable());
    }
    @Test void invalidRegistrationRejectedBeforeDatabase() throws Exception {
        mvc.perform(post("/api/mobile/devices").with(user("user").roles("USER")).contentType("application/json")
            .content("{\"installationId\":\"bad/path\",\"platform\":\"ios\",\"model\":\"iPhone\",\"build\":1,\"version\":\"1.0\",\"language\":\"en\"}"))
            .andExpect(status().isBadRequest());
    }
    @Test void demoAndApiDocsAreAvailableWithoutCredentials() throws Exception {
        mvc.perform(get("/index.html")).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }
}
