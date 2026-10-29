package com.websocketlab.server;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ClientController {

    private final ClientRegistry clientRegistry;

    public ClientController(ClientRegistry clientRegistry) {
        this.clientRegistry = clientRegistry;
    }

    @GetMapping("/clients")
    public ResponseEntity<Map<String, List<ClientInfo>>> getClients() {
        return ResponseEntity.ok(clientRegistry.getAllClientsByServer());
    }
}
