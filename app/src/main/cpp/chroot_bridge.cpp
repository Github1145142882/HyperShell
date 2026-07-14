#include <jni.h>
#include <android/log.h>

#include <cerrno>
#include <cstdlib>
#include <cstring>
#include <sched.h>
#include <string>
#include <sys/mount.h>
#include <sys/stat.h>
#include <unistd.h>

namespace {
constexpr const char* kTag = "HyperShellChroot";

class UtfChars {
public:
    UtfChars(JNIEnv* env, jstring value) : env_(env), value_(value) {
        chars_ = value == nullptr ? nullptr : env_->GetStringUTFChars(value, nullptr);
    }
    ~UtfChars() {
        if (chars_ != nullptr) env_->ReleaseStringUTFChars(value_, chars_);
    }
    const char* get() const { return chars_; }
private:
    JNIEnv* env_;
    jstring value_;
    const char* chars_ = nullptr;
};

int ensureDirectory(const char* path, mode_t mode = 0755) {
    if (mkdir(path, mode) == 0 || errno == EEXIST) return 0;
    return errno;
}

int bindMount(const char* source, const char* target, unsigned long extraFlags = 0) {
    if (mount(source, target, nullptr, MS_BIND | MS_REC, nullptr) != 0) return errno;
    if ((extraFlags & MS_RDONLY) != 0 &&
        mount(nullptr, target, nullptr, MS_BIND | MS_REMOUNT | MS_RDONLY, nullptr) != 0) {
        return errno;
    }
    return 0;
}

int prepareChroot(const char* root, const char* home) {
    if (geteuid() != 0) return EPERM;
    if (root == nullptr || home == nullptr || root[0] != '/' || home[0] != '/') return EINVAL;
    if (unshare(CLONE_NEWNS) != 0) return errno;
    if (mount(nullptr, "/", nullptr, MS_REC | MS_PRIVATE, nullptr) != 0) return errno;

    struct stat statBuffer{};
    if (stat(root, &statBuffer) != 0 || !S_ISDIR(statBuffer.st_mode)) return ENOENT;

    const std::string rootPath(root);
    const std::string dev = rootPath + "/dev";
    const std::string proc = rootPath + "/proc";
    const std::string sys = rootPath + "/sys";
    const std::string rootHome = rootPath + "/root";
    int result = ensureDirectory(dev.c_str());
    if (result != 0) return result;
    result = ensureDirectory(proc.c_str());
    if (result != 0) return result;
    result = ensureDirectory(sys.c_str());
    if (result != 0) return result;
    result = ensureDirectory(rootHome.c_str(), 0700);
    if (result != 0) return result;

    result = bindMount("/dev", dev.c_str());
    if (result != 0) return result;
    if (mount("proc", proc.c_str(), "proc", MS_NOSUID | MS_NOEXEC | MS_NODEV, nullptr) != 0) return errno;
    result = bindMount("/sys", sys.c_str(), MS_RDONLY);
    if (result != 0) return result;
    result = bindMount(home, rootHome.c_str());
    if (result != 0) return result;

    if (chdir(root) != 0) return errno;
    if (chroot(".") != 0) return errno;
    if (chdir("/root") != 0) return errno;
    return 0;
}
}  // namespace

extern "C" JNIEXPORT jint JNICALL
Java_io_github_hypershell_terminal_ChrootNative_nativeProbe(
    JNIEnv* env,
    jclass,
    jstring rootValue,
    jstring homeValue
) {
    UtfChars root(env, rootValue);
    UtfChars home(env, homeValue);
    const int result = prepareChroot(root.get(), home.get());
    if (result != 0) {
        __android_log_print(ANDROID_LOG_WARN, kTag, "probe failed: %s", strerror(result));
        return result;
    }
    return access("/bin/bash", X_OK) == 0 ? 0 : errno;
}

extern "C" JNIEXPORT jint JNICALL
Java_io_github_hypershell_terminal_ChrootNative_nativeEnter(
    JNIEnv* env,
    jclass,
    jstring rootValue,
    jstring homeValue
) {
    UtfChars root(env, rootValue);
    UtfChars home(env, homeValue);
    const int result = prepareChroot(root.get(), home.get());
    if (result != 0) return result;
    setenv("HOME", "/root", 1);
    setenv("USER", "root", 1);
    setenv("LOGNAME", "root", 1);
    setenv("SHELL", "/bin/bash", 1);
    setenv("PATH", "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin", 1);
    setenv("LANG", "C.UTF-8", 1);
    execl("/bin/bash", "/bin/bash", "--login", static_cast<char*>(nullptr));
    return errno;
}
