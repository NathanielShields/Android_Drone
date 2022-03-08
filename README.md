# PhoneUAV-server
Allows user to control RC plane through Wi-Fi. You need [USC-16 servo controller](https://www.google.com/search?q=USC-16+servo+controller), Android Lollipop enabled smartphone, laptop or another smartphone running [QGroundControl](http://qgroundcontrol.com/), and analog gamepad. Latest build of the app can be downloaded here: [PhoneUAV-debug.apk](build/outputs/apk/debug/PhoneUAV-debug.apk). For MAVLink support see also: [MAVLink UDP Android Example](https://github.com/mareksuma1985/mavlink). Please keep in mind that the recommended way of implementing MAVLink support in Android applications is [MAVSDK](https://mavsdk.mavlink.io/main/en/index.html). Feel free to use pieces of code from this project for:

* Reading and saving preferences ([MainActivity.java](PhoneUAV/src/main/java/pl.bezzalogowe/PhoneUAV/MainActivity.java), [SettingsActivity.java](PhoneUAV/src/main/java/pl.bezzalogowe/PhoneUAV/SettingsActivity.java))

* Displaying network and Wi-Fi information ([NetworkInformation.java](PhoneUAV/src/main/java/pl.bezzalogowe/PhoneUAV/NetworkInformation.java))

* Handling sensor input ([Accelerometer.java](PhoneUAV/src/main/java/pl.bezzalogowe/PhoneUAV/Accelerometer.java),
[Gravity.java](PhoneUAV/src/main/java/pl.bezzalogowe/PhoneUAV/Gravity.java), [Barometer.java](PhoneUAV/src/main/java/pl.bezzalogowe/PhoneUAV/Barometer.java), [Magnetometer.java](PhoneUAV/src/main/java/pl.bezzalogowe/PhoneUAV/Magnetometer.java))

* Taking pictures and recording videos ([CameraAPI.java](PhoneUAV/src/main/java/pl.bezzalogowe/PhoneUAV/CameraAPI.java), [Camera2API.java](PhoneUAV/src/main/java/pl.bezzalogowe/PhoneUAV/Camera2API.java))

* Sending e-mails ([GMail.java](PhoneUAV/src/main/java/pl.bezzalogowe/PhoneUAV/GMail.java), [SendMailTask.java](PhoneUAV/src/main/java/pl.bezzalogowe/PhoneUAV/SendMailTask.java))

* Getting GPS location and saving travelled path to a GPX file ([Location.java](PhoneUAV/src/main/java/pl.bezzalogowe/PhoneUAV/Location.java), [LogGPX.java](PhoneUAV/src/main/java/pl.bezzalogowe/PhoneUAV/LogGPX.java))

* Moving servos with USB servo controller ([CH340comm.java](PhoneUAV/src/main/java/pl.bezzalogowe/PhoneUAV/CH340comm.java))

* Moving servos with RS232 servo controller using FTDI Android host module ([FT311UARTInterface.java](PhoneUAV/src/main/java/pl.bezzalogowe/PhoneUAV/FT311UARTInterface.java), [SK18comm.java](PhoneUAV/src/main/java/pl.bezzalogowe/PhoneUAV/SK18comm.java))

* Receiving joystick or gamepad input through UDP socket ([Input.java](PhoneUAV/src/main/java/pl.bezzalogowe/PhoneUAV/Input.java))

* Receiving input and waypoints from QGroundControl using MAVLink ([mavlink_udp.c](PhoneUAV/src/main/cpp/mavlink_udp.c), [MAVLinkClass.java](PhoneUAV/src/main/java/pl.bezzalogowe/mavlink/MAVLinkClass.java))


## Instructions
After cloning or downloading the project you have to do a few things before you build:

 - Open `local.properties` and edit `sdk.dir` and `ndk.dir` properties (paths to your Android SDK and [NDK](https://developer.android.com/ndk/downloads)):

```
  ndk.dir=~/Library/Android/android-sdk-linux/ndk-bundle
  sdk.dir=~/Library/Android/android-sdk-linux
```

 - Download: [c_library_v2](https://github.com/mavlink/c_library_v2) or generate: [generate_libraries](https://mavlink.io/en/getting_started/generate_libraries.html) MAVLink headers.

 - Open `/PhoneUAV/src/main/cpp/Android.mk` and edit `LOCAL_CFLAGS` variable so that it points to the folder where you keep the headers.

```
LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_CFLAGS += -I ~/mavlink/generated/include
LOCAL_MODULE    := mavlink_udp
LOCAL_SRC_FILES := mavlink_udp.c
include $(BUILD_SHARED_LIBRARY)
```

 - If you're having trouble building the project (`The system cannot find the file specified`) - try downloading [older NDK version](https://developer.android.com/ndk/downloads/older_releases#ndk-16b-downloads).


## Videos

Accesory mode hardware laid out:

[![Android based autopilot project Ranger](https://i.ytimg.com/vi/sfPRkhOOxt8/hqdefault.jpg)](https://www.youtube.com/watch?v=sfPRkhOOxt8)

Moving control surfaces using gamepad:

[![Android based UAV project - Volantex Ranger](https://i.ytimg.com/vi/5pmLjqFNvdw/hqdefault.jpg)](https://www.youtube.com/watch?v=5pmLjqFNvdw)

Walkaround:

[![Volantex Ranger 757-4 1380mm wingspan](https://i.ytimg.com/vi/EdAMVYBIqLY/hqdefault.jpg)](https://www.youtube.com/watch?v=EdAMVYBIqLY)

First flight:

[![Volantex Ranger 757-4 pierwszy start](https://i.ytimg.com/vi/YN62Xx8k-T4/hqdefault.jpg)](https://www.youtube.com/watch?v=YN62Xx8k-T4)
