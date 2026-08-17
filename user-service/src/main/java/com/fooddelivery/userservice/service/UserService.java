package com.fooddelivery.userservice.service;

import com.fooddelivery.userservice.dto.*;
import com.fooddelivery.userservice.exception.DuplicateEmailException;
import com.fooddelivery.userservice.exception.InvalidCredentialsException;
import com.fooddelivery.userservice.exception.ResourceNotFoundException;
import com.fooddelivery.userservice.model.Address;
import com.fooddelivery.userservice.model.Roles;
import com.fooddelivery.userservice.model.User;
import com.fooddelivery.userservice.repository.UserRepository;
import com.fooddelivery.userservice.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public UserProfileResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateEmailException(request.getEmail());
        }

        User user = User.builder()
                .fullName(request.getFullName())
                .email(request.getEmail().toLowerCase())
                .password(passwordEncoder.encode(request.getPassword()))
                .phone(request.getPhone())
                .role(Roles.normalise(request.getRole()))
                .active(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        User saved = userRepository.save(user);
        return toProfileResponse(saved);
    }

    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail().toLowerCase())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new InvalidCredentialsException("Invalid email or password");
        }

        String role = Roles.normalise(user.getRole());
        String token = jwtUtil.generateToken(user.getId(), user.getEmail(), role);

        return LoginResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .userId(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .role(role)
                .build();
    }

    public UserProfileResponse getProfile(String userId) {
        User user = findUserOrThrow(userId);
        return toProfileResponse(user);
    }

    public UserProfileResponse updateProfile(String userId, UpdateProfileRequest request) {
        User user = findUserOrThrow(userId);

        if (request.getFullName() != null && !request.getFullName().isBlank()) {
            user.setFullName(request.getFullName());
        }
        if (request.getPhone() != null && !request.getPhone().isBlank()) {
            user.setPhone(request.getPhone());
        }
        user.setUpdatedAt(LocalDateTime.now());

        User saved = userRepository.save(user);
        return toProfileResponse(saved);
    }

    public List<Address> getAddresses(String userId) {
        return findUserOrThrow(userId).getAddresses();
    }

    public List<Address> addAddress(String userId, AddressRequest request) {
        User user = findUserOrThrow(userId);

        Address newAddress = Address.builder()
                .id(UUID.randomUUID().toString())
                .label(request.getLabel())
                .street(request.getStreet())
                .city(request.getCity())
                .state(request.getState())
                .zipCode(request.getZipCode())
                .country(request.getCountry())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .isDefault(request.isDefault())
                .build();

        if (newAddress.isDefault()) {
            user.getAddresses().forEach(a -> a.setDefault(false));
        }
        // First address added is default by default
        if (user.getAddresses().isEmpty()) {
            newAddress.setDefault(true);
        }

        user.getAddresses().add(newAddress);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
        return user.getAddresses();
    }

    public List<Address> updateAddress(String userId, String addressId, AddressRequest request) {
        User user = findUserOrThrow(userId);

        Address existing = user.getAddresses().stream()
                .filter(a -> a.getId().equals(addressId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Address not found: " + addressId));

        existing.setLabel(request.getLabel());
        existing.setStreet(request.getStreet());
        existing.setCity(request.getCity());
        existing.setState(request.getState());
        existing.setZipCode(request.getZipCode());
        existing.setCountry(request.getCountry());
        existing.setLatitude(request.getLatitude());
        existing.setLongitude(request.getLongitude());

        if (request.isDefault()) {
            user.getAddresses().forEach(a -> a.setDefault(a.getId().equals(addressId)));
        }

        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
        return user.getAddresses();
    }

    public List<Address> deleteAddress(String userId, String addressId) {
        User user = findUserOrThrow(userId);

        boolean removed = user.getAddresses().removeIf(a -> a.getId().equals(addressId));
        if (!removed) {
            throw new ResourceNotFoundException("Address not found: " + addressId);
        }

        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
        return user.getAddresses();
    }

    private User findUserOrThrow(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
    }

    private UserProfileResponse toProfileResponse(User user) {
        return UserProfileResponse.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .role(Roles.normalise(user.getRole()))
                .addresses(user.getAddresses())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
