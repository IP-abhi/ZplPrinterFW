#pragma once

#include <cstdint>
#include <cmath>

namespace zpl {

enum class DitherMode {
    None,            // Simple thresholding (sharp barcodes / line art)
    FloydSteinberg,  // Error-diffusion dithering (continuous-tone gray shading)
    Atkinson         // High-contrast error diffusion
};

enum class MediaTracking {
    Continuous,      // ^MNN (continuous paper/roll)
    WebGap,          // ^MNY (gap / die-cut label sensing)
    BlackMark        // ^MNM (reflective black line sensing)
};

enum class MediaType {
    DirectThermal,   // ^MTD (direct thermal, heat-sensitive paper)
    ThermalTransfer  // ^MTT (thermal transfer with ribbon)
};

struct ZplConfig {
    int dpi = 203;                  // Hardware resolution: 203 DPI (8 dots/mm)
    int width_dots = 812;           // Active width (default: 4.00" = 812 dots at 203 DPI)
    int length_dots = 1218;         // Active length (default: 6.00" = 1218 dots at 203 DPI)
    int darkness = 30;              // Heat burn duration (1 - 30, maps to ^MD and ~SD)
    int print_speed = 2;            // Print rate in inches/sec (2 - 6, maps to ^PR)
    int shift_x = 0;                // Horizontal origin offset in dots (^LH x)
    int shift_y = 0;                // Vertical origin offset in dots (^LH y)

    DitherMode dither = DitherMode::FloydSteinberg;
    uint8_t threshold = 140;        // Cutoff value (0-255) for DitherMode::None
    bool invert = false;            // Invert polarity (white-on-black)
    MediaTracking tracking = MediaTracking::Continuous;
    MediaType media_type = MediaType::DirectThermal;

    // Unit conversion factories
    static ZplConfig from_mm(float width_mm, float length_mm, int dpi = 203) {
        ZplConfig cfg;
        cfg.dpi = dpi;
        cfg.width_dots = static_cast<int>(std::round(width_mm * (static_cast<float>(dpi) / 25.4f)));
        cfg.length_dots = static_cast<int>(std::round(length_mm * (static_cast<float>(dpi) / 25.4f)));
        return cfg;
    }

    static ZplConfig from_inches(float width_in, float length_in, int dpi = 203) {
        ZplConfig cfg;
        cfg.dpi = dpi;
        cfg.width_dots = static_cast<int>(std::round(width_in * static_cast<float>(dpi)));
        cfg.length_dots = static_cast<int>(std::round(length_in * static_cast<float>(dpi)));
        return cfg;
    }

    float width_mm() const {
        return static_cast<float>(width_dots) / (static_cast<float>(dpi) / 25.4f);
    }

    float length_mm() const {
        return static_cast<float>(length_dots) / (static_cast<float>(dpi) / 25.4f);
    }

    float width_inches() const {
        return static_cast<float>(width_dots) / static_cast<float>(dpi);
    }

    float length_inches() const {
        return static_cast<float>(length_dots) / static_cast<float>(dpi);
    }
};

} // namespace zpl

