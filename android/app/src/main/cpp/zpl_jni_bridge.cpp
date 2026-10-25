#include <jni.h>
#include <android/bitmap.h>
#include <android/log.h>
#include <string>
#include <vector>

#include "zpl/config.hpp"
#include "zpl/dither.hpp"
#include "zpl/encoder.hpp"
#include "zpl/diagnostics.hpp"

#define TAG "ZplNative"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

namespace {

zpl::ZplConfig parse_config(JNIEnv* env, jobject cfgObj) {
    zpl::ZplConfig cfg;
    if (!cfgObj) return cfg;

    jclass cls = env->GetObjectClass(cfgObj);

    jfieldID fWidth = env->GetFieldID(cls, "widthDots", "I");
    jfieldID fLength = env->GetFieldID(cls, "lengthDots", "I");
    jfieldID fDarkness = env->GetFieldID(cls, "darkness", "I");
    jfieldID fSpeed = env->GetFieldID(cls, "speed", "I");
    jfieldID fShiftX = env->GetFieldID(cls, "shiftX", "I");
    jfieldID fShiftY = env->GetFieldID(cls, "shiftY", "I");
    jfieldID fDither = env->GetFieldID(cls, "ditherMode", "I");
    jfieldID fThresh = env->GetFieldID(cls, "threshold", "I");
    jfieldID fInvert = env->GetFieldID(cls, "invert", "Z");
    jfieldID fTrack = env->GetFieldID(cls, "tracking", "I");
    jfieldID fMedia = env->GetFieldID(cls, "mediaType", "I");

    if (fWidth) cfg.width_dots = env->GetIntField(cfgObj, fWidth);
    if (fLength) cfg.length_dots = env->GetIntField(cfgObj, fLength);
    if (fDarkness) cfg.darkness = env->GetIntField(cfgObj, fDarkness);
    if (fSpeed) cfg.print_speed = env->GetIntField(cfgObj, fSpeed);
    if (fShiftX) cfg.shift_x = env->GetIntField(cfgObj, fShiftX);
    if (fShiftY) cfg.shift_y = env->GetIntField(cfgObj, fShiftY);

    if (fDither) {
        int d = env->GetIntField(cfgObj, fDither);
        if (d == 0) cfg.dither = zpl::DitherMode::None;
        else if (d == 2) cfg.dither = zpl::DitherMode::Atkinson;
        else cfg.dither = zpl::DitherMode::FloydSteinberg;
    }

    if (fThresh) cfg.threshold = static_cast<uint8_t>(env->GetIntField(cfgObj, fThresh));
    if (fInvert) cfg.invert = env->GetBooleanField(cfgObj, fInvert);

    if (fTrack) {
        int t = env->GetIntField(cfgObj, fTrack);
        if (t == 1) cfg.tracking = zpl::MediaTracking::WebGap;
        else if (t == 2) cfg.tracking = zpl::MediaTracking::BlackMark;
        else cfg.tracking = zpl::MediaTracking::Continuous;
    }

    if (fMedia) {
        int m = env->GetIntField(cfgObj, fMedia);
        if (m == 1) cfg.media_type = zpl::MediaType::ThermalTransfer;
        else cfg.media_type = zpl::MediaType::DirectThermal;
    }

    return cfg;
}

jbyteArray string_to_jbyteArray(JNIEnv* env, const std::string& str) {
    jbyteArray arr = env->NewByteArray(static_cast<jsize>(str.size()));
    if (!arr) return nullptr;
    env->SetByteArrayRegion(arr, 0, static_cast<jsize>(str.size()), reinterpret_cast<const jbyte*>(str.data()));
    return arr;
}

} // anonymous namespace

extern "C" {

JNIEXPORT jbyteArray JNICALL
Java_com_printer_zpl_ZplNative_encodeBitmapNative(
    JNIEnv* env,
    jclass /*clazz*/,
    jobject bitmap,
    jobject configObj
) {
    if (!bitmap) {
        LOGE("encodeBitmapNative: Null bitmap provided");
        return nullptr;
    }

    AndroidBitmapInfo info;
    if (AndroidBitmap_getInfo(env, bitmap, &info) < 0) {
        LOGE("encodeBitmapNative: Failed to get AndroidBitmapInfo");
        return nullptr;
    }

    void* pixel_buffer = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixel_buffer) < 0 || !pixel_buffer) {
        LOGE("encodeBitmapNative: Failed to lock bitmap pixels");
        return nullptr;
    }

    int width = static_cast<int>(info.width);
    int height = static_cast<int>(info.height);
    std::vector<uint8_t> gray_pixels(static_cast<size_t>(width) * static_cast<size_t>(height));

    if (info.format == ANDROID_BITMAP_FORMAT_RGBA_8888) {
        const uint32_t* src = static_cast<const uint32_t*>(pixel_buffer);
        for (int y = 0; y < height; ++y) {
            size_t row_idx = static_cast<size_t>(y) * static_cast<size_t>(width);
            for (int x = 0; x < width; ++x) {
                uint32_t pixel = src[row_idx + static_cast<size_t>(x)];
                // Extract R, G, B (little endian: R in lowest byte)
                uint8_t r = static_cast<uint8_t>(pixel & 0xFF);
                uint8_t g = static_cast<uint8_t>((pixel >> 8) & 0xFF);
                uint8_t b = static_cast<uint8_t>((pixel >> 16) & 0xFF);
                uint8_t a = static_cast<uint8_t>((pixel >> 24) & 0xFF);

                if (a < 128) {
                    gray_pixels[row_idx + static_cast<size_t>(x)] = 255; // White background for transparency
                } else {
                    gray_pixels[row_idx + static_cast<size_t>(x)] = static_cast<uint8_t>(
                        (299 * r + 587 * g + 114 * b) / 1000
                    );
                }
            }
        }
    } else if (info.format == ANDROID_BITMAP_FORMAT_RGB_565) {
        const uint16_t* src = static_cast<const uint16_t*>(pixel_buffer);
        for (int y = 0; y < height; ++y) {
            size_t row_idx = static_cast<size_t>(y) * static_cast<size_t>(width);
            for (int x = 0; x < width; ++x) {
                uint16_t pixel = src[row_idx + static_cast<size_t>(x)];
                uint8_t r = static_cast<uint8_t>((pixel >> 11) << 3);
                uint8_t g = static_cast<uint8_t>(((pixel >> 5) & 0x3F) << 2);
                uint8_t b = static_cast<uint8_t>((pixel & 0x1F) << 3);
                gray_pixels[row_idx + static_cast<size_t>(x)] = static_cast<uint8_t>(
                    (299 * r + 587 * g + 114 * b) / 1000
                );
            }
        }
    } else {
        AndroidBitmap_unlockPixels(env, bitmap);
        LOGE("encodeBitmapNative: Unsupported bitmap pixel format: %d", info.format);
        return nullptr;
    }

    AndroidBitmap_unlockPixels(env, bitmap);

    zpl::ZplConfig cfg = parse_config(env, configObj);
    if (cfg.width_dots <= 0 || cfg.length_dots <= 0) {
        cfg.width_dots = width;
        cfg.length_dots = height;
    }
    zpl::MonochromeBitmap mono = zpl::DitherEngine::process(gray_pixels.data(), width, height, cfg);
    std::string zpl = zpl::ZplEncoder::encode_image(mono, cfg, /*compress=*/true);

    return string_to_jbyteArray(env, zpl);
}

JNIEXPORT jbyteArray JNICALL
Java_com_printer_zpl_ZplNative_generateRulerNative(
    JNIEnv* env,
    jclass /*clazz*/,
    jobject configObj
) {
    zpl::ZplConfig cfg = parse_config(env, configObj);
    std::string zpl = zpl::Diagnostics::generate_ruler(cfg);
    return string_to_jbyteArray(env, zpl);
}

JNIEXPORT jbyteArray JNICALL
Java_com_printer_zpl_ZplNative_generateBandsNative(
    JNIEnv* env,
    jclass /*clazz*/,
    jobject configObj
) {
    zpl::ZplConfig cfg = parse_config(env, configObj);
    std::string zpl = zpl::Diagnostics::generate_bands(cfg);
    return string_to_jbyteArray(env, zpl);
}

JNIEXPORT jbyteArray JNICALL
Java_com_printer_zpl_ZplNative_generateTestLabelNative(
    JNIEnv* env,
    jclass /*clazz*/,
    jobject configObj
) {
    zpl::ZplConfig cfg = parse_config(env, configObj);
    std::string zpl = zpl::Diagnostics::generate_test_label(cfg);
    return string_to_jbyteArray(env, zpl);
}

JNIEXPORT jbyteArray JNICALL
Java_com_printer_zpl_ZplNative_queryIdentityNative(
    JNIEnv* env,
    jclass /*clazz*/
) {
    std::string cmd = zpl::Diagnostics::query_identity();
    return string_to_jbyteArray(env, cmd);
}

JNIEXPORT jbyteArray JNICALL
Java_com_printer_zpl_ZplNative_calibrateSensorNative(
    JNIEnv* env,
    jclass /*clazz*/
) {
    std::string cmd = zpl::Diagnostics::calibrate_sensor();
    return string_to_jbyteArray(env, cmd);
}

} // extern "C"

