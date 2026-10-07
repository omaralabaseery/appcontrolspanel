package com.appcontrol.panel.service;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.appcontrol.panel.dto.Models.*;
class PolicyServiceTest {
    Policy policy(boolean maintenance) {
        return new Policy(maintenance,"Maintenance","صيانة","Update","تحديث",
            new PlatformPolicy(240,250,"2.5.0","https://play.google.com/store/apps?id=example"),
            new PlatformPolicy(12,15,"1.5.0","https://apps.apple.com/app/id123"));
    }
    @Test void forcesOnlyUnsupportedBuilds() {
        assertEquals("force_update",PolicyService.evaluate(policy(false),"android",239,"en").get("action"));
        assertEquals("optional_update",PolicyService.evaluate(policy(false),"android",240,"en").get("action"));
        assertEquals("allow",PolicyService.evaluate(policy(false),"android",250,"en").get("action"));
        assertEquals("allow",PolicyService.evaluate(policy(false),"android",999,"en").get("action"));
    }
    @Test void handlesArabicLocaleAndSeparatePlatforms() {
        assertEquals("تحديث",PolicyService.evaluate(policy(false),"ios",11,"ar-KW").get("message"));
        assertEquals("allow",PolicyService.evaluate(policy(false),"ios",15,"ar").get("action"));
        assertEquals("Update",PolicyService.evaluate(policy(false),"ios",11,"fr").get("message"));
    }
    @Test void maintenanceTakesPrecedence() {
        var result=PolicyService.evaluate(policy(true),"android",1,"ar");
        assertEquals("maintenance",result.get("action"));assertEquals("صيانة",result.get("message"));assertEquals(true,result.get("updateRequired"));
    }
    @Test void rejectsInvalidPlatformAndBuild() {
        assertThrows(IllegalArgumentException.class,()->PolicyService.evaluate(policy(false),"windows",1,"en"));
        assertThrows(IllegalArgumentException.class,()->PolicyService.evaluate(policy(false),"ios",0,"en"));
    }
    @Test void rejectsUnsafeStoreLinksAndInvertedBuilds() {
        var p=policy(false);PolicyService.validate(p);
        for(var v: new PlatformPolicy[]{new PlatformPolicy(20,10,"1","https://play.google.com/store/apps"),new PlatformPolicy(1,2,"1","https://play.google.com.evil.example/"),new PlatformPolicy(1,2,"1","javascript:alert(1)"),new PlatformPolicy(1,2,"1","https://evil@play.google.com/store/apps")}) {
            assertThrows(IllegalArgumentException.class,()->PolicyService.validate(new Policy(false,"m","m","u","u",v,p.ios())));
        }
    }
}
