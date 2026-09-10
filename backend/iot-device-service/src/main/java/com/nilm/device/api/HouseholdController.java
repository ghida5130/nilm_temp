package com.nilm.device.api;

import com.nilm.device.api.dto.HouseholdDtos;
import com.nilm.device.service.HouseholdService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Household", description = "가구 등록·조회")
@RestController
@RequestMapping("/api/devices/households")
public class HouseholdController {

    private final HouseholdService householdService;

    public HouseholdController(HouseholdService householdService) {
        this.householdService = householdService;
    }

    @Operation(summary = "가구 등록")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public HouseholdDtos.Response create(@Valid @RequestBody HouseholdDtos.CreateRequest request) {
        return householdService.create(request);
    }

    @Operation(summary = "가구 목록 조회")
    @GetMapping
    public List<HouseholdDtos.Response> list() {
        return householdService.list();
    }

    @Operation(summary = "가구 단건 조회")
    @GetMapping("/{houseId}")
    public HouseholdDtos.Response get(@PathVariable String houseId) {
        return householdService.get(houseId);
    }
}
