#pragma once

#include <string>
#include "zpl/config.hpp"

namespace zpl {

class Diagnostics {
public:
    // Generates an alignment ruler test label (outer border box + tick marks every 100 dots with labels)
    static std::string generate_ruler(const ZplConfig& config);

    // Generates horizontal alternating band graphic data (tests buffer and feed mechanism)
    static std::string generate_bands(const ZplConfig& config);

    // Generates a comprehensive test label with borders, scalable font, bitmap font, and barcode
    static std::string generate_test_label(const ZplConfig& config);

    // Generates the hardware query command (~HI: Hardware Identity)
    static std::string query_identity() {
        return "~HI\r\n";
    }

    // Generates the media sensor calibration command (~JC)
    static std::string calibrate_sensor() {
        return "~JC\r\n";
    }
};

} // namespace zpl

