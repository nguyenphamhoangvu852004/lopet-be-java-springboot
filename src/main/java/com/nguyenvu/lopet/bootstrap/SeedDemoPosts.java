package com.nguyenvu.lopet.bootstrap;

import com.github.javafaker.Faker;
import com.nguyenvu.lopet.account.entity.Account;
import com.nguyenvu.lopet.account.repository.AccountRepository;
import com.nguyenvu.lopet.post.entity.MediaType;
import com.nguyenvu.lopet.post.entity.Post;
import com.nguyenvu.lopet.post.entity.PostMedia;
import com.nguyenvu.lopet.post.repository.PostRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class SeedDemoPosts {

    private final PostRepository postRepository;
    private final AccountRepository accountRepository;
    private final String cloudinaryURL = "https://res.cloudinary.com/dmsnw2qpd/image/upload/v1787728262/";
    private final List<String> listImageURLs = new ArrayList<>(
            List.of("boy-wishes-be-football-player-600nw-2403965637_scmdwp",
                    "220805-border-collie-play-mn-1100-82d2f1_ogawcb",
                    "happy-boy-playing-with-dog-active-game-on-lawn-HW31Y4_hl2fl3",
                    "young-girl-playing-ball-with-dog-vector-23038249_dqtszx",
                    "bigstock-Young-Asian-boy-playing-with-p-6185273_vykbus",
                    "dog-care_common-dog-behavior-problems_mouthing-nipping-biting-adult-dogs_main-image_0_s5dhl9",
                    "pet-shop-long-2-768x1024_ev90j5",
                    "dog-playtime-927x388_fn0tdv",
                    "how_to_teach_your_dog_to_play_with_toys_0af94d02-0f39-45b6-8794-401f67bb265a_xfzmyr",
                    "exercises-and-sports-to-do-with-your-dog_eped7n",
                    "360_F_47165532_gLiNBCiwFYWc1de63B1VYA5weXhqTIm0_nqzff0",
                    "training-behavior_jkuy6k",
                    "playing-with-your-pet-is-important-870883818-2000-102b7fbfff1d4646856e8d0d3b8d99fa_wxkcra",
                    "dogs-1-rf-gty-bb-240314_1710421693379_hpMain_q0p60k",
                    "43ec7448d6b9ead24b51fa18f897104b_acr2nj",
                    "bd86688c8ef86810698830c3c3c6bc6d_ywx21a",
                    "934ad3d82149211504a696bb467cd605_rtqker"
            )
    );

    @Transactional
    public void execute() {
        Account demoAccount = accountRepository.findDetailByEmail(SeedDemoAccount.DEMO_EMAIL)
                .orElse(null);
        if (demoAccount == null) {
            log.warn("Skipping demo post seed: account {} not found", SeedDemoAccount.DEMO_EMAIL);
            return;
        }

        if (postRepository.existsByAccountId(demoAccount.getId())) {
            log.debug("Skipping demo post seed: account {} already has posts", SeedDemoAccount.DEMO_EMAIL);
            return;
        }

        Faker faker = new Faker();
        for (int i = 1; i < listImageURLs.size(); i++) {
            Post p = Post.builder()
                    .account(demoAccount)
                    .content(faker.lorem().paragraph())
                    .build();

            PostMedia media = PostMedia.builder()
                    .mediaType(MediaType.IMAGE)
                    .mediaUrl("https://res.cloudinary.com/dmsnw2qpd/image/upload/v1787728262/" +listImageURLs.get(i))
                    .post(p)
                    .build();
            p.getPostMedias().add(media);

            this.postRepository.save(p);
        }
        log.info("Seeded {} demo posts for {}", listImageURLs.size() - 1, SeedDemoAccount.DEMO_EMAIL);
    }

}
