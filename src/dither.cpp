#include "zpl/dither.hpp"
#include <algorithm>
#include <cmath>

namespace zpl {

namespace {

inline void set_bit(std::vector<uint8_t>& data, size_t row_offset, int x, bool is_black) {
    if (is_black) {
        data[row_offset + static_cast<size_t>(x >> 3)] |= static_cast<uint8_t>(0x80 >> (x & 7));
    }
}

MonochromeBitmap process_threshold(
    const uint8_t* gray_pixels,
    int width,
    int height,
    const ZplConfig& config
) {
    MonochromeBitmap result;
    result.width = width;
    result.height = height;
    result.bytes_per_row = (width + 7) / 8;
    result.data.assign(static_cast<size_t>(result.bytes_per_row) * static_cast<size_t>(height), 0);

    const uint8_t thresh = config.threshold;
    const bool invert = config.invert;

    for (int y = 0; y < height; ++y) {
        size_t row_in = static_cast<size_t>(y) * static_cast<size_t>(width);
        size_t row_out = static_cast<size_t>(y) * static_cast<size_t>(result.bytes_per_row);
        for (int x = 0; x < width; ++x) {
            uint8_t p = gray_pixels[row_in + static_cast<size_t>(x)];
            bool is_black = (p < thresh);
            if (invert) is_black = !is_black;
            set_bit(result.data, row_out, x, is_black);
        }
    }

    return result;
}

MonochromeBitmap process_floyd_steinberg(
    const uint8_t* gray_pixels,
    int width,
    int height,
    const ZplConfig& config
) {
    MonochromeBitmap result;
    result.width = width;
    result.height = height;
    result.bytes_per_row = (width + 7) / 8;
    result.data.assign(static_cast<size_t>(result.bytes_per_row) * static_cast<size_t>(height), 0);

    const size_t total_pixels = static_cast<size_t>(width) * static_cast<size_t>(height);
    std::vector<float> buffer(total_pixels);
    for (size_t i = 0; i < total_pixels; ++i) {
        buffer[i] = static_cast<float>(gray_pixels[i]);
    }

    const float thresh = static_cast<float>(config.threshold);
    const bool invert = config.invert;

    for (int y = 0; y < height; ++y) {
        size_t row_out = static_cast<size_t>(y) * static_cast<size_t>(result.bytes_per_row);
        for (int x = 0; x < width; ++x) {
            size_t idx = static_cast<size_t>(y) * static_cast<size_t>(width) + static_cast<size_t>(x);
            float old_val = std::clamp(buffer[idx], 0.0f, 255.0f);
            float new_val = (old_val < thresh) ? 0.0f : 255.0f;
            float err = old_val - new_val;

            bool is_black = (new_val == 0.0f);
            if (invert) is_black = !is_black;
            set_bit(result.data, row_out, x, is_black);

            // Floyd-Steinberg error distribution:
            //       X   7/16
            // 3/16 5/16 1/16
            if (x + 1 < width) {
                buffer[idx + 1] += err * (7.0f / 16.0f);
            }
            if (y + 1 < height) {
                if (x > 0) {
                    buffer[idx + static_cast<size_t>(width) - 1] += err * (3.0f / 16.0f);
                }
                buffer[idx + static_cast<size_t>(width)] += err * (5.0f / 16.0f);
                if (x + 1 < width) {
                    buffer[idx + static_cast<size_t>(width) + 1] += err * (1.0f / 16.0f);
                }
            }
        }
    }

    return result;
}

MonochromeBitmap process_atkinson(
    const uint8_t* gray_pixels,
    int width,
    int height,
    const ZplConfig& config
) {
    MonochromeBitmap result;
    result.width = width;
    result.height = height;
    result.bytes_per_row = (width + 7) / 8;
    result.data.assign(static_cast<size_t>(result.bytes_per_row) * static_cast<size_t>(height), 0);

    const size_t total_pixels = static_cast<size_t>(width) * static_cast<size_t>(height);
    std::vector<float> buffer(total_pixels);
    for (size_t i = 0; i < total_pixels; ++i) {
        buffer[i] = static_cast<float>(gray_pixels[i]);
    }

    const float thresh = static_cast<float>(config.threshold);
    const bool invert = config.invert;

    // Atkinson distributes 1/8 to 6 neighbors:
    //      X   1/8 1/8
    // 1/8 1/8 1/8
    //     1/8
    for (int y = 0; y < height; ++y) {
        size_t row_out = static_cast<size_t>(y) * static_cast<size_t>(result.bytes_per_row);
        for (int x = 0; x < width; ++x) {
            size_t idx = static_cast<size_t>(y) * static_cast<size_t>(width) + static_cast<size_t>(x);
            float old_val = std::clamp(buffer[idx], 0.0f, 255.0f);
            float new_val = (old_val < thresh) ? 0.0f : 255.0f;
            float err_fraction = (old_val - new_val) / 8.0f;

            bool is_black = (new_val == 0.0f);
            if (invert) is_black = !is_black;
            set_bit(result.data, row_out, x, is_black);

            if (x + 1 < width) buffer[idx + 1] += err_fraction;
            if (x + 2 < width) buffer[idx + 2] += err_fraction;
            if (y + 1 < height) {
                if (x > 0) buffer[idx + static_cast<size_t>(width) - 1] += err_fraction;
                buffer[idx + static_cast<size_t>(width)] += err_fraction;
                if (x + 1 < width) buffer[idx + static_cast<size_t>(width) + 1] += err_fraction;
            }
            if (y + 2 < height) {
                buffer[idx + 2 * static_cast<size_t>(width)] += err_fraction;
            }
        }
    }

    return result;
}

} // anonymous namespace

MonochromeBitmap DitherEngine::process(
    const uint8_t* gray_pixels,
    int width,
    int height,
    const ZplConfig& config
) {
    if (!gray_pixels || width <= 0 || height <= 0) {
        return MonochromeBitmap{};
    }

    switch (config.dither) {
        case DitherMode::FloydSteinberg:
            return process_floyd_steinberg(gray_pixels, width, height, config);
        case DitherMode::Atkinson:
            return process_atkinson(gray_pixels, width, height, config);
        case DitherMode::None:
        default:
            return process_threshold(gray_pixels, width, height, config);
    }
}

} // namespace zpl
