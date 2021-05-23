LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_CFLAGS += -I ~/mavlink/generated/include
#example Windows path
#LOCAL_CFLAGS += -I C:\Users\username\Documents\libraries\c_library_v2-master
#example Linux path 
#LOCAL_CFLAGS += -I /home/username/Documents/libraries/c_library_v2-master
LOCAL_MODULE    := mavlink_udp
LOCAL_SRC_FILES := mavlink_udp.c
include $(BUILD_SHARED_LIBRARY)