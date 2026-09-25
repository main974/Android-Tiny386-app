#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include "pc.h"

static long g_redraws = 0;
static long long g_pixels = 0;
static void my_redraw(void *opaque, int x, int y, int w, int h) {
    g_redraws++;
    g_pixels += (long long)w * h;
}

static double now_s(void) {
    struct timespec t; clock_gettime(CLOCK_MONOTONIC, &t);
    return t.tv_sec + t.tv_nsec / 1e9;
}

static PCConfig g_conf;
static void *g_fb = NULL;

static PC *mk(void) {
    if (!g_fb) g_fb = calloc(1, (size_t)g_conf.width * g_conf.height * 4);
    PC *pc = pc_new(my_redraw, NULL, (u8 *)g_fb, &g_conf);
    load_bios_and_reset(pc);
    return pc;
}

int main(int argc, char **argv) {
    long N = (argc > 1) ? atol(argv[1]) : 20000;
    const char *app = getenv("APP");
    if (!app) app = ".";

    char bios[600], vga[600], cd[600];
    snprintf(bios, sizeof bios, "%s/assets/bios.bin", app);
    snprintf(vga,  sizeof vga,  "%s/assets/vgabios.bin", app);
    snprintf(cd,   sizeof cd,   "/sdcard/Download/fd11src.iso");

    memset(&g_conf, 0, sizeof(g_conf));
    g_conf.bios = bios;
    g_conf.vga_bios = vga;
    g_conf.mem_size = 16 * 1024 * 1024;
    g_conf.vga_mem_size = 1024 * 1024;
    g_conf.width = 640; g_conf.height = 480;
    g_conf.cpu_gen = 3; g_conf.fpu = 0;
    g_conf.disks[1] = cd; g_conf.iscd[1] = 1;   /* 光驱挂槽位1 */

    double t0, t1;
    PC *pc;
    printf("  N = %ld 步\n\n", N);

    /* A：只跑 CPU */
    pc = mk(); t0 = now_s();
    for (long i = 0; i < N; i++) pc_step(pc);
    t1 = now_s();
    printf("  A. 只 pc_step()        : %9.0f 步/秒   (%.2fs)\n", N/(t1-t0), t1-t0);

    /* B：只跑 VGA */
    pc = mk(); t0 = now_s();
    for (long i = 0; i < N; i++) pc_vga_step(pc);
    t1 = now_s();
    printf("  B. 只 pc_vga_step()    : %9.0f 步/秒   (%.2fs)\n", N/(t1-t0), t1-t0);

    /* C：真实循环 */
    g_redraws = 0; g_pixels = 0;
    pc = mk(); t0 = now_s();
    for (long i = 0; i < N; i++) { pc_step(pc); pc_vga_step(pc); }
    t1 = now_s();
    printf("  C. 两个一起(真实)      : %9.0f 步/秒   (%.2fs)\n", N/(t1-t0), t1-t0);
    printf("     └─ 期间 vga_refresh 被调用 %ld 次，共 %lld 像素\n", g_redraws, g_pixels);
    if (N > 0) printf("     └─ 平均每步重绘 %.3f 次（%.0f 像素/步）\n",
                      (double)g_redraws/N, (double)g_pixels/N);
    return 0;
}
