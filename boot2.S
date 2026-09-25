.code16
.text
.globl _start
_start:
    cli
    ljmp $0x07c0, $start
start:
    mov $0x07c0, %ax
    mov %ax, %ds
    mov %ax, %es
    mov %ax, %ss
    mov $0x1000, %sp
    cld
    sti

    /* 路径B：直接写显存 0xB8000 —— 先写，好定位 */
    mov $0xb800, %ax
    mov %ax, %es
    xor %di, %di
    mov $'A', %al
    mov $0x0f, %ah
    mov %ax, %es:(%di)
    mov $'B', %al
    mov $0x0f, %ah
    mov %ax, %es:2(%di)
    mov $'C', %al
    mov $0x0f, %ah
    mov %ax, %es:4(%di)

    /* 路径A：用 BIOS int 0x10 打印 XYZ */
    mov $0x07c0, %ax
    mov %ax, %ds
    mov $0x0e, %ah
    mov $0x0007, %bx
    mov $'X', %al
    int $0x10
    mov $'Y', %al
    int $0x10
    mov $'Z', %al
    int $0x10

2:  cli
    hlt
    jmp 2b
    .org 510
    .word 0xaa55
