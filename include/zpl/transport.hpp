#pragma once

#include <cstdint>
#include <vector>
#include <string>
#include <chrono>

namespace zpl {

class ITransport {
public:
    virtual ~ITransport() = default;

    // Connection lifecycle
    virtual bool open() = 0;
    virtual void close() = 0;
    virtual bool is_open() const = 0;

    // Send raw bytes to the printer endpoint
    virtual bool write(const uint8_t* data, size_t size) = 0;

    // Convenience string overload
    bool write(const std::string& text) {
        return write(reinterpret_cast<const uint8_t*>(text.data()), text.size());
    }

    // Convenience byte vector overload
    bool write(const std::vector<uint8_t>& bytes) {
        return write(bytes.data(), bytes.size());
    }

    // Read responses (for status queries such as ~HI)
    virtual std::vector<uint8_t> read(
        size_t max_bytes = 1024,
        std::chrono::milliseconds timeout = std::chrono::milliseconds(1000)
    ) = 0;

    // Descriptive name (e.g. "/dev/usb/lp0", "00:11:22:33:44:55", "out.zpl")
    virtual std::string get_endpoint_name() const = 0;
};

} // namespace zpl

