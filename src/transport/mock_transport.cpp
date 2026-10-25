#include "zpl/transport/mock_transport.hpp"
#include <algorithm>

namespace zpl {

bool MockTransport::open() {
    if (fail_open_) {
        is_open_ = false;
        return false;
    }
    is_open_ = true;
    return true;
}

void MockTransport::close() {
    is_open_ = false;
}

bool MockTransport::is_open() const {
    return is_open_;
}

bool MockTransport::write(const uint8_t* data, size_t size) {
    if (!is_open_ || !data || size == 0 || fail_write_) {
        return false;
    }

    captured_data_.insert(captured_data_.end(), data, data + size);
    return true;
}

std::vector<uint8_t> MockTransport::read(size_t max_bytes, std::chrono::milliseconds timeout) {
    (void)timeout;
    if (!is_open_ || simulated_response_.empty() || max_bytes == 0) {
        return {};
    }

    size_t count = std::min(max_bytes, simulated_response_.size());
    std::vector<uint8_t> out(simulated_response_.begin(), simulated_response_.begin() + count);
    simulated_response_.erase(simulated_response_.begin(), simulated_response_.begin() + count);
    return out;
}

} // namespace zpl

