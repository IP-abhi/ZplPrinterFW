#pragma once

#include <string>
#include <vector>
#include "zpl/config.hpp"
#include "zpl/dither.hpp"

namespace zpl {

class ZplEncoder {
public:
    // Generate complete ZPL job string for a 1-bit monochrome bitmap
    static std::string encode_image(
        const MonochromeBitmap& bitmap,
        const ZplConfig& config,
        bool compress = true
    );

    // Compress raw 1-bit monochrome data into ZPL ^GFA payload body
    static std::string compress_gfa_payload(
        const uint8_t* data,
        size_t total_bytes,
        int bytes_per_row
    );

    // Compress a single row of hexadecimal characters according to ZPL ASCII RLE rules
    static std::string compress_row(const std::string& hex_row);

    // Decompress a single row of ZPL compressed ASCII RLE back to raw hex characters
    static std::string decompress_row(
        const std::string& compressed_row,
        size_t expected_hex_len
    );

    // Decompress a full ZPL ^GFA payload (including line repeats ':') back to raw binary bytes
    static std::vector<uint8_t> decompress_gfa_payload(
        const std::string& payload,
        size_t total_bytes,
        int bytes_per_row
    );

    // Converts a single byte buffer to an uppercase hexadecimal string
    static std::string to_hex_string(const uint8_t* data, size_t length);

    // Converts a hexadecimal string back to raw bytes
    static std::vector<uint8_t> from_hex_string(const std::string& hex);

    // Generates the standard ZPL preamble (^XA, ^PW, ^LL, ^MD, ^PR, ^LH, ^MN, ^MT)
    static std::string build_preamble(const ZplConfig& config);
};

} // namespace zpl
