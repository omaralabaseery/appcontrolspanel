package com.appcontrol.panel.controller;
import jakarta.validation.Valid;
import com.appcontrol.panel.service.OccasionService;
import com.appcontrol.panel.service.PanelService;
import com.appcontrol.panel.service.PolicyService;
import com.appcontrol.panel.service.PushRequestService;
import com.fasterxml.jackson.databind.JsonNode;
import java.security.Principal;
import java.util.Map;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import static com.appcontrol.panel.dto.Models.*;
@RestController
@RequestMapping("/api")
@SecurityRequirement(name="firebaseBearer")
class PanelController {
    private final PanelService service;
    private final PushRequestService pushes;
    private final OccasionService occasions;
    PanelController(PanelService service,PushRequestService pushes,OccasionService occasions) { this.service=service;this.pushes=pushes;this.occasions=occasions; }
    @GetMapping("/admin/stats") Object stats() throws Exception { return service.stats(); }
    @GetMapping("/admin/users") Object users(@RequestParam(defaultValue="") String cursor) throws Exception { return service.page("users",cursor,null); }
    @GetMapping("/admin/users/{uid}") Object user(@PathVariable String uid) throws Exception { return service.user(uid); }
    @GetMapping("/admin/events") Object events(@RequestParam(defaultValue="") String cursor,@RequestParam(defaultValue="") String uid) throws Exception { return service.page("events",cursor,uid); }
    @GetMapping("/admin/notifications") Object notifications(@RequestParam(defaultValue="") String cursor) throws Exception { return service.page("notifications",cursor,null); }
    @PostMapping({"/admin/notifications","/admin/notifications/requests"}) Object send(@RequestBody JsonNode push,@RequestHeader("Idempotency-Key") String key,Principal p) throws Exception { return pushes.send(push,p.getName(),key); }
    @GetMapping("/admin/notifications/{id}") Object pushDetails(@PathVariable String id) throws Exception {return pushes.details(id);}
    @GetMapping("/admin/notifications/{id}/recipients") Object pushRecipients(@PathVariable String id,@RequestParam(defaultValue="") String cursor) throws Exception {return pushes.recipients(id,cursor);}
    @GetMapping("/admin/settings") Object settings() throws Exception { return service.policy(); }
    @PutMapping("/admin/settings") Object settings(@Valid @RequestBody Policy policy,Principal p) throws Exception { return service.savePolicy(policy,p.getName()); }
    @GetMapping("/admin/occasions") Object occasions() throws Exception { return occasions.list(); }
    @PostMapping("/admin/occasions") Object createOccasion(@Valid @RequestBody Occasion o,Principal p) throws Exception { return occasions.save(null,o,p.getName()); }
    @PutMapping("/admin/occasions/{id}") Object updateOccasion(@PathVariable String id,@Valid @RequestBody Occasion o,Principal p) throws Exception { return occasions.save(id,o,p.getName()); }
    @DeleteMapping("/admin/occasions/{id}") Object deleteOccasion(@PathVariable String id,Principal p) throws Exception { occasions.delete(id,p.getName()); return Map.of("ok",true); }
    @GetMapping("/mobile/occasions") Object activeOccasions(@RequestParam(defaultValue="en") String language) throws Exception { return occasions.active(language); }
    @GetMapping("/mobile/config") Object config(@RequestParam String platform,@RequestParam long build,@RequestParam(defaultValue="en") String language) throws Exception {
        return PolicyService.evaluate(service.policy(),platform,build,language);
    }
    @PostMapping({"/mobile/devices","/mobile/settings/register-device"}) Object register(@Valid @RequestBody Registration r,Principal p) throws Exception { service.register(p.getName(),r); return Map.of("ok",true); }
    @PostMapping("/mobile/heartbeat") Object heartbeat(@Valid @RequestBody Heartbeat h,Principal p) throws Exception { service.heartbeat(p.getName(),h.installationId(),false); return Map.of("ok",true); }
    @DeleteMapping("/mobile/devices/{id}") Object unregister(@PathVariable String id,Principal p) throws Exception { service.heartbeat(p.getName(),id,true); return Map.of("ok",true); }
    @PostMapping("/mobile/events") Object event(@Valid @RequestBody Event e,Principal p) throws Exception { service.event(p.getName(),e); return Map.of("ok",true); }
}
