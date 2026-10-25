#include <gtest/gtest.h>
#include "zpl/zpl.hpp"

namespace {

class CloseSpyTransport : public zpl::MockTransport {
public:
    explicit CloseSpyTransport(bool* closed_flag) : closed_flag_(closed_flag) {}
    void close() override {
        if (closed_flag_) *closed_flag_ = true;
        MockTransport::close();
    }
private:
    bool* closed_flag_;
};

} // anonymous namespace

TEST(ZplPrinterTest, LifecycleAndConnect) {
    zpl::ZplConfig cfg;
    auto mock = std::make_unique<zpl::MockTransport>();
    auto* mock_ptr = mock.get();

    zpl::ZplPrinter printer(cfg, std::move(mock));
    EXPECT_FALSE(printer.is_connected());

    EXPECT_TRUE(printer.connect());
    EXPECT_TRUE(printer.is_connected());
    EXPECT_TRUE(mock_ptr->is_open());

    printer.disconnect();
    EXPECT_FALSE(printer.is_connected());
    EXPECT_FALSE(mock_ptr->is_open());
}

TEST(ZplPrinterTest, SetTransportClosesOld) {
    zpl::ZplConfig cfg;
    bool mock1_closed = false;
    auto mock1 = std::make_unique<CloseSpyTransport>(&mock1_closed);

    zpl::ZplPrinter printer(cfg, std::move(mock1));
    EXPECT_TRUE(printer.connect());
    EXPECT_FALSE(mock1_closed);

    auto mock2 = std::make_unique<zpl::MockTransport>("mock2");
    auto* mock2_ptr = mock2.get();

    // Setting new transport must automatically close old transport
    printer.set_transport(std::move(mock2));
    EXPECT_TRUE(mock1_closed);
    EXPECT_FALSE(mock2_ptr->is_open());

    EXPECT_TRUE(printer.connect());
    EXPECT_TRUE(mock2_ptr->is_open());
}

TEST(ZplPrinterTest, PrintRawAndDiagnostics) {
    zpl::ZplConfig cfg;
    cfg.width_dots = 400;
    cfg.length_dots = 600;

    auto mock = std::make_unique<zpl::MockTransport>();
    auto* mock_ptr = mock.get();
    zpl::ZplPrinter printer(cfg, std::move(mock));

    EXPECT_TRUE(printer.print_alignment_ruler());
    std::string captured = mock_ptr->get_captured_string();
    EXPECT_NE(captured.find("^PW400\n"), std::string::npos);
    mock_ptr->clear_captured_data();

    EXPECT_TRUE(printer.print_raw("^XA^FDTEST^FS^XZ"));
    EXPECT_EQ(mock_ptr->get_captured_string(), "^XA^FDTEST^FS^XZ");
    mock_ptr->clear_captured_data();

    EXPECT_TRUE(printer.calibrate_sensor());
    EXPECT_EQ(mock_ptr->get_captured_string(), "~JC\r\n");
}

TEST(ZplPrinterTest, PrintImage) {
    zpl::ZplConfig cfg;
    cfg.width_dots = 16;
    cfg.length_dots = 8;

    auto mock = std::make_unique<zpl::MockTransport>();
    auto* mock_ptr = mock.get();
    zpl::ZplPrinter printer(cfg, std::move(mock));

    std::vector<uint8_t> gray(16 * 8, 255); // White canvas
    gray[0] = 0; // 1 black pixel

    EXPECT_TRUE(printer.print_image(gray, 16, 8));
    std::string captured = mock_ptr->get_captured_string();
    EXPECT_NE(captured.find("^GFA,"), std::string::npos);
    EXPECT_NE(captured.find("^XZ\n"), std::string::npos);
}

TEST(ZplPrinterTest, QueryIdentity) {
    zpl::ZplConfig cfg;
    auto mock = std::make_unique<zpl::MockTransport>();
    auto* mock_ptr = mock.get();
    zpl::ZplPrinter printer(cfg, std::move(mock));

    mock_ptr->set_simulated_read_response("LabelPrinter,V1.11,8,0KB\r\n");
    std::string id = printer.query_identity();
    EXPECT_EQ(id, "LabelPrinter,V1.11,8,0KB\r\n");
    EXPECT_EQ(printer.last_error(), zpl::PrinterError::None);
}

TEST(ZplPrinterTest, ErrorHandling) {
    zpl::ZplConfig cfg;

    // 1. No transport configured
    zpl::ZplPrinter printer_no_transport(cfg, nullptr);
    EXPECT_FALSE(printer_no_transport.connect());
    EXPECT_EQ(printer_no_transport.last_error(), zpl::PrinterError::NoTransport);
    EXPECT_FALSE(printer_no_transport.last_error_message().empty());

    // 2. Open failure
    auto mock_fail_open = std::make_unique<zpl::MockTransport>();
    mock_fail_open->set_fail_open(true);
    zpl::ZplPrinter printer_fail_open(cfg, std::move(mock_fail_open));
    EXPECT_FALSE(printer_fail_open.connect());
    EXPECT_EQ(printer_fail_open.last_error(), zpl::PrinterError::ConnectionFailed);

    // 3. Write failure
    auto mock_fail_write = std::make_unique<zpl::MockTransport>();
    mock_fail_write->set_fail_write(true);
    zpl::ZplPrinter printer_fail_write(cfg, std::move(mock_fail_write));
    EXPECT_FALSE(printer_fail_write.print_raw("TEST"));
    EXPECT_EQ(printer_fail_write.last_error(), zpl::PrinterError::WriteFailed);

    // 4. Invalid data
    auto mock = std::make_unique<zpl::MockTransport>();
    zpl::ZplPrinter printer(cfg, std::move(mock));
    EXPECT_FALSE(printer.print_raw(static_cast<const uint8_t*>(nullptr), 0));
    EXPECT_EQ(printer.last_error(), zpl::PrinterError::InvalidData);

    std::vector<uint8_t> empty_pixels;
    EXPECT_FALSE(printer.print_image(empty_pixels, 10, 10));
    EXPECT_EQ(printer.last_error(), zpl::PrinterError::InvalidData);
}

