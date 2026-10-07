package com.appcontrol.panel;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.Map;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import static com.appcontrol.panel.Models.*;
@RestController
@RequestMapping("/api")
@SecurityRequirement(name="firebaseBearer")
class PanelController {
    private final PanelService service;
    PanelController(PanelService service) { this.service=service; }
    @GetMapping("/admin/stats") Object stats() throws Exception { return service.stats(); }
    @GetMapping("/admin/users") Object users(@RequestParam(defaultValue="") String cursor) throws Exception { return service.page("users",cursor,null); }
    @GetMapping("/admin/users/{uid}") Object user(@PathVariable String uid) throws Exception { return service.user(uid); }
    @GetMapping("/admin/events") Object events(@RequestParam(defaultValue="") String cursor,@RequestParam(defaultValue="") String uid) throws Exception { return service.page("events",cursor,uid); }
    @GetMapping("/admin/notifications") Object notifications(@RequestParam(defaultValue="") String cursor) throws Exception { return service.page("notifications",cursor,null); }
    @PostMapping("/admin/notifications") Object send(@Valid @RequestBody Push push,@RequestHeader("Idempotency-Key") String key,Principal p) throws Exception { return service.push(push,p.getName(),key); }
    @GetMapping("/admin/settings") Object settings() throws Exception { return service.policy(); }
    @PutMapping("/admin/settings") Object settings(@Valid @RequestBody Policy policy,Principal p) throws Exception { return service.savePolicy(policy,p.getName()); }
    @GetMapping("/mobile/config") Object config(@RequestParam String platform,@RequestParam long build,@RequestParam(defaultValue="en") String language) throws Exception {
        return PolicyService.evaluate(service.policy(),platform,build,language);
    }
    @PostMapping("/mobile/devices") Object register(@Valid @RequestBody Registration r,Principal p) throws Exception { service.register(p.getName(),r); return Map.of("ok",true); }
    @PostMapping("/mobile/heartbeat") Object heartbeat(@Valid @RequestBody Heartbeat h,Principal p) throws Exception { service.heartbeat(p.getName(),h.installationId(),false); return Map.of("ok",true); }
    @DeleteMapping("/mobile/devices/{id}") Object unregister(@PathVariable String id,Principal p) throws Exception { service.heartbeat(p.getName(),id,true); return Map.of("ok",true); }
    @PostMapping("/mobile/events") Object event(@Valid @RequestBody Event e,Principal p) throws Exception { service.event(p.getName(),e); return Map.of("ok",true); }
}
