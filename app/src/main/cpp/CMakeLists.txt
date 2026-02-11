cmake_minimum_required(VERSION 3.18.1)
project("native-lib")

# ONLY your JNI library - NO nmap dependency
add_library(native-lib SHARED native-lib.cpp)

find_library(log-lib log)
# ✅ REMOVED: nmap linking
target_link_libraries(native-lib ${log-lib})