#include <jni.h>
#include <stdlib.h>
#include <string.h>
#include <android/log.h>

#include "power_reader.h"

#define TAG "PowerReaderJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

#define MAX_PATH_LENGTH 256
#define MAX_LINE_LENGTH 128

// File descriptor cache structure
typedef struct {
    FILE *voltage_fp;
    FILE *current_fp;
    FILE *capacity_fp;
    FILE *status_fp;
    FILE *temp_fp;
    int initialized;
} FileCache;

static FileCache g_cache = {NULL, NULL, NULL, NULL, NULL, 0};

// Open and cache file descriptors
static int init_file_cache() {
    if (g_cache.initialized) {
        return 1;
    }

    g_cache.voltage_fp = fopen("/sys/class/power_supply/battery/voltage_now", "r");
    if (!g_cache.voltage_fp) {
        LOGE("%s: Failed to open voltage_now", __func__);
        return 0;
    }

    g_cache.current_fp = fopen("/sys/class/power_supply/battery/current_now", "r");
    if (!g_cache.current_fp) {
        LOGE("%s: Failed to open current_now", __func__);
        fclose(g_cache.voltage_fp);
        g_cache.voltage_fp = NULL;
        return 0;
    }

    g_cache.capacity_fp = fopen("/sys/class/power_supply/battery/capacity", "r");
    if (!g_cache.capacity_fp) {
        LOGE("%s: Failed to open capacity", __func__);
        fclose(g_cache.voltage_fp);
        fclose(g_cache.current_fp);
        g_cache.current_fp = NULL;
        return 0;
    }

    g_cache.status_fp = fopen("/sys/class/power_supply/battery/status", "r");
    if (!g_cache.status_fp) {
        LOGE("%s: Failed to open status", __func__);
        fclose(g_cache.voltage_fp);
        g_cache.voltage_fp = NULL;
        fclose(g_cache.current_fp);
        g_cache.current_fp = NULL;
        fclose(g_cache.capacity_fp);
        g_cache.capacity_fp = NULL;
        return 0;
    }

    g_cache.temp_fp = fopen("/sys/class/power_supply/battery/temp", "r");
    if (!g_cache.temp_fp) {
        LOGE("%s: Failed to open temp", __func__);
        fclose(g_cache.voltage_fp);
        g_cache.voltage_fp = NULL;
        fclose(g_cache.current_fp);
        g_cache.current_fp = NULL;
        fclose(g_cache.capacity_fp);
        g_cache.capacity_fp = NULL;
        fclose(g_cache.status_fp);
        g_cache.status_fp = NULL;
        return 0;
    }

    g_cache.initialized = 1;
    return 1;
}

// Read a long value from file
static long read_long(FILE *fp) {
    char buffer[MAX_LINE_LENGTH];
    rewind(fp);
    fflush(fp);

    if (!fgets(buffer, MAX_LINE_LENGTH, fp)) {
        return 0;
    }

    return atol(buffer);
}

// Read a int value from file
static int read_int(FILE *fp) {
    char buffer[MAX_LINE_LENGTH];
    rewind(fp);
    fflush(fp);

    if (!fgets(buffer, MAX_LINE_LENGTH, fp)) {
        return 0;
    }

    return atoi(buffer);
}

static jint native_init(JNIEnv *env __attribute__((unused)), jclass clazz __attribute__((unused))) {
    return init_file_cache();
}

static jlong native_get_voltage(JNIEnv *env __attribute__((unused)), jclass clazz __attribute__((unused))) {
    if (!g_cache.initialized || !g_cache.voltage_fp) {
        return 0;
    }
    return read_long(g_cache.voltage_fp);
}

static jlong native_get_current(JNIEnv *env __attribute__((unused)), jclass clazz __attribute__((unused))) {
    if (!g_cache.initialized || !g_cache.current_fp) {
        return 0;
    }
    return read_long(g_cache.current_fp);
}

static jint native_get_capacity(JNIEnv *env __attribute__((unused)), jclass clazz __attribute__((unused))) {
    if (!g_cache.initialized || !g_cache.capacity_fp) {
        return 0;
    }
    return read_int(g_cache.capacity_fp);
}

static jstring native_get_status(JNIEnv *env, jclass clazz __attribute__((unused))) {
    if (!g_cache.initialized || !g_cache.status_fp) {
        return env->NewStringUTF("");
    }

    FILE *fp = g_cache.status_fp;
    rewind(fp);
    fflush(fp);

    char buffer[MAX_LINE_LENGTH];
    if (fgets(buffer, MAX_LINE_LENGTH, fp)) {
        // 部分设备的 status 节点可能短暂返回 Unknown，重开节点后再取一次真实状态。
        if (buffer[0] == 'U') {
            fclose(g_cache.status_fp);
            g_cache.status_fp = fopen("/sys/class/power_supply/battery/status", "r");
            if (!g_cache.status_fp) {
                LOGE("%s: Failed to reopen status", __func__);
                return env->NewStringUTF("");
            }
            fp = g_cache.status_fp;
            rewind(fp);
            fflush(fp);
            if (fgets(buffer, MAX_LINE_LENGTH, fp)) {
                return env->NewStringUTF(buffer);
            }
            return env->NewStringUTF("");
        }
        return env->NewStringUTF(buffer);
    }

    fclose(g_cache.status_fp);
    g_cache.status_fp = fopen("/sys/class/power_supply/battery/status", "r");
    if (!g_cache.status_fp) {
        LOGE("%s: Failed to reopen status", __func__);
        return env->NewStringUTF("");
    }
    fp = g_cache.status_fp;
    rewind(fp);
    fflush(fp);
    if (fgets(buffer, MAX_LINE_LENGTH, fp)) {
        return env->NewStringUTF(buffer);
    }

    return env->NewStringUTF("");
}

static jint native_get_temp(JNIEnv *env __attribute__((unused)), jclass clazz __attribute__((unused))) {
    if (!g_cache.initialized || !g_cache.temp_fp) {
        return 0;
    }

    return read_int(g_cache.temp_fp);
}

int register_sysfs_native_methods(JNIEnv* env) {
    const char* class_name = "yangfentuozi/batteryrecorder/server/sampler/SysfsSampler";
    jclass clazz = env->FindClass(class_name);
    if (!clazz) {
        LOGE("%s: Failed to find class %s", __func__, class_name);
        return JNI_FALSE;
    }
    static const JNINativeMethod methods[] = {
            {"nativeInit", "()I", reinterpret_cast<void*>(native_init)},
            {"nativeGetVoltage", "()J", reinterpret_cast<void*>(native_get_voltage)},
            {"nativeGetCurrent", "()J", reinterpret_cast<void*>(native_get_current)},
            {"nativeGetCapacity", "()I", reinterpret_cast<void*>(native_get_capacity)},
            {"nativeGetStatus", "()Ljava/lang/String;", reinterpret_cast<void*>(native_get_status)},
            {"nativeGetTemp", "()I", reinterpret_cast<void*>(native_get_temp)},
    };
    if (env->RegisterNatives(clazz, methods, sizeof(methods) / sizeof(methods[0])) < 0) {
        LOGE("%s: RegisterNatives failed for %s", __func__, class_name);
        env->DeleteLocalRef(clazz);
        return JNI_FALSE;
    }
    env->DeleteLocalRef(clazz);
    return JNI_TRUE;
}
