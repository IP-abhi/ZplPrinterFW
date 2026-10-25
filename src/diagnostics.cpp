#include "zpl/diagnostics.hpp"
#include "zpl/encoder.hpp"
#include <vector>

namespace zpl {

std::string Diagnostics::generate_ruler(const ZplConfig& config) {
    const int W = config.width_dots;
    const int H = config.length_dots;

    std::string s = ZplEncoder::build_preamble(config);

    // Outer border box (4 dots thick)
    s += "^FO0,0^GB" + std::to_string(W) + "," + std::to_string(H) + ",4^FS\n";

    // Horizontal tick marks and numbers every 100 dots
    for (int x = 100; x < W; x += 100) {
        s += "^FO" + std::to_string(x) + ",0^GB2,40,2^FS\n";
        s += "^FO" + std::to_string(x) + ",50^A0N,30,30^FD" + std::to_string(x) + "^FS\n";
    }

    // Vertical tick marks and numbers every 100 dots
    for (int y = 100; y < H; y += 100) {
        s += "^FO0," + std::to_string(y) + "^GB40,2,2^FS\n";
        s += "^FO50," + std::to_string(y - 12) + "^A0N,30,30^FD" + std::to_string(y) + "^FS\n";
    }

    s += "^XZ\n";
    return s;
}

std::string Diagnostics::generate_bands(const ZplConfig& config) {
    const int W = config.width_dots;
    const int H = config.length_dots;
    const int bpr = (W + 7) / 8;
    const size_t total = static_cast<size_t>(bpr) * static_cast<size_t>(H);

    // Generate binary buffer: 24 dots black (0xFF), 76 dots white (0x00)
    std::vector<uint8_t> buffer(total, 0x00);
    for (int y = 0; y < H; ++y) {
        if ((y % 100) < 24) {
            size_t row_start = static_cast<size_t>(y) * static_cast<size_t>(bpr);
            for (int b = 0; b < bpr; ++b) {
                buffer[row_start + static_cast<size_t>(b)] = 0xFF;
            }
        }
    }

    // Highly optimized compressed GFA payload using row repeat ':' and run-lengths
    std::string payload = ZplEncoder::compress_gfa_payload(buffer.data(), total, bpr);

    std::string s = ZplEncoder::build_preamble(config);
    s += "^FO0,0^GFA," + std::to_string(total) + ","
                       + std::to_string(total) + ","
                       + std::to_string(bpr) + ","
                       + payload + "^FS\n";
    s += "^XZ\n";
    return s;
}

std::string Diagnostics::generate_test_label(const ZplConfig& config) {
    const int W = config.width_dots;
    const int H = config.length_dots;

    std::string s = ZplEncoder::build_preamble(config);

    // Border box
    int margin = 20;
    int box_w = (W > 2 * margin) ? (W - 2 * margin) : W;
    int box_h = (H > 2 * margin) ? (H - 2 * margin) : H;
    s += "^FO" + std::to_string(margin) + "," + std::to_string(margin)
       + "^GB" + std::to_string(box_w) + "," + std::to_string(box_h) + ",4^FS\n";

    // Scalable Font Title
    s += "^FO50,50^A0N,44,44^FDZPL Core Engine Test^FS\n";
    s += "^FO50,105^A0N,28,28^FDRongta / Milestone MHT-L1091 (203 DPI)^FS\n";

    // Divider line
    int line_w = (W > 100) ? (W - 100) : W;
    s += "^FO50,145^GB" + std::to_string(line_w) + ",2,2^FS\n";

    // Config info text
    s += "^FO50,165^ADN,36,20^FDWidth: " + std::to_string(W) + " dots ("
       + std::to_string(static_cast<int>(config.width_mm())) + " mm)^FS\n";
    s += "^FO50,210^ADN,36,20^FDLength: " + std::to_string(H) + " dots ("
       + std::to_string(static_cast<int>(config.length_mm())) + " mm)^FS\n";
    s += "^FO50,255^ADN,36,20^FDDarkness: " + std::to_string(config.darkness)
       + "  Speed: " + std::to_string(config.print_speed) + " ips^FS\n";

    // Code 128 Barcode
    s += "^FO50,310^BY2,3,60^BCN,60,Y,N,N^FD1234567890^FS\n";

    s += "^XZ\n";
    return s;
}

} // namespace zpl
