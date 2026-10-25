#include "zpl/printer.hpp"

namespace zpl {

ZplPrinter::ZplPrinter(ZplConfig config, std::unique_ptr<ITransport> transport)
    : config_(config), transport_(std::move(transport)) {
}

ZplPrinter::~ZplPrinter() {
    disconnect();
}

bool ZplPrinter::connect() {
    if (!transport_) {
        set_error(PrinterError::NoTransport, "No transport configured");
        return false;
    }
    if (transport_->is_open()) {
        set_error(PrinterError::None);
        return true;
    }
    if (!transport_->open()) {
        set_error(PrinterError::ConnectionFailed, "Failed to open transport endpoint: " + transport_->get_endpoint_name());
        return false;
    }
    set_error(PrinterError::None);
    return true;
}

void ZplPrinter::disconnect() {
    if (transport_ && transport_->is_open()) {
        transport_->close();
    }
}

bool ZplPrinter::is_connected() const {
    return transport_ && transport_->is_open();
}

void ZplPrinter::set_transport(std::unique_ptr<ITransport> transport) {
    if (transport_ && transport_->is_open()) {
        transport_->close();
    }
    transport_ = std::move(transport);
    set_error(PrinterError::None);
}

bool ZplPrinter::print_raw(const std::string& raw_zpl) {
    return print_raw(reinterpret_cast<const uint8_t*>(raw_zpl.data()), raw_zpl.size());
}

bool ZplPrinter::print_raw(const uint8_t* data, size_t size) {
    if (!data || size == 0) {
        set_error(PrinterError::InvalidData, "Cannot print empty data");
        return false;
    }
    if (!is_connected()) {
        if (!connect()) return false;
    }
    if (!transport_->write(data, size)) {
        set_error(PrinterError::WriteFailed, "Failed to write data to transport: " + transport_->get_endpoint_name());
        return false;
    }
    set_error(PrinterError::None);
    return true;
}

bool ZplPrinter::print_image(const uint8_t* grayscale_pixels, int width, int height) {
    if (!grayscale_pixels || width <= 0 || height <= 0) {
        set_error(PrinterError::InvalidData, "Invalid image dimensions or null pixel buffer");
        return false;
    }

    MonochromeBitmap mono = DitherEngine::process(grayscale_pixels, width, height, config_);
    if (mono.data.empty()) {
        set_error(PrinterError::InvalidData, "Dithering failed to generate bitmap data");
        return false;
    }
    return print_monochrome(mono);
}

bool ZplPrinter::print_monochrome(const MonochromeBitmap& bitmap) {
    if (bitmap.data.empty() || bitmap.width <= 0 || bitmap.height <= 0) {
        set_error(PrinterError::InvalidData, "Monochrome bitmap is empty or has invalid dimensions");
        return false;
    }

    std::string zpl = ZplEncoder::encode_image(bitmap, config_, /*compress=*/true);
    return print_raw(zpl);
}

bool ZplPrinter::print_alignment_ruler() {
    std::string zpl = Diagnostics::generate_ruler(config_);
    return print_raw(zpl);
}

bool ZplPrinter::print_bands_test() {
    std::string zpl = Diagnostics::generate_bands(config_);
    return print_raw(zpl);
}

bool ZplPrinter::print_test_label() {
    std::string zpl = Diagnostics::generate_test_label(config_);
    return print_raw(zpl);
}

std::string ZplPrinter::query_identity(std::chrono::milliseconds timeout) {
    if (!is_connected()) {
        if (!connect()) return "";
    }

    std::string cmd = Diagnostics::query_identity();
    if (!transport_->write(cmd)) {
        set_error(PrinterError::WriteFailed, "Failed to send ~HI query command");
        return "";
    }

    std::vector<uint8_t> resp = transport_->read(256, timeout);
    if (resp.empty()) {
        set_error(PrinterError::ReadTimeout, "Timeout waiting for ~HI query response");
        return "";
    }

    set_error(PrinterError::None);
    return std::string(resp.begin(), resp.end());
}

bool ZplPrinter::calibrate_sensor() {
    return print_raw(Diagnostics::calibrate_sensor());
}

std::string ZplPrinter::last_error_message() const {
    if (!last_error_msg_.empty()) {
        return last_error_msg_;
    }
    switch (last_error_) {
        case PrinterError::None: return "Success";
        case PrinterError::NoTransport: return "No transport configured";
        case PrinterError::ConnectionFailed: return "Connection failed";
        case PrinterError::WriteFailed: return "Write failed";
        case PrinterError::ReadTimeout: return "Read timeout";
        case PrinterError::InvalidData: return "Invalid data";
    }
    return "Unknown error";
}

} // namespace zpl
