package com.fieldops.orders.api.controller;

import com.fieldops.orders.application.dto.ClientResponse;
import com.fieldops.orders.application.dto.CreateClientRequest;
import com.fieldops.orders.application.dto.PageResponse;
import com.fieldops.orders.application.service.ClientService;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/clients")
public class ClientController {

    private final ClientService clientService;

    public ClientController(ClientService clientService) {
        this.clientService = clientService;
    }

    @PostMapping
    public ResponseEntity<ClientResponse> createClient(@Valid @RequestBody CreateClientRequest request) {
        ClientResponse response = clientService.createClient(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.id())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping
    public ResponseEntity<PageResponse<ClientResponse>> getClients(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        int boundedSize = Math.min(Math.max(1, size), 100);
        Pageable pageable = PageRequest.of(page, boundedSize, Sort.by(Sort.Direction.ASC, "businessName"));
        return ResponseEntity.ok(clientService.getClients(pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ClientResponse> getClientById(@PathVariable("id") Long id) {
        return ResponseEntity.ok(clientService.getClientById(id));
    }
}