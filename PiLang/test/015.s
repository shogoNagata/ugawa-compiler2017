	.section .data
	@ 大域変数の定義
	.section .text
	.global _start
_start:
	@ main関数を呼出す．戻り値は r0 に入る
	bl main
	@ EXITシステムコール
	mov r7, #1
	swi #0
z:
	@ prologue
	str r11, [sp, #-4]!
	mov r11, sp
	str r14, [sp, #-4]!
	str r1, [sp, #-4]!
	sub sp, sp, #4
	ldr r0, =#1
	str r0, [r11, #-12]
	ldr r0, [r11, #-12]
	b L0
	mov r0, #0
L0:
	@ epilogue
	add sp, sp, #4
	ldr r1, [sp], #4
	ldr r14, [sp], #4
	ldr r11, [sp], #4
	bx r14
main:
	@ prologue
	str r11, [sp, #-4]!
	mov r11, sp
	str r14, [sp, #-4]!
	str r1, [sp, #-4]!
	sub sp, sp, #8
	ldr r0, =#100
	str r0, [r11, #-12]
	bl z
	add sp, sp, #0
	str r0, [r11, #-16]
	ldr r0, [r11, #-12]
	str r1, [sp, #-4]!
	mov r1, r0
	ldr r0, [r11, #-16]
	add r0, r1, r0
	ldr r1, [sp], #4
	b L1
	mov r0, #0
L1:
	@ epilogue
	add sp, sp, #8
	ldr r1, [sp], #4
	ldr r14, [sp], #4
	ldr r11, [sp], #4
	bx r14
