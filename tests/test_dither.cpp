#include <gtest/gtest.h>
#include "zpl/dither.hpp"

TEST(DitherEngineTest, InputBoundsValidation) {
    zpl::ZplConfig cfg;

    // Null pointer input
    auto mono1 = zpl::DitherEngine::process(static_cast<const uint8_t*>(nullptr), 100, 100, cfg);
    EXPECT_EQ(mono1.width, 0);
    EXPECT_EQ(mono1.total_bytes(), 0);

    // Negative / zero dimensions
    std::vector<uint8_t> pixels(100, 128);
    auto mono2 = zpl::DitherEngine::process(pixels.data(), 0, 100, cfg);
    EXPECT_EQ(mono2.width, 0);

    auto mono3 = zpl::DitherEngine::process(pixels.data(), 100, -5, cfg);
    EXPECT_EQ(mono3.width, 0);

    // Buffer smaller than width * height
    std::vector<uint8_t> undersized(50, 128);
    auto mono4 = zpl::DitherEngine::process(undersized, 10, 10, cfg); // needs 100 bytes
    EXPECT_EQ(mono4.width, 0);
    EXPECT_EQ(mono4.total_bytes(), 0);
}

TEST(DitherEngineTest, ThresholdMode) {
    zpl::ZplConfig cfg;
    cfg.dither = zpl::DitherMode::None;
    cfg.threshold = 128;
    cfg.invert = false;

    // 8 pixels wide, 2 rows
    // Row 0: all black (0) -> packed byte 0xFF
    // Row 1: all white (255) -> packed byte 0x00
    std::vector<uint8_t> pixels = {
        0, 0, 0, 0, 0, 0, 0, 0,
        255, 255, 255, 255, 255, 255, 255, 255
    };

    auto mono = zpl::DitherEngine::process(pixels, 8, 2, cfg);
    EXPECT_EQ(mono.width, 8);
    EXPECT_EQ(mono.height, 2);
    EXPECT_EQ(mono.bytes_per_row, 1);
    ASSERT_EQ(mono.data.size(), 2);
    EXPECT_EQ(mono.data[0], 0xFF);
    EXPECT_EQ(mono.data[1], 0x00);
}

TEST(DitherEngineTest, PolarityInversion) {
    zpl::ZplConfig cfg;
    cfg.dither = zpl::DitherMode::None;
    cfg.threshold = 128;
    cfg.invert = true; // Inverted

    std::vector<uint8_t> pixels = {
        0, 0, 0, 0, 0, 0, 0, 0,
        255, 255, 255, 255, 255, 255, 255, 255
    };

    auto mono = zpl::DitherEngine::process(pixels, 8, 2, cfg);
    ASSERT_EQ(mono.data.size(), 2);
    // Inverted: black becomes white (0x00), white becomes black (0xFF)
    EXPECT_EQ(mono.data[0], 0x00);
    EXPECT_EQ(mono.data[1], 0xFF);
}

TEST(DitherEngineTest, FloydSteinbergDiffusion) {
    zpl::ZplConfig cfg;
    cfg.dither = zpl::DitherMode::FloydSteinberg;
    cfg.threshold = 128;

    // 16x16 gradient or mid-gray
    std::vector<uint8_t> mid_gray(16 * 16, 128);
    auto mono = zpl::DitherEngine::process(mid_gray, 16, 16, cfg);
    EXPECT_EQ(mono.width, 16);
    EXPECT_EQ(mono.height, 16);
    EXPECT_EQ(mono.bytes_per_row, 2);
    EXPECT_EQ(mono.data.size(), 32);

    // Floyd-Steinberg should produce a dither pattern containing both 0 and 1 bits
    bool has_non_zero = false;
    bool has_non_full = false;
    for (uint8_t b : mono.data) {
        if (b > 0x00) has_non_zero = true;
        if (b < 0xFF) has_non_full = true;
    }
    EXPECT_TRUE(has_non_zero);
    EXPECT_TRUE(has_non_full);
}

TEST(DitherEngineTest, AtkinsonDiffusion) {
    zpl::ZplConfig cfg;
    cfg.dither = zpl::DitherMode::Atkinson;
    cfg.threshold = 128;

    std::vector<uint8_t> mid_gray(16 * 16, 128);
    auto mono = zpl::DitherEngine::process(mid_gray, 16, 16, cfg);
    EXPECT_EQ(mono.width, 16);
    EXPECT_EQ(mono.height, 16);
    EXPECT_EQ(mono.bytes_per_row, 2);
    EXPECT_EQ(mono.data.size(), 32);
}

