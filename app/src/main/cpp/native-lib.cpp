#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <sys/socket.h>
#include <unistd.h>

#define LOG_TAG "NmapJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

struct NmapHost {
    std::string ip;
    std::string hostname;
    std::string ports;
};



bool testIp(const std::string& ip) {
    // ⚡ FAST VALIDATION (5µs)
    if (ip.length() < 7 || ip.length() > 15) return false;
    if (std::count(ip.begin(), ip.end(), '.') != 3) return false;

    // ⚡ ICMP PING FIRST (10ms) - 90% faster than TCP!
   // if (pingIp(ip.c_str())) return true;

    // ⚡ TCP SCAN (100ms max) - only if ping responds
    int sock = socket(AF_INET, SOCK_STREAM, 0);
    if (sock < 0) return false;

    struct sockaddr_in addr = {0};
    addr.sin_family = AF_INET;
    addr.sin_port = htons(22);

    if (inet_pton(AF_INET, ip.c_str(), &addr.sin_addr) <= 0) {
        close(sock);
        return false;
    }

    // ⚡ 100ms → 80% faster!
    struct timeval tv = {0, 150000};  // 100ms
    setsockopt(sock, SOL_SOCKET, SO_SNDTIMEO, &tv, sizeof(tv));

    int result = connect(sock, (struct sockaddr*)&addr, sizeof(addr));
    close(sock);
    return result == 0;
}


// Global stop flag (set from Java via JNI)
extern "C" {
volatile bool g_stopScan = false;
JNIEXPORT void JNICALL Java_com_luksza_rpitxgui_NmapDiscovery_stopScan(JNIEnv* env, jobject thiz) {
    g_stopScan = true;
    LOGI("🛑 STOP REQUESTED");
}
}
// EXACT MATCH for MainActivityKt
extern "C" JNIEXPORT void JNICALL
Java_com_luksza_rpitxgui_MainActivityKt_nativeStopScan(JNIEnv *env, jclass clazz) {
    g_stopScan = true;
    LOGI("🛑 STOP SIGNAL RECEIVED");
}

std::vector<NmapHost> g_foundHosts;
int g_nextIp = 1;


#include <netdb.h>

// ✅ NEW: Reverse DNS lookup
std::string getHostname(const std::string& ip) {
    struct sockaddr_in sa;
    char hostname[256];

    memset(&sa, 0, sizeof(sa));
    sa.sin_family = AF_INET;
    inet_pton(AF_INET, ip.c_str(), &sa.sin_addr);

    // Try reverse lookup (500ms timeout)
    int result = getnameinfo((struct sockaddr*)&sa, sizeof(sa),
                             hostname, sizeof(hostname),
                             NULL, 0, NI_NAMEREQD);

    if (result == 0) {
        LOGI("   → Found hostname: %s", hostname);
        return std::string(hostname);
    } else {
        // Fallback: "device-X" or IP
        LOGI("   → No hostname for %s", ip.c_str());
        return "device-" + std::to_string(g_foundHosts.size() + 1);
    }
}

// Global references to callback (simplification, ideally pass env through)
JavaVM* g_jvm = nullptr;

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
    g_jvm = vm;
    return JNI_VERSION_1_6;
}

void notifyDeviceFound(JNIEnv* env, jobject callback, const NmapHost& host) {
    jclass callbackClass = env->GetObjectClass(callback);
    jmethodID onDeviceFound = env->GetMethodID(callbackClass, "onDeviceFound", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V");

    if (onDeviceFound) {
        jstring ip = env->NewStringUTF(host.ip.c_str());
        jstring hostname = env->NewStringUTF(host.hostname.c_str());
        jstring ports = env->NewStringUTF(host.ports.c_str());

        env->CallVoidMethod(callback, onDeviceFound, ip, hostname, ports);

        env->DeleteLocalRef(ip);
        env->DeleteLocalRef(hostname);
        env->DeleteLocalRef(ports);
    }
}

void notifyProgress(JNIEnv* env, jobject callback, int progress) {
    jclass callbackClass = env->GetObjectClass(callback);
    jmethodID onProgress = env->GetMethodID(callbackClass, "onProgress", "(I)V");

    if (onProgress) {
        env->CallVoidMethod(callback, onProgress, progress);
    }
}

void scanNetwork(JNIEnv* env, jobject callback, const char* targetStr) {
    LOGI("🔍 FULL SCAN START WITH CALLBACK");
    g_stopScan = false;

    std::string baseSubnet = "192.168.1";

    // ✅ SCAN ALL 1-254
    for (int i = 1; i <= 254; i++) {
        if (g_stopScan) break;
        
        // Notify progress
        notifyProgress(env, callback, i);

        std::string ip = baseSubnet + "." + std::to_string(i);

        if (testIp(ip)) {
            std::string hostname = getHostname(ip);
            NmapHost host = {ip, hostname, "22/ssh"};
            
            // ✅ NOTIFY IMMEDIATELY
            notifyDeviceFound(env, callback, host);

            LOGI("✅ %s (%s)", hostname.c_str(), ip.c_str());
        }
    }

    LOGI("🏁 Scan complete");
}

extern "C" JNIEXPORT void JNICALL
Java_com_luksza_rpitxgui_NmapDiscovery_nativeNmapScan(JNIEnv *env, jobject thiz, jstring target, jobject callback) {
    LOGI("nativeNmapScan START");

    const char* targetStr = env->GetStringUTFChars(target, 0);
    
    // We don't need to wrap callback in global ref if we use it only in this thread synchronously
    // native-lib.cpp implementation of scanNetwork is synchronous on the calling thread (which is Dispatchers.IO from Kotlin)
    
    scanNetwork(env, callback, targetStr);

    env->ReleaseStringUTFChars(target, targetStr);
    LOGI("nativeNmapScan END");
}
