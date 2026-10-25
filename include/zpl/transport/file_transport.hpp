#pragma once

#include "zpl/transport.hpp"
#include <fstream>
#include <string>

namespace zpl {

/// Write-only transport that dumps ZPL to a disk file.
class FileTransport : public ITransport {
public:
    explicit FileTransport(std::string file_path = "out.zpl")
        : file_path_(std::move(file_path)), is_open_(false) {}

    ~FileTransport() override {
        close();
    }

    bool open() override;
    void close() override;
    bool is_open() const override;

    bool write(const uint8_t* data, size_t size) override;

    std::vector<uint8_t> read(
        size_t max_bytes = 1024,
        std::chrono::milliseconds timeout = std::chrono::milliseconds(1000)
    ) override;

    std::string get_endpoint_name() const override {
        return file_path_;
    }

private:
    std::string file_path_;
    std::ofstream file_stream_;
    bool is_open_ = false;
};

} // namespace zpl
