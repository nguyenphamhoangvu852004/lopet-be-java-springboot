package com.nguyenvu.lopet.accountprofile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.nguyenvu.lopet.account.repository.AccountRepository;
import com.nguyenvu.lopet.auth.AuthService;
import com.nguyenvu.lopet.auth.dto.RegisterRequest;
import com.nguyenvu.lopet.common.exception.NotFoundException;
import com.nguyenvu.lopet.email.OtpStore;
import com.nguyenvu.lopet.accountprofile.dto.AccountProfileDtos;
import com.nguyenvu.lopet.accountprofile.entity.AccountProfile;
import com.nguyenvu.lopet.accountprofile.repository.AccountProfileRepository;
import com.nguyenvu.lopet.support.IntegrationTestBase;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("AccountProfile Refactor")
class AccountProfileRefactorIntegrationTest extends IntegrationTestBase {

    private static final String RUN = Long.toString(System.nanoTime(), 36);
    private static final String ADMIN_EMAIL = "profile-admin@lopet.local";
    private static final String ADMIN_USERNAME = "profile-admin";

    @DynamicPropertySource
    static void configuration(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> IntegrationTestBase.jdbcUrl("lopet_java_profile_test"));
        registry.add("lopet.admin.email", () -> ADMIN_EMAIL);
        registry.add("lopet.admin.username", () -> ADMIN_USERNAME);
        registry.add("lopet.admin.password", () -> "admin-password");
    }

    @Autowired
    private AuthService authService;
    @Autowired
    private AccountProfileService profileService;
    @Autowired
    private AccountProfileRepository accountProfileRepository;
    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private OtpStore otpStore;

    private Integer register(String name) {
        String unique = name + "-" + RUN;
        String email = unique + "@profile.local";
        otpStore.markVerified(email);
        return authService.register(
                new RegisterRequest(email, unique, "password123", "password123")).id();
    }

    private AccountProfile profileOf(Integer accountId) {
        return inTransaction(() -> accountProfileRepository.findByAccountId(accountId).orElse(null));
    }

    @Nested
    @DisplayName("The profile is provisioned when the account is created")
    class ProfileProvisioning {

        @Test
        @DisplayName("registering already yields a profile carrying the right seed data")
        void registerProvisionsTheProfile() {
            Integer accountId = register("seed-user");

            AccountProfile profile = profileOf(accountId);
            assertThat(profile).as("register must provision the profile rather than leave it to the user").isNotNull();
            assertThat(profile.getFullName()).isEqualTo("seed-user-" + RUN);
            assertThat(profile.getBio()).isEqualTo("Hello Lopet, I am seed-user-" + RUN);
            assertThat(profile.getPhoneNumber()).isEmpty();
            assertThat(profile.getHometown()).isEmpty();
            assertThat(profile.getAvatarUrl()).isEmpty();
            assertThat(profile.getCoverUrl()).isEmpty();
            assertThat(profile.getSex()).isNull();
            assertThat(profile.getDateOfBirth()).isNull();
        }

        @Test
        @DisplayName("the admin account created by bootstrap has a profile too")
        void bootstrappedAdminAlsoHasAProfile() {
            Integer adminId = inTransaction(() -> accountRepository.findDetailByEmail(ADMIN_EMAIL)
                    .orElseThrow(() -> new AssertionError("StartupRunner has not created the admin account"))
                    .getId());

            assertThat(profileOf(adminId))
                    .as("patching register() but forgetting AdminInitializer still leaves the admin profile-less")
                    .isNotNull();
        }

        @Test
        @DisplayName("no API creates a standalone profile any more — every profile belongs to an account")
        void noOrphanProfilesRemain() {
            register("no-orphan");

            long orphans = inTransaction(() -> accountProfileRepository.findAll().stream()
                    .filter(profile -> profile.getAccount() == null)
                    .count());

            assertThat(orphans).as("an orphan profile is permanently unfixable because it has no owner").isZero();
        }
    }

    @Nested
    @DisplayName("Updating your own profile")
    class Updates {

        @Test
        @DisplayName("a field that is not sent keeps its value (merge semantics)")
        void onlySubmittedFieldsChange() {
            Integer accountId = register("merge-user");
            profileService.updateMine(accountId, "Original Name", "0900000000", "Original bio",
                    null, null, LocalDate.of(2000, 1, 15), "Da Nang", 1);

            AccountProfileDtos.ProfileEntity updated = profileService.updateMine(accountId, "New Name",
                    null, null, null, null, null, null, null);

            assertThat(updated.fullName()).isEqualTo("New Name");
            assertThat(updated.bio()).isEqualTo("Original bio");
            assertThat(updated.phoneNumber()).isEqualTo("0900000000");
            assertThat(updated.hometown()).isEqualTo("Da Nang");
            assertThat(updated.dateOfBirth()).isEqualTo(LocalDate.of(2000, 1, 15));
            assertThat(updated.sex()).isEqualTo(1);
        }

        @Test
        @DisplayName("an empty string DOES overwrite — a user must be able to clear their bio")
        void emptyStringStillOverwrites() {
            Integer accountId = register("clear-bio");
            profileService.updateMine(accountId, null, null, "Bio to be cleared",
                    null, null, null, null, null);

            AccountProfileDtos.ProfileEntity updated = profileService.updateMine(accountId, null, null, "",
                    null, null, null, null, null);

            assertThat(updated.bio()).isEmpty();
        }

        @Test
        @DisplayName("an update without files does NOT clear the existing images")
        void updateWithoutFilesKeepsImages() {
            Integer accountId = register("keep-avatar");
            profileService.updateMine(accountId, null, null, null,
                    "https://cdn.local/avatar.png", "https://cdn.local/cover.png", null, null, null);

            AccountProfileDtos.ProfileEntity updated = profileService.updateMine(accountId, null, null,
                    "Editing the bio only", null, null, null, null, null);

            assertThat(updated.avatarUrl()).isEqualTo("https://cdn.local/avatar.png");
            assertThat(updated.coverUrl()).isEqualTo("https://cdn.local/cover.png");
            assertThat(updated.bio()).isEqualTo("Editing the bio only");
        }
    }

    @Nested
    @DisplayName("Isolation between accounts")
    class Isolation {

        @Test
        @DisplayName("editing your own profile does not touch anyone elses")
        void editingOwnProfileLeavesOthersAlone() {
            Integer victim = register("victim");
            Integer stranger = register("stranger");
            profileService.updateMine(victim, "Victim profile", null, "Victim bio",
                    "https://cdn.local/victim.png", null, null, null, null);

            profileService.updateMine(stranger, "Stranger profile", null, "Stranger bio", null, null, null, null, null);

            AccountProfileDtos.ProfileSummary victimProfile = profileService.findByAccountId(victim);
            assertThat(victimProfile.fullName()).isEqualTo("Victim profile");
            assertThat(victimProfile.bio()).isEqualTo("Victim bio");
            assertThat(victimProfile.avatarUrl()).isEqualTo("https://cdn.local/victim.png");
            assertThat(profileService.findByAccountId(stranger).fullName()).isEqualTo("Stranger profile");
        }

        @Test
        @DisplayName("each account owns its own profile, never a shared row")
        void eachAccountOwnsItsProfile() {
            Integer first = register("own-a");
            Integer second = register("own-b");

            assertThat(profileOf(first).getId()).isNotEqualTo(profileOf(second).getId());
        }
    }

    @Nested
    @DisplayName("Reading your own profile")
    class ReadOwnProfile {

        @Test
        @DisplayName("GET /me returns the callers own profile without needing a profileId")
        void returnsTheCallersOwnProfile() {
            Integer accountId = register("read-me");
            profileService.updateMine(accountId, "Display name", null, null, null, null, null, null, null);

            AccountProfileDtos.ProfileSummary mine = profileService.findByAccountId(accountId);

            assertThat(mine.id()).isEqualTo(profileOf(accountId).getId());
            assertThat(mine.fullName()).isEqualTo("Display name");
        }

        @Test
        @DisplayName("updating an account without a profile fails with an error naming the backfill")
        void missingProfileFailsWithAClearError() {
            assertThatThrownBy(() -> profileService.updateMine(-1, "x", null, null,
                    null, null, null, null, null))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("backfill");
        }
    }
}
