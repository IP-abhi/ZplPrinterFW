#pragma once

// Main include header for libzplcore
#include "zpl/config.hpp"
#include "zpl/dither.hpp"
#include "zpl/encoder.hpp"
#include "zpl/diagnostics.hpp"
#include "zpl/transport.hpp"
#include "zpl/printer.hpp"
#include "zpl/transport/file_transport.hpp"
#include "zpl/transport/mock_transport.hpp"

#if defined(__linux__) && !defined(__ANDROID__)
#include "zpl/transport/linux_usb_transport.hpp"
#include "zpl/transport/linux_bt_transport.hpp"
#endif
