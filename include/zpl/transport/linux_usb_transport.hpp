#pragma once

#include "zpl/transport.hpp"
#include <string>

namespace zpl {

class LinuxUsbTransport : public ITransport {
public:
    explicit LinuxUsbTransport(std::string device_path = "/dev/usb/lp0");
    ~LinuxUsbTransport() override;

    bool open() override;
    void close() override;
    bool is_open() const override;

    bool write(const uint8_t* data, size_t size) override;

    std::vector<uint8_t> read(
        size_t max_bytes = 1024,
        std::chrono::milliseconds timeout = std::chrono::milliseconds(1000)
    ) override;

    std::string get_endpoint_name() const override {
        return device_path_;
    }

private:
    std::string device_path_;
    int fd_;
};

} // namespace zpl

