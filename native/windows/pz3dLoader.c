#define WIN32_LEAN_AND_MEAN
#include <windows.h>

/* Thin JVM instrumentation bridge. No updater and no mod-loading policy here. */
static HMODULE self, instrument;
static int (*onLoad)(void *, char *, void *);
static void (*onUnload)(void *);
static char jar[4 * MAX_PATH];
BOOL WINAPI DllMain(HINSTANCE module, DWORD reason, LPVOID reserved) {
    if (reason == DLL_PROCESS_ATTACH) self = module;
    return TRUE;
}
static int fail(const char *message) {
    DWORD written;
    OutputDebugStringA(message);
    WriteFile(GetStdHandle(STD_ERROR_HANDLE), message, lstrlenA(message), &written, NULL);
    return -1;
}
static int parent(WCHAR *path) {
    int i = lstrlenW(path);
    while (i > 0 && path[i] != L'\\') --i;
    if (!i) return 0;
    path[i] = 0;
    return 1;
}
__declspec(dllexport) int Agent_OnLoad(void *vm, char *options, void *reserved) {
    WCHAR runtime[MAX_PATH], source[MAX_PATH];
    if (options && options[0]) return fail("[pz3d Loader] Native bridge takes no options.\n");
    HMODULE jvm = GetModuleHandleW(L"jvm.dll");
    DWORD n = jvm ? GetModuleFileNameW(jvm, runtime, MAX_PATH) : 0;
    if (!n || n >= MAX_PATH || !parent(runtime) || !parent(runtime) || lstrlenW(runtime) > MAX_PATH-17)
        return fail("[pz3d Loader] Cannot locate the game's Java runtime.\n");
    lstrcatW(runtime, L"\\instrument.dll");
    instrument = LoadLibraryExW(runtime, NULL, LOAD_LIBRARY_SEARCH_DLL_LOAD_DIR | LOAD_LIBRARY_SEARCH_DEFAULT_DIRS);
    if (!instrument) return fail("[pz3d Loader] Cannot load the game's instrument.dll.\n");
    onLoad = (int (*)(void *, char *, void *))GetProcAddress(instrument, "Agent_OnLoad");
    onUnload = (void (*)(void *))GetProcAddress(instrument, "Agent_OnUnload");
    if (!onLoad) return fail("[pz3d Loader] instrument.dll has no Agent_OnLoad.\n");
    n = GetModuleFileNameW(self, source, MAX_PATH);
    if (!n || n >= MAX_PATH || !parent(source) || lstrlenW(source) > MAX_PATH-17)
        return fail("[pz3d Loader] Cannot locate pz3dLoader.jar.\n");
    lstrcatW(source, L"\\pz3dLoader.jar");
    if (!WideCharToMultiByte(CP_UTF8, 0, source, -1, jar, sizeof(jar), NULL, NULL))
        return fail("[pz3d Loader] Cannot encode the loader path.\n");
    return onLoad(vm, jar, reserved);
}
__declspec(dllexport) void Agent_OnUnload(void *vm) {
    if (onUnload) onUnload(vm);
    if (instrument) FreeLibrary(instrument);
}
