package com.appcontrol.panel.controller;

import com.appcontrol.panel.service.DemoSeedService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.security.Principal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/database/demo")
@SecurityRequirement(name="firebaseBearer")
class DemoSeedController {
    private final DemoSeedService service;
    DemoSeedController(DemoSeedService service) {this.service=service;}
    public record Confirmation(String projectId) {}
    @PostMapping Object seed(@RequestBody Confirmation request,Principal principal) throws Exception {
        return service.apply(principal.getName(),request.projectId(),false);
    }
    @DeleteMapping Object remove(@RequestBody Confirmation request,Principal principal) throws Exception {
        return service.apply(principal.getName(),request.projectId(),true);
    }
}
