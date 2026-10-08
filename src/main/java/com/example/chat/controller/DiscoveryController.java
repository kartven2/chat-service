package com.example.chat.controller;

import com.example.chat.service.ServiceDiscoveryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Service discovery REST endpoint.
 * Per README: "Service discovery service gives the client a list of DNS hostnames
 * of the chat servers that the client could connect to."
 *
 * GET /api/discovery/servers → list of available chat server addresses
 */
@RestController
@RequestMapping("/api/discovery")
public class DiscoveryController {

    private final ServiceDiscoveryService discoveryService;

    public DiscoveryController(ServiceDiscoveryService discoveryService) {
        this.discoveryService = discoveryService;
    }

    @GetMapping("/servers")
    public ResponseEntity<Map<String, List<String>>> getAvailableServers() {
        return ResponseEntity.ok(Map.of("servers", discoveryService.getAvailableServers()));
    }
}
