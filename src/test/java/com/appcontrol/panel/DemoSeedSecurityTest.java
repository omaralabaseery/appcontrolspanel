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
class DemoSeedSecurityTest {
    @Autowired MockMvc mvc;
    @Test void seedAndRemovalRequireAdministrator() throws Exception {
        for(var method:java.util.List.of(org.springframework.http.HttpMethod.POST,org.springframework.http.HttpMethod.DELETE)) {
            mvc.perform(request(method,"/api/admin/database/demo").contentType("application/json").content("{\"projectId\":\"insta-11358\"}")).andExpect(status().isUnauthorized());
            mvc.perform(request(method,"/api/admin/database/demo").with(user("mobile").roles("USER")).contentType("application/json").content("{\"projectId\":\"insta-11358\"}")).andExpect(status().isForbidden());
            mvc.perform(request(method,"/api/admin/database/demo").with(user("admin").roles("ADMIN")).contentType("application/json").content("{\"projectId\":\"wrong\"}")).andExpect(status().isBadRequest());
        }
    }
}
