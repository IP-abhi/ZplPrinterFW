#pragma once

#include "zpl/transport.hpp"
#include <vector>
#include <string>

namespace zpl {

/// In-memory transport for testing and dry-run inspection.
class MockTransport : public ITransport {
public:
    explicit MockTransport(std::string name = "mock://in-memory")
        : endpoint_name_(std::move(name)), is_open_(false) {}

    ~MockTransport() override {
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
        return endpoint_name_;
    }

    // Inspection helpers
    const std::vector<uint8_t>& get_captured_data() const {
        return captured_data_;
    }

    std::string get_captured_string() const {
        return std::string(captured_data_.begin(), captured_data_.end());
    }

    void clear_captured_data() {
        captured_data_.clear();
    }

    // Simulated responses
    void set_simulated_read_response(std::vector<uint8_t> data) {
        simulated_response_ = std::move(data);
    }

    void set_simulated_read_response(const std::string& str) {
        simulated_response_.assign(str.begin(), str.end());
    }

    // Failure injection for tests
    void set_fail_open(bool fail) { fail_open_ = fail; }
    void set_fail_write(bool fail) { fail_write_ = fail; }

private:
    std::string endpoint_name_;
    bool is_open_ = false;
    bool fail_open_ = false;
    bool fail_write_ = false;
    std::vector<uint8_t> captured_data_;
    std::vector<uint8_t> simulated_response_;
};

} // namespace zpl

