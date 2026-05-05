package com.team26.freelance.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class UserCacheKeysTest {

    @Test
    void detailKeysUseExactRedisKeys() {
        assertEquals("user-service::user::56", UserCacheKeys.user(56L));
        assertEquals("user-service::user-skill::9", UserCacheKeys.userSkill(9L));
    }

    @Test
    void idOnlyFeatureKeysUseRawIds() {
        assertEquals("user-service::S1-F3::56", UserCacheKeys.contractSummary(56L));
        assertEquals("user-service::S1-F8::56", UserCacheKeys.userProfile(56L));
    }

    @Test
    void multiParameterFeatureKeysUseStableSha256Hashes() {
        assertEquals(
                "user-service::S1-F1::7eb49bf4edee800525e85256ae262ffc42549cb0343883f80660bfb3ae0400a8",
                UserCacheKeys.userSearch("Ann", "ann@example.com", "FREELANCER")
        );
        assertEquals(
                "user-service::S1-F5::616f70260c1f8b5839ed777c22b576d2cdc40fe1cdbc773614a32c622addcd65",
                UserCacheKeys.preferenceSearch("language", "en")
        );
        assertEquals(
                "user-service::S1-F6::f67f080f9a026d29160be801224e93130cd7b3b0e2fcffc420898fda16193e6b",
                UserCacheKeys.topFreelancers(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), 5)
        );
        assertEquals(
                "user-service::S1-F9::89ea2ea3a1524e0c4dd710a1be93b4aee771023875d2f8bcba0f38708647fd79",
                UserCacheKeys.languagePreference("en", 3)
        );
    }

    @Test
    void nullValuesHaveStableHashEncoding() {
        assertEquals(
                "user-service::S1-F1::d65f7d200ed8d9b2e0fd250de2bd90849023655a2002e8ce85d4280ca95bae22",
                UserCacheKeys.userSearch(null, null, null)
        );
    }

    @Test
    void futureActivityFeedKeyIncludesUserIdAndPagedHash() {
        assertEquals(
                "user-service::S1-F12::56::dc7482a6e5fc7f7942e1c81bc2112a5d7dfa79931e599fd90a06529cbf6e95b0",
                UserCacheKeys.activityFeed(56L, 2, 25)
        );
    }
}
