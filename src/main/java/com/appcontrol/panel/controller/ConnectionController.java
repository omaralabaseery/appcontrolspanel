package com.appcontrol.panel.controller;
import com.appcontrol.panel.service.ConnectionService;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/connection")
class ConnectionController {
    private final ConnectionService connection;
    ConnectionController(ConnectionService connection) {this.connection=connection;}
    @GetMapping Object status() {return connection.status();}
}
