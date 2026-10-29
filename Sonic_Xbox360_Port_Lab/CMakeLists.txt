cmake_minimum_required(VERSION 3.25)
project(sonic_x360 C)

add_executable(sonic_x360
    src/main.c
    src/rom_probe.c
)
set_target_properties(sonic_x360 PROPERTIES C_STANDARD 17 C_EXTENSIONS ON)
target_compile_options(sonic_x360 PRIVATE -Wall -Wextra -Werror)
target_include_directories(sonic_x360 PRIVATE src)

xsdk_add_xex(sonic_x360 TITLE_ID 0x534F4E58 NAME "Sonic X360 Port Lab" VERSION 0.1.0.0)
