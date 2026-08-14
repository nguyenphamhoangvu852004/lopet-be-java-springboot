package com.nguyenvu.lopet.pet;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nguyenvu.lopet.common.exception.ConflictException;
import com.nguyenvu.lopet.common.exception.ForbiddenException;
import com.nguyenvu.lopet.common.exception.NotFoundException;
import com.nguyenvu.lopet.pet.dto.PetDtos;
import com.nguyenvu.lopet.pet.entity.Pet;
import com.nguyenvu.lopet.pet.entity.PetOwnership;
import com.nguyenvu.lopet.pet.entity.PetOwnershipType;
import com.nguyenvu.lopet.pet.entity.PetStatus;
import com.nguyenvu.lopet.pet.repository.PetOwnershipRepository;
import com.nguyenvu.lopet.pet.repository.PetRepository;

import lombok.RequiredArgsConstructor;

/**
 * Toàn bộ quy tắc nghiệp vụ của Pet Core nằm ở đây, không có dòng nào trong controller.
 *
 * <p>{@code callerId}/{@code ownerId} LUÔN là id lấy từ token (controller truyền
 * {@code CurrentUser.require().id()}), không bao giờ từ body — mọi tham số nhận từ client chỉ là
 * dữ liệu hồ sơ, không phải danh tính.
 *
 * <p>{@code viewerId} của luồng đọc thì có thể {@code null} = khách chưa đăng nhập, và phải được
 * truyền xuống repository để lọc quyền xem NGAY TRONG câu truy vấn.
 */
@Service
@RequiredArgsConstructor
public class PetService {

    private final PetRepository petRepository;
    private final PetOwnershipRepository petOwnershipRepository;

    /**
     * Tạo hồ sơ và trao quyền PRIMARY_OWNER cho chính người tạo, trong MỘT transaction.
     *
     * <p>Nếu bước trao quyền hỏng thì bản ghi {@code pets} cũng bị rollback: một thú cưng không có
     * chủ là hồ sơ vĩnh viễn không ai sửa hay xoá được, vì mọi kiểm tra quyền của module này đều
     * hỏi {@code pet_ownerships}.
     */
    @Transactional
    public PetDtos.PetDetail create(Integer ownerId, PetDtos.CreatePetRequest request) {
        Pet pet = Pet.builder()
                .name(PetPolicy.requireName(request.name()))
                .species(PetPolicy.parseSpecies(request.species()))
                .breed(PetPolicy.trimToNull(request.breed()))
                .gender(PetPolicy.parseGender(request.gender()))
                .dateOfBirth(PetPolicy.requireDateOfBirth(request.dateOfBirth()))
                .bio(PetPolicy.trimToNull(request.bio()))
                .status(PetStatus.ACTIVE)
                .visibility(PetPolicy.parseVisibility(request.visibility()))
                .build();

        Pet saved = petRepository.save(pet);
        addOwner(saved.getId(), ownerId, PetOwnershipType.PRIMARY_OWNER);
        return PetMapper.toDetail(saved, ownerId);
    }

    /**
     * Thêm một chủ sở hữu. Chưa endpoint nào gọi tới — quản lý co-owner là use case tương lai — nhưng
     * quy tắc "mỗi thú cưng chỉ có MỘT PRIMARY_OWNER" phải sống ở đây ngay từ đầu, vì đây là cửa duy
     * nhất ghi vào {@code pet_ownerships}.
     *
     * <p>Đặt ràng buộc này vào khoá chính là sai: PK {@code (pet_id, user_id)} diễn tả "một người chỉ
     * giữ một vai trò trên một thú cưng"; thêm {@code ownership_type} vào PK sẽ cho phép đúng cái
     * trạng thái vô nghĩa đó — cùng một người vừa là PRIMARY vừa là CO_OWNER.
     */
    @Transactional
    public void addOwner(Integer petId, Integer userId, PetOwnershipType ownershipType) {
        if (ownershipType == PetOwnershipType.PRIMARY_OWNER
                && petOwnershipRepository.existsByPetIdAndOwnershipType(petId, PetOwnershipType.PRIMARY_OWNER)) {
            throw new ConflictException("Thú cưng này đã có chủ sở hữu chính");
        }
        if (petOwnershipRepository.findByPetIdAndUserId(petId, userId).isPresent()) {
            throw new ConflictException("Tài khoản này đã là chủ sở hữu của thú cưng");
        }

        petOwnershipRepository.save(PetOwnership.builder()
                .petId(petId)
                .userId(userId)
                .ownershipType(ownershipType)
                .build());
    }

    /**
     * Trả 404 (không phải 403) khi người xem không đủ quyền: phản hồi phải giống hệt trường hợp hồ
     * sơ không tồn tại, nếu không endpoint này thành công cụ dò xem một thú cưng PRIVATE có tồn tại
     * hay không — giống hệt lý do ở {@code PostService.getOneById}.
     */
    @Transactional(readOnly = true)
    public PetDtos.PetDetail getOneById(Integer petId, Integer viewerId) {
        Pet pet = petRepository.findVisibleById(petId, viewerId).orElseThrow(NotFoundException::new);
        return PetMapper.toDetail(pet, primaryOwnerIdOf(petId));
    }

    /**
     * Thú cưng của chính người gọi, gồm cả những con mình chỉ là CO_OWNER.
     *
     * <p>Vai trò sở hữu được nạp bằng MỘT truy vấn cho cả danh sách rồi ghép trong bộ nhớ; hỏi
     * từng con một sẽ thành N+1.
     */
    @Transactional(readOnly = true)
    public List<PetDtos.PetListItem> getOwnedBy(Integer ownerId) {
        Map<Integer, PetOwnershipType> roles = petOwnershipRepository.findAllByUserId(ownerId).stream()
                .collect(Collectors.toMap(PetOwnership::getPetId, PetOwnership::getOwnershipType,
                        (first, second) -> first));

        return petRepository.findAllOwnedBy(ownerId).stream()
                .map(pet -> PetMapper.toListItem(pet, roles.get(pet.getId())))
                .toList();
    }

    /**
     * Sửa hồ sơ. Cả PRIMARY_OWNER lẫn CO_OWNER đều sửa được — đồng sở hữu mà không sửa được hồ sơ
     * thì vai trò đó không có nghĩa gì.
     *
     * <p>Kiểm tra sở hữu ở đây là lớp thứ hai (lớp thứ nhất là {@link PetAccessGuard} trên
     * controller): lớp trong này chặn cả trường hợp service được gọi từ nơi khác, đúng cách
     * {@code PostService.update} đang làm.
     */
    @Transactional
    public PetDtos.PetDetail update(Integer petId, Integer callerId, PetDtos.UpdatePetRequest request) {
        Pet pet = petRepository.findVisibleById(petId, callerId).orElseThrow(NotFoundException::new);
        requireOwner(petId, callerId);

        pet.setName(PetPolicy.requireName(request.name()));
        pet.setSpecies(PetPolicy.parseSpecies(request.species()));
        pet.setBreed(PetPolicy.trimToNull(request.breed()));
        pet.setGender(PetPolicy.parseGender(request.gender()));
        pet.setDateOfBirth(PetPolicy.requireDateOfBirth(request.dateOfBirth()));
        pet.setBio(PetPolicy.trimToNull(request.bio()));
        pet.setVisibility(PetPolicy.parseVisibility(request.visibility()));
        // status/deletedAt/createdAt/updatedAt cố ý KHÔNG xuất hiện ở đây: chúng do domain quản lý,
        // và UpdatePetRequest cũng không mang trường nào trong số đó để client đụng tới được.

        return PetMapper.toDetail(petRepository.save(pet), primaryOwnerIdOf(petId));
    }

    /**
     * Lưu trữ hồ sơ — xoá MỀM, không xoá cứng.
     *
     * <p>Ghi cả hai cột là có chủ đích: {@code deletedAt} là thứ khiến hồ sơ biến mất khỏi mọi truy
     * vấn (qua {@code @SQLRestriction} trên entity, không endpoint nào đi vòng qua được), còn
     * {@code status = ARCHIVED} giữ cho bản ghi tự mô tả được trạng thái khi đọc thẳng trong DB.
     * Chỉ ghi status thì hồ sơ vẫn hiện ra ở mọi luồng đọc; chỉ ghi deletedAt thì một hàng "đã xoá"
     * lại mang status ACTIVE.
     *
     * <p>Chỉ PRIMARY_OWNER được lưu trữ. CO_OWNER sửa được nhưng không xoá được: xoá là hành động
     * không tự hoàn tác được qua API, nên nó thuộc về người chịu trách nhiệm chính.
     */
    @Transactional
    public PetDtos.ArchivePetResponse archive(Integer petId, Integer callerId) {
        Pet pet = petRepository.findVisibleById(petId, callerId).orElseThrow(NotFoundException::new);

        PetOwnership ownership = petOwnershipRepository.findByPetIdAndUserId(petId, callerId)
                .orElseThrow(() -> new ForbiddenException("Bạn không sở hữu thú cưng này"));
        if (ownership.getOwnershipType() != PetOwnershipType.PRIMARY_OWNER) {
            throw new ForbiddenException("Chỉ chủ sở hữu chính mới được lưu trữ thú cưng");
        }

        pet.setStatus(PetStatus.ARCHIVED);
        pet.setDeletedAt(LocalDateTime.now());
        petRepository.save(pet);

        return new PetDtos.ArchivePetResponse(petId, PetStatus.ARCHIVED);
    }

    /** Tập tài khoản được phép đụng vào hồ sơ — dùng chung với {@link PetAccessGuard} */
    @Transactional(readOnly = true)
    public List<Integer> ownerIdsOf(Integer petId) {
        return petOwnershipRepository.findAllByPetId(petId).stream()
                .map(PetOwnership::getUserId)
                .toList();
    }

    private void requireOwner(Integer petId, Integer callerId) {
        if (petOwnershipRepository.findByPetIdAndUserId(petId, callerId).isEmpty()) {
            throw new ForbiddenException("Bạn không sở hữu thú cưng này");
        }
    }

    /** {@code null} chỉ xảy ra với dữ liệu cũ chưa có bản ghi sở hữu — luồng tạo mới không sinh ra được */
    private Integer primaryOwnerIdOf(Integer petId) {
        return petOwnershipRepository.findAllByPetId(petId).stream()
                .filter(ownership -> ownership.getOwnershipType() == PetOwnershipType.PRIMARY_OWNER)
                .findFirst()
                .map(PetOwnership::getUserId)
                .orElse(null);
    }
}
