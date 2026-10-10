package com.appcontrol.panel;

import com.appcontrol.panel.service.*;
import com.appcontrol.panel.dto.Models.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

@SpringBootTest(properties="panel.firebase.enabled=false")
@AutoConfigureMockMvc
class ApiContractTest {
    @Autowired MockMvc mvc;
    @Autowired com.fasterxml.jackson.databind.ObjectMapper mapper;
    @MockitoBean PanelService panel;
    @MockitoBean PushRequestService pushes;
    @MockitoBean DatabaseService database;
    @MockitoBean DemoSeedService seeds;
    private static final String REG="""
        {"id":"device_test_001","pushNotificationToken":"test-token","deviceVersion":"2.4.1",
         "platform":"android","model":"Test","build":241,"language":"en"}
        """;
    @BeforeEach void setup() throws Exception {
        when(panel.policy()).thenReturn(PolicyService.defaults());
        when(panel.page(anyString(),anyString(),nullable(String.class))).thenReturn(Map.of("items",List.of(),"nextCursor",""));
    }
    @ParameterizedTest @ValueSource(strings={"/api/admin/stats","/api/admin/users","/api/admin/users/user_1","/api/admin/events","/api/admin/notifications","/api/admin/notifications/push_001","/api/admin/notifications/push_001/recipients","/api/admin/settings","/api/admin/database","/api/admin/database/schema"})
    void allAdminReadsEnforceRoles(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(get(path).with(user("member").roles("USER"))).andExpect(status().isForbidden());
        mvc.perform(get(path).with(user("admin").roles("ADMIN"))).andExpect(status().isOk());
    }
    @ParameterizedTest @org.junit.jupiter.params.provider.CsvSource({
        "POST,/api/admin/notifications", "POST,/api/admin/notifications/requests",
        "PUT,/api/admin/settings", "POST,/api/admin/database/initialize",
        "POST,/api/admin/database/demo", "DELETE,/api/admin/database/demo",
        "POST,/api/mobile/devices", "POST,/api/mobile/settings/register-device",
        "POST,/api/mobile/heartbeat", "POST,/api/mobile/events", "DELETE,/api/mobile/devices/device_test_001"
    })
    void allWritesRequireAuthentication(String method,String path) throws Exception {
        var verb=org.springframework.http.HttpMethod.valueOf(method);
        mvc.perform(request(verb,path).contentType("application/json").content("{}"))
            .andExpect(status().isUnauthorized());
        if(path.startsWith("/api/admin/")) mvc.perform(request(verb,path).with(user("member").roles("USER")).contentType("application/json").content("{}"))
            .andExpect(status().isForbidden());
    }
    @Test void queryParametersReachServices() throws Exception {
        mvc.perform(get("/api/admin/events?uid=member&cursor=event_001").with(user("admin").roles("ADMIN")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.items").isArray());
        verify(panel).page("events","event_001","member");
        mvc.perform(get("/api/admin/notifications/push_001/recipients?cursor=member").with(user("admin").roles("ADMIN"))).andExpect(status().isOk());
        verify(pushes).recipients("push_001","member");
    }
    @ParameterizedTest @ValueSource(strings={"/api/mobile/devices","/api/mobile/settings/register-device"})
    void registrationAliasesUseAuthenticatedOwner(String path) throws Exception {
        mvc.perform(post(path).with(user("member").roles("USER")).contentType("application/json").content(REG))
            .andExpect(status().isOk()).andExpect(jsonPath("$.ok").value(true));
        verify(panel).register(eq("member"),argThat(r->r.installationId().equals("device_test_001") && r.fcmToken().equals("test-token") && r.version().equals("2.4.1")));
    }
    @Test void heartbeatAndUnlinkUseAuthenticatedOwner() throws Exception {
        mvc.perform(post("/api/mobile/heartbeat").with(user("member").roles("USER")).contentType("application/json").content("{\"installationId\":\"device_test_001\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.ok").value(true));
        mvc.perform(delete("/api/mobile/devices/device_test_001").with(user("member").roles("USER"))).andExpect(status().isOk());
        verify(panel).heartbeat("member","device_test_001",false);
        verify(panel).heartbeat("member","device_test_001",true);
    }
    @Test void telemetryPreservesInputOutputAndOwner() throws Exception {
        mvc.perform(post("/api/mobile/events").with(user("member").roles("USER")).contentType("application/json").content("""
          {"eventId":"event_test_001","feature":"screen","method":"GET","path":"/screen","status":500,
           "durationMs":25,"message":"failed","input":{"page":1},"output":{"error":"test"}}
          """)).andExpect(status().isOk());
        verify(panel).event(eq("member"),argThat(e->e.input().get("page").asInt()==1 && e.output().get("error").asText().equals("test")));
    }
    @ParameterizedTest @ValueSource(strings={"/api/admin/notifications","/api/admin/notifications/requests"})
    void pushRoutesPassArrayAndIdempotencyKey(String path) throws Exception {
        mvc.perform(post(path).with(user("admin").roles("ADMIN")).header("Idempotency-Key","push_test_001")
            .contentType("application/json").content("[{\"id\":0,\"title\":\"test\",\"message\":\"test\"}]"))
            .andExpect(status().isOk());
        verify(pushes).send(argThat(n->n.isArray() && n.get(0).get("id").asInt()==0),eq("admin"),eq("push_test_001"));
    }
    @Test void missingHeadersAndQueryParamsAreClientErrors() throws Exception {
        mvc.perform(get("/api/mobile/config")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").exists());
        mvc.perform(post("/api/admin/notifications").with(user("admin").roles("ADMIN")).contentType("application/json").content("[]"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(pushes);
    }
    @Test void malformedAndInvalidBodiesDoNotReachServices() throws Exception {
        mvc.perform(post("/api/mobile/settings/register-device").with(user("member")).contentType("application/json").content("{\"id\":\"bad/path\"}"))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/mobile/events").with(user("member")).contentType("application/json").content("{"))
            .andExpect(status().isBadRequest());
        verify(panel,never()).register(anyString(),any());
        verify(panel,never()).event(anyString(),any());
    }
    @Test void settingsAndDatabaseWritesPassActor() throws Exception {
        mvc.perform(put("/api/admin/settings").with(user("admin").roles("ADMIN")).contentType("application/json").content(mapper.writeValueAsString(PolicyService.defaults())))
            .andExpect(status().isOk());
        verify(panel).savePolicy(any(Policy.class),eq("admin"));
        mvc.perform(post("/api/admin/database/initialize").with(user("admin").roles("ADMIN"))).andExpect(status().isOk());
        verify(database).initialize("admin");
        for(var method:List.of("POST","DELETE")) {
            mvc.perform(request(org.springframework.http.HttpMethod.valueOf(method),"/api/admin/database/demo").with(user("admin").roles("ADMIN"))
                .contentType("application/json").content("{\"projectId\":\"test-project\"}")).andExpect(status().isOk());
        }
        verify(seeds).apply("admin","test-project",false);
        verify(seeds).apply("admin","test-project",true);
    }
    @Test void publicPolicyReturnsActionAndRejectsInvalidPlatform() throws Exception {
        mvc.perform(get("/api/mobile/config?platform=android&build=1&language=ar"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.action").value("allow")).andExpect(jsonPath("$.language").value("ar"));
        mvc.perform(get("/api/mobile/config?platform=windows&build=1")).andExpect(status().isBadRequest());
    }
}
