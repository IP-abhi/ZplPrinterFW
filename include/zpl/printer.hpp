#pragma once

#include <memory>
#include <string>
#include <vector>
#include <chrono>

#include "zpl/config.hpp"
#include "zpl/transport.hpp"
#include "zpl/dither.hpp"
#include "zpl/encoder.hpp"
#include "zpl/diagnostics.hpp"

namespace zpl {

enum class PrinterError {
    None,
    NoTransport,
    ConnectionFailed,
    WriteFailed,
    ReadTimeout,
    InvalidData
};

class ZplPrinter {
public:
    explicit ZplPrinter(ZplConfig config, std::unique_ptr<ITransport> transport = nullptr);
    ~ZplPrinter();

    // Move-only semantics because it owns unique_ptr
    ZplPrinter(const ZplPrinter&) = delete;
    ZplPrinter& operator=(const ZplPrinter&) = delete;
    ZplPrinter(ZplPrinter&&) noexcept = default;
    ZplPrinter& operator=(ZplPrinter&&) noexcept = default;

    // Transport lifecycle
    bool connect();
    void disconnect();
    bool is_connected() const;

    // High-level printing methods
    bool print_raw(const std::string& raw_zpl);
    bool print_raw(const uint8_t* data, size_t size);

    // Prints an 8-bit grayscale image (0 = black, 255 = white)
    bool print_image(const uint8_t* grayscale_pixels, int width, int height);
    bool print_image(const std::vector<uint8_t>& grayscale_pixels, int width, int height) {
        if (width <= 0 || height <= 0 ||
            grayscale_pixels.size() < static_cast<size_t>(width) * static_cast<size_t>(height)) {
            return false;
        }
        return print_image(grayscale_pixels.data(), width, height);
    }

    // Prints an already-dithered 1-bit packed monochrome bitmap
    bool print_monochrome(const MonochromeBitmap& bitmap);

    // Diagnostic and test routines
    bool print_alignment_ruler();
    bool print_bands_test();
    bool print_test_label();

    // Hardware query & configuration commands
    std::string query_identity(std::chrono::milliseconds timeout = std::chrono::milliseconds(1000));
    bool calibrate_sensor();

    // Configuration accessors
    const ZplConfig& get_config() const { return config_; }
    void set_config(const ZplConfig& config) { config_ = config; }

    // Transport ownership & inspection
    ITransport* get_transport() const { return transport_.get(); }
    void set_transport(std::unique_ptr<ITransport> transport);

    // Error diagnostics
    PrinterError last_error() const { return last_error_; }
    std::string last_error_message() const;

private:
    void set_error(PrinterError err, std::string msg = "") {
        last_error_ = err;
        last_error_msg_ = std::move(msg);
    }

    ZplConfig config_;
    std::unique_ptr<ITransport> transport_;
    PrinterError last_error_ = PrinterError::None;
    std::string last_error_msg_;
};

} // namespace zpl
