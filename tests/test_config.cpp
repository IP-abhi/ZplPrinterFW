#include <gtest/gtest.h>
#include "zpl/config.hpp"

TEST(ZplConfigTest, DefaultParameters) {
    zpl::ZplConfig cfg;
    EXPECT_EQ(cfg.dpi, 203);
    EXPECT_EQ(cfg.width_dots, 812);
    EXPECT_EQ(cfg.length_dots, 1218);
    EXPECT_EQ(cfg.darkness, 30);
    EXPECT_EQ(cfg.print_speed, 2);
    EXPECT_EQ(cfg.shift_x, 0);
    EXPECT_EQ(cfg.shift_y, 0);
    EXPECT_EQ(cfg.dither, zpl::DitherMode::FloydSteinberg);
    EXPECT_EQ(cfg.threshold, 140);
    EXPECT_FALSE(cfg.invert);
    EXPECT_EQ(cfg.tracking, zpl::MediaTracking::Continuous);
    EXPECT_EQ(cfg.media_type, zpl::MediaType::DirectThermal);
}

TEST(ZplConfigTest, FromInches) {
    auto cfg = zpl::ZplConfig::from_inches(4.0f, 6.0f, 203);
    EXPECT_EQ(cfg.width_dots, 812);
    EXPECT_EQ(cfg.length_dots, 1218);
    EXPECT_NEAR(cfg.width_inches(), 4.0f, 0.01f);
    EXPECT_NEAR(cfg.length_inches(), 6.0f, 0.01f);
}

TEST(ZplConfigTest, FromMm) {
    // 100mm x 150mm @ 203 DPI:
    // 100 * (203 / 25.4) = 799.21 -> 799 or 800 dots
    // 150 * (203 / 25.4) = 1198.81 -> 1199 dots
    auto cfg = zpl::ZplConfig::from_mm(100.0f, 150.0f, 203);
    EXPECT_GE(cfg.width_dots, 799);
    EXPECT_LE(cfg.width_dots, 800);
    EXPECT_GE(cfg.length_dots, 1198);
    EXPECT_LE(cfg.length_dots, 1200);

    EXPECT_NEAR(cfg.width_mm(), 100.0f, 0.5f);
    EXPECT_NEAR(cfg.length_mm(), 150.0f, 0.5f);
}

