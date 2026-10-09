package com.appcontrol.panel.service;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class TelemetryPayloadTest {
    final ObjectMapper mapper=new ObjectMapper();
    @Test void retainsNestedJsonAndRedactsCredentialKeys() throws Exception {
        var value=TelemetryPayload.normalize(mapper.readTree("{\"rows\":[[1,true,null]],\"headers\":{\"Authorization\":\"secret\"},\"body\":{\"password\":\"secret\",\"itemId\":7}}"),mapper);
        var node=mapper.valueToTree(value);
        assertEquals("[REDACTED]",node.at("/headers/Authorization").asText());
        assertEquals("[REDACTED]",node.at("/body/password").asText());
        assertEquals(7,node.at("/body/itemId").asInt());assertTrue(node.at("/rows/0/1").asBoolean());
    }
    @Test void rejectsOversizeAndDeepPayloads() throws Exception {
        assertThrows(IllegalArgumentException.class,()->TelemetryPayload.normalize(mapper.valueToTree("a".repeat(17000)),mapper));
        assertThrows(IllegalArgumentException.class,()->TelemetryPayload.normalize(mapper.readTree("[".repeat(14)+"0"+"]".repeat(14)),mapper));
        assertNull(TelemetryPayload.normalize(null,mapper));
    }
    @Test void registrationAliasesMapToTheExistingDeviceContract() throws Exception {
        var device=mapper.readValue("{\"id\":\"device-123456\",\"pushNotificationToken\":\"token\",\"deviceVersion\":\"2.4.1\",\"platform\":\"android\",\"model\":\"Pixel\",\"build\":241,\"language\":\"ar\"}",com.appcontrol.panel.dto.Models.Registration.class);
        assertEquals("device-123456",device.installationId());assertEquals("token",device.fcmToken());assertEquals("2.4.1",device.version());
    }
}
