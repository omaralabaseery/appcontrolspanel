package com.appcontrol.panel.controller;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.security.Principal;
import com.appcontrol.panel.service.DatabaseService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/database")
@SecurityRequirement(name="firebaseBearer")
class DatabaseController {
    private final DatabaseService database;
    DatabaseController(DatabaseService database) { this.database=database; }
    @GetMapping("/schema") Object schema() { return database.schema(); }
    @GetMapping Object status() throws Exception { return database.status(); }
    @PostMapping("/initialize") Object initialize(Principal principal) throws Exception {
        return database.initialize(principal.getName());
    }
}
