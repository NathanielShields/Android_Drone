/*******************************************************************************
 Copyright (C) 2010  Bryan Godbolt godbolt ( a t ) ualberta.ca

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>.

 ****************************************************************************/
/*
 This program sends some data to qgroundcontrol using the mavlink protocol. The sent packets
 cause qgroundcontrol to respond with heartbeats. Any settings or custom commands sent from
 qgroundcontrol are printed by this program along with the heartbeats.


 The rt library is needed for the clock_gettime on linux.
 */

/* These headers are for QNX, but should all be standard on unix/linux */
#include <stdio.h>
#include <errno.h>
#include <string.h>
#include <sys/socket.h>
#include <sys/types.h>
#include <netdb.h>
#include <netinet/in.h>
#include <unistd.h>
#include <stdlib.h>
#include <fcntl.h>
#include <time.h>

#if (defined __QNX__) | (defined __QNXNTO__)
/* QNX specific headers */
#include <unix.h>
#else
/* Linux / MacOS POSIX timer headers */
#include <sys/time.h>
#include <time.h>
#include <arpa/inet.h>
#include <stdbool.h> /* required for the definition of bool in C99 */

#endif

/* Path to generated MAVLink headers must be given in Android.mk file. */
#include <common/mavlink.h>

#define BUFFER_LENGTH 2041 // minimum buffer size that can be used with qnx (I don't know why)

// global, used also outside heartbeat thread
int sock;
struct sockaddr_in gcAddr;
struct sockaddr_in locAddr;
uint8_t buf[BUFFER_LENGTH];
int bytes_sent;

void sendACK(mavlink_command_long_t command_long);

void sendProtocolVersion(int number);

void sendAutopilotVersion();

void send_MISSION_REQUEST_INT(int index, int type);
void send_MISSION_ACK(int target_system, int target_component, int mission_type);
uint16_t missionItemCount;
uint64_t microsSinceEpoch();

#define THIS_SYSTEM 1
#define THIS_COMPONENT 1

/* Android specific */
#include <pthread.h>
#include <jni.h>

bool running = false;
bool heartbeat_running = false;
static pthread_t heartbeat_thread, rcv_thread, mission_request_timeout_thread;
static pthread_key_t current_jni_env;
static JavaVM *java_vm;
static jmethodID set_message_method_id, set_buttons_method_id, set_address_method_id, set_log_method_id, set_progress_method_id, set_servos_method_id, process_button_method_id, add_waypoint_method_id, take_photo_method_id, set_sound_method_id, restart_method_id;

char groundStationHost[128];
float hdg = 0;
int battery_voltage, battery_level;

struct timeval tv;
long unsigned int last_beat_time, current_time;
bool communication = false;

int16_t buttons_before = 0x00;

/** https://stackoverflow.com/questions/111928/is-there-a-printf-converter-to-print-in-binary-format */
#define BYTE_TO_BINARY_PATTERN "%c%c%c%c%c%c%c%c"
#define BYTE_TO_BINARY(byte)  \
  (byte & 0x80 ? '1' : '0'), \
  (byte & 0x40 ? '1' : '0'), \
  (byte & 0x20 ? '1' : '0'), \
  (byte & 0x10 ? '1' : '0'), \
  (byte & 0x08 ? '1' : '0'), \
  (byte & 0x04 ? '1' : '0'), \
  (byte & 0x02 ? '1' : '0'), \
  (byte & 0x01 ? '1' : '0')

/** https://gstreamer.freedesktop.org/documentation/tutorials/android/a-running-pipeline.html */
static JNIEnv *attach_current_thread(void) {
    JNIEnv *env;
    JavaVMAttachArgs args;
    printf("Attaching thread %p", pthread_self());
    args.version = JNI_VERSION_1_4;
    args.name = NULL;
    args.group = NULL;

    if ((*java_vm)->AttachCurrentThread(java_vm, &env, &args) < 0) {
        printf("Failed to attach current thread");
        return NULL;
    }
    return env;
}

static void detach_current_thread(void *env) {
    printf("Detaching thread %p", pthread_self());
    (*java_vm)->DetachCurrentThread(java_vm);
}

static JNIEnv *get_jni_env(void) {
    JNIEnv *env;

    if ((env = pthread_getspecific(current_jni_env)) == NULL) {
        env = attach_current_thread();
        pthread_setspecific(current_jni_env, env);
    }
    return env;
}

/** prints a message in Logcat */
static void set_log_message(jobject obj, const char *message) {
    JNIEnv *env = get_jni_env();

    //printf("Setting message to: %s", message);
    jstring jmessage = (*env)->NewStringUTF(env, message);
    (*env)->CallVoidMethod(env, obj, set_log_method_id, jmessage);
    if ((*env)->ExceptionCheck(env)) {
        printf("Failed to call Java method");
        (*env)->ExceptionClear(env);
    }
    (*env)->DeleteLocalRef(env, jmessage);
}

/** displays a message in the app's GUI */
static void set_ui_message(jobject obj, const char *message, bool blink) {
    JNIEnv *env = get_jni_env();

    printf("Setting message to: %s", message);
    jstring jmessage = (*env)->NewStringUTF(env, message);
    (*env)->CallVoidMethod(env, obj, set_message_method_id, jmessage, blink);
    if ((*env)->ExceptionCheck(env)) {
        printf("Failed to call Java method");
        (*env)->ExceptionClear(env);
    }
    (*env)->DeleteLocalRef(env, jmessage);
}

/** displays a message in the app's GUI, but in a different TextView */
static void set_ui_message_buttons(jobject obj, const char *message, bool blink) {
    JNIEnv *env = get_jni_env();

    printf("Setting message to: %s", message);
    jstring jmessage = (*env)->NewStringUTF(env, message);
    (*env)->CallVoidMethod(env, obj, set_buttons_method_id, jmessage, blink);
    if ((*env)->ExceptionCheck(env)) {
        printf("Failed to call Java method");
        (*env)->ExceptionClear(env);
    }
    (*env)->DeleteLocalRef(env, jmessage);
}

/** displays a message in the app's GUI, in ground control station address TextView */
static void set_ui_message_address(jobject obj, const char *message, bool blink) {
    JNIEnv *env = get_jni_env();

    printf("Setting message to: %s", message);
    jstring jmessage = (*env)->NewStringUTF(env, message);
    (*env)->CallVoidMethod(env, obj, set_address_method_id, jmessage, blink);
    if ((*env)->ExceptionCheck(env)) {
        printf("Failed to call Java method");
        (*env)->ExceptionClear(env);
    }
    (*env)->DeleteLocalRef(env, jmessage);
}

/** moves seekbars */
static void set_progress(jobject obj, int16_t x, int16_t y, int16_t z, int16_t r) {
    JNIEnv *env = get_jni_env();

    (*env)->CallVoidMethod(env, obj, set_progress_method_id, x, y, z, r);
    if ((*env)->ExceptionCheck(env)) {
        printf("Failed to call Java method");
        (*env)->ExceptionClear(env);
    }
}

static void set_servos(jobject obj, int16_t x, int16_t y, int16_t z, int16_t r) {
    /** sets positions of servos for each axis joystick/gamepad axes */
    JNIEnv *env = get_jni_env();

    (*env)->CallVoidMethod(env, obj, set_servos_method_id, x, y, z, r);
    if ((*env)->ExceptionCheck(env)) {
        printf("Failed to call Java method");
        (*env)->ExceptionClear(env);
    }
}

static void process_buttons(jobject obj, int16_t buttons) {
    JNIEnv *env = get_jni_env();
    char buttons_char[64];
    /** displays bit field representing pressed buttons */
    sprintf(buttons_char, BYTE_TO_BINARY_PATTERN BYTE_TO_BINARY_PATTERN,
            BYTE_TO_BINARY(buttons >> 8),
            BYTE_TO_BINARY(buttons));


    if (buttons != buttons_before) {
        for (int n = 0; n < sizeof(uint16_t) * 8; n++) {
            /* check if button status has changed */
            if ((buttons & (1 << n)) != (buttons_before & (1 << n))) {
                /* check if button is pressed */
                /** https://en.wikipedia.org/wiki/Bit_field#Extracting_bits_from_flag_words */
                bool nth_is_set = (buttons & (1 << n)) != 0;
                (*env)->CallVoidMethod(env, obj, process_button_method_id, n, nth_is_set);
                if ((*env)->ExceptionCheck(env)) {
                    printf("Failed to call Java method");
                    (*env)->ExceptionClear(env);
                }
            }
        }
    }
    /* save button bit field for next comparison */
    buttons_before = buttons;
}

static void add_waypoint(jobject obj, double lat, double lon, double ele, int frame, int index) {
    /** adds waypoint received via MAVLink */
    JNIEnv *env = get_jni_env();

    (*env)->CallVoidMethod(env, obj, add_waypoint_method_id, lat, lon, ele, frame, index);
    if ((*env)->ExceptionCheck(env)) {
        printf("Failed to call Java method");
        (*env)->ExceptionClear(env);
    }
}

static void take_photo(jobject obj, bool value) {
    /** takes a photo */
    JNIEnv *env = get_jni_env();

    (*env)->CallVoidMethod(env, obj, take_photo_method_id, value);
    if ((*env)->ExceptionCheck(env)) {
        printf("Failed to call Java method");
        (*env)->ExceptionClear(env);
    }
}

static void set_sound(jobject obj, short soundID) {
    JNIEnv *env = get_jni_env();
    printf("Playing sound: %d", soundID);
    (*env)->CallVoidMethod(env, obj, set_sound_method_id, soundID);
    if ((*env)->ExceptionCheck(env)) {
        printf("Failed to call Java method");
        (*env)->ExceptionClear(env);
    }
}

static void restart(jobject obj) {
    /** restarts the app */
    JNIEnv *env = get_jni_env();

    (*env)->CallVoidMethod(env, obj, restart_method_id);
    if ((*env)->ExceptionCheck(env)) {
        printf("Failed to call Java method");
        (*env)->ExceptionClear(env);
    }
}

static void sendBooleanParameter(char *name, bool value) {
/** https://mavlink.io/en/services/parameter.html
  * https://ardupilot.org/dev/docs/mavlink-get-set-params.html
  */
    mavlink_param_union_t param;
    int32_t asInt = value ? 1 : 0;
    param.param_int32 = asInt;
    param.type = MAV_PARAM_TYPE_INT32;

    mavlink_message_t msgParameter;
    mavlink_msg_param_value_pack(THIS_SYSTEM, THIS_COMPONENT, &msgParameter, name,
                                 param.param_float, param.type, 1, 0);
    uint16_t len = mavlink_msg_to_send_buffer(buf, &msgParameter);

    bytes_sent = sendto(sock, buf, len, 0, (struct sockaddr *) &gcAddr, sizeof(struct sockaddr_in));
}

jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    JNIEnv *env = NULL;
    java_vm = vm;

    if ((*vm)->GetEnv(vm, (void **) &env, JNI_VERSION_1_4) != JNI_OK) {
        printf("Could not retrieve JNIEnv");
        return 0;
    }

    pthread_key_create(&current_jni_env, detach_current_thread);
    return JNI_VERSION_1_4;
}

/** Package name must be lowercase, otherwise you won't see method usages */
jstring Java_pl_bezzalogowe_mavlink_MAVLinkClass_stringFromJNI(JNIEnv *env, jobject thiz) {
    /** send string from C code to Java code */
    return (*env)->NewStringUTF(env, "JNI is working");
}

/** data types mapping: https://docs.oracle.com/javase/7/docs/technotes/guides/jni/spec/types.html*/

void Java_pl_bezzalogowe_mavlink_MAVLinkClass_classInit(JNIEnv *env, jclass klass) {
    /** binds Java methods that can be called from C code */
    set_message_method_id = (*env)->GetMethodID(env, klass, "setMessage", "(Ljava/lang/String;Z)V");
    set_buttons_method_id = (*env)->GetMethodID(env, klass, "setButtons", "(Ljava/lang/String;Z)V");
    set_address_method_id = (*env)->GetMethodID(env, klass, "setAddress", "(Ljava/lang/String;Z)V");
    set_log_method_id = (*env)->GetMethodID(env, klass, "setLog", "(Ljava/lang/String;)V");
    //set_progress_method_id = (*env)->GetMethodID(env, klass, "setProgress", "(SSSS)V");
    set_servos_method_id = (*env)->GetMethodID(env, klass, "setRCchannels", "(SSSS)V");
    process_button_method_id = (*env)->GetMethodID(env, klass, "processButton", "(SZ)V");
    add_waypoint_method_id = (*env)->GetMethodID(env, klass, "addMAVLinkWaypoint", "(DDDII)V");
    take_photo_method_id = (*env)->GetMethodID(env, klass, "takePhoto", "(Z)V");
    set_sound_method_id = (*env)->GetMethodID(env, klass, "setSound", "(I)V");
    restart_method_id = (*env)->GetMethodID(env, klass, "restartApp", "()V");
}

void
Java_pl_bezzalogowe_mavlink_MAVLinkClass_setGroundStationIP(JNIEnv *env, jclass klass, jstring ip) {
    /** receives ip address from java code and saves it into global variable.*/
    /* https://stackoverflow.com/questions/4247002/passing-string-from-java-into-jni */
    const char *ipArray = (*env)->GetStringUTFChars(env, ip, 0);
    strcpy(groundStationHost, ipArray);
    (*env)->ReleaseStringUTFChars(env, ip, ipArray);
}

void
Java_pl_bezzalogowe_mavlink_MAVLinkClass_setHeadingDegrees(JNIEnv *env, jclass klass,
                                                           double heading) {
    if (running) {
        /* https://stackoverflow.com/questions/14920675/is-there-a-function-in-c-language-to-calculate-degrees-radians */
        hdg = heading * M_PI / 180.0;
    }
}

void Java_pl_bezzalogowe_mavlink_MAVLinkClass_sendAttitude(JNIEnv *env, jclass klass, float roll,
                                                           float pitch /*, float heading*/) {
    if (running) {
        mavlink_message_t msgAttitude;

        /* in radians, heading comes from a different sensor */
        mavlink_msg_attitude_pack(THIS_SYSTEM, THIS_COMPONENT, &msgAttitude, microsSinceEpoch(),
                                  roll, pitch, hdg, 0.00, 0.00, 0.00);

        /* in degrees */
        //mavlink_msg_attitude_pack(THIS_SYSTEM, THIS_COMPONENT, &msgAttitude, microsSinceEpoch(), roll * M_PI / 180.0, pitch * M_PI / 180.0, hdg * M_PI / 180.0, 0.00, 0.00, 0.00);

        uint16_t len = mavlink_msg_to_send_buffer(buf, &msgAttitude);
        bytes_sent = sendto(sock, buf, len, 0, (struct sockaddr *) &gcAddr,
                            sizeof(struct sockaddr_in));
    }
}

void Java_pl_bezzalogowe_mavlink_MAVLinkClass_setBattery(JNIEnv *env, jclass klass, jint voltage,
                                                         jint level) {
    battery_voltage = voltage;
    battery_level = level;
}

void
Java_pl_bezzalogowe_mavlink_MAVLinkClass_sendGlobalPosition(JNIEnv *env, jclass klass,
                                                            double latitude, double longitude,
                                                            double altitude,
                                                            double relativeAltitude) {
    if (running) {
        mavlink_message_t msgLocation;

        int32_t lat = latitude * 1E7;
        int32_t lon = longitude * 1E7;
        int32_t alt = altitude * 1000;
        int32_t relative_alt = alt;

        mavlink_msg_global_position_int_pack(THIS_SYSTEM, THIS_COMPONENT, &msgLocation,
                                             microsSinceEpoch(), lat, lon, alt, relative_alt, 0, 0,
                                             0, /*hdg*/ 0);
        uint16_t len = mavlink_msg_to_send_buffer(buf, &msgLocation);
        bytes_sent = sendto(sock, buf, len, 0, (struct sockaddr *) &gcAddr,
                            sizeof(struct sockaddr_in));
    }
}

/** https://mavlink.io/en/services/mission.html#download_mission */
/** https://github.com/mavlink/c_library_v1/blob/master/common/mavlink_msg_mission_count.h */
void sendMissionCount(uint8_t system_id, uint8_t component_id,
                      mavlink_mission_request_list_t request_list) {
    mavlink_message_t msgCount;
    mavlink_msg_mission_count_pack(THIS_SYSTEM, THIS_COMPONENT, &msgCount, system_id, component_id,
                                   0, request_list.mission_type);

    uint16_t len = mavlink_msg_to_send_buffer(buf, &msgCount);
    bytes_sent = sendto(sock, buf, len, 0, (struct sockaddr *) &gcAddr, sizeof(struct sockaddr_in));
}

void processCommandINT(jobject thiz, uint8_t system_id, uint8_t component_id,
                       mavlink_command_int_t command_int) {
    char feedback_ui[64];
    sprintf(feedback_ui, "COMMAND_INT: %d", command_int.command);
    set_ui_message(thiz, feedback_ui, true);
    set_log_message(thiz, feedback_ui);
}

void processDigicamControl(jobject thiz, mavlink_command_long_t command_long) {
    char feedback_log[192];
    sprintf(feedback_log,
            "MAV_CMD_DO_DIGICAM_CONTROL: %d, %d, %d, %d, #1: %f, #2: %f, #3: %f, #4: %f, #5: %f, #6: %f, #7: %f",
            command_long.command,
            command_long.target_system,
            command_long.target_component,
            command_long.confirmation,
            command_long.param1,
            command_long.param2,
            command_long.param3,
            command_long.param4,
            command_long.param5,
            command_long.param6,
            command_long.param7);
    set_log_message(thiz, feedback_log);

    take_photo(thiz, true);
}

void processCommandLONG(jobject thiz, uint8_t system_id, uint8_t component_id,
                        mavlink_command_long_t command_long) {
    switch (command_long.command) {
        case 203: {
            /** https://mavlink.io/en/messages/common.html#MAV_CMD_DO_DIGICAM_CONTROL */
            sendACK(command_long);

            char feedback_ui[64];
            sprintf(feedback_ui, "COMMAND_LONG: %d", command_long.command);
            set_ui_message(thiz, feedback_ui, true);

            processDigicamControl(thiz, command_long);
        }
            break;
        case 246: {
            /** https://mavlink.io/en/messages/common.html#MAV_CMD_PREFLIGHT_REBOOT_SHUTDOWN */
            sendACK(command_long);
            restart(thiz);
        }
            break;
        case 519: {
            /** https://mavlink.io/en/messages/common.html#MAV_CMD_REQUEST_PROTOCOL_VERSION */
            sendACK(command_long);

            sendProtocolVersion(200);
        }
            break;
        case 520: {
            /** https://mavlink.io/en/messages/common.html#MAV_CMD_REQUEST_AUTOPILOT_CAPABILITIES */
            sendACK(command_long);

            sendAutopilotVersion();
        }
            break;
        default: {
            char feedback_ui[64];
            sprintf(feedback_ui, "COMMAND_LONG: %d", command_long.command);
            set_ui_message(thiz, feedback_ui, true);

            char feedback_log[128];
            sprintf(feedback_log, "COMMAND_LONG: %d, %d, %d, %d",
                    command_long.command,
                    command_long.target_system,
                    command_long.target_component,
                    command_long.confirmation);
            set_log_message(thiz, feedback_log);
        }
            break;
    }
}

int *openSocket(jobject thiz) {
    sock = socket(PF_INET, SOCK_DGRAM, IPPROTO_UDP);

    /** listening address */
    memset(&locAddr, 0, sizeof(locAddr));
    locAddr.sin_family = AF_INET;
    locAddr.sin_port = htons(14550);
    locAddr.sin_addr.s_addr = INADDR_ANY;

    /* Bind the socket to default port 14550 - necessary to receive packets from ground control station */
    if (-1 == bind(sock, (struct sockaddr *) &locAddr, sizeof(struct sockaddr))) {
        perror("error bind failed");
        close(sock);
        //exit(EXIT_FAILURE);
        return -1;
    }

/* Attempt to make it non blocking */
#if (defined __QNX__) | (defined __QNXNTO__)
    if (fcntl(sock, F_SETFL, O_NONBLOCK | FASYNC) < 0)
#else
    if (fcntl(sock, F_SETFL, O_NONBLOCK | O_ASYNC) < 0)
#endif
    {
        fprintf(stderr, "error setting nonblocking: %s\n", strerror(errno));
        close(sock);
        exit(EXIT_FAILURE);
    }

    /** ground station address */
    memset(&gcAddr, 0, sizeof(gcAddr));
    gcAddr.sin_family = AF_INET;
    gcAddr.sin_port = htons(14550);

    /** dynDNS */
    /** https://www.binarytides.com/hostname-to-ip-address-c-sockets-linux/ */

    char *hostname = groundStationHost;
    /** empty string from java code is NULL NULL NULL NULL*/
    if ((hostname[0] == 0 && hostname[1] == 0 && hostname[2] == 0 && hostname[3] == 0)) {
        /** hostname not passed */
        set_log_message(thiz, "host name not passed");
        gcAddr.sin_addr.s_addr = INADDR_ANY;
        return -1;
    } else {
        struct hostent *ghbn = gethostbyname(hostname);
        if (ghbn == NULL)
        {
            char seqArr[128];
            sprintf(seqArr, "invalid host name: %s", hostname);
            set_log_message(thiz, seqArr);
            return -1;
        }
        else {
        struct in_addr **addr_list;
        addr_list = (struct in_addr **) ghbn->h_addr_list;
        gcAddr.sin_addr = *addr_list[0];
        char seqArr[128];
        sprintf(seqArr, "gethostbyname returned: %s (%s)", inet_ntoa(gcAddr.sin_addr), ghbn->h_name);
        set_log_message(thiz, seqArr);
/*
for (int i = 0; i < sizeof(addr_list)/sizeof(addr_list[0]) ; i++) {
    char seqArr[128];
    sprintf(seqArr, "GCS name: %s, GCS address: %s\n", ghbn->h_name, inet_ntoa(*addr_list[i]));
    set_log_message(thiz, seqArr);
}
 */
        return 0;
    }
    }
}

int *receiveFunction(jobject thiz) {

    uint8_t rcv_buf[BUFFER_LENGTH];
    ssize_t recsize;
    socklen_t fromlen = sizeof(gcAddr);
    int i = 0;
    unsigned int temp = 0;

    missionItemCount = 0;

    while (running) {
        memset(rcv_buf, 0, BUFFER_LENGTH);
        recsize = recvfrom(sock, (void *) rcv_buf, BUFFER_LENGTH, 0, (struct sockaddr *) &gcAddr, &fromlen);

        /*INADDR_LOOPBACK is in different endianness*/
        /** https://linux.die.net/man/3/inet_ntoa */
        if (recsize > 0 && gcAddr.sin_addr.s_addr != 0x100007f) {
            // Something received - process it.
            //printf("Bytes Received from: %s\n", inet_ntoa(gcAddr.sin_addr));
            mavlink_message_t rcv_msg;
            mavlink_status_t status;
            char feedback[128];

            for (i = 0; i < recsize; ++i) {
                /*
                sprintf(feedback, "byte received: %02d", (unsigned char) rcv_buf[i]);
                set_log_message(thiz, feedback);
                */
                if (mavlink_parse_char(MAVLINK_COMM_0, rcv_buf[i], &rcv_msg, &status) == 1) {
                    /* Packet received */
                    printf("\nReceived packet: SYS: %d, COMP: %d, LEN: %d, MSG ID: %d\n", rcv_msg.sysid, rcv_msg.compid, rcv_msg.len, rcv_msg.msgid);

                    switch (rcv_msg.msgid) {
                        case 0: {
                            /** https://mavlink.io/en/messages/common.html#HEARTBEAT */
                            //sprintf(feedback, "MSG ID: HEARTBEAT, SEQ: %d", rcv_msg.seq);
                            //set_log_message(thiz, feedback);

                            /* will blink the GCS address every time a heartbeat message is received */
                            //char feedback_ui[64];
                            //sprintf(feedback_ui, "%s", inet_ntoa(gcAddr.sin_addr));
                            //set_ui_message_address(thiz, feedback_ui, true);

                            //saves event time in milliseconds
                            gettimeofday(&tv, NULL);
                            last_beat_time = (tv.tv_sec % 1000000) * 1000 + tv.tv_usec / 1000;

                            if (communication == false) {
                               /*
                                if (gcAddr.sin_addr.s_addr != 0x100007f)
                                {}
                                */

                                    /* communication regained */
                                    char message[256];
                                    sprintf(message, "communication regained with: %s (%x)\n",
                                            inet_ntoa(gcAddr.sin_addr), gcAddr.sin_addr.s_addr);
                                    set_ui_message(thiz, message, true);
                                    set_log_message(thiz, message);
                                    set_sound(thiz, 1);
                                    communication = true;
                            }
                        }
                            break;
                        case 2: {
                            /** https://mavlink.io/en/messages/common.html#SYSTEM_TIME */
                            set_ui_message(thiz, "SYSTEM_TIME", true);
                        }
                            break;
                        case 20: {
                            /** https://mavlink.io/en/messages/common.html#PARAM_REQUEST_READ */
                            set_ui_message(thiz, "PARAM_REQUEST_READ", true);

                            sprintf(feedback,
                                    "MSG ID: PARAM_REQUEST_READ, SYS: %d, COMP: %d, LEN: %d, SEQ: %d",
                                    rcv_msg.sysid, rcv_msg.compid, rcv_msg.len,
                                    rcv_msg.seq);
                            set_log_message(thiz, feedback);
                        }
                            break;
                        case 21: {
                            /** https://mavlink.io/en/messages/common.html#PARAM_REQUEST_LIST */
                            set_ui_message(thiz, "PARAM_REQUEST_LIST", true);
                            /* This app sends magnetometer indication (not GPS) as heading */
                            sendBooleanParameter("COMPASS_USE", true);
                        }
                            break;
                        case 43: {
                            /** https://mavlink.io/en/messages/common.html#MISSION_REQUEST_LIST */
                            set_ui_message(thiz, "MISSION_REQUEST_LIST", true);
                            mavlink_mission_request_list_t request_list;
                            mavlink_msg_mission_request_list_decode(&rcv_msg, &request_list);
                            /** https://mavlink.io/en/messages/common.html#MAV_MISSION_TYPE */
                            sprintf(feedback,
                                    "MSG ID: MISSION_REQUEST_LIST, SYS: %d, COMP: %d, LEN: %d, SEQ: %d, mission_type: %d",
                                    rcv_msg.sysid, rcv_msg.compid, rcv_msg.len, rcv_msg.seq,
                                    request_list.mission_type);
                            set_log_message(thiz, feedback);

                        }
                            break;
                        case 44: {
                            /** https://mavlink.io/en/messages/common.html#MISSION_COUNT */
                            set_ui_message(thiz, "MISSION_COUNT", true);
                            missionItemCount = mavlink_msg_mission_count_get_count(&rcv_msg);

                            /** https://mavlink.io/en/messages/common.html#MAV_MISSION_TYPE */
                            sprintf(feedback,
                                    "MSG ID: MISSION_COUNT, SYS: %d, COMP: %d, LEN: %d, SEQ: %d, count: %d",
                                    rcv_msg.sysid, rcv_msg.compid, rcv_msg.len, rcv_msg.seq,
                                    missionItemCount);
                            set_log_message(thiz, feedback);

                            /** https://mavlink.io/en/services/mission.html#uploading_mission */
                            uint8_t mission_type = mavlink_msg_mission_count_get_mission_type(&rcv_msg);

                            // requesting initial item
                            if (missionItemCount > 0) {
                            if (mission_type == MAV_MISSION_TYPE_MISSION) {
                                /** https://mavlink.io/en/messages/common.html#MAV_MISSION_TYPE_MISSION */
                                send_MISSION_REQUEST_INT(0, MAV_MISSION_TYPE_MISSION);
                                sprintf(feedback, "Mission type is: mission");
                            }
                            else if (mission_type == MAV_MISSION_TYPE_FENCE) {
                                //TODO: https://mavlink.io/en/messages/common.html#MAV_MISSION_TYPE_FENCE
                                send_MISSION_ACK(rcv_msg.sysid, rcv_msg.compid, mission_type);
                                //send_MISSION_REQUEST_INT(0, MAV_MISSION_TYPE_FENCE);
                                sprintf(feedback, "Mission type is: fence");
                            }
                            else if (mission_type == MAV_MISSION_TYPE_RALLY) {
                                //TODO: https://mavlink.io/en/messages/common.html#MAV_MISSION_TYPE_RALLY
                                send_MISSION_REQUEST_INT(0, MAV_MISSION_TYPE_RALLY);
                                sprintf(feedback, "Mission type is: rally");
                            }
                            else {
                                send_MISSION_REQUEST_INT(0, mission_type);
                                sprintf(feedback, "Mission type is: %d", mission_type);
                            }
                                set_log_message(thiz, feedback);
                        }
                        }
                            break;
                        case 47: {
                            /** https://mavlink.io/en/messages/common.html#MISSION_ACK */
                            set_ui_message(thiz, "MISSION_ACK", true);

                            sprintf(feedback,
                                    "MSG ID: MISSION_ACK, SYS: %d, COMP: %d, LEN: %d, SEQ: %d",
                                    rcv_msg.sysid, rcv_msg.compid, rcv_msg.len,
                                    rcv_msg.seq);
                            set_log_message(thiz, feedback);
                        }
                            break;
                        case 69: {
                            /** https://mavlink.io/en/messages/common.html#MANUAL_CONTROL */
                            mavlink_manual_control_t manual_control;
                            mavlink_msg_manual_control_decode(&rcv_msg, &manual_control);

                            process_buttons(thiz, manual_control.buttons);
                            set_servos(thiz, manual_control.x, manual_control.y, manual_control.z,
                                       manual_control.r);
                        }
                            break;
                        case 73: {
                            /** https://mavlink.io/en/messages/common.html#MISSION_ITEM_INT */
                            mavlink_mission_item_int_t content;
                            mavlink_msg_mission_item_int_decode(&rcv_msg, &content);

                            sprintf(feedback,
                                    "MSG ID: MISSION_ITEM_INT, SYS: %d, COMP: %d, LEN: %d, SEQ: %d",
                                    rcv_msg.sysid, rcv_msg.compid, rcv_msg.len, content.seq);
                            set_log_message(thiz, feedback);

                            if (content.frame == MAV_FRAME_GLOBAL || content.frame == MAV_FRAME_GLOBAL_RELATIVE_ALT)
                            {
                                /**
                                 x = latitude in degrees * 10^7
                                 y = longitude in degrees * 10^7
                                 x = altitude in meters (relative or absolute, depending on frame
                                 */
                                add_waypoint(thiz, ((double) content.x)/10000000,((double) content.y)/10000000, (double) content.z, content.frame, content.seq);
                            }
                            else
                            {
                                sprintf(feedback, "Frame: %d", content.frame);
                                set_log_message(thiz, feedback);
                            }

                            /** stop timeout */
                            pthread_join(mission_request_timeout_thread, NULL);

                            uint8_t mission_type = mavlink_msg_mission_item_int_get_mission_type(&rcv_msg);
                            if (content.seq < missionItemCount -1) {
                                /** send next request */
                                sprintf(feedback, "requesting: %d", content.seq + 1);
                                set_log_message(thiz, feedback);

                                if (missionItemCount > 0) {
                                    if (mission_type == MAV_MISSION_TYPE_MISSION) {
                                        send_MISSION_REQUEST_INT(content.seq + 1, MAV_MISSION_TYPE_MISSION);
                                        sprintf(feedback, "Mission type is: mission");
                                    }
                                    else if (mission_type == MAV_MISSION_TYPE_FENCE) {
                                        send_MISSION_REQUEST_INT(content.seq + 1, MAV_MISSION_TYPE_FENCE);
                                        sprintf(feedback, "Mission type is: fence");
                                    }
                                    else if (mission_type == MAV_MISSION_TYPE_RALLY) {
                                        send_MISSION_REQUEST_INT(content.seq + 1, MAV_MISSION_TYPE_RALLY);
                                        sprintf(feedback, "Mission type is: rally");
                                    }
                                    else {
                                        send_MISSION_REQUEST_INT(content.seq + 1, mission_type);
                                        sprintf(feedback, "Mission type is: %d", mission_type);
                                    }
                                    set_log_message(thiz, feedback);
                            }
                            }
                            if (content.seq == missionItemCount -1)
                            {
                                sprintf(feedback, "content.seq = %d -1", missionItemCount);
                                set_log_message(thiz, feedback);
                                //FIXME: failed to send final ACK
                                send_MISSION_ACK(content.target_system, content.target_component, mission_type);
                                set_sound(thiz, 1);
                            }

                        }
                            break;
                        case 75: {
                            /** https://mavlink.io/en/messages/common.html#COMMAND_INT */
                            mavlink_command_int_t command_int;
                            mavlink_msg_command_int_decode(&rcv_msg, &command_int);
                            processCommandINT(thiz, rcv_msg.sysid, rcv_msg.compid, command_int);
                        }
                            break;
                        case 76: {
                            /** https://mavlink.io/en/messages/common.html#COMMAND_LONG */
                            mavlink_command_long_t command_long;
                            mavlink_msg_command_long_decode(&rcv_msg, &command_long);
                            processCommandLONG(thiz, rcv_msg.sysid, rcv_msg.compid, command_long);
                        }
                            break;
                        default: {
                            sprintf(feedback, "MSG ID: %d, SYS: %d, COMP: %d, LEN: %d, SEQ: %d",
                                    rcv_msg.msgid, rcv_msg.sysid, rcv_msg.compid, rcv_msg.len,
                                    rcv_msg.seq);
                            set_log_message(thiz, feedback);
                        }
                            break;
                    }
                }
            }
            printf("\n");
        }
        memset(rcv_buf, 0, BUFFER_LENGTH);
        /** The typical RC servo expects to receive a pulse every 20 ms... */
        usleep(20);
    }
    return 0;
}

/** https://github.com/mavlink/mavlink/blob/master/examples/linux/mavlink_udp.c */
int *heartbeatFunction(jobject thiz) {
    mavlink_message_t msg;
    uint16_t len;

    while (heartbeat_running) {
/* send heartbeat message if the ground station ever sent something and it's address is known */
        if (gcAddr.sin_addr.s_addr != -1) {
            mavlink_msg_heartbeat_pack(THIS_SYSTEM, THIS_COMPONENT, &msg, MAV_TYPE_GENERIC,
                                       MAV_AUTOPILOT_GENERIC, MAV_MODE_MANUAL_ARMED, 0,
                                       MAV_STATE_ACTIVE);

            len = mavlink_msg_to_send_buffer(buf, &msg);
            bytes_sent = sendto(sock, buf, len, 0, (struct sockaddr *) &gcAddr,
                                sizeof(struct sockaddr_in));

//prints a log entry every time a heartbeat message is sent
/*
            char seqArr[32];
            sprintf(seqArr, "heartbeat sent: %d\n", msg.seq);
            set_log_message(thiz, seqArr);
*/
            if (battery_voltage != 0 && battery_level != 0) {
                mavlink_msg_sys_status_pack(THIS_SYSTEM, THIS_COMPONENT, &msg, 0, 0, 0, 500,
                                            battery_voltage, -1, battery_level, 0, 0, 0, 0, 0, 0);

                len = mavlink_msg_to_send_buffer(buf, &msg);
                bytes_sent = sendto(sock, buf, len, 0, (struct sockaddr *) &gcAddr,
                                    sizeof(struct sockaddr_in));

                char feedback[128];
                if (bytes_sent == -1) {
                    sprintf(feedback, "ERROR: bytes_sent == -1");
                    set_log_message(thiz, feedback);
                }
            }

            gettimeofday(&tv, NULL);
            current_time = (tv.tv_sec % 1000000) * 1000 + tv.tv_usec / 1000;

            if (communication && last_beat_time != NULL && (current_time > last_beat_time + 1999)) {
                /* communication lost */
                set_ui_message(thiz, "communication lost", true);
                set_log_message(thiz, "communication lost");
                set_sound(thiz, 2);
                communication = false;
            }
        } else {
            /*
            char feedback[128];
            sprintf(feedback, "no messages from ground control station yet");
            set_log_message(thiz, feedback);
             */
        }
        sleep(1);
    }
    close(sock);
    return 0;
}


int *mission_request_timeout_function() {
    usleep(1000);
    printf("ERROR: MISSION_REQUEST_INT timeout has elapsed");
    return 0;
}

jint Java_pl_bezzalogowe_mavlink_MAVLinkClass_receiveInit(JNIEnv *env, jobject thiz) {
    jobject *data = (*env)->NewGlobalRef(env, thiz);
    set_ui_message(thiz, "started receiving thread", false);
    running = true;

    int returnvalue;
    returnvalue = openSocket(data);
    pthread_create(&rcv_thread, NULL, &receiveFunction, data);
    return returnvalue;
}

jint Java_pl_bezzalogowe_mavlink_MAVLinkClass_receiveStop(JNIEnv *env, jobject thiz) {
    set_ui_message(thiz, "stopped receiving thread", false);
    running = false;
    pthread_join(rcv_thread, NULL);
}

jint Java_pl_bezzalogowe_mavlink_MAVLinkClass_heartBeatInit(JNIEnv *env, jobject thiz) {
    jobject *data = (*env)->NewGlobalRef(env, thiz);
    set_ui_message(thiz, "started heartbeat thread", false);
    heartbeat_running = true;
    communication = true;
    pthread_create(&heartbeat_thread, NULL, &heartbeatFunction, data);
    return 0;
}

jint Java_pl_bezzalogowe_mavlink_MAVLinkClass_heartBeatStop(JNIEnv *env, jobject thiz) {
    set_ui_message(thiz, "stopped heartbeat thread", false);
    heartbeat_running = false;
    pthread_join(heartbeat_thread, NULL);
}

void sendACK(mavlink_command_long_t command_long) {
    mavlink_message_t msg;
    mavlink_msg_command_ack_pack(THIS_SYSTEM, THIS_COMPONENT, &msg, command_long.command,
                                 MAV_RESULT_ACCEPTED, 0, 0, command_long.target_system,
                                 command_long.target_component);
    uint16_t len;
    len = mavlink_msg_to_send_buffer(buf, &msg);
    bytes_sent = sendto(sock, buf, len, 0, (struct sockaddr *) &gcAddr, sizeof(struct sockaddr_in));
}

void sendProtocolVersion(int number) {
    /* https://mavlink.io/en/messages/common.html#PROTOCOL_VERSION */
    mavlink_message_t message;
    mavlink_protocol_version_t *ver;
//FIXME: does nothing
/*
    ver->version = number;
    ver->min_version = 100;
    ver->max_version = number;
*/
    mavlink_msg_protocol_version_encode(THIS_SYSTEM, THIS_COMPONENT, &message, &ver);
    uint16_t len;
    len = mavlink_msg_to_send_buffer(buf, &message);
    bytes_sent = sendto(sock, buf, len, 0, (struct sockaddr *) &gcAddr, sizeof(struct sockaddr_in));
}

void sendAutopilotVersion() {
    mavlink_message_t msg;
    /* bit field */
    //uint64_t capabilities = 0;
    uint64_t capabilities = UINT64_MAX;
    mavlink_msg_autopilot_version_pack(THIS_SYSTEM, THIS_COMPONENT, &msg, &capabilities, 0, 0, 0, 0,
                                       0, 0, 0, 0, 0, 0, 0);
    uint16_t len;
    len = mavlink_msg_to_send_buffer(buf, &msg);
    bytes_sent = sendto(sock, buf, len, 0, (struct sockaddr *) &gcAddr, sizeof(struct sockaddr_in));
}

jint Java_pl_bezzalogowe_mavlink_MAVLinkClass_sendProtocol(JNIEnv *env, jobject thiz) {
    sendProtocolVersion(200);
    set_ui_message(thiz, "version sent", false);
}

/* currently unused */
jint Java_pl_bezzalogowe_mavlink_MAVLinkClass_sendHello(JNIEnv *env, jobject thiz) {
    char message[5] = "HELLO";
    int length = sizeof(message);
    bytes_sent = sendto(sock, message, length, 0, (struct sockaddr *) &gcAddr,
                        sizeof(struct sockaddr_in));
    char feedback[128];
    sprintf(feedback, "sent: %s, %d bytes", message, bytes_sent);
    set_ui_message(thiz, feedback, false);
}

/** https://mavlink.io/en/messages/common.html#MISSION_REQUEST_INT */
void send_MISSION_REQUEST_INT(int index, int type) {
    mavlink_message_t message;
    mavlink_msg_mission_request_int_pack(THIS_SYSTEM, THIS_COMPONENT, &message, 0, 0, index, type);
    uint16_t len;
    len = mavlink_msg_to_send_buffer(buf, &message);
    bytes_sent = sendto(sock, buf, len, 0, (struct sockaddr *) &gcAddr, sizeof(struct sockaddr_in));

    /** start timeout */
    pthread_create(&mission_request_timeout_thread, NULL, &mission_request_timeout_function, NULL);
}

/** https://mavlink.io/en/messages/common.html#MISSION_ACK */
void send_MISSION_ACK(int target_system, int target_component, int mission_type) {
    mavlink_message_t message;
    mavlink_msg_mission_ack_pack(THIS_SYSTEM, THIS_COMPONENT, &message, target_system, target_component, MAV_MISSION_ACCEPTED, mission_type);
    uint16_t len;
    len = mavlink_msg_to_send_buffer(buf, &message);
    bytes_sent = sendto(sock, buf, len, 0, (struct sockaddr *) &gcAddr, sizeof(struct sockaddr_in));
}

/* QNX timer version */
#if (defined __QNX__) | (defined __QNXNTO__)
uint64_t microsSinceEpoch()
{

    struct timespec time;

    uint64_t micros = 0;

    clock_gettime(CLOCK_REALTIME, &time);
    micros = (uint64_t)time.tv_sec * 1000000 + time.tv_nsec/1000;

    return micros;
}
#else

uint64_t microsSinceEpoch() {
    struct timeval tv;
    uint64_t micros = 0;
    gettimeofday(&tv, NULL);
    micros = ((uint64_t) tv.tv_sec) * 1000000 + tv.tv_usec;
    return micros;
}
#endif
