cmake_minimum_required(VERSION 3.22.1)
project(hypershell_pty LANGUAGES C CXX)

add_library(hypershell_pty SHARED pty_bridge.cpp)
target_link_libraries(hypershell_pty PRIVATE log)

