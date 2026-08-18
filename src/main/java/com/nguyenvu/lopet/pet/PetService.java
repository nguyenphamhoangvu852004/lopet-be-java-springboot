package com.nguyenvu.lopet.pet;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nguyenvu.lopet.account.entity.Account;
import com.nguyenvu.lopet.account.repository.AccountRepository;
import com.nguyenvu.lopet.common.exception.ForbiddenException;
import com.nguyenvu.lopet.common.exception.NotFoundException;
import com.nguyenvu.lopet.pet.dto.PetDtos;
import com.nguyenvu.lopet.pet.entity.Pet;
import com.nguyenvu.lopet.pet.entity.PetStatus;
import com.nguyenvu.lopet.pet.repository.PetRepository;
import com.nguyenvu.lopet.petprofile.PetProfilePolicy;
import com.nguyenvu.lopet.petprofile.PetProfileService;
import com.nguyenvu.lopet.security.petcontext.PetOwnerResolver;

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
    private final AccountRepository accountRepository;
    private final PetProfileService petProfileService;
    private final PetOwnerResolver petOwnerResolver;

    /**
     * Tạo con vật VÀ hồ sơ công khai của nó trong MỘT transaction.
     *
     * <p>Đây là bất biến cứng của mô hình: không tồn tại Pet thiếu PetProfile, và ngược lại. Nếu
     * bước tạo hồ sơ hỏng thì bản ghi {@code pets} cũng bị rollback — một con vật không có hồ sơ sẽ
     * biến mất khỏi mọi luồng đọc (chúng đều join {@code pet_profiles} để lấy phạm vi riêng tư) mà
     * chủ của nó không có endpoint nào để sửa tình trạng đó.
     *
     * <p>Thứ tự bắt buộc: {@code save(pet)} trước, vì handle mặc định sinh từ {@code pet.id}
     * ({@link PetProfilePolicy#seedHandle}).
     */
    @Transactional
    public PetDtos.PetDetail create(Integer ownerId, PetDtos.CreatePetRequest request) {
        Account owner = accountRepository.findById(ownerId).orElseThrow(NotFoundException::new);

        Pet pet = Pet.builder()
                .account(owner)
                .name(PetPolicy.requireName(request.name()))
                .species(PetPolicy.parseSpecies(request.species()))
                .breed(PetPolicy.trimToNull(request.breed()))
                .gender(PetPolicy.parseGender(request.gender()))
                .dateOfBirth(PetPolicy.requireDateOfBirth(request.dateOfBirth()))
                .status(PetStatus.ACTIVE)
                .build();

        Pet saved = petRepository.save(pet);
        saved.setPetProfile(petProfileService.createFor(
                saved, PetProfilePolicy.parseVisibility(request.visibility())));

        return PetMapper.toDetail(saved);
    }

    /**
     * Trả 404 (không phải 403) khi người xem không đủ quyền: phản hồi phải giống hệt trường hợp con
     * vật không tồn tại, nếu không endpoint này thành công cụ dò xem một hồ sơ PRIVATE có tồn tại
     * hay không — giống hệt lý do ở {@code PostService.getOneById}.
     */
    @Transactional(readOnly = true)
    public PetDtos.PetDetail getOneById(Integer petId, Integer viewerId) {
        return PetMapper.toDetail(petRepository.findVisibleById(petId, viewerId)
                .orElseThrow(NotFoundException::new));
    }

    @Transactional(readOnly = true)
    public List<PetDtos.PetListItem> getOwnedBy(Integer ownerId) {
        return petRepository.findAllByAccountId(ownerId).stream()
                .map(PetMapper::toListItem)
                .toList();
    }

    /**
     * Sửa thông tin SINH HỌC. Tên hiển thị, avatar, bio, phạm vi riêng tư không nằm ở đây — chúng
     * thuộc hồ sơ công khai và đi qua {@code PUT /v1/pet-profiles/{petId}}.
     *
     * <p>Kiểm tra sở hữu ở đây là lớp thứ hai (lớp thứ nhất là {@link PetAccessGuard} trên
     * controller): lớp trong này chặn cả trường hợp service được gọi từ nơi khác, đúng cách
     * {@code PostService.update} đang làm.
     */
    @Transactional
    public PetDtos.PetDetail update(Integer petId, Integer callerId, PetDtos.UpdatePetRequest request) {
        Pet pet = petRepository.findVisibleById(petId, callerId).orElseThrow(NotFoundException::new);
        requireOwner(pet, callerId);

        pet.setName(PetPolicy.requireName(request.name()));
        pet.setSpecies(PetPolicy.parseSpecies(request.species()));
        pet.setBreed(PetPolicy.trimToNull(request.breed()));
        pet.setGender(PetPolicy.parseGender(request.gender()));
        pet.setDateOfBirth(PetPolicy.requireDateOfBirth(request.dateOfBirth()));
        // status/deletedAt/createdAt/updatedAt cố ý KHÔNG xuất hiện ở đây: chúng do domain quản lý,
        // và UpdatePetRequest cũng không mang trường nào trong số đó để client đụng tới được.

        return PetMapper.toDetail(petRepository.save(pet));
    }

    /**
     * Ngừng hoạt động — xoá MỀM, không xoá cứng. Bài viết và bình luận của người khác đang trỏ tới
     * {@code pets.id}; xoá cứng là phá vỡ nội dung không thuộc về người bấm nút.
     *
     * <p>Con vật và hồ sơ công khai của nó phải cùng chuyển trạng thái trong MỘT transaction: hồ sơ
     * còn sống sau khi con vật đã tắt thì {@code GET /v1/pet-profiles/handle/{handle}} vẫn trả về
     * nó.
     *
     * <p>Cache {@code pet:owner:<petId>} bị vô hiệu hoá NGAY tại đây. Bỏ bước này thì một pet đã
     * ngừng hoạt động vẫn qua được validate {@code X-Pet-Id} cho tới khi TTL hết hạn.
     */
    @Transactional
    public PetDtos.DeactivatePetResponse deactivate(Integer petId, Integer callerId) {
        Pet pet = petRepository.findVisibleById(petId, callerId).orElseThrow(NotFoundException::new);
        requireOwner(pet, callerId);

        pet.setStatus(PetStatus.DEACTIVATED);
        pet.setDeletedAt(LocalDateTime.now());
        petRepository.save(pet);
        petProfileService.deactivateFor(petId);
        petOwnerResolver.invalidate(petId);

        return new PetDtos.DeactivatePetResponse(petId, PetStatus.DEACTIVATED);
    }

    /** Tập tài khoản được phép đụng vào con vật — dùng chung với {@link PetAccessGuard} */
    public List<Integer> ownerIdsOf(Pet pet) {
        return List.of(pet.getAccount().getId());
    }

    private void requireOwner(Pet pet, Integer callerId) {
        if (!pet.getAccount().getId().equals(callerId)) {
            throw new ForbiddenException("Bạn không sở hữu thú cưng này");
        }
    }
}
