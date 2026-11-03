package com.omnibid.controller;

import com.omnibid.common.ApiResponse;
import com.omnibid.dto.shipment.ShipmentDetailResponse;
import com.omnibid.dto.shipment.ShipmentStatusResponse;
import com.omnibid.dto.shipment.ShipmentStatusUpdateRequest;
import com.omnibid.security.CurrentUser;
import com.omnibid.security.CustomUserDetails;
import com.omnibid.service.ShipmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class ShipmentController {

    private final ShipmentService shipmentService;

    @GetMapping("/api/v1/orders/{orderId}/shipment")
    public ResponseEntity<ApiResponse<ShipmentDetailResponse>> getShipment(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long orderId) {

        CurrentUser currentUser = CurrentUser.of(userDetails);
        ShipmentDetailResponse response = shipmentService.getShipmentByOrder(currentUser, orderId);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PatchMapping("/api/v1/shipments/{id}/status")
    public ResponseEntity<ApiResponse<ShipmentStatusResponse>> updateStatus(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody ShipmentStatusUpdateRequest request) {

        CurrentUser currentUser = CurrentUser.of(userDetails);
        ShipmentStatusResponse response = shipmentService.updateShipmentStatus(currentUser, id, request);

        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
