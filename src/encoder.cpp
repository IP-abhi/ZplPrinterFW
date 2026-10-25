#include "zpl/encoder.hpp"
#include <algorithm>
#include <cctype>

namespace zpl {

namespace {

constexpr char HEX_LOOKUP[] = "0123456789ABCDEF";

int hex_char_to_val(char c) {
    if (c >= '0' && c <= '9') return c - '0';
    if (c >= 'A' && c <= 'F') return c - 'A' + 10;
    if (c >= 'a' && c <= 'f') return c - 'a' + 10;
    return 0;
}

} // anonymous namespace

std::string ZplEncoder::to_hex_string(const uint8_t* data, size_t length) {
    if (!data || length == 0) return "";
    std::string s;
    s.resize(length * 2);
    for (size_t i = 0; i < length; ++i) {
        s[i * 2]     = HEX_LOOKUP[data[i] >> 4];
        s[i * 2 + 1] = HEX_LOOKUP[data[i] & 0x0F];
    }
    return s;
}

std::vector<uint8_t> ZplEncoder::from_hex_string(const std::string& hex) {
    std::vector<uint8_t> bytes;
    bytes.reserve(hex.size() / 2);
    for (size_t i = 0; i + 1 < hex.size(); i += 2) {
        uint8_t b = static_cast<uint8_t>((hex_char_to_val(hex[i]) << 4) | hex_char_to_val(hex[i + 1]));
        bytes.push_back(b);
    }
    return bytes;
}

std::string ZplEncoder::compress_row(const std::string& hex_row) {
    if (hex_row.empty()) return "";

    bool all_zero = true;
    bool all_one = true;
    for (char c : hex_row) {
        if (c != '0') all_zero = false;
        if (c != 'F' && c != 'f') all_one = false;
        if (!all_zero && !all_one) break;
    }
    if (all_zero) return ",";
    if (all_one) return "!";

    std::string out;
    out.reserve(hex_row.size());

    size_t i = 0;
    const size_t len = hex_row.size();

    while (i < len) {
        char ch = hex_row[i];
        size_t n = 1;
        while (i + n < len && hex_row[i + n] == ch) {
            ++n;
        }
        i += n;

        // End-of-row blank/fill suppression
        if (i >= len && (ch == '0' || ch == 'F' || ch == 'f')) {
            out.push_back(ch == '0' ? ',' : '!');
            break;
        }

        // Multiples of 20 (g = 20, h = 40, ..., z = 400)
        while (n >= 20) {
            size_t k = std::min<size_t>(n / 20, 20);
            out.push_back(static_cast<char>('g' + k - 1));
            n -= 20 * k;
        }

        // Remainder 1-19 (G = 1, H = 2, ..., Y = 19)
        if (n > 0) {
            out.push_back(static_cast<char>('G' + n - 1));
        }

        out.push_back(ch);
    }

    return out;
}

std::string ZplEncoder::decompress_row(const std::string& compressed_row, size_t expected_hex_len) {
    std::string out;
    out.reserve(expected_hex_len > 0 ? expected_hex_len : compressed_row.size() * 2);

    size_t repeat_count = 0;
    for (char c : compressed_row) {
        if (c >= 'G' && c <= 'Y') {
            repeat_count += static_cast<size_t>(c - 'G' + 1);
        } else if (c >= 'g' && c <= 'z') {
            repeat_count += static_cast<size_t>((c - 'g' + 1) * 20);
        } else if ((c >= '0' && c <= '9') || (c >= 'A' && c <= 'F') || (c >= 'a' && c <= 'f')) {
            size_t count = (repeat_count == 0) ? 1 : repeat_count;
            char upper_c = static_cast<char>(std::toupper(static_cast<unsigned char>(c)));
            out.append(count, upper_c);
            repeat_count = 0;
            if (expected_hex_len > 0 && out.size() >= expected_hex_len) {
                break;
            }
        } else if (c == ',') {
            if (expected_hex_len > out.size()) {
                out.append(expected_hex_len - out.size(), '0');
            }
            repeat_count = 0;
            break;
        } else if (c == '!') {
            if (expected_hex_len > out.size()) {
                out.append(expected_hex_len - out.size(), 'F');
            }
            repeat_count = 0;
            break;
        }
    }

    if (expected_hex_len > 0 && out.size() < expected_hex_len) {
        out.append(expected_hex_len - out.size(), '0');
    }

    return out;
}

std::string ZplEncoder::compress_gfa_payload(
    const uint8_t* data,
    size_t total_bytes,
    int bytes_per_row
) {
    if (!data || total_bytes == 0 || bytes_per_row <= 0) {
        return "";
    }

    std::string out;
    out.reserve(total_bytes * 2);

    std::string prev_row;
    std::string curr_row;
    curr_row.resize(static_cast<size_t>(bytes_per_row) * 2);

    size_t rows = total_bytes / static_cast<size_t>(bytes_per_row);
    for (size_t y = 0; y < rows; ++y) {
        const uint8_t* row_data = data + (y * static_cast<size_t>(bytes_per_row));
        for (int b = 0; b < bytes_per_row; ++b) {
            uint8_t byte = row_data[b];
            curr_row[static_cast<size_t>(b) * 2]     = HEX_LOOKUP[byte >> 4];
            curr_row[static_cast<size_t>(b) * 2 + 1] = HEX_LOOKUP[byte & 0x0F];
        }

        if (!prev_row.empty() && curr_row == prev_row) {
            out.push_back(':');
        } else {
            out.append(compress_row(curr_row));
            prev_row = curr_row;
        }
    }

    return out;
}

std::vector<uint8_t> ZplEncoder::decompress_gfa_payload(
    const std::string& payload,
    size_t total_bytes,
    int bytes_per_row
) {
    if (payload.empty() || total_bytes == 0 || bytes_per_row <= 0) {
        return {};
    }

    const size_t expected_row_hex_len = static_cast<size_t>(bytes_per_row * 2);
    const size_t rows = total_bytes / static_cast<size_t>(bytes_per_row);
    std::string prev_row_hex;
    std::vector<uint8_t> result;
    result.reserve(total_bytes);

    size_t pos = 0;
    const size_t len = payload.size();

    for (size_t r = 0; r < rows; ++r) {
        if (pos >= len) {
            result.resize(total_bytes, 0);
            break;
        }

        if (payload[pos] == ':') {
            ++pos;
            if (prev_row_hex.empty()) {
                prev_row_hex.assign(expected_row_hex_len, '0');
            }
            std::vector<uint8_t> row_bytes = from_hex_string(prev_row_hex);
            result.insert(result.end(), row_bytes.begin(), row_bytes.end());
            continue;
        }

        std::string decompressed_row_hex;
        decompressed_row_hex.reserve(expected_row_hex_len);
        size_t repeat_count = 0;

        while (pos < len) {
            char c = payload[pos++];
            if (c >= 'G' && c <= 'Y') {
                repeat_count += static_cast<size_t>(c - 'G' + 1);
            } else if (c >= 'g' && c <= 'z') {
                repeat_count += static_cast<size_t>((c - 'g' + 1) * 20);
            } else if ((c >= '0' && c <= '9') || (c >= 'A' && c <= 'F') || (c >= 'a' && c <= 'f')) {
                size_t count = (repeat_count == 0) ? 1 : repeat_count;
                char upper_c = static_cast<char>(std::toupper(static_cast<unsigned char>(c)));
                decompressed_row_hex.append(count, upper_c);
                repeat_count = 0;
                if (decompressed_row_hex.size() >= expected_row_hex_len) {
                    break;
                }
            } else if (c == ',') {
                if (decompressed_row_hex.size() < expected_row_hex_len) {
                    decompressed_row_hex.append(expected_row_hex_len - decompressed_row_hex.size(), '0');
                }
                repeat_count = 0;
                break;
            } else if (c == '!') {
                if (decompressed_row_hex.size() < expected_row_hex_len) {
                    decompressed_row_hex.append(expected_row_hex_len - decompressed_row_hex.size(), 'F');
                }
                repeat_count = 0;
                break;
            }
        }

        if (decompressed_row_hex.size() < expected_row_hex_len) {
            decompressed_row_hex.append(expected_row_hex_len - decompressed_row_hex.size(), '0');
        } else if (decompressed_row_hex.size() > expected_row_hex_len) {
            decompressed_row_hex.resize(expected_row_hex_len);
        }

        prev_row_hex = decompressed_row_hex;
        std::vector<uint8_t> row_bytes = from_hex_string(decompressed_row_hex);
        result.insert(result.end(), row_bytes.begin(), row_bytes.end());
    }

    if (result.size() < total_bytes) {
        result.resize(total_bytes, 0);
    } else if (result.size() > total_bytes) {
        result.resize(total_bytes);
    }

    return result;
}

std::string ZplEncoder::build_preamble(const ZplConfig& config) {
    std::string s;
    s.reserve(128);
    s += "^XA\n";
    s += "^PW" + std::to_string(config.width_dots) + "\n";
    s += "^LL" + std::to_string(config.length_dots) + "\n";
    s += "^MD" + std::to_string(config.darkness) + "\n";
    s += "^PR" + std::to_string(config.print_speed) + "\n";
    s += "^LH" + std::to_string(config.shift_x) + "," + std::to_string(config.shift_y) + "\n";

    switch (config.tracking) {
        case MediaTracking::Continuous: s += "^MNN\n"; break;
        case MediaTracking::WebGap:     s += "^MNY\n"; break;
        case MediaTracking::BlackMark:  s += "^MNM\n"; break;
    }
    switch (config.media_type) {
        case MediaType::DirectThermal:  s += "^MTD\n"; break;
        case MediaType::ThermalTransfer:s += "^MTT\n"; break;
    }
    return s;
}

std::string ZplEncoder::encode_image(
    const MonochromeBitmap& bitmap,
    const ZplConfig& config,
    bool compress
) {
    if (bitmap.data.empty() || bitmap.width <= 0 || bitmap.height <= 0) {
        return "";
    }

    int bpr = bitmap.bytes_per_row;
    size_t total = bitmap.data.size();

    std::string payload;
    if (compress) {
        payload = compress_gfa_payload(bitmap.data.data(), total, bpr);
    } else {
        payload = to_hex_string(bitmap.data.data(), total);
    }

    std::string zpl = build_preamble(config);
    zpl += "^FO0,0^GFA," + std::to_string(total) + ","
                         + std::to_string(total) + ","
                         + std::to_string(bpr) + ","
                         + payload + "^FS\n";
    zpl += "^XZ\n";

    return zpl;
}

} // namespace zpl
