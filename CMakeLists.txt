cmake_minimum_required(VERSION 3.15)
project(ZplEngine VERSION 1.0.0 LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_EXPORT_COMPILE_COMMANDS ON)

# Source files for the Core & Facade library
set(ZPL_CORE_SOURCES
    src/dither.cpp
    src/encoder.cpp
    src/diagnostics.cpp
    src/printer.cpp
    src/transport/file_transport.cpp
    src/transport/mock_transport.cpp
)

# Platform-specific transport sources
if(UNIX AND NOT APPLE AND NOT ANDROID)
    list(APPEND ZPL_CORE_SOURCES
        src/transport/linux_usb_transport.cpp
        src/transport/linux_bt_transport.cpp
    )
elseif(WIN32)
    list(APPEND ZPL_CORE_SOURCES
        src/transport/win_spool_transport.cpp
        src/transport/win_bt_transport.cpp
    )
endif()

# Core static library
add_library(zplcore STATIC ${ZPL_CORE_SOURCES})

target_include_directories(zplcore PUBLIC
    $<BUILD_INTERFACE:${CMAKE_CURRENT_SOURCE_DIR}/include>
    $<INSTALL_INTERFACE:include>
)

if(WIN32)
    target_link_libraries(zplcore PRIVATE winspool ws2_32)
endif()

# CLI Tool
add_executable(zpl_tool cli/main.cpp)
target_link_libraries(zpl_tool PRIVATE zplcore)

# Unit Test Runner via GoogleTest
enable_testing()

set(INSTALL_GTEST OFF CACHE BOOL "" FORCE)
set(gtest_force_shared_crt ON CACHE BOOL "" FORCE)

if(EXISTS "${CMAKE_CURRENT_SOURCE_DIR}/third_party/googletest/CMakeLists.txt")
    add_subdirectory(third_party/googletest EXCLUDE_FROM_ALL)
else()
    include(FetchContent)
    FetchContent_Declare(
        googletest
        GIT_REPOSITORY https://github.com/google/googletest.git
        GIT_TAG v1.14.0
    )
    FetchContent_MakeAvailable(googletest)
endif()

set(ZPL_TEST_SOURCES
    tests/test_config.cpp
    tests/test_dither.cpp
    tests/test_encoder.cpp
    tests/test_diagnostics.cpp
    tests/test_printer.cpp
)

add_executable(zpl_tests ${ZPL_TEST_SOURCES})
target_link_libraries(zpl_tests PRIVATE zplcore gtest_main)
add_test(NAME AllGTests COMMAND zpl_tests)

