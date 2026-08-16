package com.fooddelivery.deliveryservice.controller;

import com.fooddelivery.deliveryservice.dto.RegisterRiderRequest;
import com.fooddelivery.deliveryservice.dto.RiderResponse;
import com.fooddelivery.deliveryservice.service.RiderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/riders")
@RequiredArgsConstructor
public class RiderController {

    private final RiderService riderService;

    @PostMapping
    public ResponseEntity<RiderResponse> register(@Valid @RequestBody RegisterRiderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(riderService.register(request));
    }

    @GetMapping
    public ResponseEntity<List<RiderResponse>> getAll() {
        return ResponseEntity.ok(riderService.getAll());
    }

    @GetMapping("/available")
    public ResponseEntity<List<RiderResponse>> getAvailable() {
        return ResponseEntity.ok(riderService.getAvailable());
    }

    @GetMapping("/{riderId}")
    public ResponseEntity<RiderResponse> getById(@PathVariable String riderId) {
        return ResponseEntity.ok(riderService.getById(riderId));
    }

    /** Body: {"available": true|false}. Lets a rider go on/off duty. */
    @PatchMapping("/{riderId}/availability")
    public ResponseEntity<RiderResponse> setAvailability(@PathVariable String riderId,
                                                           @RequestBody Map<String, Boolean> body) {
        boolean available = Boolean.TRUE.equals(body.get("available"));
        return ResponseEntity.ok(riderService.setAvailability(riderId, available));
    }
}
