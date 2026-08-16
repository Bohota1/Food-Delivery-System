package com.fooddelivery.deliveryservice.service;

import com.fooddelivery.deliveryservice.dto.RegisterRiderRequest;
import com.fooddelivery.deliveryservice.dto.RiderResponse;
import com.fooddelivery.deliveryservice.exception.ResourceNotFoundException;
import com.fooddelivery.deliveryservice.model.Rider;
import com.fooddelivery.deliveryservice.repository.RiderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RiderService {

    private final RiderRepository riderRepository;

    public RiderResponse register(RegisterRiderRequest request) {
        Rider rider = Rider.builder()
                .name(request.getName())
                .phone(request.getPhone())
                .vehicleType(request.getVehicleType())
                .available(true)
                .build();

        return toResponse(riderRepository.save(rider));
    }

    public List<RiderResponse> getAll() {
        return riderRepository.findAll().stream().map(this::toResponse).toList();
    }

    public List<RiderResponse> getAvailable() {
        return riderRepository.findByAvailableTrue().stream().map(this::toResponse).toList();
    }

    public RiderResponse getById(String riderId) {
        return toResponse(findOrThrow(riderId));
    }

    public RiderResponse setAvailability(String riderId, boolean available) {
        Rider rider = findOrThrow(riderId);
        rider.setAvailable(available);
        return toResponse(riderRepository.save(rider));
    }

    Rider findOrThrow(String riderId) {
        return riderRepository.findById(riderId)
                .orElseThrow(() -> new ResourceNotFoundException("Rider not found: " + riderId));
    }

    private RiderResponse toResponse(Rider rider) {
        return RiderResponse.builder()
                .id(rider.getId())
                .name(rider.getName())
                .phone(rider.getPhone())
                .vehicleType(rider.getVehicleType())
                .available(rider.isAvailable())
                .currentDeliveryId(rider.getCurrentDeliveryId())
                .build();
    }
}
