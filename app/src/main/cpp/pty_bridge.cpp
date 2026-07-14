#include <jni.h>
#include <android/log.h>

#include <cerrno>
#include <csignal>
#include <cstdlib>
#include <cstring>
#include <fcntl.h>
#include <string>
#include <sys/ioctl.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <unistd.h>
#include <vector>

namespace {
constexpr const char* kTag = "HyperShellPty";

std::string toString(JNIEnv* env, jstring value) {
    if (value == nullptr) return {};
    const char* chars = env->GetStringUTFChars(value, nullptr);
    if (chars == nullptr) return {};
    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}

std::vector<std::string> toStrings(JNIEnv* env, jobjectArray values) {
    std::vector<std::string> result;
    if (values == nullptr) return result;
    const jsize size = env->GetArrayLength(values);
    result.reserve(static_cast<size_t>(size));
    for (jsize index = 0; index < size; ++index) {
        auto value = static_cast<jstring>(env->GetObjectArrayElement(values, index));
        result.emplace_back(toString(env, value));
        env->DeleteLocalRef(value);
    }
    return result;
}

void applyWindowSize(int fd, int rows, int columns) {
    winsize size{};
    size.ws_row = static_cast<unsigned short>(rows > 0 ? rows : 24);
    size.ws_col = static_cast<unsigned short>(columns > 0 ? columns : 80);
    ioctl(fd, TIOCSWINSZ, &size);
}

jlongArray errorResult(JNIEnv* env, int error) {
    jlong values[2] = {-1, -error};
    jlongArray result = env->NewLongArray(2);
    env->SetLongArrayRegion(result, 0, 2, values);
    return result;
}
}  // namespace

extern "C" JNIEXPORT jlongArray JNICALL
Java_io_github_hypershell_terminal_PtyNative_nativeStart(
    JNIEnv* env,
    jclass,
    jobjectArray commandArray,
    jstring cwdValue,
    jobjectArray environmentArray,
    jint rows,
    jint columns
) {
    auto command = toStrings(env, commandArray);
    if (command.empty() || command.front().empty()) return errorResult(env, EINVAL);

    const int master = posix_openpt(O_RDWR | O_NOCTTY | O_CLOEXEC);
    if (master < 0) return errorResult(env, errno);
    if (grantpt(master) != 0 || unlockpt(master) != 0) {
        const int error = errno;
        close(master);
        return errorResult(env, error);
    }

    char slaveName[128]{};
    if (ptsname_r(master, slaveName, sizeof(slaveName)) != 0) {
        const int error = errno;
        close(master);
        return errorResult(env, error);
    }

    applyWindowSize(master, rows, columns);
    const std::string cwd = toString(env, cwdValue);
    auto environment = toStrings(env, environmentArray);
    const pid_t pid = fork();
    if (pid < 0) {
        const int error = errno;
        close(master);
        return errorResult(env, error);
    }

    if (pid == 0) {
        close(master);
        if (setsid() < 0) _exit(126);
        const int slave = open(slaveName, O_RDWR);
        if (slave < 0) _exit(126);
        if (ioctl(slave, TIOCSCTTY, 0) < 0) _exit(126);
        if (dup2(slave, STDIN_FILENO) < 0 ||
            dup2(slave, STDOUT_FILENO) < 0 ||
            dup2(slave, STDERR_FILENO) < 0) {
            _exit(126);
        }
        if (slave > STDERR_FILENO) close(slave);

        for (const auto& item : environment) {
            const auto separator = item.find('=');
            if (separator != std::string::npos && separator > 0) {
                const std::string key = item.substr(0, separator);
                const std::string value = item.substr(separator + 1);
                setenv(key.c_str(), value.c_str(), 1);
            }
        }
        if (!cwd.empty() && chdir(cwd.c_str()) != 0) {
            chdir("/");
        }

        std::vector<char*> argv;
        argv.reserve(command.size() + 1);
        for (auto& item : command) argv.push_back(item.data());
        argv.push_back(nullptr);
        execvp(argv.front(), argv.data());
        __android_log_print(ANDROID_LOG_ERROR, kTag, "execvp failed: %s", strerror(errno));
        _exit(127);
    }

    jlong values[2] = {master, pid};
    jlongArray result = env->NewLongArray(2);
    env->SetLongArrayRegion(result, 0, 2, values);
    return result;
}

extern "C" JNIEXPORT jint JNICALL
Java_io_github_hypershell_terminal_PtyNative_nativeRead(
    JNIEnv* env,
    jclass,
    jint fd,
    jbyteArray buffer,
    jint offset,
    jint length
) {
    if (fd < 0 || buffer == nullptr || offset < 0 || length <= 0) return -EINVAL;
    jbyte* bytes = env->GetByteArrayElements(buffer, nullptr);
    if (bytes == nullptr) return -ENOMEM;
    const ssize_t count = read(fd, bytes + offset, static_cast<size_t>(length));
    const int error = errno;
    env->ReleaseByteArrayElements(buffer, bytes, 0);
    if (count >= 0) return static_cast<jint>(count);
    if (error == EIO) return 0;
    return -error;
}

extern "C" JNIEXPORT jint JNICALL
Java_io_github_hypershell_terminal_PtyNative_nativeWrite(
    JNIEnv* env,
    jclass,
    jint fd,
    jbyteArray buffer,
    jint offset,
    jint length
) {
    if (fd < 0 || buffer == nullptr || offset < 0 || length <= 0) return -EINVAL;
    jbyte* bytes = env->GetByteArrayElements(buffer, nullptr);
    if (bytes == nullptr) return -ENOMEM;
    const ssize_t count = write(fd, bytes + offset, static_cast<size_t>(length));
    const int error = errno;
    env->ReleaseByteArrayElements(buffer, bytes, JNI_ABORT);
    return count >= 0 ? static_cast<jint>(count) : -error;
}

extern "C" JNIEXPORT void JNICALL
Java_io_github_hypershell_terminal_PtyNative_nativeResize(
    JNIEnv*, jclass, jint fd, jint rows, jint columns
) {
    if (fd >= 0) applyWindowSize(fd, rows, columns);
}

extern "C" JNIEXPORT jint JNICALL
Java_io_github_hypershell_terminal_PtyNative_nativeWait(JNIEnv*, jclass, jint pid) {
    int status = 0;
    pid_t result;
    do {
        result = waitpid(pid, &status, 0);
    } while (result < 0 && errno == EINTR);
    if (result < 0) return -errno;
    if (WIFEXITED(status)) return WEXITSTATUS(status);
    if (WIFSIGNALED(status)) return 128 + WTERMSIG(status);
    return 255;
}

extern "C" JNIEXPORT void JNICALL
Java_io_github_hypershell_terminal_PtyNative_nativeSignal(
    JNIEnv*, jclass, jint pid, jint signalValue
) {
    if (pid > 0) kill(-pid, signalValue);
}

extern "C" JNIEXPORT void JNICALL
Java_io_github_hypershell_terminal_PtyNative_nativeTerminate(JNIEnv*, jclass, jint pid) {
    if (pid <= 0) return;
    kill(-pid, SIGHUP);
    usleep(100000);
    if (kill(-pid, 0) == 0) kill(-pid, SIGTERM);
    usleep(100000);
    if (kill(-pid, 0) == 0) kill(-pid, SIGKILL);
}

extern "C" JNIEXPORT void JNICALL
Java_io_github_hypershell_terminal_PtyNative_nativeClose(JNIEnv*, jclass, jint fd) {
    if (fd >= 0) close(fd);
}

