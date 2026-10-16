package es.devolvapp.backend.controller;

import es.devolvapp.backend.dto.CollectionOrderResponse;
import es.devolvapp.backend.dto.CreateCollectionOrderRequest;
import es.devolvapp.backend.service.CollectionOrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/pedidos")
@RequiredArgsConstructor
public class CollectionOrderController {

    private final CollectionOrderService collectionOrderService;

    @PostMapping
    public ResponseEntity<CollectionOrderResponse> create(@Valid @RequestBody CreateCollectionOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(collectionOrderService.create(request));
    }
}
