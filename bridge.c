#include <jni.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <pthread.h>
#include <unistd.h>
#include <sys/mman.h>
#ifndef NO_ALOG
#include <android/log.h>
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "tiny386", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "tiny386", __VA_ARGS__)
#else
#define LOGI(...) fprintf(stderr, __VA_ARGS__)
#define LOGE(...) fprintf(stderr, __VA_ARGS__)
#endif
#include "tiny386/pc.h"
#include "tiny386/i8042.h"

/* ---------- 平台 HAL ---------- */
uint32_t get_uticks(void) {
    struct timespec ts; clock_gettime(CLOCK_MONOTONIC, &ts);
    return (uint32_t)ts.tv_sec * 1000000u + (uint32_t)ts.tv_nsec / 1000u;
}
void *bigmalloc(size_t size) {
    void *p = mmap(NULL, size, PROT_READ | PROT_WRITE, MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
    return (p == MAP_FAILED) ? NULL : p;
}
void *pcmalloc(long size) { return malloc((size_t)size); }
int load_rom(void *phys_mem, const char *file, uword addr, int backward) {
    FILE *fp = fopen(file, "rb");
    if (!fp) { LOGE("load_rom open fail: %s", file); return -1; }
    fseek(fp, 0, SEEK_END); int len = (int)ftell(fp); rewind(fp);
    if (backward) fread((char *)phys_mem + addr - len, 1, len, fp);
    else          fread((char *)phys_mem + addr, 1, len, fp);
    fclose(fp); LOGI("load_rom %s len %d", file, len); return len;
}


/* ---------- 文字输出探针：看 BIOS 到底有没有在打印字符 ---------- */
static volatile long g_textops = 0, g_lastx = 0, g_lasty = 0;
static void my_set_cursor(void *o, int y, int x) { g_textops++; g_lastx = x; g_lasty = y; }
static void my_put_char2(void *o, int y, int x, int ch, int attr) { g_textops++; g_lastx = x; g_lasty = y; }
static void my_refresh(void *o, const uint8_t *ram, int w, int h) { g_textops++; }
static VGATextOps my_textops = { my_set_cursor, my_put_char2, my_refresh };

/* ---------- 状态 ---------- */
static PC *g_pc = NULL;
static uint8_t *g_fb = NULL;
static int g_w = 640, g_h = 480;
static volatile int g_dirty = 1, g_run = 0, g_stop = 0;
static volatile long g_steps = 0, g_refs = 0;
static void redraw_cb(void *o, int x, int y, int w, int h) { g_dirty = 1; g_refs++; }
static void *emu_loop(void *a) {
    g_run = 1;
    while (g_pc && g_pc->shutdown_state != 8 && !g_stop) { pc_step(g_pc); pc_vga_step(g_pc); g_steps++; }
    g_run = 0; return NULL;
}

JNIEXPORT jint JNICALL
Java_com_example_t386_MainActivity_nativeInit(JNIEnv *env, jclass c, jstring jini) {
    const char *ini = (*env)->GetStringUTFChars(env, jini, NULL);
    PCConfig conf; memset(&conf, 0, sizeof(conf));
    conf.mem_size = 16*1024*1024; conf.vga_mem_size = 1024*1024;
    conf.width = 640; conf.height = 480; conf.cpu_gen = 3; conf.fpu = 0;
    int err = ini_parse(ini, parse_conf_ini, &conf);
    (*env)->ReleaseStringUTFChars(env, jini, ini);
    if (err) { LOGE("ini err %d", err); return -1; }
    g_w = conf.width; g_h = conf.height;
    g_fb = (uint8_t *)bigmalloc((size_t)g_w * g_h * 4);
    if (!g_fb) return -2;
    memset(g_fb, 0x20, (size_t)g_w * g_h * 4);
    g_pc = pc_new(redraw_cb, NULL, g_fb, &conf);
    if (!g_pc) return -3;
    load_bios_and_reset(g_pc);
    g_pc->boot_start_time = get_uticks();
    vga_set_text_ops(g_pc->vga, &my_textops, NULL);   /* 挂文字探针 */
    pthread_t th;
    if (pthread_create(&th, NULL, emu_loop, NULL) != 0) return -4;
    pthread_detach(th);
    LOGI("init ok %dx%d mem=%ld", g_w, g_h, conf.mem_size);
    return 0;
}
JNIEXPORT void JNICALL Java_com_example_t386_MainActivity_nativeStop(JNIEnv *env, jclass c) {
    g_stop = 1;
    int i;
    for (i = 0; i < 300 && g_run; i++) usleep(5000);   /* 最多等 1.5 秒 */
    g_pc = NULL; g_fb = NULL;
    g_stop = 0; g_run = 0; g_dirty = 1;
    g_steps = 0; g_refs = 0; g_textops = 0;
}

JNIEXPORT jint JNICALL Java_com_example_t386_MainActivity_nativeWidth(JNIEnv *e, jclass c) { return g_w; }
JNIEXPORT jint JNICALL Java_com_example_t386_MainActivity_nativeHeight(JNIEnv *e, jclass c) { return g_h; }
JNIEXPORT jboolean JNICALL Java_com_example_t386_MainActivity_nativeTakeDirty(JNIEnv *e, jclass c) {
    int d = g_dirty; g_dirty = 0; return d ? JNI_TRUE : JNI_FALSE;
}
JNIEXPORT jobject JNICALL Java_com_example_t386_MainActivity_nativeFb(JNIEnv *env, jclass c) {
    if (!g_fb) return NULL;
    return (*env)->NewDirectByteBuffer(env, g_fb, (jlong)g_w * g_h * 4);
}
JNIEXPORT void JNICALL Java_com_example_t386_MainActivity_nativeKey(JNIEnv *e, jclass c, jint scan, jboolean down) {
    if (g_pc && g_pc->kbd) ps2_put_keycode(g_pc->kbd, down ? 1 : 0, scan);
}

JNIEXPORT jstring JNICALL Java_com_example_t386_MainActivity_nativeStats(JNIEnv *env, jclass c) {
    char buf[420];
    int nz = 0, i, n = g_w * g_h * 4;
    if (g_fb) for (i = 0; i + 3 < n; i += 4) {
        unsigned int px = g_fb[i] | (g_fb[i+1] << 8) | (g_fb[i+2] << 16);
        if (px) nz++;
    }
    unsigned int cs = 0, ip = 0, sign = 0;
    if (g_pc && g_pc->cpu) { CPUI386_State st; cpui386_get_state(g_pc->cpu, &st); cs = st.seg[1]; ip = st.ip; }
    if (g_pc && g_pc->phys_mem)
        sign = ((unsigned char)g_pc->phys_mem[0x7DFE] << 8) | (unsigned char)g_pc->phys_mem[0x7DFF];

    /* VGA 显存探针 */
    unsigned int v0 = 0; int vnz = 0;
    if (g_pc && g_pc->vga_mem) {
        v0 = ((unsigned char)g_pc->vga_mem[0] << 24) | ((unsigned char)g_pc->vga_mem[1] << 16)
           | ((unsigned char)g_pc->vga_mem[2] << 8) | (unsigned char)g_pc->vga_mem[3];
        for (i = 0; i < 32768 && i < g_pc->vga_mem_size; i++)
            if (g_pc->vga_mem[i]) vnz++;
    }
    /* BIOS 数据区 */
    unsigned int mode = 0, cur = 0;
    if (g_pc && g_pc->phys_mem) {
        mode = (unsigned char)g_pc->phys_mem[0x449];
        cur  = ((unsigned char)g_pc->phys_mem[0x450]) | ((unsigned char)g_pc->phys_mem[0x451] << 8);
    }
    /* ROM 探针：VGA ROM 在 0xC0000，系统 BIOS 在 0xF0000，复位向量在 0xFFFF0 */
    unsigned int romc0 = 0, romf0 = 0, vec = 0;
    if (g_pc && g_pc->phys_mem) {
        romc0 = ((unsigned char)g_pc->phys_mem[0xC0000] << 8) | (unsigned char)g_pc->phys_mem[0xC0001];
        romf0 = ((unsigned char)g_pc->phys_mem[0xF0000] << 8) | (unsigned char)g_pc->phys_mem[0xF0001];
        vec   = ((unsigned char)g_pc->phys_mem[0xFFFF0] << 8) | (unsigned char)g_pc->phys_mem[0xFFFF1];
    }
    snprintf(buf, sizeof(buf),
        "steps=%ld nzpx=%d txtops=%ld\nrom: C0000=%04x F0000=%04x VEC=%04x\nvm=%08x vnz=%d mode=%02x cur=%04x",
        g_steps, nz, g_textops, romc0, romf0, vec, v0, vnz, mode, cur);
    return (*env)->NewStringUTF(env, buf);
}
