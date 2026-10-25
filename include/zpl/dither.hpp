#pragma once

#include <vector>
#include <cstdint>
#include "zpl/config.hpp"

namespace zpl {

struct MonochromeBitmap {
    int width = 0;
    int height = 0;
    int bytes_per_row = 0;             // (width + 7) / 8
    std::vector<uint8_t> data;        // 1 bit per pixel (MSB first; 1 = black/burn dot, 0 = white/paper)

    size_t total_bytes() const {
        return data.size();
    }
};

class DitherEngine {
public:
    // Takes 8-bit grayscale pixels (0 = black, 255 = white) with given width & height
    // and produces 1-bit packed binary data according to config.
    static MonochromeBitmap process(
        const uint8_t* gray_pixels,
        int width,
        int height,
        const ZplConfig& config
    );

    // Convenience overload accepting a std::vector of 8-bit grayscale pixels with bounds validation
    static MonochromeBitmap process(
        const std::vector<uint8_t>& gray_pixels,
        int width,
        int height,
        const ZplConfig& config
    ) {
        if (width <= 0 || height <= 0 ||
            gray_pixels.size() < static_cast<size_t>(width) * static_cast<size_t>(height)) {
            return MonochromeBitmap{};
        }
        return process(gray_pixels.data(), width, height, config);
    }
};

} // namespace zpl
