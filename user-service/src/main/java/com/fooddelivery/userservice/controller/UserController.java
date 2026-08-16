package com.fooddelivery.userservice.controller;

import com.fooddelivery.userservice.dto.*;
import com.fooddelivery.userservice.model.Address;
import com.fooddelivery.userservice.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    // ---------- Public endpoints ----------

    @PostMapping("/register")
    public ResponseEntity<UserProfileResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(userService.login(request));
    }

    // ---------- Authenticated endpoints (JWT required) ----------
    // The caller's userId is resolved from the JWT (set into the security
    // context by JwtAuthFilter) rather than trusted from a path variable.

    @GetMapping("/profile")
    public ResponseEntity<UserProfileResponse> getProfile(Authentication authentication) {
        String userId = authentication.getName();
        return ResponseEntity.ok(userService.getProfile(userId));
    }

    @PutMapping("/profile")
    public ResponseEntity<UserProfileResponse> updateProfile(Authentication authentication,
                                                               @Valid @RequestBody UpdateProfileRequest request) {
        String userId = authentication.getName();
        return ResponseEntity.ok(userService.updateProfile(userId, request));
    }

    @GetMapping("/addresses")
    public ResponseEntity<List<Address>> getAddresses(Authentication authentication) {
        String userId = authentication.getName();
        return ResponseEntity.ok(userService.getAddresses(userId));
    }

    @PostMapping("/addresses")
    public ResponseEntity<List<Address>> addAddress(Authentication authentication,
                                                      @Valid @RequestBody AddressRequest request) {
        String userId = authentication.getName();
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.addAddress(userId, request));
    }

    @PutMapping("/addresses/{addressId}")
    public ResponseEntity<List<Address>> updateAddress(Authentication authentication,
                                                         @PathVariable String addressId,
                                                         @Valid @RequestBody AddressRequest request) {
        String userId = authentication.getName();
        return ResponseEntity.ok(userService.updateAddress(userId, addressId, request));
    }

    @DeleteMapping("/addresses/{addressId}")
    public ResponseEntity<List<Address>> deleteAddress(Authentication authentication,
                                                         @PathVariable String addressId) {
        String userId = authentication.getName();
        return ResponseEntity.ok(userService.deleteAddress(userId, addressId));
    }

    // ---------- Internal, service-to-service endpoint ----------
    // Called by Order/Delivery services (via Eureka + REST) to resolve basic
    // customer info without requiring the calling service to hold a user JWT.
    // In production this should be locked down further (e.g. network policy
    // or an internal API key) rather than left fully open.

    @GetMapping("/internal/{userId}")
    public ResponseEntity<UserProfileResponse> getUserForInternalCall(@PathVariable String userId) {
        return ResponseEntity.ok(userService.getProfile(userId));
    }
}
