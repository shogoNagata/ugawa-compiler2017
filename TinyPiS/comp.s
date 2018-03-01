.section .data
	@ 大域変数の定義
_Pi_var_answer:
	.word 0
hex:
	.ascii "00000000\n"
	.equ hexlen, . - hex
	.section .text
	.global _start
_start:
	@ 式をコンパイルした命令列
	ldr r0, =#1
	str r1, [sp, #-4]!
	mov r1, r0
	ldr r0, =#10
	add r0, r1, r0
	ldr r1, [sp], #4
	bl print
	@ EXITシステムコール
	ldr r0, =_Pi_var_answer
	ldr r0, [r0, #0]
	mov r7, #1
	swi #0
print:
	str r0, [sp, #-4]!
	str r1, [sp, #-4]!
	str r2, [sp, #-4]!
	str r7, [sp, #-4]!
	ldr r7, =hex
	add r7, r7, #7
L0:
	cmp r0, #0
	beq L1
	mov r2, #16
	udiv r1, r0, r2
	mul r2, r1, r2
	sub r0, r0, r2
	cmp r0, #10
	bpl L2
	add r0, r0, #48
	b L3
L2:
	add r0, r0, #55
L3:
	strb r0, [r7], #-1
	mov r0, r1
	b L0
L1:
	mov r7, #4
	mov r0, #1
	ldr r1, =hex
	ldr r2, =hexlen
	swi #0
	mov r0, #48
	mov r2, #8
L4:
	cmp r2, #0
	beq L5
	sub r2, r2, #1
	strb r0, [r1, r2]
	b L4
L5:
	ldr r7, [sp], #4
	ldr r2, [sp], #4
	ldr r1, [sp], #4
	ldr r0, [sp], #4
	bx r14

