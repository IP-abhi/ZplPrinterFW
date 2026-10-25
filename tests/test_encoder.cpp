#include <gtest/gtest.h>
#include "zpl/encoder.hpp"

TEST(ZplEncoderTest, HexConversion) {
    uint8_t data[] = { 0x00, 0x0F, 0xAA, 0x55, 0xFF };
    std::string hex = zpl::ZplEncoder::to_hex_string(data, 5);
    EXPECT_EQ(hex, "000FAA55FF");

    std::vector<uint8_t> back = zpl::ZplEncoder::from_hex_string(hex);
    ASSERT_EQ(back.size(), 5);
    for (size_t i = 0; i < 5; ++i) {
        EXPECT_EQ(back[i], data[i]);
    }
}

TEST(ZplEncoderTest, CompressRowSpecialTerminators) {
    // 1. All zeros compresses to ","
    EXPECT_EQ(zpl::ZplEncoder::compress_row("00000000"), ",");
    EXPECT_EQ(zpl::ZplEncoder::decompress_row(",", 8), "00000000");

    // 2. All Fs compresses to "!"
    EXPECT_EQ(zpl::ZplEncoder::compress_row("FFFFFFFF"), "!");
    EXPECT_EQ(zpl::ZplEncoder::decompress_row("!", 8), "FFFFFFFF");

    // 3. Trailing zeros suppression with ','
    std::string trailing_zeros = "AABB0000";
    std::string comp_zeros = zpl::ZplEncoder::compress_row(trailing_zeros);
    EXPECT_EQ(comp_zeros.back(), ',');
    EXPECT_EQ(zpl::ZplEncoder::decompress_row(comp_zeros, 8), trailing_zeros);

    // 4. Trailing Fs suppression with '!'
    std::string trailing_ones = "1122FFFF";
    std::string comp_ones = zpl::ZplEncoder::compress_row(trailing_ones);
    EXPECT_EQ(comp_ones.back(), '!');
    EXPECT_EQ(zpl::ZplEncoder::decompress_row(comp_ones, 8), trailing_ones);
}

TEST(ZplEncoderTest, CompressRowRunLengths) {
    // 5 'A's -> 'G' + (5 - 1) = 'K' -> "KA"
    EXPECT_EQ(zpl::ZplEncoder::compress_row("AAAAA"), "KA");
    EXPECT_EQ(zpl::ZplEncoder::decompress_row("KA", 5), "AAAAA");

    // 19 'C's -> 'Y' -> "YC"
    std::string nineteen_c(19, 'C');
    EXPECT_EQ(zpl::ZplEncoder::compress_row(nineteen_c), "YC");
    EXPECT_EQ(zpl::ZplEncoder::decompress_row("YC", 19), nineteen_c);

    // 20 'B's -> 'g' + 0 = 'g' -> "gB"
    std::string twenty_b(20, 'B');
    EXPECT_EQ(zpl::ZplEncoder::compress_row(twenty_b), "gB");
    EXPECT_EQ(zpl::ZplEncoder::decompress_row("gB", 20), twenty_b);

    // 40 'D's -> 'h' -> "hD"
    std::string forty_d(40, 'D');
    EXPECT_EQ(zpl::ZplEncoder::compress_row(forty_d), "hD");
    EXPECT_EQ(zpl::ZplEncoder::decompress_row("hD", 40), forty_d);

    // 400 'E's -> 'z' -> "zE"
    std::string four_hundred_e(400, 'E');
    EXPECT_EQ(zpl::ZplEncoder::compress_row(four_hundred_e), "zE");
    EXPECT_EQ(zpl::ZplEncoder::decompress_row("zE", 400), four_hundred_e);

    // 425 '3's -> 400 ('z') + 20 ('g') + 5 ('K') -> "zgK3"
    std::string four_twenty_five(425, '3');
    std::string comp = zpl::ZplEncoder::compress_row(four_twenty_five);
    EXPECT_EQ(comp, "zgK3");
    EXPECT_EQ(zpl::ZplEncoder::decompress_row(comp, 425), four_twenty_five);
}

TEST(ZplEncoderTest, ComprehensiveRowRoundTrip) {
    // Test round-trip fidelity for every run length from 1 to 500
    for (int run = 1; run <= 500; ++run) {
        std::string test_str(run, '9');
        std::string compressed = zpl::ZplEncoder::compress_row(test_str);
        std::string decompressed = zpl::ZplEncoder::decompress_row(compressed, run);
        EXPECT_EQ(decompressed, test_str) << "Failed for run length " << run;
    }
}

TEST(ZplEncoderTest, GfaPayloadRoundTrip) {
    // 4 rows of 4 bytes (16 bytes total)
    // Row 0: pattern
    // Row 1: same pattern (should be compressed to ':')
    // Row 2: all zeros (should use ',')
    // Row 3: another pattern
    std::vector<uint8_t> data = {
        0xAA, 0x55, 0xAA, 0x55,
        0xAA, 0x55, 0xAA, 0x55, // repeat of row 0
        0x00, 0x00, 0x00, 0x00,
        0x12, 0x34, 0x56, 0x78
    };

    std::string payload = zpl::ZplEncoder::compress_gfa_payload(data.data(), data.size(), 4);
    EXPECT_NE(payload.find(':'), std::string::npos);

    std::vector<uint8_t> recovered = zpl::ZplEncoder::decompress_gfa_payload(payload, data.size(), 4);
    ASSERT_EQ(recovered.size(), data.size());
    EXPECT_EQ(recovered, data);
}

TEST(ZplEncoderTest, EncodeImageFraming) {
    zpl::MonochromeBitmap bmp;
    bmp.width = 16;
    bmp.height = 2;
    bmp.bytes_per_row = 2;
    bmp.data = { 0xFF, 0x00, 0x00, 0xFF };

    zpl::ZplConfig cfg;
    cfg.width_dots = 16;
    cfg.length_dots = 2;

    std::string zpl = zpl::ZplEncoder::encode_image(bmp, cfg);
    EXPECT_NE(zpl.find("^XA\n"), std::string::npos);
    EXPECT_NE(zpl.find("^PW16\n"), std::string::npos);
    EXPECT_NE(zpl.find("^LL2\n"), std::string::npos);
    EXPECT_NE(zpl.find("^GFA,4,4,2,"), std::string::npos);
    EXPECT_NE(zpl.find("^FS\n"), std::string::npos);
    EXPECT_NE(zpl.find("^XZ\n"), std::string::npos);
}

