cmake_minimum_required(VERSION 3.15)
project("zplnative")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(ZPL_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../../../../..")

include_directories(
    "${ZPL_ROOT}/include"
)

add_library(
    zplnative
    SHARED
    zpl_jni_bridge.cpp
    "${ZPL_ROOT}/src/dither.cpp"
    "${ZPL_ROOT}/src/encoder.cpp"
    "${ZPL_ROOT}/src/diagnostics.cpp"
)

find_library(log-lib log)
find_library(jnigraphics-lib jnigraphics)

target_link_libraries(
    zplnative
    ${log-lib}
    ${jnigraphics-lib}
)

