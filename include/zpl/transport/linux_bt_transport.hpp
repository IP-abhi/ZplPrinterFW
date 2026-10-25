#pragma once

#include "zpl/transport.hpp"
#include <string>

namespace zpl {

class LinuxBtTransport : public ITransport {
public:
    explicit LinuxBtTransport(std::string mac_address, uint8_t channel = 1);
    ~LinuxBtTransport() override;

    bool open() override;
    void close() override;
    bool is_open() const override;

    bool write(const uint8_t* data, size_t size) override;

    std::vector<uint8_t> read(
        size_t max_bytes = 1024,
        std::chrono::milliseconds timeout = std::chrono::milliseconds(1000)
    ) override;

    std::string get_endpoint_name() const override {
        return mac_address_ + " (ch " + std::to_string(channel_) + ")";
    }

private:
    std::string mac_address_;
    uint8_t channel_;
    int sock_fd_;
};

} // namespace zpl

