#include "zpl/transport/file_transport.hpp"

namespace zpl {

bool FileTransport::open() {
    if (file_path_.empty()) {
        return false;
    }

    file_stream_.open(file_path_, std::ios::out | std::ios::binary | std::ios::trunc);
    is_open_ = file_stream_.is_open();
    return is_open_;
}

void FileTransport::close() {
    if (file_stream_.is_open()) {
        file_stream_.flush();
        file_stream_.close();
    }
    is_open_ = false;
}

bool FileTransport::is_open() const {
    return is_open_;
}

bool FileTransport::write(const uint8_t* data, size_t size) {
    if (!is_open_ || !data || size == 0) {
        return false;
    }

    file_stream_.write(reinterpret_cast<const char*>(data), size);
    return file_stream_.good();
}

std::vector<uint8_t> FileTransport::read(size_t max_bytes, std::chrono::milliseconds timeout) {
    (void)max_bytes;
    (void)timeout;
    // File transport is write-only; cannot read responses from a generic output file.
    return {};
}

} // namespace zpl
