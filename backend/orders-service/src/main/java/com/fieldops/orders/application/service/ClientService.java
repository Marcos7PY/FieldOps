package com.fieldops.orders.application.service;

import com.fieldops.orders.application.dto.ClientResponse;
import com.fieldops.orders.application.dto.CreateClientRequest;
import com.fieldops.orders.application.dto.PageResponse;
import com.fieldops.orders.domain.exception.BusinessRuleViolationException;
import com.fieldops.orders.domain.exception.ResourceNotFoundException;
import com.fieldops.orders.domain.model.Client;
import com.fieldops.orders.infrastructure.persistence.ClientRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class ClientService {

    private final ClientRepository clientRepository;
    private final WorkOrderMapper mapper;

    public ClientService(ClientRepository clientRepository, WorkOrderMapper mapper) {
        this.clientRepository = clientRepository;
        this.mapper = mapper;
    }

    public ClientResponse createClient(CreateClientRequest request) {
        if (clientRepository.findByTaxId(request.taxId()).isPresent()) {
            throw new BusinessRuleViolationException("Client already exists with tax id: " + request.taxId());
        }

        Client client = new Client();
        client.setBusinessName(request.businessName().trim());
        client.setTaxId(request.taxId().trim());
        client.setAddress(request.address() != null ? request.address().trim() : null);
        client.setLatitude(request.latitude());
        client.setLongitude(request.longitude());
        client.setPhone(request.phone() != null ? request.phone().trim() : null);
        client.setActive(true);

        Client saved = clientRepository.save(client);
        return mapper.toClientResponse(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<ClientResponse> getClients(Pageable pageable) {
        Page<Client> page = clientRepository.findByActiveTrue(pageable);
        List<ClientResponse> content = page.getContent().stream().map(mapper::toClientResponse).toList();
        return new PageResponse<>(
                content,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast()
        );
    }

    @Transactional(readOnly = true)
    public ClientResponse getClientById(Long id) {
        Client client = clientRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Client not found with id: " + id));
        return mapper.toClientResponse(client);
    }
}
