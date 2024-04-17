#include <stdio.h>
#include <riscv-pk/encoding.h>
#include "rocc.h"

int main(void) {
    uint32_t addr;
    uint32_t wdata = 100;
    uint32_t rdata;

    // asm volatile ("fence");

    printf("Start writting\r\n");
    // Write data
    for (addr = 0; addr < 64; addr++) {
        printf("Write address[%d] <-- %d\r\n", addr, wdata);
        ROCC_INSTRUCTION_DSS(1, rdata, addr, wdata, 0);
        wdata++;
    }

    printf("Start reading\r\n");
    // Read data
    for (addr = 0; addr < 64; addr++) {
        ROCC_INSTRUCTION_DSS(0, rdata, addr, 0, 0);
        printf("Read [%d]: %d\r\n", addr, rdata);
    }

    return 0;
}