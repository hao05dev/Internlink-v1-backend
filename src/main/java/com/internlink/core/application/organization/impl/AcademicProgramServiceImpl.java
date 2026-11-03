package com.internlink.core.application.organization.impl;

import com.internlink.core.application.organization.AcademicProgramService;
import com.internlink.core.application.system.AuditLogService;
import com.internlink.core.domain.organization.AcademicProgram;
import com.internlink.core.domain.organization.Department;
import com.internlink.core.infrastructure.persistence.jpa.JpaAcademicProgramRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaAssessmentSchemeRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaDepartmentRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaStudentProfileRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaStudentRosterRepository;
import com.internlink.core.presentation.organization.dto.request.AcademicProgramRequest;
import com.internlink.core.presentation.organization.dto.response.AcademicProgramResponse;
import com.internlink.core.shared.exception.BadRequestException;
import com.internlink.core.shared.exception.ResourceNotFoundException;
import com.internlink.core.shared.security.SecurityGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AcademicProgramServiceImpl implements AcademicProgramService {

    private final JpaAcademicProgramRepository programRepository;
    private final JpaDepartmentRepository departmentRepository;
    private final JpaStudentProfileRepository studentProfileRepository;
    private final JpaStudentRosterRepository studentRosterRepository;
    private final JpaAssessmentSchemeRepository assessmentSchemeRepository;
    private final AuditLogService auditLogService;
    private final SecurityGuard securityGuard;

    @Override
    @Transactional(readOnly = true)
    // Cached in Redis with NON_FINAL default typing: Stream.toList() returns a final JDK class
    // that is written without a type id and cannot be read back, so collect into an ArrayList.
    @Cacheable(value = "academic_programs", key = "'all'")
    public List<AcademicProgramResponse> getAllPrograms() {
        return programRepository.findAll().stream()
            .map(this::mapToResponse)
            .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public AcademicProgramResponse getProgramById(UUID id) {
        AcademicProgram program = programRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("AcademicProgram", "id", id));
        return mapToResponse(program);
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "academic_programs", key = "#departmentId")
    public List<AcademicProgramResponse> getProgramsByDepartment(UUID departmentId) {
        return programRepository.findByDepartmentId(departmentId).stream()
            .map(this::mapToResponse)
            .collect(Collectors.toList());
    }

    @Override
    @Transactional
    @CacheEvict(value = "academic_programs", allEntries = true)
    public AcademicProgramResponse createProgram(AcademicProgramRequest request) {
        Department department = departmentRepository.findById(request.getDepartmentId())
            .orElseThrow(() -> new ResourceNotFoundException("Department", "id", request.getDepartmentId()));

        if (programRepository.existsByCode(request.getCode().trim().toUpperCase())) {
            throw new BadRequestException("Mã ngành '" + request.getCode() + "' đã tồn tại trong hệ thống");
        }
        if (!List.of("REGULAR", "CTCLC").contains(request.getTrack()))
            throw new BadRequestException("Loại chương trình chỉ có thể là REGULAR hoặc CTCLC");

        AcademicProgram program = AcademicProgram.builder()
            .department(department)
            .code(request.getCode().trim().toUpperCase())
            .name(request.getName().trim())
            .degreeLevel(request.getDegreeLevel() != null ? request.getDegreeLevel().trim() : "UNDERGRADUATE")
            .track(request.getTrack())
            .isActive(request.getIsActive() != null ? request.getIsActive() : true)
            .build();

        AcademicProgram saved = programRepository.save(program);
        auditLogService.logAction(securityGuard.currentUser().getId(), "CREATE_ACADEMIC_PROGRAM",
            "AcademicProgram", saved.getId(), "SUCCESS", Map.of("code", saved.getCode()), null);
        return mapToResponse(saved);
    }

    @Override
    @Transactional
    @CacheEvict(value = "academic_programs", allEntries = true)
    public AcademicProgramResponse updateProgram(UUID id, AcademicProgramRequest request) {
        AcademicProgram program = programRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("AcademicProgram", "id", id));

        Department department = departmentRepository.findById(request.getDepartmentId())
            .orElseThrow(() -> new ResourceNotFoundException("Department", "id", request.getDepartmentId()));

        String newCode = request.getCode().trim().toUpperCase();
        programRepository.findByCode(newCode)
            .filter(existing -> !existing.getId().equals(id))
            .ifPresent(existing -> {
                throw new BadRequestException("Mã ngành '" + newCode + "' đã được sử dụng bởi ngành khác");
            });

        if (!List.of("REGULAR", "CTCLC").contains(request.getTrack())) {
            throw new BadRequestException("Loại chương trình chỉ có thể là REGULAR hoặc CTCLC");
        }

        program.setDepartment(department);
        program.setCode(newCode);
        program.setName(request.getName().trim());
        if (request.getDegreeLevel() != null) {
            program.setDegreeLevel(request.getDegreeLevel().trim());
        }
        program.setTrack(request.getTrack());
        if (request.getIsActive() != null) {
            program.setIsActive(request.getIsActive());
        }

        AcademicProgram saved = programRepository.save(program);
        auditLogService.logAction(securityGuard.currentUser().getId(), "UPDATE_ACADEMIC_PROGRAM",
            "AcademicProgram", saved.getId(), "SUCCESS", Map.of("code", saved.getCode(), "isActive", saved.getIsActive()), null);
        return mapToResponse(saved);
    }

    @Override
    @Transactional
    @CacheEvict(value = "academic_programs", allEntries = true)
    public void deleteProgram(UUID id) {
        AcademicProgram program = programRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("AcademicProgram", "id", id));

        if (studentProfileRepository.existsByProgram_Id(id)
            || studentRosterRepository.existsByProgram_Id(id)
            || assessmentSchemeRepository.existsByProgram_Id(id)) {
            throw new BadRequestException("Không thể xóa ngành '" + program.getName()
                + "' vì đã có hồ sơ sinh viên, danh sách thực tập hoặc khung đánh giá liên kết. Vui lòng chuyển trạng thái sang 'Ngừng hoạt động' thay vì xóa.");
        }

        programRepository.delete(program);
        auditLogService.logAction(securityGuard.currentUser().getId(), "DELETE_ACADEMIC_PROGRAM",
            "AcademicProgram", id, "SUCCESS", Map.of("code", program.getCode()), null);
    }

    private AcademicProgramResponse mapToResponse(AcademicProgram entity) {
        return AcademicProgramResponse.builder()
            .id(entity.getId())
            .departmentId(entity.getDepartment().getId())
            .departmentName(entity.getDepartment().getName())
            .code(entity.getCode())
            .name(entity.getName())
            .degreeLevel(entity.getDegreeLevel())
            .track(entity.getTrack())
            .isActive(entity.getIsActive())
            .createdAt(entity.getCreatedAt())
            .build();
    }
}
