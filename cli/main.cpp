#include "zpl/zpl.hpp"
#include <iostream>
#include <string>
#include <vector>
#include <memory>

void print_help(const char* prog) {
    std::cout << "Usage: " << prog << " [options]\n\n"
              << "Transport Options (choose one, default is --file out.zpl):\n"
              << "  --usb [PATH]          Send to USB character device (default: /dev/usb/lp0)\n"
              << "  --bt MAC              Send to Bluetooth Classic RFCOMM MAC (e.g. 00:11:22:33:44:55)\n"
              << "  --file [PATH]         Write raw ZPL to file (default: out.zpl)\n\n"
              << "Action Options:\n"
              << "  --ruler               Print physical alignment ruler test\n"
              << "  --test                Print formatted diagnostic label (text, box, barcode)\n"
              << "  --bands               Print buffer/feed test bands\n"
              << "  --query               Send ~HI device identity query and read reply\n"
              << "  --calibrate           Send ~JC media sensor calibration command\n"
              << "  --raw \"ZPL\"           Send custom raw ZPL string\n\n"
              << "Configuration Tuning:\n"
              << "  --width-mm MM         Label width in mm (default: 101.6 mm / 4.00\")\n"
              << "  --length-mm MM        Label length in mm (default: 152.4 mm / 6.00\")\n"
              << "  --darkness N          Darkness index 1 to 30 (default: 30)\n"
              << "  --speed N             Print rate in inches/sec 2 to 6 (default: 2)\n"
              << "  --shift X             Horizontal offset in dots (^LH x, default: 0)\n"
              << "  --dither MODE         Dithering mode: 'floyd', 'atkinson', 'none' (default: floyd)\n"
              << "  --tracking MODE       Media tracking: 'continuous', 'gap', 'mark' (default: continuous)\n"
              << std::endl;
}

int main(int argc, char* argv[]) {
    if (argc < 2) {
        print_help(argv[0]);
        return 0;
    }

    zpl::ZplConfig config = zpl::ZplConfig::from_inches(4.0f, 6.0f, 203);
    std::string transport_type = "file";
    std::string transport_arg = "out.zpl";

    enum class Action {
        None,
        Ruler,
        Test,
        Bands,
        Query,
        Calibrate,
        Raw
    } action = Action::None;

    std::string raw_zpl;

    for (int i = 1; i < argc; ++i) {
        std::string arg = argv[i];

        if (arg == "-h" || arg == "--help") {
            print_help(argv[0]);
            return 0;
        } else if (arg == "--usb") {
            transport_type = "usb";
            if (i + 1 < argc && argv[i + 1][0] != '\0' && argv[i + 1][0] != '-') {
                transport_arg = argv[++i];
            } else {
                transport_arg = "/dev/usb/lp0";
            }
        } else if (arg == "--bt") {
            transport_type = "bt";
            if (i + 1 < argc && argv[i + 1][0] != '\0' && argv[i + 1][0] != '-') {
                transport_arg = argv[++i];
            } else {
                std::cerr << "Error: --bt requires a MAC address argument." << std::endl;
                return 1;
            }
        } else if (arg == "--file") {
            transport_type = "file";
            if (i + 1 < argc && argv[i + 1][0] != '\0' && argv[i + 1][0] != '-') {
                transport_arg = argv[++i];
            } else {
                transport_arg = "out.zpl";
            }
        } else if (arg == "--ruler") {
            action = Action::Ruler;
        } else if (arg == "--test") {
            action = Action::Test;
        } else if (arg == "--bands") {
            action = Action::Bands;
        } else if (arg == "--query") {
            action = Action::Query;
        } else if (arg == "--calibrate") {
            action = Action::Calibrate;
        } else if (arg == "--raw") {
            action = Action::Raw;
            if (i + 1 < argc) raw_zpl = argv[++i];
        } else if (arg == "--width-mm" && i + 1 < argc) {
            float mm = std::stof(argv[++i]);
            config.width_dots = static_cast<int>(mm * (config.dpi / 25.4f) + 0.5f);
        } else if (arg == "--length-mm" && i + 1 < argc) {
            float mm = std::stof(argv[++i]);
            config.length_dots = static_cast<int>(mm * (config.dpi / 25.4f) + 0.5f);
        } else if (arg == "--darkness" && i + 1 < argc) {
            config.darkness = std::stoi(argv[++i]);
        } else if (arg == "--speed" && i + 1 < argc) {
            config.print_speed = std::stoi(argv[++i]);
        } else if (arg == "--shift" && i + 1 < argc) {
            config.shift_x = std::stoi(argv[++i]);
        } else if (arg == "--dither" && i + 1 < argc) {
            std::string d = argv[++i];
            if (d == "none") config.dither = zpl::DitherMode::None;
            else if (d == "atkinson") config.dither = zpl::DitherMode::Atkinson;
            else config.dither = zpl::DitherMode::FloydSteinberg;
        } else if (arg == "--tracking" && i + 1 < argc) {
            std::string t = argv[++i];
            if (t == "gap") config.tracking = zpl::MediaTracking::WebGap;
            else if (t == "mark") config.tracking = zpl::MediaTracking::BlackMark;
            else config.tracking = zpl::MediaTracking::Continuous;
        }
    }

    std::unique_ptr<zpl::ITransport> transport;

#if defined(__linux__) && !defined(__ANDROID__)
    if (transport_type == "usb") {
        transport = std::make_unique<zpl::LinuxUsbTransport>(transport_arg);
    } else if (transport_type == "bt") {
        transport = std::make_unique<zpl::LinuxBtTransport>(transport_arg);
    } else
#endif
    {
        transport = std::make_unique<zpl::FileTransport>(transport_arg);
    }

    std::string endpoint_desc = transport->get_endpoint_name();
    zpl::ZplPrinter printer(config, std::move(transport));

    std::cout << "[*] Transport: " << endpoint_desc << "\n"
              << "[*] Config: " << config.width_dots << "x" << config.length_dots << " dots ("
              << static_cast<int>(config.width_mm()) << "x" << static_cast<int>(config.length_mm()) << " mm) @ "
              << config.dpi << " DPI, darkness=" << config.darkness << std::endl;

    if (!printer.connect()) {
        std::cerr << "[-] Failed to open transport endpoint (" << endpoint_desc << "): "
                  << printer.last_error_message() << std::endl;
        return 1;
    }

    bool success = false;
    switch (action) {
        case Action::Ruler:
            std::cout << "[>] Printing alignment ruler..." << std::endl;
            success = printer.print_alignment_ruler();
            break;
        case Action::Test:
            std::cout << "[>] Printing formatted diagnostic test label..." << std::endl;
            success = printer.print_test_label();
            break;
        case Action::Bands:
            std::cout << "[>] Printing bands buffer test..." << std::endl;
            success = printer.print_bands_test();
            break;
        case Action::Query: {
            std::cout << "[>] Querying printer identity (~HI)..." << std::endl;
            std::string reply = printer.query_identity();
            if (!reply.empty()) {
                std::cout << "[+] Reply: " << reply << std::endl;
                success = true;
            } else {
                std::cout << "[!] No reply received from endpoint: " << printer.last_error_message() << std::endl;
            }
            break;
        }
        case Action::Calibrate:
            std::cout << "[>] Triggering media sensor calibration (~JC)..." << std::endl;
            success = printer.calibrate_sensor();
            break;
        case Action::Raw:
            std::cout << "[>] Sending raw ZPL string (" << raw_zpl.size() << " bytes)..." << std::endl;
            success = printer.print_raw(raw_zpl);
            break;
        case Action::None:
            std::cout << "[!] No action specified. Use --ruler, --test, --query, etc." << std::endl;
            return 0;
    }

    printer.disconnect();

    if (success) {
        std::cout << "[+] Operation completed successfully." << std::endl;
        return 0;
    } else {
        std::cerr << "[-] Operation failed: " << printer.last_error_message() << std::endl;
        return 1;
    }
}
