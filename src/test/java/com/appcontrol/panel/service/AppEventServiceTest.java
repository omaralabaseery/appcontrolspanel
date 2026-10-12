package com.appcontrol.panel.service;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.appcontrol.panel.dto.Models.*;
class AppEventServiceTest {
    AppEvent appEvent(long start,long end,String banner) {
        return new AppEvent("National Day","العيد الوطني","Happy National Day","كل عام وأنتم بخير",start,end,true,"national_day","#007a3d",banner);
    }
    Map<String,Object> row(boolean enabled,long start,long end) {
        var row=new HashMap<String,Object>(Map.of("id","o1","titleEn","National Day","titleAr","العيد الوطني","messageEn","Happy","messageAr","مبارك",
            "enabled",enabled,"startAt",start,"endAt",end,"createdAt",1L));
        row.put("iconKey","national_day"); row.put("accentColor",null);
        return row;
    }
    @Test void periodMustEndAfterItStarts() {
        AppEventService.validate(appEvent(1000,2000,null));
        assertThrows(IllegalArgumentException.class,()->AppEventService.validate(appEvent(2000,2000,null)));
        assertThrows(IllegalArgumentException.class,()->AppEventService.validate(appEvent(2000,1000,null)));
    }
    @Test void bannerMustBePlainHttps() {
        AppEventService.validate(appEvent(1,2,"https://cdn.example.com/banner.png"));
        for(String url:List.of("http://cdn.example.com/b.png","javascript:alert(1)","https://user@cdn.example.com/b.png","not a url","https:///b.png"))
            assertThrows(IllegalArgumentException.class,()->AppEventService.validate(appEvent(1,2,url)),url);
    }
    @Test void activeOnlyWhenEnabledAndInsidePeriod() {
        assertFalse(AppEventService.activeAt(row(true,1000,2000),999));
        assertTrue(AppEventService.activeAt(row(true,1000,2000),1000));
        assertTrue(AppEventService.activeAt(row(true,1000,2000),1999));
        assertFalse(AppEventService.activeAt(row(true,1000,2000),2000));
        assertFalse(AppEventService.activeAt(row(false,1000,2000),1500));
    }
    @Test void mobileViewIsLocalizedAndOmitsAdminFields() {
        var ar=AppEventService.present(row(true,1000,2000),true); var en=AppEventService.present(row(true,1000,2000),false);
        assertEquals("العيد الوطني",ar.get("title")); assertEquals("مبارك",ar.get("message"));
        assertEquals("National Day",en.get("title")); assertEquals("national_day",en.get("iconKey"));
        assertTrue(en.containsKey("accentColor")); assertNull(en.get("accentColor"));
        assertEquals(Set.of("id","title","message","iconKey","accentColor","bannerUrl","startAt","endAt"),en.keySet());
    }
}
