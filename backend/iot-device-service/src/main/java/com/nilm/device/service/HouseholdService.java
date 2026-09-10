package com.nilm.device.service;

import com.nilm.device.api.dto.HouseholdDtos;
import com.nilm.device.common.DuplicateResourceException;
import com.nilm.device.common.NotFoundException;
import com.nilm.device.domain.Household;
import com.nilm.device.repository.HouseholdRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class HouseholdService {

    private final HouseholdRepository householdRepository;

    public HouseholdService(HouseholdRepository householdRepository) {
        this.householdRepository = householdRepository;
    }

    @Transactional
    public HouseholdDtos.Response create(HouseholdDtos.CreateRequest request) {
        if (householdRepository.existsById(request.houseId())) {
            throw new DuplicateResourceException("가구", request.houseId());
        }
        Household saved = householdRepository.saveAndFlush(
                new Household(request.houseId(), request.alias(), request.graceMinutes()));
        return HouseholdDtos.Response.from(saved);
    }

    public HouseholdDtos.Response get(String houseId) {
        Household household = householdRepository.findById(houseId)
                .orElseThrow(() -> new NotFoundException("가구", houseId));
        return HouseholdDtos.Response.from(household);
    }

    public List<HouseholdDtos.Response> list() {
        return householdRepository.findAll().stream()
                .map(HouseholdDtos.Response::from)
                .toList();
    }
}
