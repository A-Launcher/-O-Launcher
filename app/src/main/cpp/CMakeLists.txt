cmake_minimum_required(VERSION 3.22.1)

project(oengine_bootstrap LANGUAGES CXX)

add_library(oengine_bootstrap SHARED
    oengine_jvm_probe.cpp
)

target_compile_features(oengine_bootstrap PRIVATE cxx_std_17)
target_compile_options(oengine_bootstrap PRIVATE -Wall -Wextra)
target_link_libraries(oengine_bootstrap PRIVATE dl log)
