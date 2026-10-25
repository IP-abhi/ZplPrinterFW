#include "zpl/transport/linux_usb_transport.hpp"

#include <fcntl.h>
#include <unistd.h>
#include <sys/select.h>
#include <cerrno>

namespace zpl {

LinuxUsbTransport::LinuxUsbTransport(std::string device_path)
    : device_path_(std::move(device_path)), fd_(-1) {
}

LinuxUsbTransport::~LinuxUsbTransport() {
    close();
}

bool LinuxUsbTransport::open() {
    if (fd_ >= 0) return true;

    fd_ = ::open(device_path_.c_str(), O_RDWR | O_NOCTTY);
    return fd_ >= 0;
}

void LinuxUsbTransport::close() {
    if (fd_ >= 0) {
        ::close(fd_);
        fd_ = -1;
    }
}

bool LinuxUsbTransport::is_open() const {
    return fd_ >= 0;
}

bool LinuxUsbTransport::write(const uint8_t* data, size_t size) {
    if (fd_ < 0 || !data || size == 0) return false;

    size_t written_total = 0;
    while (written_total < size) {
        ssize_t n = ::write(fd_, data + written_total, size - written_total);
        if (n < 0) {
            if (errno == EINTR) continue;
            return false;
        }
        written_total += static_cast<size_t>(n);
    }

    return true;
}

std::vector<uint8_t> LinuxUsbTransport::read(size_t max_bytes, std::chrono::milliseconds timeout) {
    if (fd_ < 0 || max_bytes == 0) return {};

    std::vector<uint8_t> buffer(max_bytes);
    fd_set read_fds;
    FD_ZERO(&read_fds);
    FD_SET(fd_, &read_fds);

    auto ms = timeout.count();
    struct timeval tv;
    tv.tv_sec = static_cast<time_t>(ms / 1000);
    tv.tv_usec = static_cast<suseconds_t>((ms % 1000) * 1000);

    int ret = ::select(fd_ + 1, &read_fds, nullptr, nullptr, &tv);
    if (ret <= 0) {
        return {};
    }

    ssize_t bytes_read = ::read(fd_, buffer.data(), buffer.size());
    if (bytes_read <= 0) {
        return {};
    }

    buffer.resize(static_cast<size_t>(bytes_read));
    return buffer;
}

} // namespace zpl

