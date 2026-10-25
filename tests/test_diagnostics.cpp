#include <gtest/gtest.h>
#include "zpl/diagnostics.hpp"

TEST(DiagnosticsTest, AlignmentRuler) {
    zpl::ZplConfig cfg;
    cfg.width_dots = 800;
    cfg.length_dots = 1200;

    std::string ruler = zpl::Diagnostics::generate_ruler(cfg);
    EXPECT_NE(ruler.find("^XA\n"), std::string::npos);
    EXPECT_NE(ruler.find("^PW800\n"), std::string::npos);
    EXPECT_NE(ruler.find("^LL1200\n"), std::string::npos);
    EXPECT_NE(ruler.find("^GB800,1200,4^FS\n"), std::string::npos);
    EXPECT_NE(ruler.find("^XZ\n"), std::string::npos);
}

TEST(DiagnosticsTest, BandsTestCompression) {
    zpl::ZplConfig cfg;
    cfg.width_dots = 800;
    cfg.length_dots = 1200;

    std::string bands = zpl::Diagnostics::generate_bands(cfg);
    EXPECT_NE(bands.find("^XA\n"), std::string::npos);
    EXPECT_NE(bands.find("^GFA,"), std::string::npos);
    EXPECT_NE(bands.find("^XZ\n"), std::string::npos);

    // Because alternating bands have massive row repetition, the compressed payload
    // should be under 2KB instead of the ~240KB of raw uncompressed hexadecimal!
    EXPECT_LT(bands.size(), 2048);
}

TEST(DiagnosticsTest, TestLabel) {
    zpl::ZplConfig cfg;
    cfg.width_dots = 800;
    cfg.length_dots = 1200;

    std::string label = zpl::Diagnostics::generate_test_label(cfg);
    EXPECT_NE(label.find("^XA\n"), std::string::npos);
    EXPECT_NE(label.find("ZPL Core Engine Test"), std::string::npos);
    EXPECT_NE(label.find("^BCN,60,Y,N,N^FD1234567890^FS"), std::string::npos);
    EXPECT_NE(label.find("^XZ\n"), std::string::npos);
}

TEST(DiagnosticsTest, HardwareCommands) {
    EXPECT_EQ(zpl::Diagnostics::query_identity(), "~HI\r\n");
    EXPECT_EQ(zpl::Diagnostics::calibrate_sensor(), "~JC\r\n");
}

