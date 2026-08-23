package com.society.module.committee.service;

import com.society.common.FileUploadService;
import com.society.exception.ResourceNotFoundException;
import com.society.module.committee.dto.CommitteeMemberCreateRequest;
import com.society.module.committee.dto.CommitteeMemberDTO;
import com.society.module.committee.dto.CommitteeMemberUpdateRequest;
import com.society.module.committee.entity.CommitteeMember;
import com.society.module.committee.repository.CommitteeMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CommitteeMemberService {

    private final CommitteeMemberRepository repository;
    private final FileUploadService fileUploadService;

    public List<CommitteeMemberDTO> getAllMembers() {
        return repository.findAllByOrderByDisplayOrderAsc()
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public List<CommitteeMemberDTO> getActiveMembers() {
        return repository.findByIsActiveTrueOrderByDisplayOrderAsc()
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public CommitteeMemberDTO getMemberById(Long id) {
        CommitteeMember member = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Committee Member", "memberId", id));
        return toDTO(member);
    }

    @Transactional
    public CommitteeMemberDTO createMember(CommitteeMemberCreateRequest request) {
        CommitteeMember member = CommitteeMember.builder()
                .fullName(request.getFullName())
                .designation(request.getDesignation())
                .phone(request.getPhone())
                .email(request.getEmail())
                .displayOrder(request.getDisplayOrder() != null ? request.getDisplayOrder() : 0)
                .isActive(true)
                .build();

        member = repository.save(member);
        return toDTO(member);
    }

    @Transactional
    public CommitteeMemberDTO updateMember(Long id, CommitteeMemberUpdateRequest request) {
        CommitteeMember member = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Committee Member", "memberId", id));

        member.setFullName(request.getFullName());
        member.setDesignation(request.getDesignation());
        member.setPhone(request.getPhone());
        member.setEmail(request.getEmail());
        if (request.getDisplayOrder() != null) {
            member.setDisplayOrder(request.getDisplayOrder());
        }
        if (request.getIsActive() != null) {
            member.setIsActive(request.getIsActive());
        }

        member = repository.save(member);
        return toDTO(member);
    }

    @Transactional
    public CommitteeMemberDTO uploadPhoto(Long id, MultipartFile file) {
        CommitteeMember member = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Committee Member", "memberId", id));

        String filePath = fileUploadService.uploadFile(file, "committee/" + id);
        member.setPhotoPath(filePath);
        member = repository.save(member);
        return toDTO(member);
    }

    @Transactional
    public void deleteMember(Long id) {
        CommitteeMember member = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Committee Member", "memberId", id));
        repository.delete(member);
    }

    private CommitteeMemberDTO toDTO(CommitteeMember entity) {
        return CommitteeMemberDTO.builder()
                .memberId(entity.getMemberId())
                .fullName(entity.getFullName())
                .designation(entity.getDesignation())
                .photoPath(entity.getPhotoPath())
                .phone(entity.getPhone())
                .email(entity.getEmail())
                .displayOrder(entity.getDisplayOrder())
                .isActive(entity.getIsActive())
                .createdBy(entity.getCreatedBy())
                .createdOn(entity.getCreatedOn())
                .modifiedBy(entity.getModifiedBy())
                .modifiedOn(entity.getModifiedOn())
                .build();
    }
}
