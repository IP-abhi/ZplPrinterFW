#include "zpl/transport/linux_bt_transport.hpp"

#include <sys/socket.h>
#include <unistd.h>
#include <sys/select.h>
#include <cerrno>
#include <cstdio>
#include <cstring>

namespace zpl {

namespace {

#ifndef AF_BLUETOOTH
#define AF_BLUETOOTH 31
#endif

#ifndef BTPROTO_RFCOMM
#define BTPROTO_RFCOMM 3
#endif

#pragma pack(push, 1)
struct BdAddr {
    uint8_t b[6];
};

struct SockAddrRc {
    sa_family_t rc_family;
    BdAddr rc_bdaddr;
    uint8_t rc_channel;
};
#pragma pack(pop)

// Parses MAC string (e.g. "AA:BB:CC:DD:EE:FF") into bdaddr_t little-endian byte order
bool parse_mac(const std::string& mac_str, BdAddr& out) {
    unsigned int b[6];
    if (std::sscanf(mac_str.c_str(), "%x:%x:%x:%x:%x:%x",
                    &b[5], &b[4], &b[3], &b[2], &b[1], &b[0]) == 6) {
        for (int i = 0; i < 6; ++i) {
            out.b[i] = static_cast<uint8_t>(b[i]);
        }
        return true;
    }
    return false;
}

} // anonymous namespace

LinuxBtTransport::LinuxBtTransport(std::string mac_address, uint8_t channel)
    : mac_address_(std::move(mac_address)), channel_(channel), sock_fd_(-1) {
}

LinuxBtTransport::~LinuxBtTransport() {
    close();
}

bool LinuxBtTransport::open() {
    if (sock_fd_ >= 0) return true;

    BdAddr target_addr{};
    if (!parse_mac(mac_address_, target_addr)) {
        return false;
    }

    sock_fd_ = ::socket(AF_BLUETOOTH, SOCK_STREAM, BTPROTO_RFCOMM);
    if (sock_fd_ < 0) {
        return false;
    }

    SockAddrRc addr{};
    std::memset(&addr, 0, sizeof(addr));
    addr.rc_family = AF_BLUETOOTH;
    addr.rc_channel = channel_;
    addr.rc_bdaddr = target_addr;

    if (::connect(sock_fd_, reinterpret_cast<struct sockaddr*>(&addr), sizeof(addr)) < 0) {
        ::close(sock_fd_);
        sock_fd_ = -1;
        return false;
    }

    return true;
}

void LinuxBtTransport::close() {
    if (sock_fd_ >= 0) {
        ::close(sock_fd_);
        sock_fd_ = -1;
    }
}

bool LinuxBtTransport::is_open() const {
    return sock_fd_ >= 0;
}

bool LinuxBtTransport::write(const uint8_t* data, size_t size) {
    if (sock_fd_ < 0 || !data || size == 0) return false;

    size_t written_total = 0;
    while (written_total < size) {
        ssize_t n = ::write(sock_fd_, data + written_total, size - written_total);
        if (n < 0) {
            if (errno == EINTR) continue;
            return false;
        }
        written_total += static_cast<size_t>(n);
    }

    return true;
}

std::vector<uint8_t> LinuxBtTransport::read(size_t max_bytes, std::chrono::milliseconds timeout) {
    if (sock_fd_ < 0 || max_bytes == 0) return {};

    std::vector<uint8_t> buffer(max_bytes);
    fd_set read_fds;
    FD_ZERO(&read_fds);
    FD_SET(sock_fd_, &read_fds);

    auto ms = timeout.count();
    struct timeval tv;
    tv.tv_sec = static_cast<time_t>(ms / 1000);
    tv.tv_usec = static_cast<suseconds_t>((ms % 1000) * 1000);

    int ret = ::select(sock_fd_ + 1, &read_fds, nullptr, nullptr, &tv);
    if (ret <= 0) {
        return {};
    }

    ssize_t bytes_read = ::read(sock_fd_, buffer.data(), buffer.size());
    if (bytes_read <= 0) {
        return {};
    }

    buffer.resize(static_cast<size_t>(bytes_read));
    return buffer;
}

} // namespace zpl

